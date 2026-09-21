package com.autotennisclub.app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.pusun.PusunNotificationParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleManager(
    context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val onNotification: (ByteArray) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<MachineState>(MachineState.Disconnected)
    val state: StateFlow<MachineState> = _state.asStateFlow()

    private var transport: AndroidBleGatt? = null

    fun connect(device: BluetoothDevice) {
        transport?.disconnect()
        _state.value = MachineState.Connecting

        transport = AndroidBleGatt(
            context = appContext,
            device = device,
            onNotification = {
                onNotification(it)
            },
            onConnectionChanged = { connected ->
                _state.value = if (connected) MachineState.Connected else MachineState.Disconnected
            }
        ).also { it.connect() }
    }

    fun disconnect() {
        transport?.disconnect()
        transport = null
        _state.value = MachineState.Disconnected
    }
}