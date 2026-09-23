package com.autotennisclub.app.machine

sealed interface MachineState {
    data object Disconnected : MachineState
    data object Connecting : MachineState
    data object Connected : MachineState
    data object Configuring : MachineState
    data object Ready : MachineState
    data object Running : MachineState

    /** BLE link dropped; transport is retrying (Figma S05). */
    data class Reconnecting(val attempt: Int) : MachineState

    /** Reconnection gave up (Figma 07 CONNECTION_FAILED). */
    data object ConnectionFailed : MachineState

    /** PUSUN fault NO_BALLS; recoverable once balls are returned (Figma S04). */
    data object OutOfBalls : MachineState

    /** Non-recoverable hardware fault (Figma S03). */
    data class Fault(val code: Int, val message: String) : MachineState
}
