package com.autotennisclub.app.payment

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Development gateway. Every payment succeeds unless the debug panel or a test
 * queues another outcome, which applies to the next attempt only.
 */
class MockPaymentGateway(
    private val processingMillis: Long = 900,
    private val verifyingMillis: Long = 900
) : PaymentGateway {
    enum class Outcome { SUCCESS, FAILED, CANCELLED, TIMEOUT, UNVERIFIED }

    override val providerName: String = "MOCK"

    private val _nextOutcome = MutableStateFlow(Outcome.SUCCESS)
    val nextOutcome: StateFlow<Outcome> = _nextOutcome.asStateFlow()

    private val _ready = MutableStateFlow(true)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Answer for [lookup]; null means "paid", as if the provider had the charge. */
    var lookupOverride: PaymentLookup? = null

    private val unverified = mutableSetOf<String>()

    var lastAmountCents: Long? = null
        private set
    var lastReference: String? = null
        private set

    fun setNextOutcome(outcome: Outcome) {
        _nextOutcome.value = outcome
    }

    fun setReady(ready: Boolean) {
        _ready.value = ready
    }

    override suspend fun checkReady(): Boolean = _ready.value

    override suspend fun startPayment(amountCents: Long, reference: String): PaymentResult {
        lastAmountCents = amountCents
        lastReference = reference
        delay(processingMillis)

        val outcome = _nextOutcome.value
        _nextOutcome.value = Outcome.SUCCESS
        val transactionId = "mock-$reference"
        return when (outcome) {
            Outcome.SUCCESS -> PaymentResult.Success(transactionId)
            Outcome.UNVERIFIED -> {
                unverified += transactionId
                PaymentResult.Success(transactionId)
            }
            Outcome.FAILED -> PaymentResult.Failed("DECLINED")
            Outcome.CANCELLED -> PaymentResult.Cancelled
            Outcome.TIMEOUT -> PaymentResult.Timeout
        }
    }

    override suspend fun verify(transactionId: String): Boolean {
        delay(verifyingMillis)
        return transactionId !in unverified
    }

    override suspend fun lookup(reference: String): PaymentLookup =
        lookupOverride ?: PaymentLookup.Paid("mock-$reference")
}
