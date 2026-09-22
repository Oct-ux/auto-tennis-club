package com.autotennisclub.app.session

sealed interface SessionState {
    data object Idle : SessionState
    data class Selected(val minutes: Int) : SessionState
    data object Preparing : SessionState
    data class Countdown(val seconds: Int) : SessionState
    data class Running(val remainingSeconds: Long) : SessionState
    data class Complete(val minutes: Int) : SessionState
}