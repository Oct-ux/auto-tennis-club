package com.autotennisclub.app.pusun

import com.autotennisclub.app.ble.BleGatt
import kotlinx.coroutines.CancellationException
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
                try {
                    gatt.write(PusunBleConfig.WRITE_UUID, frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Link down: drop the frame. The session reconfigures the machine after reconnecting.
                }
                delay(delayMs)
            }
        }
    }

    suspend fun enqueue(command: PusunCommand) {
        PusunFrameBuilder.build(command).forEach { queue.send(it) }
    }

    fun close() = queue.close()
}