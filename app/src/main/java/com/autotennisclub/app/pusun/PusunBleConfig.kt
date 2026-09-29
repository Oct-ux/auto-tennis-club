package com.autotennisclub.app.pusun

import java.util.UUID

object PusunBleConfig {
    val SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    val NOTIFY_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
    val WRITE_UUID: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
    const val COMMAND_DELAY_MS = 50L
    const val MAX_WRITE_BYTES = 244
}