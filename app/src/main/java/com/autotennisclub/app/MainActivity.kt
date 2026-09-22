package com.autotennisclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.session.CustomConfig
import com.autotennisclub.app.session.PaymentStatus
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.session.TrainingMode
import com.autotennisclub.app.ui.theme.AutoTennisClubTheme

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
        is SessionState.DurationSelection -> DurationSelectionScreen(state.mode, onDuration, modifier)
        is SessionState.CustomConfigState -> CustomConfigScreen(state, onCustomConfig, onConfirmCustom, modifier)
        is SessionState.Summary -> SummaryScreen(state, onPayment, modifier)
        is SessionState.Payment -> PaymentScreen(
            state = state,
            onPay = onPay,
            onPaymentFailure = onPaymentFailure,
            onRetry = onRetryPayment,
            onStartPaid = onStartPaid,
            modifier = modifier
        )
        SessionState.Preparing -> PreparingScreen(modifier)
        is SessionState.Countdown -> CountdownScreen(state.seconds, modifier)
        is SessionState.Running -> TrainingScreen(state.remainingSeconds, state.mode, onStop, modifier)
        SessionState.Complete -> CompleteScreen(onFinish, modifier)
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("AUTO TENNIS CLUB", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("PLAY • IMPROVE • ENJOY", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(40.dp))
        Text("STATION READY", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.75f).height(72.dp)) {
            Text("START", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TrainingSelectionScreen(onTraining: (TrainingMode) -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("CHOOSE TRAINING", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        ModeButton("BASIC", "Simple preset training", onTraining, TrainingMode.BASIC)
        Spacer(Modifier.height(12.dp))
        ModeButton("TRAINING", "Pre-programmed training", onTraining, TrainingMode.TRAINING)
        Spacer(Modifier.height(12.dp))
        ModeButton("CUSTOM", "Configure your session", onTraining, TrainingMode.CUSTOM)
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    onTraining: (TrainingMode) -> Unit,
    mode: TrainingMode
) {
    Button(
        onClick = { onTraining(mode) },
        modifier = Modifier.fillMaxWidth(0.75f).height(72.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DurationSelectionScreen(
    mode: TrainingMode,
    onDuration: (Int) -> Unit,
    modifier: Modifier
) {
    Centered(modifier) {
        Text("CHOOSE DURATION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(modeLabel(mode))
        Spacer(Modifier.height(24.dp))
        listOf(15 to 6.90, 30 to 12.00, 60 to 20.00).forEach { (minutes, price) ->
            Button(
                onClick = { onDuration(minutes) },
                modifier = Modifier.fillMaxWidth(0.75f).height(70.dp).padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$minutes MIN")
                    Text("€%.2f".format(price))
                }
            }
        }
    }
}

@Composable
private fun CustomConfigScreen(
    state: SessionState.CustomConfigState,
    onConfig: (CustomConfig) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier
) {
    val config = state.config
    Centered(modifier) {
        Text("CUSTOM CONFIG", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${state.minutes} MIN")
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(0.85f)) {
            Column(Modifier.padding(20.dp)) {
                Text("SPEED: ${config.velocity} km/h")
                Text("FREQUENCY: ${frequencyLabel(config.frequencyGrade)}")
                Text("SPIN: ${config.spin}")
                Text("SEQUENCE: ${config.sequence}")
                Text("LANDING ZONES: ${config.landingZones}")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Configuration time is NOT part of your paid session.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            OutlinedButton(onClick = {
                onConfig(config.copy(spin = nextSpin(config.spin)))
            }) {
                Text("SPIN: ${config.spin}")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                onConfig(config.copy(sequence = if (config.sequence == "ROTATE POINTS") "FIXED POINTS" else "ROTATE POINTS"))
            }) {
                Text(config.sequence)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
            Text("DONE / CONFIRM")
        }
    }
}

@Composable
private fun SummaryScreen(
    state: SessionState.Summary,
    onPayment: () -> Unit,
    modifier: Modifier
) {
    Centered(modifier) {
        Text(
            if (state.mode == TrainingMode.CUSTOM) "CUSTOM SUMMARY" else "SESSION SUMMARY",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(0.75f)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(modeLabel(state.mode), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${state.minutes} MIN")
                if (state.config != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("${state.config.velocity} km/h • ${state.config.spin}")
                }
                Spacer(Modifier.height(12.dp))
                Text("€%.2f".format(state.price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPayment, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
            Text("PROCEED TO PAYMENT")
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
    Centered(modifier) {
        Text("PAYMENT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("${modeLabel(state.mode)} • ${state.minutes} MIN")
        Text("€%.2f".format(state.price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        when (state.status) {
            PaymentStatus.WAITING -> {
                Text("WAITING FOR PAYMENT")
                Spacer(Modifier.height(20.dp))
                Button(onClick = onPay, modifier = Modifier.fillMaxWidth(0.75f).height(70.dp)) {
                    Text("TAP / PAY")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPaymentFailure) { Text("SIMULATE FAILED PAYMENT") }
            }
            PaymentStatus.PROCESSING -> {
                Text("PROCESSING PAYMENT…")
            }
            PaymentStatus.SUCCESS -> {
                Text("PAYMENT SUCCESS", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onStartPaid, modifier = Modifier.fillMaxWidth(0.75f).height(70.dp)) {
                    Text("CONTINUE")
                }
            }
            PaymentStatus.FAILED -> {
                Text("PAYMENT FAILED", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
                    Text("RETRY PAYMENT")
                }
            }
        }
    }
}

@Composable
private fun PreparingScreen(modifier: Modifier) {
    Centered(modifier) {
        Text("PREPARING MACHINE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Connecting and configuring the training machine…")
    }
}

@Composable
private fun CountdownScreen(seconds: Int, modifier: Modifier) {
    Centered(modifier) {
        Text("GET READY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("$seconds", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrainingScreen(
    remainingSeconds: Long,
    mode: TrainingMode,
    onStop: () -> Unit,
    modifier: Modifier
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Centered(modifier) {
        Text("TRAINING SESSION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("${modeLabel(mode)} • MACHINE ACTIVE")
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.height(64.dp)) {
            Text("STOP")
        }
    }
}

@Composable
private fun CompleteScreen(onFinish: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("✓", style = MaterialTheme.typography.displayLarge)
        Text("SESSION COMPLETE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Great training!")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
            Text("FINISH")
        }
    }
}

private fun modeLabel(mode: TrainingMode): String = when (mode) {
    TrainingMode.BASIC -> "BASIC"
    TrainingMode.TRAINING -> "TRAINING"
    TrainingMode.CUSTOM -> "CUSTOM"
}

private fun nextSpin(current: String): String = when (current) {
    "TOPSPIN" -> "BACKSPIN"
    "BACKSPIN" -> "NO SPIN"
    else -> "TOPSPIN"
}

private fun frequencyLabel(grade: Int): String {
    val seconds = grade / 10.0
    return "%.1f sec".format(seconds)
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
