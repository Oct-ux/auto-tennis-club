package com.autotennisclub.app.payment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Credentials from the SumUp dashboard. Sandbox keys drive the Virtual Solo; live keys a real Solo. */
data class SumUpConfig(
    val apiKey: String,
    val merchantCode: String,
    /** The application identifier the Affiliate Key was created for. */
    val affiliateAppId: String,
    val affiliateKey: String,
    val baseUrl: String = "https://api.sumup.com"
)

/** Which Solo this station charges on. Set once by pairing from the operator panel. */
interface ReaderRegistry {
    val readerId: StateFlow<String?>
    fun save(readerId: String)
}

class InMemoryReaderRegistry(initial: String? = null) : ReaderRegistry {
    private val _readerId = MutableStateFlow(initial)
    override val readerId: StateFlow<String?> = _readerId.asStateFlow()
    override fun save(readerId: String) {
        _readerId.value = readerId
    }
}

data class ReaderStatus(val online: Boolean, val state: String?, val batteryLevel: Int?)

/**
 * SumUp Solo through the Cloud API (SumUp's recommendation for unattended stations).
 *
 * The customer presses PAY on the tablet, the Solo shows the amount and takes the
 * card. Our reference travels as affiliate.foreign_transaction_id, so the result,
 * and any payment interrupted by an app restart, is found by that reference.
 */
