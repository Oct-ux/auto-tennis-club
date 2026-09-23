package com.autotennisclub.app.payment

/**
 * Production placeholder until the SumUp SDK is integrated (phase 6). The self
 * check reports the terminal as not ready, so the station shows S02 instead of
 * letting anyone play without paying.
 */
object NotConfiguredPaymentGateway : PaymentGateway {
    override val providerName: String = "NOT CONFIGURED"

    override suspend fun checkReady(): Boolean = false

    override suspend fun startPayment(amountCents: Long, reference: String): PaymentResult =
        PaymentResult.Failed("NOT_CONFIGURED")

    override suspend fun verify(transactionId: String): Boolean = false

    override suspend fun lookup(reference: String): PaymentLookup = PaymentLookup.Unreachable
}
