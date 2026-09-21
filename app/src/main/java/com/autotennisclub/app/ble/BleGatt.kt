package com.autotennisclub.app.ble

import java.util.UUID

interface BleGatt {
    suspend fun write(characteristic: UUID, data: ByteArray)
    fun disconnect()
}