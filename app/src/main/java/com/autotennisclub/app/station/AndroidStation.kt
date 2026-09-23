package com.autotennisclub.app.station

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.autotennisclub.app.kiosk.Kiosk
import com.autotennisclub.app.kiosk.bluetoothPermissions
import com.autotennisclub.app.session.ActiveSession
import com.autotennisclub.app.session.CustomConfig
import com.autotennisclub.app.session.ErrorEntry
import com.autotennisclub.app.session.ErrorLogStore
import com.autotennisclub.app.session.LandingZone
import com.autotennisclub.app.session.SessionStore
import com.autotennisclub.app.session.SpinIntensity
import com.autotennisclub.app.session.TrainingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Polled rather than callback-based: the operator panel only needs a few seconds of freshness. */
fun Context.deviceStatusFlow(intervalMillis: Long = 3_000): Flow<DeviceStatus> {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    return flow {
        while (true) {
            emit(
                DeviceStatus(
                    online = runCatching {
                        connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }.getOrDefault(false),
                    bluetoothOn = runCatching { adapter?.isEnabled == true }.getOrDefault(false),
                    bluetoothPermission = bluetoothPermissions().all {
                        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                    },
                    kiosk = Kiosk.status(this@deviceStatusFlow)
                )
            )
            delay(intervalMillis)
        }
    }.distinctUntilChanged()
}

/** Saved with commit(): the paid session must be on disk before the app can die. */
class PrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("active_session", Context.MODE_PRIVATE)

    override fun load(): ActiveSession? = runCatching {
        val reference = prefs.getString("reference", null) ?: return null
        ActiveSession(
            reference = reference,
            transactionId = prefs.getString("transactionId", null),
            mode = TrainingMode.valueOf(prefs.getString("mode", null)!!),
            minutes = prefs.getInt("minutes", 0),
            priceCents = prefs.getLong("priceCents", 0),
            config = if (prefs.getBoolean("hasConfig", false)) {
                CustomConfig(
                    velocity = prefs.getInt("velocity", 80),
                    frequencyGrade = prefs.getInt("frequencyGrade", 30),
                    spin = prefs.getString("spin", null) ?: "TOPSPIN",
                    intensity = prefs.getString("intensity", null)
                        ?.let { SpinIntensity.valueOf(it) } ?: SpinIntensity.MEDIUM,
                    sequence = prefs.getString("sequence", null) ?: CustomConfig.ROTATE_POINTS,
                    zones = prefs.getString("zones", null)
                        ?.split(',')?.filter { it.isNotBlank() }?.map { LandingZone.valueOf(it) }?.toSet()
                        ?: LandingZone.entries.toSet()
                )
            } else {
                null
            },
            remainingSeconds = prefs.getLong("remainingSeconds", 0),
            savedAtMillis = prefs.getLong("savedAtMillis", 0)
        )
    }.getOrNull()

    override fun save(session: ActiveSession) {
        prefs.edit(commit = true) {
            clear()
            putString("reference", session.reference)
            putString("transactionId", session.transactionId)
            putString("mode", session.mode.name)
            putInt("minutes", session.minutes)
            putLong("priceCents", session.priceCents)
            putBoolean("hasConfig", session.config != null)
            session.config?.let {
                putInt("velocity", it.velocity)
                putInt("frequencyGrade", it.frequencyGrade)
                putString("spin", it.spin)
                putString("intensity", it.intensity.name)
                putString("sequence", it.sequence)
                putString("zones", it.zones.joinToString(",") { zone -> zone.name })
            }
            putLong("remainingSeconds", session.remainingSeconds)
            putLong("savedAtMillis", session.savedAtMillis)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }
}

class PrefsErrorLogStore(context: Context) : ErrorLogStore {
    private val prefs = context.getSharedPreferences("error_log", Context.MODE_PRIVATE)

    override fun load(): List<ErrorEntry> =
        prefs.getString("entries", null).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            ErrorEntry(at, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }

    override fun save(entries: List<ErrorEntry>) {
        val text = entries.joinToString("\n") { entry ->
            listOf(entry.atMillis.toString(), entry.code, entry.detail)
                .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
        }
        prefs.edit { putString("entries", text) }
    }
}
