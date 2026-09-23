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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PUSUN MAX B over BLE. Owns the link: connects, and when the link drops
 * unexpectedly retries [maxReconnectAttempts] times (Figma S05) before
 * reporting ConnectionFailed.
 */
class MachineController(
    private val scope: CoroutineScope,
    private val gatt: BleGatt,
    private val maxReconnectAttempts: Int = 5,
    private val reconnectDelayMillis: Long = 2_000,
    private val clock: () -> Long = System::currentTimeMillis
) : TennisMachine {
    private val queue = PusunCommandQueue(scope, gatt)

    private val _state = MutableStateFlow<MachineState>(MachineState.Disconnected)
    override val state: StateFlow<MachineState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(MachineDiagnostics())
    override val diagnostics: StateFlow<MachineDiagnostics> = _diagnostics.asStateFlow()

    private var wantConnected = false
    private var reconnectJob: Job? = null

    init {
        scope.launch { gatt.notifications.collect(::onNotification) }
        scope.launch {
            gatt.connected.collect { up ->
                if (!up && wantConnected && isLinked(_state.value)) reconnect()
            }
        }
    }

    override suspend fun connect() {
        wantConnected = true
        val current = _state.value
        if (current != MachineState.Disconnected && current != MachineState.ConnectionFailed) return
        _state.value = MachineState.Connecting
        _state.value = if (gatt.connect()) MachineState.Connected else MachineState.ConnectionFailed
    }

    override suspend fun configure(
        velocity: Int,
        frequencyGrade: Int,
        spin: SpinType,
        spinValue: Int
    ) {
        _state.value = MachineState.Configuring
        queue.enqueue(PusunCommand.SetVelocity(velocity))
        queue.enqueue(PusunCommand.SetFrequencyGrade(frequencyGrade))
        queue.enqueue(PusunCommand.SetSpin(spin, spinValue))
        _state.value = MachineState.Ready
    }

    override suspend fun start(mode: StartMode) {
        queue.enqueue(PusunCommand.Start(mode))
        _state.value = MachineState.Running
    }

    override suspend fun stop() {
        queue.enqueue(PusunCommand.Stop)
        _state.value = MachineState.Ready
    }

    override suspend fun reset() {
        queue.enqueue(PusunCommand.Stop)
        _state.value = if (gatt.connected.value) MachineState.Connected else MachineState.Disconnected
    }

    fun shutdown() {
        wantConnected = false
        reconnectJob?.cancel()
        queue.close()
        gatt.disconnect()
        _state.value = MachineState.Disconnected
    }

    private fun reconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            for (attempt in 1..maxReconnectAttempts) {
                _state.value = MachineState.Reconnecting(attempt)
                delay(reconnectDelayMillis)
                if (gatt.connect()) {
                    _state.value = MachineState.Ready
                    return@launch
                }
            }
            _state.value = MachineState.ConnectionFailed
        }
    }

    private fun onNotification(frame: ByteArray) {
        val notification = PusunNotificationParser.parse(frame) ?: return
        _diagnostics.update { it.copy(lastResponseAtMillis = clock()) }
        when (notification) {
            is PusunNotification.Fault -> {
                _diagnostics.update { it.copy(lastFault = notification.type.name) }
                _state.value = if (notification.type == FaultType.NO_BALLS) {
                    MachineState.OutOfBalls
                } else {
                    MachineState.Fault(notification.code, notification.type.name)
                }
            }
            is PusunNotification.Battery ->
                _diagnostics.update { it.copy(batteryPercent = notification.percentage) }
            is PusunNotification.Unknown -> Unit
        }
    }

    private fun isLinked(state: MachineState): Boolean = when (state) {
        MachineState.Disconnected,
        MachineState.Connecting,
        MachineState.ConnectionFailed,
        is MachineState.Reconnecting -> false
        else -> true
    }
}
