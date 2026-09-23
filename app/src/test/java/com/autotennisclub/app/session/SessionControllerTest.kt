package com.autotennisclub.app.session

import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.payment.MockPaymentGateway.Outcome
import com.autotennisclub.app.payment.PaymentLookup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private class Station(
        val session: SessionController,
        val machine: MockPusunMachine,
        val payments: MockPaymentGateway,
        val store: SessionStore,
        val errors: ErrorLog
    ) {
        val state: SessionState get() = session.state.value
        val paymentStatus: PaymentState get() = (state as SessionState.Payment).status
        val errorCodes: List<String> get() = errors.entries.value.map { it.code }
    }

    private fun TestScope.station(
        store: SessionStore = InMemorySessionStore(),
        payments: MockPaymentGateway = MockPaymentGateway()
    ): Station {
        val clock = { testScheduler.currentTime }
        val machine = MockPusunMachine(backgroundScope)
        val errors = ErrorLog(clock = clock)
        val session = SessionController(
            backgroundScope, machine, payments,
            store = store, errors = errors, clock = clock, countdownSeconds = 1
        )
        runCurrent()
        return Station(session, machine, payments, store, errors)
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
        assertTrue(station.state is SessionState.Running)
        return station
    }

    private fun saved(
        transactionId: String? = "tx-1",
        remainingSeconds: Long = 600,
        savedAtMillis: Long = -2 * 60_000L
    ) = ActiveSession(
        reference = "ref-1",
        transactionId = transactionId,
        mode = TrainingMode.BASIC,
        minutes = 15,
        priceCents = 690,
        config = null,
        remainingSeconds = remainingSeconds,
        savedAtMillis = savedAtMillis
    )

    // --- Self check ---

    @Test fun selfCheckConnectsMachineAndGoesIdle() = runTest {
        val station = station()
        assertEquals(SessionState.StartingUp, station.state)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun terminalOfflineMakesStationUnavailable() = runTest {
        val payments = MockPaymentGateway().apply { setReady(false) }
        val station = station(payments = payments)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Unavailable(UnavailableReason.PAYMENT_OFFLINE), station.state)
    }

    @Test fun faultBeforePaymentMakesStationUnavailable() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Unavailable(UnavailableReason.MACHINE_FAULT), station.state)
    }

    @Test fun noBallsBeforePaymentMakesStationUnavailable() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.machine.simulateFault(3)
        runCurrent()
        assertEquals(SessionState.Unavailable(UnavailableReason.OUT_OF_BALLS), station.state)
    }

    // --- Payment ---

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

    @Test fun attemptIsSavedBeforeChargingAndPaidAfterVerification() = runTest {
        val station = atPayment()
        station.session.pay()
        runCurrent()
        val pending = station.store.load()!!
        assertNull(pending.transactionId)
        assertEquals(station.payments.lastReference, pending.reference)

        advanceTimeBy(2_000)
        assertTrue(station.store.load()!!.paid)
    }

    @Test fun terminalTimeoutIsRetryableAndLogged() = runTest {
        val station = atPayment()
        station.payments.setNextOutcome(Outcome.TIMEOUT)
        station.session.pay()
        advanceTimeBy(1_000)
        assertEquals(PaymentState.TIMEOUT, station.paymentStatus)
        assertNull(station.store.load())
        assertEquals(listOf("PAYMENT_TIMEOUT"), station.errorCodes)
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
        assertNull(station.store.load())
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

    // --- Playing ---

    @Test fun runningSessionIsSavedEveryTenSeconds() = runTest {
        val station = runningSession()
        advanceTimeBy(10_000)
        assertEquals(15 * 60L - 10, station.store.load()!!.remainingSeconds)
    }

    @Test fun stopClearsSavedSession() = runTest {
        val station = runningSession()
        station.session.stopSession()
        assertTrue(station.state is SessionState.Complete)
        assertNull(station.store.load())
    }

    @Test fun outOfBallsPausesTimerAndResumesAfterRecovery() = runTest {
        val station = runningSession()
        station.machine.simulateFault(3)
        runCurrent()
        val paused = station.state as SessionState.BallsRequired
        assertEquals(TrainingOverlay.OUT_OF_BALLS, paused.trainingOverlay)
        advanceTimeBy(10_000)
        assertEquals(paused, station.state)

        station.session.confirmBallsReturned()
        assertTrue(station.state is SessionState.Recovering)
        advanceTimeBy(1_600)
        val running = station.state as SessionState.Running
        assertEquals(paused.remainingSeconds, running.remainingSeconds)
    }

    @Test fun connectionLossPausesAndReconnectRecovers() = runTest {
        val station = runningSession()
        station.machine.simulateConnectionLost()
        runCurrent()
        assertEquals(1, (station.state as SessionState.Reconnecting).attempt)
        advanceTimeBy(2_100)
        assertEquals(2, (station.state as SessionState.Reconnecting).attempt)

        station.machine.simulateMachineOk()
        advanceTimeBy(2_000)
        assertEquals(TrainingOverlay.RECOVERING, station.state.trainingOverlay)
        advanceTimeBy(1_600)
        assertTrue(station.state is SessionState.Running)
    }

    @Test fun connectionThatNeverComesBackEndsInOperatorNotice() = runTest {
        val station = runningSession()
        station.machine.simulateConnectionLost()
        advanceTimeBy(10_100)
        val fault = station.state as SessionState.MachineFault
        assertEquals("CONNECTION_FAILED", fault.reason)
        assertNotNull(fault.reference)
    }

    @Test fun faultDuringPaidSessionLogsUnusedTimeForTheOperator() = runTest {
        val station = runningSession()
        advanceTimeBy(60_000)
        station.machine.simulateFault(1)
        runCurrent()
        val fault = station.state as SessionState.MachineFault
        assertEquals("WHEEL_PROTECTION", fault.reason)
        assertEquals(15 * 60L - 60, fault.remainingSeconds)
        assertEquals(TrainingOverlay.MACHINE_FAULT, fault.trainingOverlay)
        assertNull(station.store.load())
        val entry = station.errors.entries.value.first()
        assertEquals("MACHINE_FAULT", entry.code)
        assertTrue(entry.detail.contains("unused=14:00"))
    }

    @Test fun faultWhileProcessingPaymentShowsFullScreenMachineFault() = runTest {
        val station = atPayment()
        station.session.pay()
        station.machine.simulateFault(1)
        runCurrent()
        val fault = station.state as SessionState.MachineFault
        assertNotNull(fault.reference)
        assertNull(fault.trainingOverlay)
    }

    // --- Recovery after an app restart (S09) ---

    @Test fun paidSessionResumesAfterRestart() = runTest {
        val station = station(store = InMemorySessionStore(saved()))
        val recovering = station.state as SessionState.Recovering
        assertTrue(recovering.afterRestart)
        assertEquals(RecoveryStep.CHECKING, recovering.step)

        advanceTimeBy(1_500)
        assertEquals(RecoveryStep.PAYMENT_VERIFIED, (station.state as SessionState.Recovering).step)
        advanceTimeBy(2_500)
        assertEquals(SessionState.Running(600, TrainingMode.BASIC), station.state)
    }

    @Test fun paymentInterruptedMidwayIsLookedUpNotChargedAgain() = runTest {
        val station = station(store = InMemorySessionStore(saved(transactionId = null)))
        advanceTimeBy(4_000)
        assertEquals(SessionState.Running(600, TrainingMode.BASIC), station.state)
        assertNull(station.payments.lastReference)
        assertTrue(station.store.load()!!.paid)
    }

    @Test fun unpaidAttemptIsDroppedAfterRestart() = runTest {
        val payments = MockPaymentGateway().apply { lookupOverride = PaymentLookup.NotPaid }
        val station = station(InMemorySessionStore(saved(transactionId = null)), payments)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
        assertNull(station.store.load())
    }

    @Test fun providerUnreachableKeepsSavedSession() = runTest {
        val payments = MockPaymentGateway().apply { lookupOverride = PaymentLookup.Unreachable }
        val station = station(InMemorySessionStore(saved(transactionId = null)), payments)
        runCurrent()
        assertEquals(SessionState.Unavailable(UnavailableReason.PAYMENT_OFFLINE), station.state)
        assertNotNull(station.store.load())
    }

    @Test fun sessionInterruptedOverTenMinutesAgoIsNotResumed() = runTest {
        val station = station(store = InMemorySessionStore(saved(savedAtMillis = -11 * 60_000L)))
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
        assertNull(station.store.load())
        assertEquals(listOf("SESSION_EXPIRED"), station.errorCodes)
    }

    // --- Return to Home on inactivity ---

    @Test fun abandonedSelectionReturnsHomeAfterOneMinute() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.session.start()
        advanceTimeBy(59_000)
        assertEquals(SessionState.TrainingSelection, station.state)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun touchesKeepTheCustomerOnTheirScreen() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.session.start()
        advanceTimeBy(50_000)
        station.session.userActivity()
        advanceTimeBy(50_000)
        assertEquals(SessionState.TrainingSelection, station.state)
        advanceTimeBy(11_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun abandonedPaymentScreenReturnsHome() = runTest {
        val station = atPayment()
        advanceTimeBy(61_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun completeScreenReturnsHomeAfterThirtySeconds() = runTest {
        val station = runningSession()
        station.session.stopSession()
        advanceTimeBy(29_000)
        assertTrue(station.state is SessionState.Complete)
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun playingSessionNeverTimesOut() = runTest {
        val station = runningSession()
        advanceTimeBy(5 * 60_000L)
        assertTrue(station.state is SessionState.Running)
    }

    // --- Maintenance ---

    @Test fun maintenanceIsRefusedWhileAPaidSessionPlays() = runTest {
        val station = runningSession()
        assertFalse(station.session.enterMaintenance())
        assertTrue(station.state is SessionState.Running)
    }

    @Test fun maintenanceIgnoresMachineEvents() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        assertTrue(station.session.enterMaintenance())
        station.machine.simulateFault(1)
        runCurrent()
        assertEquals(SessionState.Maintenance, station.state)
    }

    @Test fun resetStationClearsFaultAndReturnsToIdle() = runTest {
        val station = station()
        advanceTimeBy(2_000)
        station.machine.simulateFault(1)
        runCurrent()
        assertTrue(station.session.enterMaintenance())
        station.session.resetStation()
        advanceTimeBy(2_000)
        assertEquals(SessionState.Idle, station.state)
    }

    @Test fun operatorCanEndAStuckSavedSession() = runTest {
        val payments = MockPaymentGateway().apply { lookupOverride = PaymentLookup.Unreachable }
        val station = station(InMemorySessionStore(saved(transactionId = null)), payments)
        runCurrent()
        assertTrue(station.session.enterMaintenance())
        station.session.endSavedSession()
        assertNull(station.store.load())
        assertEquals(listOf("SESSION_ENDED_BY_OPERATOR"), station.errorCodes)
    }
}
