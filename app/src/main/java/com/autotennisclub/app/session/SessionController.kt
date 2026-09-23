package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.machine.TennisMachine
import com.autotennisclub.app.payment.PaymentGateway
import com.autotennisclub.app.payment.PaymentLookup
import com.autotennisclub.app.payment.PaymentResult
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
import java.util.UUID
import kotlin.math.roundToLong

class SessionController(
    private val scope: CoroutineScope,
    private val machine: TennisMachine,
    private val payments: PaymentGateway,
    private val store: SessionStore = InMemorySessionStore(),
    private val errors: ErrorLog = ErrorLog(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val timer: TrainingTimer = TrainingTimer(scope),
    private val countdownSeconds: Int = 5,
    private val selfCheckMillis: Long = 1500,
    private val recoveryStepMillis: Long = 500,
    private val saveEverySeconds: Long = 10,
    /** A session interrupted longer than this is not resumed: the customer has probably left. */
    private val maxResumeGapMillis: Long = 10 * 60_000L
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.StartingUp)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _activeSession = MutableStateFlow(store.load())
    /** The paid (or being paid) session, as saved on disk. */
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _diagnostics = MutableStateFlow(SessionDiagnostics())
    val diagnostics: StateFlow<SessionDiagnostics> = _diagnostics.asStateFlow()

    private var sessionJob: Job? = null
    private var paymentJob: Job? = null

    init {
        scope.launch {
            machine.state.collect(::onMachineState)
        }

        scope.launch {
            timer.remainingSeconds.collect { remaining ->
                val current = _state.value
                if (current is SessionState.Running) {
                    _state.value = current.copy(remainingSeconds = remaining)
                    if (remaining % saveEverySeconds == 0L) saveRemaining(remaining)
                }
            }
        }

        runSelfCheck()
    }

    /** S01 → Idle or S02. With a session saved before a restart, goes to S09 instead. */
    fun runSelfCheck() {
        cancelJobs()
        timer.stop()
        _activeSession.value?.let {
            recoverAfterRestart(it)
            return
        }
        _state.value = SessionState.StartingUp
        sessionJob = scope.launch {
            machine.connect()
            val paymentReady = payments.checkReady()
            _diagnostics.update { it.copy(paymentReady = paymentReady) }
            delay(selfCheckMillis)
            _state.value = unavailableReason(paymentReady)?.let { SessionState.Unavailable(it) }
                ?: SessionState.Idle
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

        val attempt = ActiveSession(
            reference = UUID.randomUUID().toString(),
            transactionId = null,
            mode = current.mode,
            minutes = current.minutes,
            priceCents = (current.price * 100).roundToLong(),
            config = current.config,
            remainingSeconds = current.minutes * 60L,
            savedAtMillis = clock()
        )
        // Saved before charging: if the app dies mid-payment, S09 asks the provider about this reference.
        persist(attempt)
        _diagnostics.update { it.copy(lastReference = attempt.reference) }
        _state.value = current.copy(status = PaymentState.PROCESSING)

        paymentJob?.cancel()
        paymentJob = scope.launch {
            val outcome = when (val result = payments.startPayment(attempt.priceCents, attempt.reference)) {
                is PaymentResult.Success -> {
                    setPaymentState(PaymentState.VERIFYING)
                    // No timeout here: the customer may already be charged, so S08
                    // keeps saying "do not pay again" until the provider answers.
                    if (payments.verify(result.transactionId)) {
                        persist(attempt.copy(transactionId = result.transactionId))
                        PaymentState.SUCCESS
                    } else {
                        errors.record("PAYMENT_UNVERIFIED", "ref=${attempt.reference}")
                        PaymentState.FAILED
                    }
                }
                is PaymentResult.Failed -> {
                    errors.record("PAYMENT_FAILED", "${result.reason} ref=${attempt.reference}")
                    PaymentState.FAILED
                }
                PaymentResult.Cancelled -> PaymentState.CANCELLED
                PaymentResult.Timeout -> {
                    errors.record("PAYMENT_TIMEOUT", "ref=${attempt.reference}")
                    PaymentState.TIMEOUT
                }
            }
            if (outcome != PaymentState.SUCCESS) persist(null)
            _diagnostics.update { it.copy(lastPayment = outcome) }
            setPaymentState(outcome)
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
        launchSession(_activeSession.value ?: return)
    }

    /** S04 action: "I'VE RETURNED THE BALLS". */
    fun confirmBallsReturned() {
        val current = _state.value as? SessionState.BallsRequired ?: return
        resumeSession(current.remainingSeconds, current.mode)
    }

    fun stopSession() {
        val session = _activeSession.value
        cancelJobs()
        _state.value = SessionState.Complete(session?.minutes ?: 0, session?.mode ?: TrainingMode.BASIC)
        timer.stop()
        persist(null)
        scope.launch { machine.stop() }
    }

    fun reset() {
        cancelJobs()
        timer.stop()
        persist(null)
        _state.value = SessionState.Idle
    }

    fun dispose() {
        cancelJobs()
        timer.stop()
    }

    // --- Maintenance (operator layer) ---

    /**
     * Refused while a paid session is playing, so a customer never loses time to it.
     * Allowed when the station is stuck (S01, S02, S03, S09 after a restart).
     */
    fun enterMaintenance(): Boolean {
        val current = _state.value
        val stuck = current is SessionState.Unavailable ||
            current is SessionState.MachineFault ||
            current == SessionState.StartingUp ||
            (current is SessionState.Recovering && current.afterRestart)
        if (_activeSession.value != null && !stuck) return false
        cancelJobs()
        timer.stop()
        _state.value = SessionState.Maintenance
        return true
    }

    /** Leaves maintenance: stops any machine test and runs the self check. */
    fun exitMaintenance() {
        if (_state.value != SessionState.Maintenance) return
        scope.launch {
            machine.stop()
            runSelfCheck()
        }
    }

    /** RESET STATION: stops the machine, clears faults, runs the self check. */
    fun resetStation() {
        if (_state.value != SessionState.Maintenance) return
        scope.launch {
            machine.reset()
            runSelfCheck()
        }
    }

    fun reconnectMachine() {
        if (_state.value != SessionState.Maintenance) return
        scope.launch { machine.connect() }
    }

    fun testPayment() {
        if (_state.value != SessionState.Maintenance) return
        scope.launch { _diagnostics.update { it.copy(paymentReady = payments.checkReady()) } }
    }

    /** END SESSION: drops a saved session the operator has settled with the customer. */
    fun endSavedSession() {
        if (_state.value != SessionState.Maintenance) return
        val session = _activeSession.value ?: return
        errors.record(
            "SESSION_ENDED_BY_OPERATOR",
            "ref=${session.reference} paid=${session.paid} unused=${formatSeconds(session.remainingSeconds)}"
        )
        persist(null)
    }

    fun runMachineTest(test: MachineTest) {
        if (_state.value != SessionState.Maintenance) return
        scope.launch {
            when (test) {
                MachineTest.START -> {
                    machine.configure(velocity = 80, frequencyGrade = 30)
                    machine.start(StartMode.FIXED)
                }
                MachineTest.STOP -> machine.stop()
                MachineTest.SPEED_FREQUENCY -> {
                    machine.configure(velocity = 120, frequencyGrade = 10)
                    machine.start(StartMode.FIXED)
                }
                MachineTest.SPIN -> {
                    machine.configure(velocity = 80, frequencyGrade = 30, spin = SpinType.TOPSPIN, spinValue = 10)
                    machine.start(StartMode.FIXED)
                }
            }
        }
    }

    // --- Internals ---

    private fun launchSession(session: ActiveSession) {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            _state.value = SessionState.Preparing
            configureMachine(session)

            for (second in countdownSeconds downTo 1) {
                _state.value = SessionState.Countdown(second)
                delay(1000)
            }

            machine.start(startModeFor(session.mode))
            _state.value = SessionState.Running(session.remainingSeconds, session.mode)
            timer.start(session.remainingSeconds, ::onTimeUp)
        }
    }

    private fun onTimeUp() {
        scope.launch {
            machine.stop()
            val session = _activeSession.value
            persist(null)
            _state.value = SessionState.Complete(session?.minutes ?: 0, session?.mode ?: TrainingMode.BASIC)
        }
    }

    /** S09 after an app restart: payment → machine → timer, then countdown and play. */
    private fun recoverAfterRestart(saved: ActiveSession) {
        _state.value = SessionState.Recovering(
            RecoveryStep.CHECKING, saved.remainingSeconds, saved.mode, afterRestart = true
        )
        sessionJob = scope.launch {
            if (clock() - saved.savedAtMillis > maxResumeGapMillis) {
                errors.record(
                    "SESSION_EXPIRED",
                    "ref=${saved.reference} paid=${saved.paid} unused=${formatSeconds(saved.remainingSeconds)}"
                )
                persist(null)
                runSelfCheck()
                return@launch
            }

            val transactionId = saved.transactionId ?: when (val found = payments.lookup(saved.reference)) {
                is PaymentLookup.Paid -> found.transactionId
                PaymentLookup.NotPaid -> {
                    persist(null)
                    runSelfCheck()
                    return@launch
                }
                PaymentLookup.Unreachable -> {
                    // Keep the saved session: TRY AGAIN re-runs this recovery.
                    _state.value = SessionState.Unavailable(UnavailableReason.PAYMENT_OFFLINE)
                    return@launch
                }
            }
            if (!payments.verify(transactionId)) {
                errors.record("PAYMENT_UNVERIFIED", "ref=${saved.reference}")
                persist(null)
                runSelfCheck()
                return@launch
            }
            val paid = saved.copy(transactionId = transactionId)
            persist(paid)
            advance(RecoveryStep.PAYMENT_VERIFIED)

            machine.connect()
            when (machine.state.value) {
                MachineState.OutOfBalls -> {
                    _state.value = SessionState.BallsRequired(paid.remainingSeconds, paid.mode)
                    return@launch
                }
                MachineState.Connected, MachineState.Ready -> advance(RecoveryStep.MACHINE_VERIFIED)
                else -> {
                    abort("MACHINE_NOT_READY", null, UnavailableReason.MACHINE_OFFLINE)
                    return@launch
                }
            }

            if (paid.remainingSeconds <= 0) {
                persist(null)
                _state.value = SessionState.Complete(paid.minutes, paid.mode)
                return@launch
            }
            advance(RecoveryStep.TIMER_VERIFIED)
            advance(RecoveryStep.RECOVERED)
            launchSession(paid)
        }
    }

    /** S09 after S04 / S05: payment was verified in this run; re-check machine and timer. */
    private fun resumeSession(remainingSeconds: Long, mode: TrainingMode) {
        sessionJob?.cancel()
        _state.value = SessionState.Recovering(RecoveryStep.PAYMENT_VERIFIED, remainingSeconds, mode)
        sessionJob = scope.launch {
            advance(RecoveryStep.MACHINE_VERIFIED)
            advance(RecoveryStep.TIMER_VERIFIED)
            advance(RecoveryStep.RECOVERED)
            // The machine may have lost its settings while it was disconnected.
            _activeSession.value?.let { configureMachine(it) }
            machine.start(startModeFor(mode))
            _state.value = SessionState.Running(remainingSeconds, mode)
            timer.start(remainingSeconds, ::onTimeUp)
        }
    }

    private suspend fun advance(step: RecoveryStep) {
        delay(recoveryStepMillis)
        val current = _state.value as? SessionState.Recovering ?: return
        _state.value = current.copy(step = step)
    }

    private fun onMachineState(machineState: MachineState) {
        val current = _state.value
        if (current == SessionState.Maintenance || current is SessionState.Complete) return

        when (machineState) {
            is MachineState.Fault ->
                abort(machineState.message, machineState.code, UnavailableReason.MACHINE_FAULT)
            MachineState.ConnectionFailed ->
                abort("CONNECTION_FAILED", null, UnavailableReason.MACHINE_OFFLINE)
            MachineState.OutOfBalls -> if (_activeSession.value == null) {
                // Nobody has paid yet: don't let a customer pay for a machine with no balls.
                abort("NO_BALLS", null, UnavailableReason.OUT_OF_BALLS)
            } else pauseSession(current) { remaining, mode ->
                // Stop feeding so nobody collecting balls near the machine gets hit when they go back in.
                scope.launch { machine.stop() }
                SessionState.BallsRequired(remaining, mode)
            }
            is MachineState.Reconnecting -> when (current) {
                is SessionState.Reconnecting -> _state.value = current.copy(attempt = machineState.attempt)
                is SessionState.BallsRequired -> _state.value =
                    SessionState.Reconnecting(current.remainingSeconds, current.mode, machineState.attempt)
                else -> pauseSession(current) { remaining, mode ->
                    SessionState.Reconnecting(remaining, mode, machineState.attempt)
                }
            }
            MachineState.Connected, MachineState.Ready -> if (current is SessionState.Reconnecting) {
                resumeSession(current.remainingSeconds, current.mode)
            }
            else -> Unit
        }
    }

    /** Pauses a session that is playing or about to play; the timer keeps its remaining seconds. */
    private fun pauseSession(current: SessionState, paused: (Long, TrainingMode) -> SessionState) {
        val session = _activeSession.value ?: return
        val (remaining, mode) = when (current) {
            is SessionState.Running -> current.remainingSeconds to current.mode
            is SessionState.Recovering -> if (current.afterRestart) return else current.remainingSeconds to current.mode
            SessionState.Preparing, is SessionState.Countdown -> session.remainingSeconds to session.mode
            else -> return
        }
        sessionJob?.cancel()
        _state.value = paused(remaining, mode)
        timer.stop()
        saveRemaining(remaining)
    }

    /**
     * Paid or paying session → S03 with a reference for the operator (no automatic refund).
     * Nothing paid → S02.
     */
    private fun abort(reason: String, code: Int?, unavailableReason: UnavailableReason) {
        val current = _state.value
        if (current is SessionState.MachineFault || current is SessionState.Unavailable) return
        val session = _activeSession.value
        val playedRemaining = when (current) {
            is SessionState.Running -> current.remainingSeconds
            is SessionState.BallsRequired -> current.remainingSeconds
            is SessionState.Reconnecting -> current.remainingSeconds
            is SessionState.Recovering -> if (current.afterRestart) null else current.remainingSeconds
            else -> null
        }
        cancelJobs()

        if (session == null) {
            errors.record("STATION_UNAVAILABLE", reason + (code?.let { " code=$it" } ?: ""))
            _state.value = SessionState.Unavailable(unavailableReason)
            timer.stop()
            return
        }

        val unused = playedRemaining ?: session.remainingSeconds
        errors.record(
            "MACHINE_FAULT",
            "$reason ref=${session.reference} paid=${session.paid} unused=${formatSeconds(unused)}"
        )
        _state.value = SessionState.MachineFault(
            reason = reason,
            code = code,
            reference = session.shortReference,
            remainingSeconds = playedRemaining,
            mode = session.mode
        )
        timer.stop()
        persist(null)
    }

    private fun unavailableReason(paymentReady: Boolean): UnavailableReason? = when (machine.state.value) {
        is MachineState.Fault -> UnavailableReason.MACHINE_FAULT
        MachineState.OutOfBalls -> UnavailableReason.OUT_OF_BALLS
        MachineState.Disconnected,
        MachineState.Connecting,
        MachineState.ConnectionFailed,
        is MachineState.Reconnecting -> UnavailableReason.MACHINE_OFFLINE
        else -> if (paymentReady) null else UnavailableReason.PAYMENT_OFFLINE
    }

    private suspend fun configureMachine(session: ActiveSession) {
        val config = session.config ?: CustomConfig()
        machine.configure(
            velocity = config.velocity,
            frequencyGrade = config.frequencyGrade,
            spin = config.spinType,
            spinValue = config.spinValue
        )
    }

    private fun startModeFor(mode: TrainingMode): StartMode = when (mode) {
        TrainingMode.BASIC -> StartMode.FIXED
        TrainingMode.TRAINING -> StartMode.PROGRAM
        TrainingMode.CUSTOM -> StartMode.PROGRAM
    }

    private fun saveRemaining(remaining: Long) {
        _activeSession.value?.let { persist(it.copy(remainingSeconds = remaining)) }
    }

    private fun persist(session: ActiveSession?) {
        val saved = session?.copy(savedAtMillis = clock())
        if (saved == null) store.clear() else store.save(saved)
        _activeSession.value = saved
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

internal fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
