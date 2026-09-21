package com.autotennisclub.app.machine

import com.autotennisclub.app.ble.BleGatt
import com.autotennisclub.app.pusun.PusunCommand
import com.autotennisclub.app.pusun.PusunCommandQueue
import com.autotennisclub.app.pusun.PusunNotification
import com.autotennisclub.app.pusun.PusunNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MachineController(
    scope: CoroutineScope,
    gatt: BleGatt
) {
    private val queue = PusunCommandQueue(scope, gatt)
    private val _state = MutableStateFlow<MachineState>(MachineState.Disconnected)
    val state: StateFlow<MachineState> = _state.asStateFlow()

    suspend fun configure(
        velocity: Int,
        frequencyGrade: Int,
        spin: Int = 0,
        spinValue: Int = 0
    ) {
        _state.value = MachineState.Configuring
        queue.enqueue(PusunCommand.SetVelocity(velocity))
        queue.enqueue(PusunCommand.SetFrequencyGrade(frequencyGrade))
        queue.enqueue(
            PusunCommand.SetSpin(
                type = when (spin) { 1 -> com.autotennisclub.app.pusun.SpinType.TOPSPIN; 2 -> com.autotennisclub.app.pusun.SpinType.BACKSPIN; else -> com.autennisclub.app.pusun.SpinType.NONE },
                value = spinValue
            )
        )
        _state.value = MachineState.Ready
    }

    suspend fun start(mode: com.autotennisclub.app.pusun.StartMode) {
        queue.enqueue(PusunCommand.Start(mode))
        _state.value = MachineState.Running
    }

    suspend fun stop() {
        queue.enqueue(PusunCommand.Stop)
        _state.value = MachineState.Ready
    }

    fun onNotification(frame: ByteArray) {
        when (val notification = PusunNotificationParser.parse(frame)) {
            is PusunNotification.Fault ->
                _state.value = MachineState.Fault(notification.code, notification.type.name)
            else -> Unit
        }
    }

    fun disconnect() {
        _state.value = MachineState.Disconnected
    }
}