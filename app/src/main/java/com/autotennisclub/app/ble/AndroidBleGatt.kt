package com.autotennisclub.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.autotennisclub.app.pusun.PusunBleConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Real BLE transport. The first [connect] scans for a device advertising the
 * PUSUN service; later reconnects reuse that device.
 * Requires BLUETOOTH_SCAN / BLUETOOTH_CONNECT to be granted on Android 12+.
 */
@SuppressLint("MissingPermission")
class AndroidBleGatt(
    context: Context,
    private val adapter: BluetoothAdapter?,
    private val scanTimeoutMillis: Long = 10_000,
    private val connectTimeoutMillis: Long = 10_000
) : BleGatt {
    private val appContext = context.applicationContext

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val notifications: SharedFlow<ByteArray> = _notifications.asSharedFlow()

    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var pendingReady: CompletableDeferred<Boolean>? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    override suspend fun connect(): Boolean {
        if (_connected.value) return true
        val bluetooth = adapter?.takeIf { it.isEnabled } ?: return false
        return try {
            val target = device ?: findMachine(bluetooth) ?: return false
            device = target

            val ready = CompletableDeferred<Boolean>()
            pendingReady = ready
            withContext(Dispatchers.Main) {
                gatt?.close()
                gatt = target.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            }
            val ok = withTimeoutOrNull(connectTimeoutMillis) { ready.await() } ?: false
            if (!ok) closeGatt()
            ok
        } catch (e: SecurityException) {
            false
        }
    }

    private suspend fun findMachine(bluetooth: BluetoothAdapter): BluetoothDevice? {
        val scanner = BleScanner(bluetooth)
        val found = CompletableDeferred<BluetoothDevice>()
        return try {
            scanner.start { found.complete(it) }
            withTimeoutOrNull(scanTimeoutMillis) { found.await() }
        } finally {
            scanner.stop()
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = false
                pendingReady?.complete(false)
                pendingWrite?.complete(false)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !enableNotifications(g)) {
                pendingReady?.complete(false)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                val ok = status == BluetoothGatt.GATT_SUCCESS
                _connected.value = ok
                pendingReady?.complete(ok)
            }
        }

        @Deprecated("Used on Android 12 and lower")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            if (characteristic.uuid == PusunBleConfig.NOTIFY_UUID) {
                _notifications.tryEmit(characteristic.value.copyOf())
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == PusunBleConfig.NOTIFY_UUID) {
                _notifications.tryEmit(value.copyOf())
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingWrite = null
        }
    }

    private fun enableNotifications(g: BluetoothGatt): Boolean {
        val characteristic = g.getService(PusunBleConfig.SERVICE_UUID)
            ?.getCharacteristic(PusunBleConfig.NOTIFY_UUID) ?: return false
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    override suspend fun write(characteristic: UUID, data: ByteArray) {
        require(data.size <= PusunBleConfig.MAX_WRITE_BYTES)
        val g = gatt?.takeIf { _connected.value } ?: error("BLE is not connected")
        val c = g.getService(PusunBleConfig.SERVICE_UUID)
            ?.getCharacteristic(characteristic) ?: error("Characteristic not found: $characteristic")

        withContext(Dispatchers.Main.immediate) {
            val result = CompletableDeferred<Boolean>()
            pendingWrite = result

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                c.value = data
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }

            check(result.await()) { "BLE characteristic write failed" }
        }
    }

    override fun disconnect() {
        gatt?.disconnect()
        closeGatt()
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        _connected.value = false
        pendingWrite?.cancel()
        pendingWrite = null
    }

    companion object {
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
