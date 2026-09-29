package com.autotennisclub.app.machine

import com.autotennisclub.app.ble.MockBleGatt
import com.autotennisclub.app.pusun.PusunBleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Software-only MAX B: the real [MachineController] over a simulated BLE link,
 * so the session flow runs the same connect / fault / reconnect logic it will
 * run on hardware.
 */
class MockPusunMachine private constructor(
    private val scope: CoroutineScope,
    private val gatt: MockBleGatt,
    private val controller: MachineController
) : TennisMachine by controller {

    constructor(scope: CoroutineScope, reconnectDelayMillis: Long = 2_000) :
        this(scope, MockBleGatt(), reconnectDelayMillis)

    private constructor(scope: CoroutineScope, gatt: MockBleGatt, reconnectDelayMillis: Long) :
        this(scope, gatt, MachineController(scope, gatt, reconnectDelayMillis = reconnectDelayMillis))

    fun writtenFrames(): List<ByteArray> = gatt.writes.map { it.data.copyOf() }

    fun isUsingPusunWriteCharacteristic(): Boolean =
        gatt.writes.all { it.characteristic == PusunBleConfig.WRITE_UUID }

    /** Sends a PUSUN fault notification: 1 wheel, 2 entrance, 3 no balls. */
    fun simulateFault(code: Int) {
        gatt.notify(byteArrayOf(0xBB.toByte(), 0x5E, code.toByte(), 0xB5.toByte()))
    }

    /** Link drops and stays down: S05 counts attempts, then ConnectionFailed. */
    fun simulateConnectionLost() {
        gatt.connectSucceeds = false
        gatt.dropLink()
    }

    /** Machine healthy again: the next reconnect attempt succeeds and any fault is cleared. */
    fun simulateMachineOk() {
        gatt.connectSucceeds = true
        scope.launch {
            when (state.value) {
                is MachineState.Fault, MachineState.OutOfBalls -> reset()
                MachineState.Disconnected, MachineState.ConnectionFailed -> connect()
                else -> Unit
            }
        }
    }

    fun shutdown() = controller.shutdown()
}