class SumUpCloudPaymentGateway(
    private val config: SumUpConfig,
    private val http: HttpClient,
    private val readers: ReaderRegistry,
    private val pollIntervalMillis: Long = 2_000,
    /** The Solo must start within 60 s; then the customer needs time to tap and enter a PIN. */
    private val paymentTimeoutMillis: Long = 120_000,
    private val retryDelayMillis: Long = 3_000
) : PaymentGateway {
    override val providerName: String = "SUMUP SOLO"

    val readerId: StateFlow<String?> get() = readers.readerId

    override suspend fun checkReady(): Boolean = readerStatus()?.online == true

    suspend fun readerStatus(): ReaderStatus? {
        val reader = readers.readerId.value ?: return null
        val response = call("GET", "/v0.1/merchants/${config.merchantCode}/readers/$reader/status") ?: return null
        if (response.code != 200) return null
        val data = parse(response.body)?.optJSONObject("data") ?: return null
        return ReaderStatus(
            online = data.optString("status") == "ONLINE",
            state = data.optString("state").ifEmpty { null },
            batteryLevel = if (data.has("battery_level")) data.optInt("battery_level") else null
        )
    }

    /** Pairs the Solo showing [pairingCode] (Solo: Connections → API → Connect) with this station. */
    suspend fun pairReader(pairingCode: String, name: String): Result<String> {
        val body = JSONObject().put("pairing_code", pairingCode.trim().uppercase()).put("name", name)
        val response = call("POST", "/v0.1/merchants/${config.merchantCode}/readers", body.toString())
            ?: return Result.failure(IOException("SumUp not reachable"))
        if (response.code !in 200..201) {
            return Result.failure(IllegalStateException("Pairing refused (HTTP ${response.code})"))
        }
        val id = parse(response.body)?.optString("id").orEmpty()
        if (id.isEmpty()) return Result.failure(IllegalStateException("Pairing response without reader id"))
        readers.save(id)
        return Result.success(id)
    }

    override suspend fun startPayment(amountCents: Long, reference: String): PaymentResult {
        val reader = readers.readerId.value ?: return PaymentResult.Failed("NO_READER_PAIRED")
        val body = JSONObject()
            .put("total_amount", JSONObject().put("value", amountCents).put("currency", "EUR").put("minor_unit", 2))
            .put("description", "Auto Tennis Club")
            .put(
                "affiliate",
                JSONObject()
                    .put("app_id", config.affiliateAppId)
                    .put("key", config.affiliateKey)
                    .put("foreign_transaction_id", reference)
            )
        val created = call("POST", "/v0.1/merchants/${config.merchantCode}/readers/$reader/checkout", body.toString())
            ?: return PaymentResult.Failed("NETWORK")
        when (created.code) {
            200, 201 -> Unit
            401, 403 -> return PaymentResult.Failed("AUTH")
            404 -> return PaymentResult.Failed("READER_NOT_FOUND")
            422 -> return PaymentResult.Failed("READER_BUSY_OR_OFFLINE")
            else -> return PaymentResult.Failed("HTTP_${created.code}")
        }

        return try {
            withTimeoutOrNull(paymentTimeoutMillis) { awaitResult(reference) }
                ?: run {
                    // Customer walked away: stop the Solo, then check once more for a last-second tap.
                    terminate(reader)
                    when (val last = transaction("foreign_transaction_id", reference)) {
                        is Lookup.Found -> if (last.status == "SUCCESSFUL") PaymentResult.Success(last.id) else PaymentResult.Timeout
                        else -> PaymentResult.Timeout
                    }
                }
        } catch (e: CancellationException) {
            // The station gave up (e.g. machine fault): make sure the Solo stops asking for a card.
            withContext(NonCancellable) { terminate(reader) }
            throw e
        }
    }

    /** Waits for the Solo, retrying through network hiccups: the customer may already be charged. */
    override suspend fun verify(transactionId: String): Boolean {
        while (true) {
            when (val found = transaction("id", transactionId)) {
                is Lookup.Found -> return found.status == "SUCCESSFUL"
                Lookup.Missing -> return false
                Lookup.Unreachable -> delay(retryDelayMillis)
            }
        }
    }

    override suspend fun lookup(reference: String): PaymentLookup =
        when (val found = transaction("foreign_transaction_id", reference)) {
            is Lookup.Found -> when (found.status) {
                "SUCCESSFUL" -> PaymentLookup.Paid(found.id)
                "PENDING" -> PaymentLookup.Unreachable
                else -> PaymentLookup.NotPaid
            }
            Lookup.Missing -> PaymentLookup.NotPaid
            Lookup.Unreachable -> PaymentLookup.Unreachable
        }

    private suspend fun awaitResult(reference: String): PaymentResult {
        while (true) {
            delay(pollIntervalMillis)
            val found = transaction("foreign_transaction_id", reference) as? Lookup.Found ?: continue
            when (found.status) {
                "SUCCESSFUL" -> return PaymentResult.Success(found.id)
                "FAILED" -> return PaymentResult.Failed("DECLINED")
                "CANCELLED" -> return PaymentResult.Cancelled
                else -> Unit // PENDING: still on the Solo
            }
        }
    }

    private suspend fun terminate(reader: String) {
        call("POST", "/v0.1/merchants/${config.merchantCode}/readers/$reader/terminate", "{}")
    }

    private sealed interface Lookup {
        data class Found(val id: String, val status: String) : Lookup
        data object Missing : Lookup
        data object Unreachable : Lookup
    }

    private suspend fun transaction(param: String, value: String): Lookup {
        val query = "$param=" + URLEncoder.encode(value, "UTF-8")
        val response = call("GET", "/v2.1/merchants/${config.merchantCode}/transactions?$query")
            ?: return Lookup.Unreachable
        return when (response.code) {
            200 -> parse(response.body)?.let { Lookup.Found(it.optString("id"), it.optString("status")) }
                ?: Lookup.Unreachable
            404 -> Lookup.Missing
            else -> Lookup.Unreachable
        }
    }

    /** Null for a body that is not JSON (e.g. an HTML error page from a proxy). */
    private fun parse(body: String): JSONObject? = try {
        JSONObject(body)
    } catch (e: JSONException) {
        null
    }

    /** Null when SumUp cannot be reached. */
    private suspend fun call(method: String, path: String, body: String? = null): HttpResponse? = try {
        http.request(
            method,
            config.baseUrl + path,
            mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Content-Type" to "application/json",
                "Accept" to "application/json"
            ),
            body
        )
    } catch (e: IOException) {
        null
    }
}
