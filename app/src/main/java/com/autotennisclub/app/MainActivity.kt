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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.session.CustomConfig
import com.autotennisclub.app.session.PaymentStatus
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.session.TrainingMode
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
    val session = remember { SessionController(scope, machine) }
    val state by session.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            session.dispose()
            machine.shutdown()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        SessionUi(
            state = state,
            onStart = session::start,
            onTraining = session::selectTraining,
            onDuration = session::selectDuration,
            onCustomConfig = session::updateCustomConfig,
            onConfirmCustom = session::confirmCustomConfig,
            onPayment = session::proceedToPayment,
            onPay = session::simulatePayment,
            onPaymentFailure = session::simulatePaymentFailure,
            onRetryPayment = session::retryPayment,
            onStartPaid = session::startPaidSession,
            onStop = session::stopSession,
            onFinish = session::reset,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
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
    onPaymentFailure: () -> Unit,
    onRetryPayment: () -> Unit,
    onStartPaid: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        SessionState.Idle -> HomeScreen(onStart, modifier)
        SessionState.TrainingSelection -> TrainingSelectionScreen(onTraining, modifier)
        is SessionState.DurationSelection -> DurationSelectionScreen(onDuration, modifier)
        is SessionState.CustomConfigState -> CustomConfigScreen(state, onCustomConfig, onConfirmCustom, modifier)
        is SessionState.Summary -> SummaryScreen(state, onPayment, modifier)
        is SessionState.Payment -> PaymentScreen(state, onPay, onPaymentFailure, onRetryPayment, onStartPaid, modifier)
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
    onPaymentFailure: () -> Unit,
    onRetry: () -> Unit,
    onStartPaid: () -> Unit,
    modifier: Modifier
) {
    if (state.status == PaymentStatus.SUCCESS) {
        LaunchedEffect(state) {
            delay(800)
            onStartPaid()
        }
    }
    Centered(modifier) {
        Text(priceText(state.price), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(32.dp))
        when (state.status) {
            PaymentStatus.WAITING -> {
                Button(onClick = onPay, modifier = Modifier.width(320.dp).height(90.dp)) {
                    Text("TAP YOUR CARD OR PHONE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Simulate failed payment",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onPaymentFailure).padding(8.dp)
                )
            }
            PaymentStatus.PROCESSING -> {
                Text("PROCESSING PAYMENT...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            PaymentStatus.SUCCESS -> {
                Text("PAYMENT SUCCESSFUL", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenDark)
                Spacer(Modifier.height(8.dp))
                Text("Preparing your training...", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            PaymentStatus.FAILED -> {
                Text("PAYMENT NOT COMPLETED", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                Spacer(Modifier.height(8.dp))
                Text("Try again.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.width(240.dp).height(70.dp)) {
                    Text("TRY AGAIN")
                }
            }
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
