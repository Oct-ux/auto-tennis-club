package com.autotennisclub.app.session

import com.autotennisclub.app.pusun.SpinType

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

/** Figma S09: what has been checked before handing a paid session back to the customer. */
enum class RecoveryStep {
    CHECKING,
    PAYMENT_VERIFIED,
    MACHINE_VERIFIED,
    TIMER_VERIFIED,
    RECOVERED
}

/** Figma 09 overlay model: what is shown over the paused training timer. */
enum class TrainingOverlay {
    OUT_OF_BALLS,
    CONNECTION_LOST,
    MACHINE_FAULT,
    RECOVERING
}

/** Operator machine tests (Figma MAINTENANCE · MACHINE TEST). */
enum class MachineTest {
    START,
    STOP,
    SPEED_FREQUENCY,
    SPIN
}

/** Figma 04 court grid. */
enum class LandingZone { NET_LEFT, NET_RIGHT, BACK_LEFT, BACK_RIGHT }

/** Spin strength; PUSUN spin values to be tuned on the MAX B. */
enum class SpinIntensity(val spinValue: Int) { LIGHT(5), MEDIUM(10), HEAVY(15) }

data class CustomConfig(
    val velocity: Int = 80,
    val frequencyGrade: Int = 30,
    val spin: String = "TOPSPIN",
    val intensity: SpinIntensity = SpinIntensity.MEDIUM,
    val sequence: String = ROTATE_POINTS,
    val zones: Set<LandingZone> = LandingZone.entries.toSet()
) {
    val spinType: SpinType
        get() = when (spin) {
            "BACKSPIN" -> SpinType.BACKSPIN
            "NONE", "NO SPIN" -> SpinType.NONE
            else -> SpinType.TOPSPIN
        }

    val spinValue: Int get() = if (spinType == SpinType.NONE) 0 else intensity.spinValue

    val fixedPoint: Boolean get() = sequence == FIXED_POINT

    /** FIXED POINT plays one zone, so a tap replaces it; ROTATE POINTS toggles zones in and out. */
    fun toggle(zone: LandingZone): CustomConfig =
        if (fixedPoint) copy(zones = setOf(zone))
        else copy(zones = if (zone in zones) zones - zone else zones + zone)

    fun withSequence(sequence: String): CustomConfig = copy(
        sequence = sequence,
        zones = if (sequence == FIXED_POINT) zones.take(1).toSet() else zones
    )

    companion object {
        const val FIXED_POINT = "FIXED POINT"
        const val ROTATE_POINTS = "ROTATE POINTS"
    }
}

/** Session-side facts for the operator panel. */
data class SessionDiagnostics(
    val paymentReady: Boolean? = null,
    val lastPayment: PaymentState? = null,
    val lastReference: String? = null
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

    /** S09 — checking payment, machine and timer before resuming a paid session. */
    data class Recovering(
        val step: RecoveryStep,
        val remainingSeconds: Long,
        val mode: TrainingMode,
        /** True when resuming after the app was restarted, false after S04 / S05. */
        val afterRestart: Boolean = false
    ) : SessionState

    /**
     * S03 — hardware fault. With a paid session, [reference] lets the operator find
     * the payment; [remainingSeconds] is set when the session had already started.
     */
    data class MachineFault(
        val reason: String,
        val code: Int? = null,
        val reference: String? = null,
        val remainingSeconds: Long? = null,
        val mode: TrainingMode? = null
    ) : SessionState

    data class Complete(val minutes: Int, val mode: TrainingMode) : SessionState

    /** Operator-only layer; not part of the customer flow. */
    data object Maintenance : SessionState
}

/** The overlay to draw over the paused training timer, or null when the timer is not on screen. */
val SessionState.trainingOverlay: TrainingOverlay?
    get() = when (this) {
        is SessionState.BallsRequired -> TrainingOverlay.OUT_OF_BALLS
        is SessionState.Reconnecting -> TrainingOverlay.CONNECTION_LOST
        is SessionState.MachineFault -> if (remainingSeconds != null) TrainingOverlay.MACHINE_FAULT else null
        is SessionState.Recovering -> if (!afterRestart) TrainingOverlay.RECOVERING else null
        else -> null
    }
