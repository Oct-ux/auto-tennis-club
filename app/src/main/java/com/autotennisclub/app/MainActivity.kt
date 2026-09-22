package com.autotennisclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
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
    val sessionState by session.state.collectAsState()
    var showTrainingChoice by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            session.dispose()
            machine.shutdown()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        SessionUi(
            state = sessionState,
            showTrainingChoice = showTrainingChoice,
            onStart = {
                session.reset()
                showTrainingChoice = true
            },
            onDuration = { minutes ->
                session.selectDuration(minutes)
                showTrainingChoice = false
            },
            onContinue = session::startSelected,
            onStop = session::stopSession,
            onFinish = {
                session.reset()
                showTrainingChoice = false
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
private fun SessionUi(
    state: SessionState,
    showTrainingChoice: Boolean,
    onStart: () -> Unit,
    onDuration: (Int) -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state is SessionState.Idle && showTrainingChoice ->
            DurationChoiceScreen(onDuration, modifier)
        state is SessionState.Idle ->
            HomeScreen(onStart, modifier)
        state is SessionState.Selected ->
            DurationSelectedScreen(state.minutes, onContinue, modifier)
        state is SessionState.Preparing ->
            PreparingScreen(modifier)
        state is SessionState.Countdown ->
            CountdownScreen(state.seconds, modifier)
        state is SessionState.Running ->
            TrainingScreen(state.remainingSeconds, onStop, modifier)
        state is SessionState.Complete ->
            CompleteScreen(onFinish, modifier)
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
private fun DurationChoiceScreen(onDuration: (Int) -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("CHOOSE DURATION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        listOf(15, 30, 60).forEach { minutes ->
            Button(
                onClick = { onDuration(minutes) },
                modifier = Modifier.fillMaxWidth(0.75f).height(64.dp).padding(bottom = 8.dp)
            ) {
                Text("$minutes MIN")
            }
        }
    }
}

@Composable
private fun DurationSelectedScreen(minutes: Int, onContinue: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("SESSION SUMMARY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(0.75f)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BASIC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$minutes MIN")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
            Text("CONTINUE")
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
private fun TrainingScreen(remainingSeconds: Long, onStop: () -> Unit, modifier: Modifier) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Centered(modifier) {
        Text("TRAINING SESSION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("BASIC • MACHINE ACTIVE")
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
        Text("Great training! 🎾")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) {
            Text("FINISH")
        }
    }
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
