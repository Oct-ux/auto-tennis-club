package com.autotennisclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.debug.DebugPanel
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.session.CustomConfig
import com.autotennisclub.app.session.PaymentState
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.session.TrainingMode
import com.autotennisclub.app.session.UnavailableReason
import com.autotennisclub.app.ui.theme.AutoTennisClubTheme
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.ErrorRed
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.GreenDark
import com.autotennisclub.app.ui.theme.GreenPale
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AutoTennisClubTheme { AutoTennisClubApp() } }
    }
}

@Composable
fun AutoTennisClubApp() {
    val scope = rememberCoroutineScope()
    val machine = remember { MockPusunMachine(scope) }
    val payments = remember { MockPaymentGateway() }
    val session = remember { SessionController(scope, machine, payments) }
    val state by session.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            session.dispose()
            machine.shutdown()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(Modifier.fillMaxSize()) {
            SessionUi(
                state = state,
                onStart = session::start,
                onTraining = session::selectTraining,
                onDuration = session::selectDuration,
                onCustomConfig = session::updateCustomConfig,
                onConfirmCustom = session::confirmCustomConfig,
                onPayment = session::proceedToPayment,
                onPay = session::pay,
                onRetryPayment = session::retryPayment,
                onCancelPayment = session::cancelPayment,
                onStartPaid = session::startPaidSession,
                onStop = session::stopSession,
                onFinish = session::reset,
                onBallsReturned = session::confirmBallsReturned,
                onSelfCheck = session::runSelfCheck,
                onExitMaintenance = session::exitMaintenance,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            if (BuildConfig.DEBUG) {
                DebugPanel(
                    state = state,
                    session = session,
                    machine = machine,
                    payments = payments,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionUi(
    state: SessionState,
    onStart: () -> Unit,
    onTraining: (TrainingMode) -> Unit,
    onDuration: (Int) -> Unit,
    onCustomConfig: (CustomConfig) -> Unit,
    onConfirmCustom: () -> Unit,
    onPayment: () -> Unit,
    onPay: () -> Unit,
    onRetryPayment: () -> Unit,
    onCancelPayment: () -> Unit,
    onStartPaid: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onBallsReturned: () -> Unit,
    onSelfCheck: () -> Unit,
    onExitMaintenance: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        SessionState.StartingUp -> StatusScreen(
            label = "S01", title = "STATION STARTUP", icon = "✓",
            heading = "Starting station...",
            body = "Checking tablet, application, network, Bluetooth, machine and payment readiness.",
            modifier = modifier
        )
        is SessionState.Unavailable -> StatusScreen(
            label = "S02", title = "STATION UNAVAILABLE", icon = "!",
            heading = "Station temporarily unavailable",
            body = "This station is currently unavailable. Please try again later.",
            action = "TRY AGAIN" to onSelfCheck,
            modifier = modifier
        )
        is SessionState.MachineFault -> StatusScreen(
            label = "S03", title = "MACHINE ERROR", icon = "!",
            heading = "Machine error detected",
            body = "The training machine has detected an error. Please wait while we check the station.",
            action = "TRY AGAIN" to onSelfCheck,
            modifier = modifier
        )
        is SessionState.BallsRequired -> StatusScreen(
            label = "S04", title = "BALLS NEEDED", icon = "●",
            heading = "Return the balls to the machine",
            body = "Collect the balls and return them to the machine to continue. Your session is paused.",
            action = "I’VE RETURNED THE BALLS" to onBallsReturned,
            modifier = modifier
        )
        is SessionState.Reconnecting -> StatusScreen(
            label = "S05", title = "CONNECTION LOST", icon = "●",
            heading = "Reconnecting to machine...",
            body = "Your session is paused while we try to restore the Bluetooth connection.",
            modifier = modifier
        )
        is SessionState.Recovering -> StatusScreen(
            label = "S09", title = "SESSION INTERRUPTED", icon = null,
            heading = "Recovering session",
            body = "We're checking payment, machine and timer status before continuing.",
            modifier = modifier
        )
        SessionState.Maintenance -> MaintenanceScreen(onExitMaintenance, modifier)
        SessionState.Idle -> HomeScreen(onStart, modifier)
        SessionState.TrainingSelection -> TrainingSelectionScreen(onTraining, modifier)
        is SessionState.DurationSelection -> DurationSelectionScreen(onDuration, modifier)
        is SessionState.CustomConfigState -> CustomConfigScreen(state, onCustomConfig, onConfirmCustom, modifier)
        is SessionState.Summary -> SummaryScreen(state, onPayment, modifier)
        is SessionState.Payment -> when (state.status) {
            PaymentState.VERIFYING -> StatusScreen(
                label = "S08", title = "VERIFYING PAYMENT", icon = "●",
                heading = "Please wait",
                body = "We're verifying your payment. Do not pay again while verification is in progress.",
                modifier = modifier
            )
            PaymentState.TIMEOUT -> StatusScreen(
                label = "S06", title = "PAYMENT TIMEOUT", icon = "!",
                heading = "Payment terminal did not respond",
                body = "We didn't receive a response from the payment terminal. Please try again.",
                action = "TRY AGAIN" to onRetryPayment,
                modifier = modifier
            )
            PaymentState.CANCELLED -> StatusScreen(
                label = "S07", title = "PAYMENT CANCELLED", icon = "!",
                heading = "Payment cancelled",
                body = "The payment was cancelled. No completed payment should be treated as successful.",
                action = "TRY AGAIN" to onRetryPayment,
                modifier = modifier
            )
            else -> PaymentScreen(state, onPay, onRetryPayment, onCancelPayment, onStartPaid, modifier)
        }
        SessionState.Preparing -> PreparingScreen(modifier)
        is SessionState.Countdown -> CountdownScreen(state.seconds, modifier)
        is SessionState.Running -> TrainingScreen(state.remainingSeconds, state.mode, onStop, modifier)
        is SessionState.Complete -> CompleteScreen(state.minutes, state.mode, onFinish, modifier)
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        Centered(Modifier.fillMaxSize()) {
            Text("AUTO TENNIS CLUB", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenDark)
            Spacer(Modifier.height(16.dp))
            Text("PLAY • IMPROVE • ENJOY", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your private training session starts here.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart, modifier = Modifier.width(300.dp).height(90.dp)) {
                Text("START", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Text("ES · CAT · EN", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        StatusBadge("STATION READY", modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

@Composable
private fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    Text("●  $text", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenDark, modifier = modifier)
}

@Composable
private fun TrainingSelectionScreen(onTraining: (TrainingMode) -> Unit, modifier: Modifier) {
    var selected by remember { mutableStateOf<TrainingMode?>(null) }
    Centered(modifier) {
        Text("CHOOSE YOUR TRAINING", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrainingMode.entries.forEach { mode ->
                SelectableCard(
                    label = modeLabel(mode),
                    selected = selected == mode,
                    onClick = { selected = mode },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { selected?.let(onTraining) },
            enabled = selected != null,
            modifier = Modifier.width(240.dp).height(70.dp)
        ) {
            Text("CONTINUE")
        }
    }
}

@Composable
private fun SelectableCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    subLabelColor: Color = GreenDark
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) GreenPale else Color.White),
        border = BorderStroke(2.dp, if (selected) Green else BorderGray)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
            if (subLabel != null) {
                Spacer(Modifier.height(12.dp))
                Text(subLabel, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = subLabelColor)
            }
        }
    }
}

@Composable
private fun DurationSelectionScreen(onDuration: (Int) -> Unit, modifier: Modifier) {
    var selected by remember { mutableStateOf<Int?>(null) }
    Centered(modifier) {
        Text("CHOOSE DURATION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                SelectableCard(
                    label = "$minutes MIN",
                    subLabel = priceFor(minutes),
                    selected = selected == minutes,
                    onClick = { selected = minutes },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { selected?.let(onDuration) },
            enabled = selected != null,
            modifier = Modifier.width(240.dp).height(70.dp)
        ) {
            Text("CONTINUE")
        }
    }
}

private fun priceFor(minutes: Int): String = when (minutes) {
    15 -> "€6.90"
    30 -> "€12"
    60 -> "€20"
    else -> ""
}

private fun priceText(price: Double): String {
    val formatted = "€%.2f".format(price)
    return if (formatted.endsWith(".00")) formatted.dropLast(3) else formatted
}

@Composable
private fun CustomConfigScreen(
    state: SessionState.CustomConfigState,
    onConfig: (CustomConfig) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier
) {
    val config = state.config
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CUSTOM TRAINING", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(4.dp))
        Text("${state.minutes} MIN", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Column(Modifier.padding(24.dp)) {
                ConfigSlider(
                    label = "SPEED",
                    valueLabel = "${config.velocity} km/h",
                    value = config.velocity.toFloat(),
                    range = 40f..120f,
                    onChange = { onConfig(config.copy(velocity = it.toInt())) }
                )
                Spacer(Modifier.height(20.dp))
                ConfigSlider(
                    label = "FREQUENCY",
                    valueLabel = frequencyLabel(config.frequencyGrade),
                    value = config.frequencyGrade.toFloat(),
                    range = 10f..50f,
                    onChange = { onConfig(config.copy(frequencyGrade = it.toInt())) }
                )
                Spacer(Modifier.height(24.dp))
                Text("SPIN", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NONE", "TOPSPIN", "BACKSPIN").forEach { spin ->
                        OptionPill(
                            label = spin,
                            selected = config.spin == spin,
                            onClick = { onConfig(config.copy(spin = spin)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("SEQUENCE MODE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FIXED POINT", "ROTATE POINTS").forEach { seq ->
                        OptionPill(
                            label = seq,
                            selected = config.sequence == seq,
                            onClick = { onConfig(config.copy(sequence = seq)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Configuration time is NOT part of your paid session.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirm, modifier = Modifier.width(280.dp).height(70.dp)) {
            Text("CONFIRM")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text(valueLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green)
        )
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) GreenPale else Color.White),
        border = BorderStroke(1.5.dp, if (selected) Green else BorderGray)
    ) {
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
    }
}

private fun frequencyLabel(grade: Int): String = "%.1f s".format(grade / 10.0)

@Composable
private fun SummaryScreen(state: SessionState.Summary, onPayment: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text(
            if (state.mode == TrainingMode.CUSTOM) "CUSTOM SUMMARY" else "SESSION SUMMARY",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryCard(label = "TRAINING", value = modeLabel(state.mode), modifier = Modifier.weight(1f))
            SummaryCard(label = "DURATION", value = "${state.minutes} MIN", modifier = Modifier.weight(1f))
            SummaryCard(
                label = "PRICE",
                value = priceText(state.price),
                valueColor = GreenDark,
                modifier = Modifier.weight(1f)
            )
        }
        if (state.config != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "${state.config.velocity} km/h · ${state.config.spin}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPayment, modifier = Modifier.width(300.dp).height(75.dp)) {
            Text("PROCEED TO PAYMENT")
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Navy
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
        }
    }
}

@Composable
private fun PaymentScreen(
    state: SessionState.Payment,
    onPay: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onStartPaid: () -> Unit,
    modifier: Modifier
) {
    if (state.status == PaymentState.SUCCESS) {
        LaunchedEffect(state) {
            delay(800)
            onStartPaid()
        }
    }
    Centered(modifier) {
        Text(priceText(state.price), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(32.dp))
        when (state.status) {
            PaymentState.WAITING -> {
                Button(onClick = onPay, modifier = Modifier.width(320.dp).height(90.dp)) {
                    Text("TAP YOUR CARD OR PHONE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "CANCEL PAYMENT",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onCancel).padding(8.dp)
                )
            }
            PaymentState.PROCESSING -> {
                Text("PROCESSING PAYMENT...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            PaymentState.SUCCESS -> {
                Text("PAYMENT SUCCESSFUL", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenDark)
                Spacer(Modifier.height(8.dp))
                Text("Preparing your training...", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            PaymentState.FAILED -> {
                Text("PAYMENT NOT COMPLETED", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                Spacer(Modifier.height(8.dp))
                Text("Try again.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.width(240.dp).height(70.dp)) {
                    Text("TRY AGAIN")
                }
            }
            // Rendered as full-screen Phase 4.5 states in SessionUi.
            PaymentState.VERIFYING, PaymentState.CANCELLED, PaymentState.TIMEOUT -> Unit
        }
    }
}

/** Phase 4.5 production / error state card (Figma S01–S09). */
@Composable
private fun StatusScreen(
    label: String,
    title: String,
    icon: String?,
    heading: String,
    body: String,
    modifier: Modifier,
    action: Pair<String, () -> Unit>? = null
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 32.dp, top = 24.dp)) {
            Text(label, fontSize = 12.sp, color = Green)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Card(
            modifier = Modifier.align(Alignment.Center).width(680.dp).height(450.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Text(icon, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.height(16.dp))
                }
                Text(heading, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Text(body, fontSize = 17.sp, color = Navy.copy(alpha = 0.78f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                if (action != null) {
                    Button(
                        onClick = action.second,
                        modifier = Modifier.width(340.dp).height(58.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(action.first, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    CircularProgressIndicator(color = Green, modifier = Modifier.size(46.dp))
                }
            }
        }
        Text(
            "PHASE 4.5 · PRODUCTION / ERROR STATE",
            fontSize = 13.sp,
            color = Navy,
            modifier = Modifier.align(Alignment.BottomStart).padding(32.dp)
        )
    }
}

/** Operator-only layer. Diagnostics and machine tests come with hardware integration. */
@Composable
private fun MaintenanceScreen(onExit: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("MAINTENANCE MODE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(8.dp))
        Text("OPERATOR ONLY · NOT PART OF CUSTOMER FLOW", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onExit, modifier = Modifier.width(270.dp).height(58.dp)) {
            Text("RESET STATION")
        }
    }
}

@Composable
private fun PreparingScreen(modifier: Modifier) {
    Centered(modifier) {
        Text("Preparing your training...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(16.dp))
        Text("Connecting to machine...", fontSize = 17.sp, color = TextSecondary)
    }
}

@Composable
private fun CountdownScreen(seconds: Int, modifier: Modifier) {
    Centered(modifier) {
        Text("GET READY", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        Spacer(Modifier.height(24.dp))
        Text("$seconds", fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(24.dp))
        Text("Training starts automatically", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

@Composable
private fun TrainingScreen(remainingSeconds: Long, mode: TrainingMode, onStop: () -> Unit, modifier: Modifier) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Box(modifier.fillMaxSize()) {
        Centered(Modifier.fillMaxSize()) {
            Text("TRAINING SESSION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text("TIME REMAINING", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Text(modeLabel(mode), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GreenDark)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStop, modifier = Modifier.width(240.dp).height(75.dp)) {
                Text("STOP")
            }
        }
        StatusBadge("MACHINE ACTIVE", modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

@Composable
private fun CompleteScreen(minutes: Int, mode: TrainingMode, onFinish: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = GreenDark, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("SESSION COMPLETE", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(12.dp))
        Text("Great training!", fontSize = 22.sp, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Text("$minutes MIN · ${modeLabel(mode)}", style = MaterialTheme.typography.titleLarge, color = Navy)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.width(240.dp).height(75.dp)) {
            Text("FINISH")
        }
    }
}

private fun modeLabel(mode: TrainingMode): String = when (mode) {
    TrainingMode.BASIC -> "BASIC"
    TrainingMode.TRAINING -> "TRAINING"
    TrainingMode.CUSTOM -> "CUSTOM"
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/** Phase 4.5 production / error states (Figma S01–S09 + Maintenance). */
private class PhaseStatesProvider : PreviewParameterProvider<SessionState> {
    override val values = sequenceOf(
        SessionState.StartingUp,
        SessionState.Unavailable(UnavailableReason.MACHINE_FAULT),
        SessionState.MachineFault("WHEEL_PROTECTION", 1),
        SessionState.BallsRequired(600, TrainingMode.BASIC),
        SessionState.Reconnecting(600, TrainingMode.BASIC, attempt = 1),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.TIMEOUT),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.CANCELLED),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.VERIFYING),
        SessionState.Recovering(600, TrainingMode.BASIC),
        SessionState.Maintenance
    )
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PhaseStatesPreview(@PreviewParameter(PhaseStatesProvider::class) state: SessionState) {
    AutoTennisClubTheme {
        SessionUi(
            state = state,
            onStart = {},
            onTraining = {},
            onDuration = {},
            onCustomConfig = {},
            onConfirmCustom = {},
            onPayment = {},
            onPay = {},
            onRetryPayment = {},
            onCancelPayment = {},
            onStartPaid = {},
            onStop = {},
            onFinish = {},
            onBallsReturned = {},
            onSelfCheck = {},
            onExitMaintenance = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
