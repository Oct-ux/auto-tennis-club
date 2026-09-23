package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.machine.TennisMachine
import com.autotennisclub.app.payment.PaymentGateway
import com.autotennisclub.app.payment.PaymentResult
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
import java.util.UUID
import kotlin.math.roundToLong

class SessionController(
    private val scope: CoroutineScope,
    private val machine: TennisMachine,
    private val payments: PaymentGateway,
    private val timer: TrainingTimer = TrainingTimer(scope),
    private val countdownSeconds: Int = 5,
    private val selfCheckMillis: Long = 1500,
    private val recoveryMillis: Long = 1500
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.StartingUp)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var paymentJob: Job? = null
    private var activeMinutes: Int = 0
    private var activeMode: TrainingMode = TrainingMode.BASIC
    private var activeStartMode: StartMode = StartMode.FIXED

    init {
        scope.launch {
            machine.state.collect(::onMachineState)
        }

        scope.launch {
            timer.remainingSeconds.collectLatest { remaining ->
                val current = _state.value
                if (current is SessionState.Running) {
                    _state.value = current.copy(remainingSeconds = remaining)
                }
            }
        }

        runSelfCheck()
    }

    /** S01 → Idle, or S02 when the machine cannot take customers. */
    fun runSelfCheck() {
        cancelJobs()
        timer.stop()
        _state.value = SessionState.StartingUp
        sessionJob = scope.launch {
            delay(selfCheckMillis)
            _state.value = when (machine.state.value) {
                is MachineState.Fault -> SessionState.Unavailable(UnavailableReason.MACHINE_FAULT)
                MachineState.OutOfBalls -> SessionState.Unavailable(UnavailableReason.OUT_OF_BALLS)
                MachineState.Disconnected,
                MachineState.Connecting,
                MachineState.ConnectionFailed,
                is MachineState.Reconnecting -> SessionState.Unavailable(UnavailableReason.MACHINE_OFFLINE)
                else -> SessionState.Idle
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

    /** WAITING → PROCESSING → VERIFYING (S08) → SUCCESS, or FAILED / CANCELLED (S07) / TIMEOUT (S06). */
    fun pay() {
        val current = _state.value as? SessionState.Payment ?: return
        if (current.status != PaymentState.WAITING) return

        _state.value = current.copy(status = PaymentState.PROCESSING)
        paymentJob?.cancel()
        paymentJob = scope.launch {
            val reference = UUID.randomUUID().toString()
            when (val result = payments.startPayment((current.price * 100).roundToLong(), reference)) {
                is PaymentResult.Success -> {
                    setPaymentState(PaymentState.VERIFYING)
                    // No timeout here: the customer may already be charged, so S08
                    // keeps saying "do not pay again" until the provider answers.
                    val verified = payments.verify(result.transactionId)
                    setPaymentState(if (verified) PaymentState.SUCCESS else PaymentState.FAILED)
                }
                is PaymentResult.Failed -> setPaymentState(PaymentState.FAILED)
                PaymentResult.Cancelled -> setPaymentState(PaymentState.CANCELLED)
                PaymentResult.Timeout -> setPaymentState(PaymentState.TIMEOUT)
            }
        }
    }

    /** S07 — customer backs out before paying. Once the terminal is processing, only it can cancel. */
    fun cancelPayment() {
        val current = _state.value as? SessionState.Payment ?: return
        if (current.status != PaymentState.WAITING) return
        _state.value = current.copy(status = PaymentState.CANCELLED)
    }

    fun retryPayment() {
        val current = _state.value as? SessionState.Payment ?: return
        if (!current.status.canRetry) return
        _state.value = current.copy(status = PaymentState.WAITING)
    }

    fun startPaidSession() {
        val payment = _state.value as? SessionState.Payment ?: return
        if (payment.status != PaymentState.SUCCESS) return
        activeMinutes = payment.minutes
        activeMode = payment.mode
        activeStartMode = when (payment.mode) {
            TrainingMode.BASIC -> StartMode.FIXED
            TrainingMode.TRAINING -> StartMode.PROGRAM
            TrainingMode.CUSTOM -> StartMode.PROGRAM
        }

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

            machine.start(activeStartMode)
            timer.start(payment.minutes) {
                scope.launch {
                    machine.stop()
                    _state.value = SessionState.Complete(activeMinutes, activeMode)
                }
            }
            _state.value = SessionState.Running(payment.minutes * 60L, payment.mode)
        }
    }

    /** S04 action: "I'VE RETURNED THE BALLS". */
    fun confirmBallsReturned() {
        val current = _state.value as? SessionState.BallsRequired ?: return
        resumeSession(current.remainingSeconds, current.mode)
    }

    fun stopSession() {
        cancelJobs()
        timer.stop()
        scope.launch {
            machine.stop()
            _state.value = SessionState.Complete(activeMinutes, activeMode)
        }
    }

    fun enterMaintenance() {
        cancelJobs()
        timer.stop()
        _state.value = SessionState.Maintenance
    }

    fun exitMaintenance() {
        if (_state.value != SessionState.Maintenance) return
        runSelfCheck()
    }

    fun reset() {
        cancelJobs()
        timer.stop()
        _state.value = SessionState.Idle
    }

    fun dispose() {
        cancelJobs()
        timer.stop()
    }

    private fun onMachineState(machineState: MachineState) {
        val current = _state.value
        if (current == SessionState.Maintenance || current is SessionState.Complete) return

        when (machineState) {
            is MachineState.Fault -> abort(machineState.message, machineState.code, UnavailableReason.MACHINE_FAULT)
            MachineState.ConnectionFailed -> abort("CONNECTION_FAILED", null, UnavailableReason.MACHINE_OFFLINE)
            MachineState.OutOfBalls -> when (current) {
                is SessionState.Running -> {
                    timer.pause()
                    _state.value = SessionState.BallsRequired(current.remainingSeconds, current.mode)
                }
                is SessionState.Recovering -> {
                    sessionJob?.cancel()
                    _state.value = SessionState.BallsRequired(current.remainingSeconds, current.mode)
                }
                else -> Unit
            }
            is MachineState.Reconnecting -> when (current) {
                is SessionState.Running -> {
                    timer.pause()
                    _state.value = SessionState.Reconnecting(current.remainingSeconds, current.mode, machineState.attempt)
                }
                is SessionState.BallsRequired ->
                    _state.value = SessionState.Reconnecting(current.remainingSeconds, current.mode, machineState.attempt)
                is SessionState.Reconnecting ->
                    _state.value = current.copy(attempt = machineState.attempt)
                else -> Unit
            }
            MachineState.Connected, MachineState.Ready -> if (current is SessionState.Reconnecting) {
                resumeSession(current.remainingSeconds, current.mode)
            }
            else -> Unit
        }
    }

    /** S09 — verify before handing control back to the customer. */
    private fun resumeSession(remainingSeconds: Long, mode: TrainingMode) {
        sessionJob?.cancel()
        _state.value = SessionState.Recovering(remainingSeconds, mode)
        sessionJob = scope.launch {
            delay(recoveryMillis)
            if (_state.value !is SessionState.Recovering) return@launch
            machine.start(activeStartMode)
            _state.value = SessionState.Running(remainingSeconds, mode)
            timer.resume()
        }
    }

    /** Paid session → S03; before payment → S02. */
    private fun abort(reason: String, code: Int?, unavailableReason: UnavailableReason) {
        val current = _state.value
        if (current is SessionState.MachineFault || current is SessionState.Unavailable) return
        // Once the terminal is processing, the customer may already have been charged.
        val paid = when (current) {
            is SessionState.Payment -> current.status in setOf(
                PaymentState.PROCESSING, PaymentState.VERIFYING, PaymentState.SUCCESS
            )
            is SessionState.Preparing,
            is SessionState.Countdown,
            is SessionState.Running,
            is SessionState.BallsRequired,
            is SessionState.Reconnecting,
            is SessionState.Recovering -> true
            else -> false
        }

        cancelJobs()
        timer.stop()
        _state.value = if (paid) {
            SessionState.MachineFault(reason, code)
        } else {
            SessionState.Unavailable(unavailableReason)
        }
    }

    private fun setPaymentState(status: PaymentState) {
        val latest = _state.value as? SessionState.Payment ?: return
        _state.value = latest.copy(status = status)
    }

    private fun cancelJobs() {
        sessionJob?.cancel()
        sessionJob = null
        paymentJob?.cancel()
        paymentJob = null
    }

    private fun priceFor(minutes: Int): Double = when (minutes) {
        15 -> 6.90
        30 -> 12.00
        60 -> 20.00
        else -> error("Unsupported duration")
    }
}
