package com.autotennisclub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.pusun.PusunBleConfig
import com.autotennisclub.app.session.MachineTest
import com.autotennisclub.app.session.PaymentState
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.session.formatSeconds
import com.autotennisclub.app.station.StationState
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.GreenPale
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MaintenanceActions(
    val onClose: () -> Unit = {},
    val onResetStation: () -> Unit = {},
    val onReconnect: () -> Unit = {},
    val onTestPayment: () -> Unit = {},
    val onEndSession: () -> Unit = {},
    val onMachineTest: (MachineTest) -> Unit = {},
    /** Only with the simulated machine. */
    val onSimulateFault: (() -> Unit)? = null
)

/** Figma THIRD LAYER — MAINTENANCE MODE / OPERATOR. */
@Composable
internal fun MaintenanceScreen(station: StationState, actions: MaintenanceActions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("THIRD LAYER", fontSize = 14.sp, color = Navy)
                Text("MAINTENANCE MODE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            Spacer(Modifier.weight(1f))
            Text("OPERATOR ONLY · NOT PART OF CUSTOMER FLOW", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
            TextButton(onClick = actions.onClose) { Text("CLOSE", color = Navy, fontWeight = FontWeight.Bold) }
        }

        Row(Modifier.height(190.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            val diagnostics = station.sessionDiagnostics
            InfoCard(
                "STATION STATUS",
                "Tablet" to "ONLINE",
                "Network" to if (station.online) "ONLINE" else "OFFLINE",
                "Bluetooth" to if (station.bluetoothOn) "ON" else "OFF",
                "Machine" to machineLabel(station.machine),
                "Payment" to readyLabel(diagnostics.paymentReady)
            )
            InfoCard(
                "MACHINE",
                "BLE service" to shortUuid(PusunBleConfig.SERVICE_UUID.toString()),
                "Notify" to shortUuid(PusunBleConfig.NOTIFY_UUID.toString()),
                "Write" to shortUuid(PusunBleConfig.WRITE_UUID.toString()),
                "Last fault" to (station.machineDiagnostics.lastFault ?: "NONE"),
                "Last response" to (station.machineDiagnostics.lastResponseAtMillis?.let(::clockTime) ?: "--")
            )
            InfoCard(
                "PAYMENT",
                "Provider" to station.paymentProvider,
                "Connection" to readyLabel(diagnostics.paymentReady),
                "Last result" to (diagnostics.lastPayment?.name ?: "--"),
                "Verification" to if ((station.session as? SessionState.Payment)?.status == PaymentState.VERIFYING) "IN PROGRESS" else "IDLE",
                "Last ref" to (diagnostics.lastReference?.take(8)?.uppercase() ?: "--")
            )
            val active = station.activeSession
            InfoCard(
                "RECOVERY",
                "Active session" to (active?.shortReference ?: "NONE"),
                "Timer state" to (active?.let { formatSeconds(it.remainingSeconds) } ?: "--"),
                "Payment state" to (active?.let { if (it.paid) "PAID" else "PENDING" } ?: "--"),
                "Saved at" to (active?.let { clockTime(it.savedAtMillis) } ?: "--")
            )
        }

        Row(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            PanelCard("MACHINE TEST") {
                OperatorButton("START TEST") { actions.onMachineTest(MachineTest.START) }
                OperatorButton("STOP TEST") { actions.onMachineTest(MachineTest.STOP) }
                OperatorButton("SPEED / FREQUENCY TEST") { actions.onMachineTest(MachineTest.SPEED_FREQUENCY) }
                OperatorButton("SPIN TEST") { actions.onMachineTest(MachineTest.SPIN) }
                OperatorButton("FAULT SIMULATION", enabled = actions.onSimulateFault != null) {
                    actions.onSimulateFault?.invoke()
                }
            }
            PanelCard("ERROR LOG") {
                if (station.errors.isEmpty()) {
                    Text("No recent errors", fontSize = 14.sp, color = Navy)
                } else {
                    station.errors.take(6).forEach { entry ->
                        Text(
                            "${clockTime(entry.atMillis)}  ${entry.code}  ${entry.detail}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Navy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("OPERATOR ACTIONS", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    OperatorButton("RESET STATION", Modifier.weight(1f), onClick = actions.onResetStation)
                    OperatorButton("RECONNECT BLE", Modifier.weight(1f), onClick = actions.onReconnect)
                    OperatorButton("TEST PAYMENT", Modifier.weight(1f), onClick = actions.onTestPayment)
                    OperatorButton(
                        "END SESSION",
                        Modifier.weight(1f),
                        enabled = station.activeSession != null,
                        onClick = actions.onEndSession
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.InfoCard(title: String, vararg rows: Pair<String, String>) {
    PanelCard(title) {
        rows.forEach { (label, value) ->
            Row {
                Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RowScope.PanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun OperatorButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GreenPale, contentColor = Color.Black),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(label, fontSize = 13.sp)
    }
}

private fun machineLabel(state: MachineState): String = when (state) {
    MachineState.Disconnected -> "DISCONNECTED"
    MachineState.Connecting -> "CONNECTING"
    MachineState.Connected -> "CONNECTED"
    MachineState.Configuring -> "CONFIGURING"
    MachineState.Ready -> "READY"
    MachineState.Running -> "RUNNING"
    is MachineState.Reconnecting -> "RECONNECTING (${state.attempt})"
    MachineState.ConnectionFailed -> "CONNECTION FAILED"
    MachineState.OutOfBalls -> "NO BALLS"
    is MachineState.Fault -> "FAULT ${state.code}"
}

private fun readyLabel(ready: Boolean?): String = when (ready) {
    true -> "READY"
    false -> "NOT READY"
    null -> "--"
}

private fun shortUuid(uuid: String): String = uuid.substring(4, 8).uppercase()

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun clockTime(millis: Long): String =
    timeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
