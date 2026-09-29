package com.autotennisclub.app.machine

import com.autotennisclub.app.pusun.SpinType
import com.autotennisclub.app.pusun.StartMode
import kotlinx.coroutines.flow.StateFlow

/** What the operator panel shows about the machine (Figma MAINTENANCE · MACHINE). */
data class MachineDiagnostics(
    val lastFault: String? = null,
    val lastResponseAtMillis: Long? = null,
    val batteryPercent: Int? = null
)

interface TennisMachine {
    val state: StateFlow<MachineState>
    val diagnostics: StateFlow<MachineDiagnostics>

    /** Connects if not connected yet; no-op otherwise. Ends in Connected or ConnectionFailed. */
    suspend fun connect()

    suspend fun configure(
        velocity: Int,
        frequencyGrade: Int,
        spin: SpinType = SpinType.NONE,
        spinValue: Int = 0
    )

    suspend fun start(mode: StartMode)
    suspend fun stop()

    /** Operator reset: stops the machine and clears Fault / OutOfBalls. */
    suspend fun reset()
}
