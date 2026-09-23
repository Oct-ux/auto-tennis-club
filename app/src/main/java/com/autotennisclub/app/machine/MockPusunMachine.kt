package com.autotennisclub.app.machine

import com.autotennisclub.app.ble.MockBleGatt
import com.autotennisclub.app.pusun.PusunCommand
import com.autotennisclub.app.pusun.PusunCommandQueue
import com.autotennisclub.app.pusun.PusunFrameBuilder
import com.autotennisclub.app.pusun.SpinType
import com.autotennisclub.app.pusun.StartMode
import com.autotennisclub.app.pusun.PusunBleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Software-only PUSUN simulator.
 *
 * It uses the same command queue/frame builder as the real machine, so the
 * session flow can be tested before the physical MAX B is available.
 */
class MockPusunMachine(
    scope: CoroutineScope
) : TennisMachine {
    private val gatt = MockBleGatt()
    private val queue = PusunCommandQueue(scope, gatt)

    private val _state = MutableStateFlow<MachineState>(MachineState.Disconnected)
    override val state: StateFlow<MachineState> = _state.asStateFlow()

    var lastVelocity: Int = 0
        private set
    var lastFrequencyGrade: Int = 0
        private set
    var lastSpin: SpinType = SpinType.NONE
        private set
    var lastSpinValue: Int = 0
        private set
    var lastStartMode: StartMode? = null
        private set

    init {
        _state.value = MachineState.Connected
    }

    override suspend fun configure(
        velocity: Int,
        frequencyGrade: Int,
        spin: SpinType,
        spinValue: Int
    ) {
        _state.value = MachineState.Configuring
        lastVelocity = velocity
        lastFrequencyGrade = frequencyGrade
        lastSpin = spin
        lastSpinValue = spinValue

        queue.enqueue(PusunCommand.SetVelocity(velocity))
        queue.enqueue(PusunCommand.SetFrequencyGrade(frequencyGrade))
        queue.enqueue(PusunCommand.SetSpin(spin, spinValue))
        _state.value = MachineState.Ready
    }

    override suspend fun start(mode: StartMode) {
        lastStartMode = mode
        queue.enqueue(PusunCommand.Start(mode))
        _state.value = MachineState.Running
    }

    override suspend fun stop() {
        queue.enqueue(PusunCommand.Stop)
        _state.value = MachineState.Ready
    }

    fun writtenFrames(): List<ByteArray> = gatt.writes.map { it.data.copyOf() }

    fun simulateFault(code: Int) {
        val message = when (code) {
            1 -> "WHEEL_PROTECTION"
            2 -> "ENTRANCE_PROTECTION"
            3 -> {
                _state.value = MachineState.OutOfBalls
                return
            }
            else -> "UNKNOWN"
        }
        _state.value = MachineState.Fault(code, message)
    }

    fun simulateConnectionLost(attempt: Int = 1) {
        _state.value = MachineState.Reconnecting(attempt)
    }

    fun simulateReconnected() {
        _state.value = MachineState.Ready
    }

    fun simulateConnectionFailed() {
        _state.value = MachineState.ConnectionFailed
    }

    fun isUsingPusunWriteCharacteristic(): Boolean =
        gatt.writes.all { it.characteristic == PusunBleConfig.WRITE_UUID }

    fun shutdown() {
        queue.close()
        gatt.disconnect()
        _state.value = MachineState.Disconnected
    }
}
