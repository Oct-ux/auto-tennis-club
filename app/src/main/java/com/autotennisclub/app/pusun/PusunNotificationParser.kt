package com.autotennisclub.app.pusun

object PusunNotificationParser {
    fun parse(frame: ByteArray): PusunNotification? {
        if (frame.size < 4) return null
        if ((frame.first().toInt() and 0xFF) != 0xBB) return null
        if ((frame.last().toInt() and 0xFF) != 0xB5) return null

        val command = frame[1].toInt() and 0xFF
        return when (command) {
            0x03 -> PusunNotification.Battery((frame[2].toInt() and 0xFF).coerceIn(0, 100))
            0x5E -> {
                val code = frame[2].toInt() and 0xFF
                PusunNotification.Fault(code, when (code) {
                    1 -> FaultType.WHEEL_PROTECTION
                    2 -> FaultType.ENTRANCE_PROTECTION
                    3 -> FaultType.NO_BALLS
                    else -> FaultType.UNKNOWN
                })
            }
            else -> PusunNotification.Unknown(command, frame.copyOfRange(2, frame.size - 1))
        }
    }
}