package com.autotennisclub.app.payment

sealed interface PaymentResult {
    data class Success(val transactionId: String) : PaymentResult
    data class Failed(val reason: String) : PaymentResult
    data object Cancelled : PaymentResult
    data object Timeout : PaymentResult
}

/**
 * Payment provider boundary. Compose and SessionController never see the
 * provider SDK: MockPaymentGateway for development, SumUp for production.
 */
interface PaymentGateway {
    /**
     * Charges [amountCents] in EUR. [reference] is unique per attempt, so a payment
     * interrupted by an app restart can be looked up instead of charged twice.
     * Implementations report their own terminal timeout as [PaymentResult.Timeout].
     */
    suspend fun startPayment(amountCents: Long, reference: String): PaymentResult

    /** Confirms with the provider that [transactionId] was captured (Figma S08). */
    suspend fun verify(transactionId: String): Boolean
}
