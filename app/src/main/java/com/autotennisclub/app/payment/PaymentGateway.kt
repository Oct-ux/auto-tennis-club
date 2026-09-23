package com.autotennisclub.app.payment

sealed interface PaymentResult {
    data class Success(val transactionId: String) : PaymentResult
    data class Failed(val reason: String) : PaymentResult
    data object Cancelled : PaymentResult
    data object Timeout : PaymentResult
}

/** What the provider knows about an earlier attempt, looked up by our reference. */
sealed interface PaymentLookup {
    data class Paid(val transactionId: String) : PaymentLookup
    data object NotPaid : PaymentLookup
    data object Unreachable : PaymentLookup
}

/**
 * Payment provider boundary. Compose and SessionController never see the
 * provider SDK: MockPaymentGateway for development, SumUp for production.
 */
interface PaymentGateway {
    val providerName: String

    /** Terminal / account ready to take a payment (checked by the S01 self check). */
    suspend fun checkReady(): Boolean

    /**
     * Charges [amountCents] in EUR. [reference] is unique per attempt, so a payment
     * interrupted by an app restart can be looked up instead of charged twice.
     * Implementations report their own terminal timeout as [PaymentResult.Timeout].
     */
    suspend fun startPayment(amountCents: Long, reference: String): PaymentResult

    /** Confirms with the provider that [transactionId] was captured (Figma S08). */
    suspend fun verify(transactionId: String): Boolean

    /** Finds the outcome of an attempt made before an app restart (Figma S09). */
    suspend fun lookup(reference: String): PaymentLookup
}
