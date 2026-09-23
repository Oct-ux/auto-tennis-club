package com.autotennisclub.app.machine

import com.autotennisclub.app.ble.MockBleGatt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MachineControllerTest {
    private val gatt = MockBleGatt()

    private fun TestScope.controller(): MachineController =
        MachineController(backgroundScope, gatt, clock = { testScheduler.currentTime }).also { runCurrent() }

    private fun fault(code: Int) = byteArrayOf(0xBB.toByte(), 0x5E, code.toByte(), 0xB5.toByte())

    @Test fun connectEndsConnected() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        assertEquals(MachineState.Connected, machine.state.value)
    }

    @Test fun unreachableMachineEndsConnectionFailed() = runTest {
        gatt.connectSucceeds = false
        val machine = controller()
        machine.connect()
        runCurrent()
        assertEquals(MachineState.ConnectionFailed, machine.state.value)
    }

    @Test fun noBallsIsNotAFault() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        gatt.notify(fault(3))
        runCurrent()
        assertEquals(MachineState.OutOfBalls, machine.state.value)
    }

    @Test fun faultIsRecordedForTheOperator() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        advanceTimeBy(5_000)
        gatt.notify(fault(1))
        runCurrent()
        assertEquals(MachineState.Fault(1, "WHEEL_PROTECTION"), machine.state.value)
        assertEquals("WHEEL_PROTECTION", machine.diagnostics.value.lastFault)
        assertEquals(5_000L, machine.diagnostics.value.lastResponseAtMillis)
    }

    @Test fun resetClearsFault() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        gatt.notify(fault(1))
        runCurrent()
        machine.reset()
        assertEquals(MachineState.Connected, machine.state.value)
    }

    @Test fun droppedLinkReconnects() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        gatt.connectSucceeds = false
        gatt.dropLink()
        runCurrent()
        assertEquals(MachineState.Reconnecting(1), machine.state.value)
        advanceTimeBy(2_100)
        assertEquals(MachineState.Reconnecting(2), machine.state.value)

        gatt.connectSucceeds = true
        advanceTimeBy(2_000)
        assertEquals(MachineState.Ready, machine.state.value)
    }

    @Test fun droppedLinkGivesUpAfterFiveAttempts() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        gatt.connectSucceeds = false
        gatt.dropLink()
        advanceTimeBy(10_100)
        assertEquals(MachineState.ConnectionFailed, machine.state.value)
        assertEquals(1 + 5, gatt.connectAttempts)
    }

    @Test fun shutdownDoesNotReconnect() = runTest {
        val machine = controller()
        machine.connect()
        runCurrent()
        machine.shutdown()
        advanceTimeBy(10_000)
        assertEquals(MachineState.Disconnected, machine.state.value)
        assertEquals(1, gatt.connectAttempts)
    }
}
