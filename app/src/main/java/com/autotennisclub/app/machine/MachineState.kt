package com.autotennisclub.app.machine

sealed interface MachineState {
    data object Disconnected : MachineState
    data object Connecting : MachineState
    data object Connected : MachineState
    data object Configuring : MachineState
    data object Ready : MachineState
    data object Running : MachineState
    data class Fault(val code: Int, val message: String) : MachineState
}