package com.autotennisclub.app.session

enum class TrainingMode {
    BASIC,
    TRAINING,
    CUSTOM
}

enum class PaymentStatus {
    WAITING,
    PROCESSING,
    SUCCESS,
    FAILED
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
        val status: PaymentStatus = PaymentStatus.WAITING,
        val config: CustomConfig? = null
    ) : SessionState
    data object Preparing : SessionState
    data class Countdown(val seconds: Int) : SessionState
    data class Running(val remainingSeconds: Long, val mode: TrainingMode) : SessionState
    data class Complete(val minutes: Int, val mode: TrainingMode) : SessionState
}
