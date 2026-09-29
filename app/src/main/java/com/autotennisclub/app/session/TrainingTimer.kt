package com.autotennisclub.app.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Counts down paid time. A pause is [stop] + [start] again with the remaining
 * seconds the session kept, which also covers resuming after an app restart.
 */
class TrainingTimer(private val scope: CoroutineScope) {
    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()
    private var job: Job? = null

    fun start(seconds: Long, onComplete: () -> Unit) {
        stop()
        _remainingSeconds.value = seconds
        job = scope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000)
                if (isActive) _remainingSeconds.value -= 1
            }
            if (isActive && _remainingSeconds.value == 0L) onComplete()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _remainingSeconds.value = 0L
    }
}
