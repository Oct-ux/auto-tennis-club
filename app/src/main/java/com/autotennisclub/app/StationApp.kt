package com.autotennisclub.app

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.autotennisclub.app.ble.AndroidBleGatt
import com.autotennisclub.app.machine.MachineController
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.machine.TennisMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.payment.NotConfiguredPaymentGateway
import com.autotennisclub.app.payment.PaymentGateway
import com.autotennisclub.app.session.ErrorLog
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.station.PrefsErrorLogStore
import com.autotennisclub.app.station.PrefsSessionStore
import com.autotennisclub.app.station.deviceStatusFlow
import com.autotennisclub.app.station.stationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StationApp : Application() {
    /**
     * One station per process. Every Activity instance (launcher icon, kiosk home
     * screen, recreation after a configuration change) shows the same session and
     * shares one machine connection.
     */
    lateinit var station: Station
        private set

    override fun onCreate() {
        super.onCreate()
        station = Station(this)
    }
}

class Station(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Demo builds simulate the MAX B and the card terminal; production talks to the real ones. */
    val simulatedMachine: MockPusunMachine? = if (BuildConfig.SIMULATED) MockPusunMachine(scope) else null
    val simulatedPayments: MockPaymentGateway? = if (BuildConfig.SIMULATED) MockPaymentGateway() else null

    val machine: TennisMachine = simulatedMachine ?: MachineController(
        scope,
        AndroidBleGatt(context, context.getSystemService(BluetoothManager::class.java)?.adapter)
    )
    // TODO(phase 6): SumUpPaymentGateway.
    val payments: PaymentGateway = simulatedPayments ?: NotConfiguredPaymentGateway

    val errors = ErrorLog(PrefsErrorLogStore(context))
    val session = SessionController(scope, machine, payments, PrefsSessionStore(context), errors)
    val state = stationState(scope, session, machine, payments.providerName, errors, context.deviceStatusFlow())
}
