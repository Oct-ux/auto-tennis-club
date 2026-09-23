package com.autotennisclub.app.station

import com.autotennisclub.app.machine.MachineDiagnostics
import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.machine.TennisMachine
import com.autotennisclub.app.session.ActiveSession
import com.autotennisclub.app.session.ErrorEntry
import com.autotennisclub.app.session.ErrorLog
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionDiagnostics
import com.autotennisclub.app.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Read-only view of the whole station: tablet, machine, payment, session,
 * recovery and maintenance. Nothing writes to it; each part has one owner.
 */
data class StationState(
    val session: SessionState = SessionState.StartingUp,
    val machine: MachineState = MachineState.Disconnected,
    val machineDiagnostics: MachineDiagnostics = MachineDiagnostics(),
    val paymentProvider: String = "",
    val sessionDiagnostics: SessionDiagnostics = SessionDiagnostics(),
    val activeSession: ActiveSession? = null,
    val online: Boolean = false,
    val bluetoothOn: Boolean = false,
    val errors: List<ErrorEntry> = emptyList()
) {
    val maintenance: Boolean get() = session == SessionState.Maintenance
}

fun stationState(
    scope: CoroutineScope,
    session: SessionController,
    machine: TennisMachine,
    paymentProvider: String,
    errors: ErrorLog,
    online: Flow<Boolean>,
    bluetoothOn: Flow<Boolean>
): StateFlow<StationState> {
    val core = combine(
        session.state,
        machine.state,
        machine.diagnostics,
        session.diagnostics,
        session.activeSession
    ) { sessionState, machineState, machineDiagnostics, sessionDiagnostics, active ->
        StationState(
            session = sessionState,
            machine = machineState,
            machineDiagnostics = machineDiagnostics,
            paymentProvider = paymentProvider,
            sessionDiagnostics = sessionDiagnostics,
            activeSession = active
        )
    }
    return combine(core, errors.entries, online, bluetoothOn) { station, entries, isOnline, btOn ->
        station.copy(errors = entries, online = isOnline, bluetoothOn = btOn)
    }.stateIn(scope, SharingStarted.Eagerly, StationState(paymentProvider = paymentProvider))
}
