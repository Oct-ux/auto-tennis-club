package com.autotennisclub.app.session

enum class TrainingMode {
    BASIC,
    TRAINING,
    CUSTOM
}

/** Figma 06 state model + Phase 4.5 screens S06 (TIMEOUT), S07 (CANCELLED), S08 (VERIFYING). */
enum class PaymentState {
    WAITING,
    PROCESSING,
    VERIFYING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT;

    /** States where the customer can start a new payment attempt. */
    val canRetry: Boolean get() = this == FAILED || this == CANCELLED || this == TIMEOUT
}

/** Why the station refuses new sessions (Figma S02). */
enum class UnavailableReason {
    MACHINE_OFFLINE,
    MACHINE_FAULT,
    OUT_OF_BALLS,
    PAYMENT_OFFLINE
}

data class CustomConfig(
    val velocity: Int = 80,
    val frequencyGrade: Int = 30,
    val spin: String = "TOPSPIN",
    val spinValue: Int = 10,
    val sequence: String = "ROTATE POINTS",
    val landingZones: Int = 4
)

sealed interface SessionState {
    /** S01 — station self check before accepting customers. */
    data object StartingUp : SessionState

    /** S02 — station cannot accept new sessions. */
    data class Unavailable(val reason: UnavailableReason) : SessionState

    data object Idle : SessionState
    data object TrainingSelection : SessionState
    data class DurationSelection(val mode: TrainingMode) : SessionState
    data class CustomConfigState(
        val minutes: Int,
        val config: CustomConfig = CustomConfig()
    ) : SessionState
    data class Summary(
        val mode: TrainingMode,
        val minutes: Int,
        val price: Double,
        val config: CustomConfig? = null
    ) : SessionState
    data class Payment(
        val mode: TrainingMode,
        val minutes: Int,
        val price: Double,
        val status: PaymentState = PaymentState.WAITING,
        val config: CustomConfig? = null
    ) : SessionState
    data object Preparing : SessionState
    data class Countdown(val seconds: Int) : SessionState
    data class Running(val remainingSeconds: Long, val mode: TrainingMode) : SessionState

    /** S04 — paid session paused until balls are returned. Timer is paused. */
    data class BallsRequired(val remainingSeconds: Long, val mode: TrainingMode) : SessionState

    /** S05 — paid session paused while BLE reconnects. Timer is paused. */
    data class Reconnecting(
        val remainingSeconds: Long,
        val mode: TrainingMode,
        val attempt: Int
    ) : SessionState

    /** S09 — checking payment, machine and timer before resuming a paused session. */
    data class Recovering(val remainingSeconds: Long, val mode: TrainingMode) : SessionState

    /** S03 — hardware fault during a paid session. */
    data class MachineFault(val reason: String, val code: Int? = null) : SessionState

    data class Complete(val minutes: Int, val mode: TrainingMode) : SessionState

    /** Operator-only layer; not part of the customer flow. */
    data object Maintenance : SessionState
}
