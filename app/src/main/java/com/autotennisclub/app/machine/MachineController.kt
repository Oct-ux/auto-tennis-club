package com.autotennisclub.app.machine

import com.autotennisclub.app.ble.BleGatt
import com.autotennisclub.app.pusun.FaultType
import com.autotennisclub.app.pusun.PusunCommand
import com.autotennisclub.app.pusun.PusunCommandQueue
import com.autotennisclub.app.pusun.PusunNotification
import com.autotennisclub.app.pusun.PusunNotificationParser
import com.autotennisclub.app.pusun.SpinType
import com.autotennisclub.app.pusun.StartMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MachineController(scope: CoroutineScope, gatt: BleGatt) {
    private val queue = PusunCommandQueue(scope, gatt)
    private val _state = MutableStateFlow<MachineState>(MachineState.Disconnected)
    val state: StateFlow<MachineState> = _state.asStateFlow()

    fun connected() { _state.value = MachineState.Connected }

    suspend fun configure(velocity: Int, frequencyGrade: Int, spin: SpinType = SpinType.NONE, spinValue: Int = 0) {
        _state.value = MachineState.Configuring
        queue.enqueue(PusunCommand.SetVelocity(velocity))
        queue.enqueue(PusunCommand.SetFrequencyGrade(frequencyGrade))
        queue.enqueue(PusunCommand.SetSpin(spin, spinValue))
        _state.value = MachineState.Ready
    }

    suspend fun start(mode: StartMode) {
        queue.enqueue(PusunCommand.Start(mode))
        _state.value = MachineState.Running
    }

    suspend fun stop() {
        queue.enqueue(PusunCommand.Stop)
        _state.value = MachineState.Ready
    }

    fun onNotification(frame: ByteArray) {
        val notification = PusunNotificationParser.parse(frame)
        if (notification is PusunNotification.Fault) {
            _state.value = MachineState.Fault(notification.code, notification.type.name)
        }
    }

    fun disconnected() { _state.value = MachineState.Disconnected }
}