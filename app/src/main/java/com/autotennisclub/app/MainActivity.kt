package com.autotennisclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.ui.theme.AutoTennisClubTheme
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.GreenDark
import com.autotennisclub.app.ui.theme.GreenPale
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary

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
            CompleteScreen(state.minutes, onFinish, modifier)
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
private fun DurationChoiceScreen(onDuration: (Int) -> Unit, modifier: Modifier) {
    var selected by remember { mutableStateOf<Int?>(null) }
    Centered(modifier) {
        Text("CHOOSE DURATION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                DurationCard(
                    minutes = minutes,
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

@Composable
private fun DurationCard(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            Text("$minutes MIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(12.dp))
            Text(priceFor(minutes), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        }
    }
}

@Composable
private fun DurationSelectedScreen(minutes: Int, onContinue: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text("SESSION SUMMARY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryCard(label = "TRAINING", value = "BASIC", modifier = Modifier.weight(1f))
            SummaryCard(label = "DURATION", value = "$minutes MIN", modifier = Modifier.weight(1f))
            SummaryCard(
                label = "PRICE",
                value = priceFor(minutes),
                valueColor = GreenDark,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue, modifier = Modifier.width(300.dp).height(75.dp)) {
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

private fun priceFor(minutes: Int): String = when (minutes) {
    15 -> "€6.90"
    30 -> "€12"
    60 -> "€20"
    else -> ""
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
private fun TrainingScreen(remainingSeconds: Long, onStop: () -> Unit, modifier: Modifier) {
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
            Text("BASIC", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GreenDark)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStop, modifier = Modifier.width(240.dp).height(75.dp)) {
                Text("STOP")
            }
        }
        StatusBadge("MACHINE ACTIVE", modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

@Composable
private fun CompleteScreen(minutes: Int, onFinish: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = GreenDark, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("SESSION COMPLETE", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(12.dp))
        Text("Great training!", fontSize = 22.sp, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Text("$minutes MIN · BASIC", style = MaterialTheme.typography.titleLarge, color = Navy)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.width(240.dp).height(75.dp)) {
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
