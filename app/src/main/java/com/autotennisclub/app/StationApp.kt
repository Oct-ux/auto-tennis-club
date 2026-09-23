package com.autotennisclub.app

import android.app.Application
import android.content.Context
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
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
    val machine = MockPusunMachine(scope)
    val payments = MockPaymentGateway()
    val errors = ErrorLog(PrefsErrorLogStore(context))
    val session = SessionController(scope, machine, payments, PrefsSessionStore(context), errors)
    val state = stationState(scope, session, machine, payments.providerName, errors, context.deviceStatusFlow())
}
