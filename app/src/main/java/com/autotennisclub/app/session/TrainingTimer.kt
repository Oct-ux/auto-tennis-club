package com.autotennisclub.app.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrainingTimer(private val scope: CoroutineScope) {
    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()
    private var job: Job? = null
    private var onComplete: (() -> Unit)? = null

    fun start(minutes: Int, onComplete: () -> Unit) {
        stop()
        _remainingSeconds.value = minutes * 60L
        this.onComplete = onComplete
        tick()
    }

    /** Freezes the countdown, keeping the remaining time. */
    fun pause() {
        job?.cancel()
        job = null
    }

    fun resume() {
        if (job == null && onComplete != null && _remainingSeconds.value > 0) tick()
    }

    fun stop() {
        job?.cancel()
        job = null
        onComplete = null
        _remainingSeconds.value = 0L
    }

    private fun tick() {
        job = scope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000)
                if (isActive) _remainingSeconds.value -= 1
            }
            if (isActive && _remainingSeconds.value == 0L) onComplete?.invoke()
        }
    }
}
