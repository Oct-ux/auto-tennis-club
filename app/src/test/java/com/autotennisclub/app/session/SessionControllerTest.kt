package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MockPusunMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private fun TestScope.station(): Pair<SessionController, MockPusunMachine> {
        val machine = MockPusunMachine(backgroundScope)
        val session = SessionController(backgroundScope, machine, countdownSeconds = 1)
        runCurrent()
        return session to machine
    }

    private fun TestScope.runningSession(): Pair<SessionController, MockPusunMachine> {
        val (session, machine) = station()
        advanceTimeBy(2_000)
        session.start()
        session.selectTraining(TrainingMode.BASIC)
        session.selectDuration(15)
        session.proceedToPayment()
        session.simulatePayment()
        advanceTimeBy(2_000)
        session.startPaidSession()
        advanceTimeBy(1_500)
        assertTrue(session.state.value is SessionState.Running)
        return session to machine
    }

    @Test fun selfCheckGoesIdleWhenMachineHealthy() = runTest {
        val (session, _) = station()
        assertEquals(SessionState.StartingUp, session.state.value)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, session.state.value)
    }

    @Test fun faultBeforePaymentMakesStationUnavailable() = runTest {
        val (session, machine) = station()
        advanceTimeBy(2_000)
        machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Unavailable(UnavailableReason.MACHINE_FAULT), session.state.value)
    }

    @Test fun paymentGoesThroughVerifying() = runTest {
        val (session, _) = station()
        advanceTimeBy(2_000)
        session.start()
        session.selectTraining(TrainingMode.BASIC)
        session.selectDuration(30)
        session.proceedToPayment()
        session.simulatePayment()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.VERIFYING, (session.state.value as SessionState.Payment).status)
        advanceTimeBy(1_000)
        assertEquals(PaymentState.SUCCESS, (session.state.value as SessionState.Payment).status)
    }

    @Test fun timeoutAndCancelAreRetryable() = runTest {
        val (session, _) = station()
        advanceTimeBy(2_000)
        session.start()
        session.selectTraining(TrainingMode.BASIC)
        session.selectDuration(15)
        session.proceedToPayment()
        session.simulatePaymentTimeout()
        assertEquals(PaymentState.TIMEOUT, (session.state.value as SessionState.Payment).status)
        session.retryPayment()
        session.cancelPayment()
        assertEquals(PaymentState.CANCELLED, (session.state.value as SessionState.Payment).status)
        session.retryPayment()
        assertEquals(PaymentState.WAITING, (session.state.value as SessionState.Payment).status)
    }

    @Test fun outOfBallsPausesTimerAndResumesAfterRecovery() = runTest {
        val (session, machine) = runningSession()
        machine.simulateFault(3)
        runCurrent()
        val paused = session.state.value as SessionState.BallsRequired
        advanceTimeBy(10_000)
        assertEquals(paused, session.state.value)

        session.confirmBallsReturned()
        assertTrue(session.state.value is SessionState.Recovering)
        advanceTimeBy(1_600)
        val running = session.state.value as SessionState.Running
        assertEquals(paused.remainingSeconds, running.remainingSeconds)
    }

    @Test fun connectionLossPausesAndReconnectRecovers() = runTest {
        val (session, machine) = runningSession()
        machine.simulateConnectionLost(attempt = 1)
        runCurrent()
        assertEquals(1, (session.state.value as SessionState.Reconnecting).attempt)
        machine.simulateConnectionLost(attempt = 2)
        runCurrent()
        assertEquals(2, (session.state.value as SessionState.Reconnecting).attempt)

        machine.simulateReconnected()
        runCurrent()
        assertTrue(session.state.value is SessionState.Recovering)
        advanceTimeBy(1_600)
        assertTrue(session.state.value is SessionState.Running)
    }

    @Test fun faultDuringPaidSessionShowsMachineFault() = runTest {
        val (session, machine) = runningSession()
        machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.MachineFault("WHEEL_PROTECTION", 1), session.state.value)
    }

    @Test fun maintenanceIgnoresMachineEvents() = runTest {
        val (session, machine) = runningSession()
        session.enterMaintenance()
        machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Maintenance, session.state.value)
    }
}
