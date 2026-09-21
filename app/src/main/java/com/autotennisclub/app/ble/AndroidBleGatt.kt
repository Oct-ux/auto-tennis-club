package com.autotennisclub.app.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.autotennisclub.app.pusun.PusunBleConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AndroidBleGatt(
    private val context: Context,
    private val device: BluetoothDevice,
    private val onNotification: (ByteArray) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) : BleGatt {

    private var gatt: BluetoothGatt? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    fun connect() {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onConnectionChanged(true)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onConnectionChanged(false)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotifications(g)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == PusunBleConfig.NOTIFY_UUID) {
                onNotification(characteristic.value.copyOf())
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == PusunBleConfig.NOTIFY_UUID) {
                onNotification(value.copyOf())
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID)) {
                // Notification setup completed.
            }
        }
    }

    private fun enableNotifications(g: BluetoothGatt) {
        val characteristic = g.getService(PusunBleConfig.SERVICE_UUID)
            ?.getCharacteristic(PusunBleConfig.NOTIFY_UUID) ?: return

        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(
            UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        ) ?: return

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(descriptor)
    }

    override suspend fun write(characteristic: UUID, data: ByteArray) {
        require(data.size <= PusunBleConfig.MAX_WRITE_BYTES)
        val g = gatt ?: error("BLE is not connected")
        val c = g.getService(PusunBleConfig.SERVICE_UUID)
            ?.getCharacteristic(characteristic) ?: error("Characteristic not found: $characteristic")

        withContext(Dispatchers.Main.immediate) {
            val result = CompletableDeferred<Boolean>()
            pendingWrite = result

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    c,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }

            check(result.await()) { "BLE characteristic write failed" }
        }
    }

    override fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        pendingWrite?.cancel()
        pendingWrite = null
    }

    companion object {
        const val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            "00002902-0000-1000-8000-00805F9B34FB"
    }
}