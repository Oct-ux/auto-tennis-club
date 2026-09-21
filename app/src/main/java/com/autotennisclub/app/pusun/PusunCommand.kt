package com.autotennisclub.app.pusun

sealed interface PusunCommand {
    data class SetFrequencyGrade(val grade: Int) : PusunCommand
    data class SetSpin(val type: SpinType, val value: Int) : PusunCommand
    data class SetVelocity(val value: Int) : PusunCommand
    data class SetDirection(val lr: Int, val ud: Int) : PusunCommand
    data class SetPoint(val point: Int, val lr: Int, val ud: Int) : PusunCommand
    data class ProgramPoints(val points: List<Int>) : PusunCommand
    data class Start(val mode: StartMode) : PusunCommand
    data object Stop : PusunCommand
}

enum class SpinType(val protocolValue: Int) { NONE(0), TOPSPIN(1), BACKSPIN(2) }

enum class StartMode(val protocolValue: Int) {
    FIXED(1), HORIZONTAL(2), VERTICAL(3), RANDOM(4), PROGRAM(5)
}