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

    private var sessionJob: Job? = null
    private var paymentJob: Job? = null

    init {
        scope.launch {
            machine.state.collectLatest { machineState ->
                if (machineState is MachineState.Fault &&
                    _state.value !is SessionState.Complete
                ) {
                    timer.stop()
                    sessionJob?.cancel()
                    paymentJob?.cancel()
                    _state.value = SessionState.Idle
                }
            }
        }

        scope.launch {
            timer.remainingSeconds.collectLatest { remaining ->
                val current = _state.value
                if (current is SessionState.Running) {
                    _state.value = current.copy(remainingSeconds = remaining)
                }
            }
        }
    }

    fun start() {
        reset()
        _state.value = SessionState.TrainingSelection
    }

    fun selectTraining(mode: TrainingMode) {
        _state.value = SessionState.DurationSelection(mode)
    }

    fun selectDuration(minutes: Int) {
        require(minutes in setOf(15, 30, 60)) { "Unsupported MVP duration: $minutes" }
        val current = _state.value as? SessionState.DurationSelection ?: return
        if (current.mode == TrainingMode.CUSTOM) {
            _state.value = SessionState.CustomConfigState(minutes)
        } else {
            _state.value = SessionState.Summary(
                mode = current.mode,
                minutes = minutes,
                price = priceFor(minutes)
            )
        }
    }

    fun updateCustomConfig(config: CustomConfig) {
        val current = _state.value as? SessionState.CustomConfigState ?: return
        _state.value = current.copy(config = config)
    }

    fun confirmCustomConfig() {
        val current = _state.value as? SessionState.CustomConfigState ?: return
        _state.value = SessionState.Summary(
            mode = TrainingMode.CUSTOM,
            minutes = current.minutes,
            price = priceFor(current.minutes),
            config = current.config
        )
    }

    fun proceedToPayment() {
        when (val current = _state.value) {
            is SessionState.Summary -> {
                _state.value = SessionState.Payment(
                    mode = current.mode,
                    minutes = current.minutes,
                    price = current.price,
                    config = current.config
                )
            }
            else -> Unit
        }
    }

    fun simulatePayment() {
        val current = _state.value as? SessionState.Payment ?: return
        if (current.status != PaymentStatus.WAITING) return

        _state.value = current.copy(status = PaymentStatus.PROCESSING)
        paymentJob?.cancel()
        paymentJob = scope.launch {
            delay(900)
            val latest = _state.value as? SessionState.Payment ?: return@launch
            _state.value = latest.copy(status = PaymentStatus.SUCCESS)
        }
    }

    fun simulatePaymentFailure() {
        val current = _state.value as? SessionState.Payment ?: return
        _state.value = current.copy(status = PaymentStatus.FAILED)
    }

    fun retryPayment() {
        val current = _state.value as? SessionState.Payment ?: return
        _state.value = current.copy(status = PaymentStatus.WAITING)
    }

    fun startPaidSession() {
        val payment = _state.value as? SessionState.Payment ?: return
        if (payment.status != PaymentStatus.SUCCESS) return

        sessionJob?.cancel()
        sessionJob = scope.launch {
            _state.value = SessionState.Preparing

            val config = payment.config ?: CustomConfig()
            val spin = when (config.spin) {
                "BACKSPIN" -> SpinType.BACKSPIN
                "NO SPIN" -> SpinType.NONE
                else -> SpinType.TOPSPIN
            }
            machine.configure(
                velocity = config.velocity,
                frequencyGrade = config.frequencyGrade,
                spin = spin,
                spinValue = config.spinValue
            )

            for (second in countdownSeconds downTo 1) {
                _state.value = SessionState.Countdown(second)
                delay(1000)
            }

            val startMode = when (payment.mode) {
                TrainingMode.BASIC -> StartMode.FIXED
                TrainingMode.TRAINING -> StartMode.PROGRAM
                TrainingMode.CUSTOM -> StartMode.PROGRAM
            }
            machine.start(startMode)
            timer.start(payment.minutes) {
                scope.launch {
                    machine.stop()
                    _state.value = SessionState.Complete
                }
            }
            _state.value = SessionState.Running(payment.minutes * 60L, payment.mode)
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        timer.stop()
        scope.launch {
            machine.stop()
            _state.value = SessionState.Complete
        }
    }

    fun reset() {
        sessionJob?.cancel()
        sessionJob = null
        paymentJob?.cancel()
        paymentJob = null
        timer.stop()
        _state.value = SessionState.Idle
    }

    fun dispose() {
        sessionJob?.cancel()
        paymentJob?.cancel()
        timer.stop()
    }

    private fun priceFor(minutes: Int): Double = when (minutes) {
        15 -> 6.90
        30 -> 12.00
        60 -> 20.00
        else -> error("Unsupported duration")
    }
}
