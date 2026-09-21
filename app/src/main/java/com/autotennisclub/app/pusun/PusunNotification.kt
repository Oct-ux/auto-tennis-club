package com.autotennisclub.app.pusun

sealed interface PusunNotification {
    data class Battery(val percentage: Int) : PusunNotification
    data class Fault(val code: Int, val type: FaultType) : PusunNotification
    data class Unknown(val command: Int, val data: ByteArray) : PusunNotification
}

enum class FaultType { WHEEL_PROTECTION, ENTRANCE_PROTECTION, NO_BALLS, UNKNOWN }