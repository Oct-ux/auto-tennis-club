package com.autotennisclub.app.pusun

import com.autotennisclub.app.ble.BleGatt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PusunCommandQueue(
    scope: CoroutineScope,
    private val gatt: BleGatt,
    private val delayMs: Long = PusunBleConfig.COMMAND_DELAY_MS
) {
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (frame in queue) {
                gatt.write(PusunBleConfig.WRITE_UUID, frame)
                delay(delayMs)
            }
        }
    }

    suspend fun enqueue(command: PusunCommand) {
        PusunFrameBuilder.build(command).forEach { queue.send(it) }
    }

    fun close() = queue.close()
}