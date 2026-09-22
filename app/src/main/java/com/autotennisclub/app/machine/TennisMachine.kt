package com.autotennisclub.app.machine

import com.autotennisclub.app.pusun.SpinType
import com.autotennisclub.app.pusun.StartMode
import kotlinx.coroutines.flow.StateFlow

interface TennisMachine {
    val state: StateFlow<MachineState>

    suspend fun configure(
        velocity: Int,
        frequencyGrade: Int,
        spin: SpinType = SpinType.NONE,
        spinValue: Int = 0
    )

    suspend fun start(mode: StartMode)
    suspend fun stop()
}
