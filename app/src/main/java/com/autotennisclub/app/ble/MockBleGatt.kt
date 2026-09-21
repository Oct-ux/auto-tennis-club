package com.autotennisclub.app.ble

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockBleGatt : BleGatt {
    data class Write(val characteristic: UUID, val data: ByteArray)
    private val mutex = Mutex()
    private val _writes = mutableListOf<Write>()
    val writes: List<Write> get() = _writes.toList()

    override suspend fun write(characteristic: UUID, data: ByteArray) {
        mutex.withLock { _writes += Write(characteristic, data.copyOf()) }
    }

    override fun disconnect() = Unit
}