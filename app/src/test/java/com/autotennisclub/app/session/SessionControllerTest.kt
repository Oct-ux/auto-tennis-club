package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.payment.MockPaymentGateway.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private class Station(
        val session: SessionController,
        val machine: MockPusunMachine,
        val payments: MockPaymentGateway
    ) {
        val paymentStatus: PaymentState
            get() = (session.state.value as SessionState.Payment).status
    }

    private fun TestScope.station(): Station {
        val machine = MockPusunMachine(backgroundScope)
        val payments = MockPaymentGateway()
        val session = SessionController(backgroundScope, machine, payments, countdownSeconds = 1)
        runCurrent()
        return Station(session, machine, payments)
    }

    /** Self check done, customer on the payment screen for [minutes]. */
    private fun TestScope.atPayment(minutes: Int = 15): Station {
        val station = station()
        advanceTimeBy(2_000)
        with(station.session) {
            start()
            selectTraining(TrainingMode.BASIC)
            selectDuration(minutes)
            proceedToPayment()
        }
        return station
    }

    private fun TestScope.runningSession(): Station {
        val station = atPayment()
        station.session.pay()
        advanceTimeBy(2_000)
        station.session.startPaidSession()
        advanceTimeBy(1_500)
        assertTrue(station.session.state.value is SessionState.Running)
        return station
    }

    @Test fun selfCheckGoesIdleWhenMachineHealthy() = runTest {
        val station = station()
        assertEquals(SessionState.StartingUp, station.session.state.value)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.session.state.value)
    }

    @Test fun faultBeforePaymentMakesStationUnavailable() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Unavailable(UnavailableReason.MACHINE_FAULT), station.session.state.value)
    }

    @Test fun paymentGoesThroughVerifyingAndChargesInCents() = runTest {
        val station = atPayment(minutes = 30)
        station.session.pay()
        assertEquals(PaymentState.PROCESSING, station.paymentStatus)
        advanceTimeBy(1_000)
        assertEquals(PaymentState.VERIFYING, station.paymentStatus)
        advanceTimeBy(1_000)
        assertEquals(PaymentState.SUCCESS, station.paymentStatus)
        assertEquals(1_200L, station.payments.lastAmountCents)
    }

    @Test fun terminalTimeoutIsRetryable() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.TIMEOUT)
        station.session.pay()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.TIMEOUT, station.paymentStatus)
        station.session.retryPayment()
        assertEquals(PaymentState.WAITING, station.paymentStatus)
    }

    @Test fun cancelledOnTerminalIsRetryable() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.CANCELLED)
        station.session.pay()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.CANCELLED, station.paymentStatus)
        station.session.retryPayment()
        assertEquals(PaymentState.WAITING, station.paymentStatus)
    }

    @Test fun declinedCardFails() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.FAILED)
        station.session.pay()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.FAILED, station.paymentStatus)
    }

    @Test fun failedVerificationFails() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.UNVERIFIED)
        station.session.pay()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.VERIFYING, station.paymentStatus)
        advanceTimeBy(1_000)
        assertEquals(PaymentState.FAILED, station.paymentStatus)
    }

    @Test fun outcomeAppliesToNextAttemptOnly() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.FAILED)
        station.session.pay()
        advanceTimeBy(1_000)
        val firstReference = station.payments.lastReference
        station.session.retryPayment()
        station.session.pay()
        advanceTimeBy(2_000)
        assertEquals(PaymentState.SUCCESS, station.paymentStatus)
        assertNotEquals(firstReference, station.payments.lastReference)
    }

    @Test fun customerCanOnlyCancelBeforePaying() = runTest {
        val station = atPayment()
        station.session.cancelPayment()
        assertEquals(PaymentState.CANCELLED, station.paymentStatus)
        station.session.retryPayment()

        station.session.pay()
        station.session.cancelPayment()
        assertEquals(PaymentState.PROCESSING, station.paymentStatus)
        advanceTimeBy(2_000)
        assertEquals(PaymentState.SUCCESS, station.paymentStatus)
    }

    @Test fun outOfBallsPausesTimerAndResumesAfterRecovery() = runTest {
        val station = runningSession()
        station.machine.simulateFault(3)
        runCurrent()
        val paused = station.session.state.value as SessionState.BallsRequired
        advanceTimeBy(10_000)
        assertEquals(paused, station.session.state.value)

        station.session.confirmBallsReturned()
        assertTrue(station.session.state.value is SessionState.Recovering)
        advanceTimeBy(1_600)
        val running = station.session.state.value as SessionState.Running
        assertEquals(paused.remainingSeconds, running.remainingSeconds)
    }

    @Test fun connectionLossPausesAndReconnectRecovers() = runTest {
        val station = runningSession()
        station.machine.simulateConnectionLost(attempt = 1)
        runCurrent()
        assertEquals(1, (station.session.state.value as SessionState.Reconnecting).attempt)
        station.machine.simulateConnectionLost(attempt = 2)
        runCurrent()
        assertEquals(2, (station.session.state.value as SessionState.Reconnecting).attempt)

        station.machine.simulateReconnected()
        runCurrent()
        assertTrue(station.session.state.value is SessionState.Recovering)
        advanceTimeBy(1_600)
        assertTrue(station.session.state.value is SessionState.Running)
    }

    @Test fun faultDuringPaidSessionShowsMachineFault() = runTest {
        val station = runningSession()
        station.machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.MachineFault("WHEEL_PROTECTION", 1), station.session.state.value)
    }

    @Test fun faultWhileProcessingPaymentShowsMachineFault() = runTest {
        val station = atPayment()
        station.session.pay()
        station.machine.simulateFault(1)
        runCurrent()
        assertTrue(station.session.state.value is SessionState.MachineFault)
    }

    @Test fun maintenanceIgnoresMachineEvents() = runTest {
        val station = runningSession()
        station.session.enterMaintenance()
        station.machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Maintenance, station.session.state.value)
    }
}
