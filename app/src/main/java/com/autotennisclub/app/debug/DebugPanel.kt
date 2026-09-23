package com.autotennisclub.app.debug

import android.os.Process
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.payment.MockPaymentGateway.Outcome
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.ErrorRed
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary

/**
 * Debug-build only: forces the Phase 4.5 error states on the mock machine so
 * they can be reviewed without real hardware failures.
 */
@Composable
fun DebugPanel(
    state: SessionState,
    session: SessionController,
    machine: MockPusunMachine,
    payments: MockPaymentGateway,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Column(modifier, horizontalAlignment = Alignment.End) {
        if (open) {
            Card(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Now: ${state::class.simpleName}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Section("STATION")
                    DebugButton("S01 · Self check", onClick = session::runSelfCheck)
                    DebugButton("Maintenance mode") { session.enterMaintenance() }
                    // Reopen the app within 10 min to see S09 resume the saved session.
                    DebugButton("S09 · Kill app (reopen it)") { Process.killProcess(Process.myPid()) }

                    Section("MACHINE")
                    DebugButton("S03/S02 · Machine fault") { machine.simulateFault(1) }
                    DebugButton("S04 · Out of balls") { machine.simulateFault(3) }
                    DebugButton("S05 · Connection lost (fails after 5 tries)", onClick = machine::simulateConnectionLost)
                    DebugButton("Machine OK (reconnect / clear fault)", onClick = machine::simulateMachineOk)

                    val nextOutcome by payments.nextOutcome.collectAsState()
                    val terminalReady by payments.ready.collectAsState()
                    Section("PAYMENT · next attempt: $nextOutcome")
                    DebugButton("S06 · Terminal timeout") { payments.setNextOutcome(Outcome.TIMEOUT) }
                    DebugButton("S07 · Cancelled on terminal") { payments.setNextOutcome(Outcome.CANCELLED) }
                    DebugButton("Card declined") { payments.setNextOutcome(Outcome.FAILED) }
                    DebugButton("S08 · Verification fails") { payments.setNextOutcome(Outcome.UNVERIFIED) }
                    DebugButton(if (terminalReady) "S02 · Terminal offline" else "Terminal back online") {
                        payments.setReady(!terminalReady)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { open = !open },
            modifier = Modifier.padding(top = 8.dp),
            border = BorderStroke(1.dp, ErrorRed)
        ) {
            Text(if (open) "CLOSE DEBUG" else "DEBUG", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Navy,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DebugButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}
