package com.autotennisclub.app.payment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SumUpCloudPaymentGatewayTest {
    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    /** Answers by method + path; each route can script a sequence of responses (the last one repeats). */
    private class FakeSumUp : HttpClient {
        val calls = mutableListOf<Call>()
        private val routes = mutableMapOf<String, ArrayDeque<() -> HttpResponse>>()

        fun on(method: String, pathPart: String, vararg responses: () -> HttpResponse) {
            routes["$method $pathPart"] = ArrayDeque(responses.toList())
        }

        override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
            calls += Call(method, url, headers, body)
            val route = routes.entries.firstOrNull { (key, _) ->
                val (m, part) = key.split(" ", limit = 2)
                m == method && url.contains(part)
            } ?: return HttpResponse(500, "")
            val queue = route.value
            val next = if (queue.size > 1) queue.removeFirst() else queue.first()
            return next()
        }
    }

    private val config = SumUpConfig("sup_sk_test", "MTEST", "com.autotennisclub.app", "sup_afk_test")
    private val sumUp = FakeSumUp()
    private val readers = InMemoryReaderRegistry("rdr_1")
    private val gateway = SumUpCloudPaymentGateway(config, sumUp, readers, pollIntervalMillis = 1_000, paymentTimeoutMillis = 10_000)

    private fun ok(json: String) = { HttpResponse(200, json) }
    private fun created(json: String) = { HttpResponse(201, json) }
    private val notFound = { HttpResponse(404, """{"error_code":"NOT_FOUND"}""") }
    private fun tx(status: String, entryMode: String = "contactless") =
        ok("""{"id":"tx_9","status":"$status","entry_mode":"$entryMode","foreign_transaction_id":"ref-1"}""")
    private val checkoutAccepted = created("""{"data":{"checkout_id":"co_1","client_transaction_id":"ctx_1"}}""")

    @Test fun checkoutCarriesAmountCurrencyAffiliateAndOurReference() = runTest {
        sumUp.on("POST", "/readers/rdr_1/checkout", checkoutAccepted)
        sumUp.on("GET", "/transactions", tx("SUCCESSFUL"))

        val result = gateway.startPayment(690, "ref-1")

        assertEquals(PaymentResult.Success("tx_9"), result)
        val checkout = sumUp.calls.first { it.method == "POST" }
        assertEquals("https://api.sumup.com/v0.1/merchants/MTEST/readers/rdr_1/checkout", checkout.url)
        assertEquals("Bearer sup_sk_test", checkout.headers["Authorization"])
        val body = JSONObject(checkout.body!!)
        assertEquals(690, body.getJSONObject("total_amount").getInt("value"))
        assertEquals("EUR", body.getJSONObject("total_amount").getString("currency"))
        assertEquals(2, body.getJSONObject("total_amount").getInt("minor_unit"))
        val affiliate = body.getJSONObject("affiliate")
        assertEquals("com.autotennisclub.app", affiliate.getString("app_id"))
        assertEquals("sup_afk_test", affiliate.getString("key"))
        assertEquals("ref-1", affiliate.getString("foreign_transaction_id"))
        assertTrue(sumUp.calls.last().url.endsWith("/transactions?foreign_transaction_id=ref-1"))
    }

    @Test fun waitsWhileTheCustomerHasNotTappedYet() = runTest {
        sumUp.on("POST", "/checkout", checkoutAccepted)
        sumUp.on("GET", "/transactions", notFound, tx("PENDING"), tx("SUCCESSFUL"))
        assertEquals(PaymentResult.Success("tx_9"), gateway.startPayment(1200, "ref-1"))
    }

    @Test fun declinedAndCancelledOnTheSolo() = runTest {
        sumUp.on("POST", "/checkout", checkoutAccepted)
        sumUp.on("GET", "/transactions", tx("FAILED"))
        assertEquals(PaymentResult.Failed("DECLINED"), gateway.startPayment(690, "ref-1"))

        sumUp.on("GET", "/transactions", tx("CANCELLED"))
        assertEquals(PaymentResult.Cancelled, gateway.startPayment(690, "ref-2"))
    }

    /** Seen on the Virtual Solo: the cancel button leaves a FAILED transaction with no card read. */
    @Test fun cancellingOnTheSoloIsACancelNotADecline() = runTest {
        sumUp.on("POST", "/checkout", checkoutAccepted)
        sumUp.on("GET", "/transactions", tx("FAILED", entryMode = "none"))
        assertEquals(PaymentResult.Cancelled, gateway.startPayment(690, "ref-1"))
    }

    @Test fun customerWalksAwayThenTheSoloIsStoppedAndItTimesOut() = runTest {
        sumUp.on("POST", "/checkout", checkoutAccepted)
        sumUp.on("POST", "/terminate", ok("{}"))
        sumUp.on("GET", "/transactions", notFound)

        assertEquals(PaymentResult.Timeout, gateway.startPayment(690, "ref-1"))
        assertTrue(sumUp.calls.any { it.url.endsWith("/readers/rdr_1/terminate") })
    }

    @Test fun readerBusyOrOfflineIsRefusedUpFront() = runTest {
        sumUp.on("POST", "/checkout", { HttpResponse(422, "{}") })
        assertEquals(PaymentResult.Failed("READER_BUSY_OR_OFFLINE"), gateway.startPayment(690, "ref-1"))
    }

    @Test fun noPairedReaderMeansNoCharge() = runTest {
        val unpaired = SumUpCloudPaymentGateway(config, sumUp, InMemoryReaderRegistry(null))
        assertEquals(PaymentResult.Failed("NO_READER_PAIRED"), unpaired.startPayment(690, "ref-1"))
        assertFalse(unpaired.checkReady())
        assertTrue(sumUp.calls.isEmpty())
    }

    @Test fun stationGivingUpStopsTheSolo() = runTest {
        sumUp.on("POST", "/checkout", checkoutAccepted)
        sumUp.on("POST", "/terminate", ok("{}"))
        sumUp.on("GET", "/transactions", notFound)
        val job = launch { gateway.startPayment(690, "ref-1") }
        advanceTimeBy(3_000)
        job.cancel()
        runCurrent()
        assertTrue(sumUp.calls.any { it.url.endsWith("/terminate") })
    }

    @Test fun readyOnlyWhenTheSoloIsOnline() = runTest {
        sumUp.on("GET", "/readers/rdr_1/status", ok("""{"data":{"status":"ONLINE","state":"IDLE","battery_level":80}}"""))
        assertTrue(gateway.checkReady())
        assertEquals(ReaderStatus(online = true, state = "IDLE", batteryLevel = 80), gateway.readerStatus())

        sumUp.on("GET", "/readers/rdr_1/status", ok("""{"data":{"status":"OFFLINE"}}"""))
        assertFalse(gateway.checkReady())
    }

    @Test fun lookupAfterRestartFindsThePaymentByOurReference() = runTest {
        sumUp.on("GET", "/transactions", tx("SUCCESSFUL"))
        assertEquals(PaymentLookup.Paid("tx_9"), gateway.lookup("ref-1"))

        sumUp.on("GET", "/transactions", notFound)
        assertEquals(PaymentLookup.NotPaid, gateway.lookup("ref-1"))

        sumUp.on("GET", "/transactions", tx("PENDING"))
        assertEquals(PaymentLookup.Unreachable, gateway.lookup("ref-1"))
    }

    /** The station died while the Solo was asking for a card: it must not keep charging for a forgotten session. */
    @Test fun lookupStopsASoloStillWaitingBeforeSayingNotPaid() = runTest {
        sumUp.on("POST", "/terminate", ok("{}"))
        sumUp.on("GET", "/transactions", notFound)
        assertEquals(PaymentLookup.NotPaid, gateway.lookup("ref-1"))
        assertTrue(sumUp.calls.any { it.url.endsWith("/readers/rdr_1/terminate") })

        // The customer tapped just before the Solo was stopped.
        sumUp.on("GET", "/transactions", notFound, tx("SUCCESSFUL"))
        assertEquals(PaymentLookup.Paid("tx_9"), gateway.lookup("ref-1"))
    }

    @Test fun lookupWithoutNetworkIsUnreachableNotUnpaid() = runTest {
        val offline = SumUpCloudPaymentGateway(config, object : HttpClient {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
                throw IOException("no network")
        }, readers)
        assertEquals(PaymentLookup.Unreachable, offline.lookup("ref-1"))
        assertEquals(PaymentResult.Failed("NETWORK"), offline.startPayment(690, "ref-1"))
    }

    @Test fun verifyKeepsAskingThroughErrorsBecauseTheCustomerMayBeCharged() = runTest {
        sumUp.on("GET", "/transactions", { HttpResponse(503, "<html>busy</html>") }, tx("SUCCESSFUL"))
        assertTrue(gateway.verify("tx_9"))
        assertTrue(sumUp.calls.last().url.endsWith("/transactions?id=tx_9"))
    }

    @Test fun pairingSavesTheReader() = runTest {
        val fresh = InMemoryReaderRegistry(null)
        val pairing = SumUpCloudPaymentGateway(config, sumUp, fresh)
        sumUp.on("POST", "/merchants/MTEST/readers", created("""{"id":"rdr_NEW","status":"paired"}"""))

        assertEquals(Result.success("rdr_NEW"), pairing.pairReader("ab12cd34", "Station"))
        assertEquals("rdr_NEW", fresh.readerId.value)
        assertEquals("AB12CD34", JSONObject(sumUp.calls.last().body!!).getString("pairing_code"))
    }
}
