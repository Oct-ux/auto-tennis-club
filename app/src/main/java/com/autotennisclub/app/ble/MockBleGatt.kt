package com.autotennisclub.app.ble

import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockBleGatt : BleGatt {
    data class Write(val characteristic: UUID, val data: ByteArray)
    private val mutex = Mutex()
    private val _writes = mutableListOf<Write>()
    val writes: List<Write> get() = _writes.toList()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val notifications: SharedFlow<ByteArray> = _notifications.asSharedFlow()

    /** Result of the next [connect] calls; tests flip it to simulate an unreachable machine. */
    var connectSucceeds: Boolean = true
    var connectAttempts: Int = 0
        private set

    override suspend fun connect(): Boolean {
        connectAttempts++
        _connected.value = connectSucceeds
        return connectSucceeds
    }

    override suspend fun write(characteristic: UUID, data: ByteArray) {
        mutex.withLock { _writes += Write(characteristic, data.copyOf()) }
    }

    override fun disconnect() {
        _connected.value = false
    }

    /** Link lost without [disconnect] being called, e.g. machine switched off. */
    fun dropLink() {
        _connected.value = false
    }

    fun notify(frame: ByteArray) {
        _notifications.tryEmit(frame)
    }
}
