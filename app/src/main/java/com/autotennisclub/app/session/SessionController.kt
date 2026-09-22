package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.machine.TennisMachine
import com.autotennisclub.app.pusun.SpinType
import com.autotennisclub.app.pusun.StartMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionController(
    private val scope: CoroutineScope,
    private val machine: TennisMachine,
    private val timer: TrainingTimer = TrainingTimer(scope),
    private val countdownSeconds: Int = 5
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var activeMinutes: Int = 0

    init {
        scope.launch {
            machine.state.collectLatest { machineState ->
                if (machineState is MachineState.Fault &&
                    _state.value !is SessionState.Complete
                ) {
                    timer.stop()
                    countdownJob?.cancel()
                    _state.value = SessionState.Idle
                }
            }
        }

        scope.launch {
            timer.remainingSeconds.collectLatest { remaining ->
                if (_state.value is SessionState.Running) {
                    _state.value = SessionState.Running(remaining)
                }
            }
        }
    }

    fun selectDuration(minutes: Int) {
        require(minutes in setOf(15, 30, 60)) { "Unsupported MVP duration: $minutes" }
        if (_state.value is SessionState.Idle || _state.value is SessionState.Selected) {
            _state.value = SessionState.Selected(minutes)
        }
    }

    fun startSelected(
        velocity: Int = 80,
        frequencyGrade: Int = 30,
        spin: SpinType = SpinType.TOPSPIN,
        spinValue: Int = 10,
        mode: StartMode = StartMode.FIXED
    ) {
        val selected = _state.value as? SessionState.Selected ?: return
        activeMinutes = selected.minutes
        countdownJob?.cancel()
        countdownJob = scope.launch {
            _state.value = SessionState.Preparing
            machine.configure(velocity, frequencyGrade, spin, spinValue)

            for (second in countdownSeconds downTo 1) {
                _state.value = SessionState.Countdown(second)
                delay(1000)
            }

            machine.start(mode)
            timer.start(selected.minutes) {
                scope.launch {
                    machine.stop()
                    _state.value = SessionState.Complete(activeMinutes)
                }
            }
            _state.value = SessionState.Running(selected.minutes * 60L)
        }
    }

    fun stopSession() {
        countdownJob?.cancel()
        countdownJob = null
        timer.stop()
        scope.launch {
            machine.stop()
            _state.value = SessionState.Complete(activeMinutes)
        }
    }

    fun reset() {
        countdownJob?.cancel()
        countdownJob = null
        timer.stop()
        _state.value = SessionState.Idle
    }

    fun dispose() {
        countdownJob?.cancel()
        timer.stop()
    }
}
