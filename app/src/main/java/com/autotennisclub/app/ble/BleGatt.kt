package com.autotennisclub.app.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** BLE link to the MAX B: service FFF0, notify FFF1, write FFF2. */
interface BleGatt {
    /** True while the link is up and notifications are enabled. */
    val connected: StateFlow<Boolean>
    val notifications: Flow<ByteArray>

    /** Finds and connects to the machine. Returns false if it could not connect. */
    suspend fun connect(): Boolean
    suspend fun write(characteristic: UUID, data: ByteArray)
    fun disconnect()
}
