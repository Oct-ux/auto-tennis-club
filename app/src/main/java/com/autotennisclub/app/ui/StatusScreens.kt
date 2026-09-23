package com.autotennisclub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.ErrorRed
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary

/** Phase 4.5 production / error state card (Figma S01–S09). */
@Composable
internal fun StatusScreen(
    icon: String?,
    heading: String,
    body: String,
    modifier: Modifier,
    action: Pair<String, () -> Unit>? = null,
    footnote: String? = null,
    /** Hidden operator access on stuck screens (S01, S02, S03, S09). */
    onHeadingHold: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize()) {
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
                Text(
                    heading,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    textAlign = TextAlign.Center,
                    modifier = if (onHeadingHold != null) Modifier.holdToOpen(onHold = onHeadingHold) else Modifier
                )
                Spacer(Modifier.height(20.dp))
                Text(body, fontSize = 17.sp, color = Navy.copy(alpha = 0.78f), textAlign = TextAlign.Center)
                if (footnote != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(footnote, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(32.dp))
                StatusAction(action, width = 340)
            }
        }
    }
}

/**
 * Figma 09 overlay: dims the training screen and shows the state in a card at
 * the bottom, so the paused timer stays visible. Blocks touches underneath.
 */
@Composable
internal fun StatusOverlay(
    icon: String?,
    heading: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    footnote: String? = null
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Navy.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = 32.dp)
                .widthIn(max = 1000.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (icon != null) {
                    Text(icon, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Navy)
                }
                Column(Modifier.weight(1f)) {
                    Text(heading, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.height(8.dp))
                    Text(body, fontSize = 17.sp, color = Navy.copy(alpha = 0.78f))
                    if (footnote != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(footnote, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }
                StatusAction(action, width = 300)
            }
        }
    }
}

@Composable
private fun StatusAction(action: Pair<String, () -> Unit>?, width: Int) {
    if (action != null) {
        Button(
            onClick = action.second,
            modifier = Modifier.width(width.dp).height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(action.first, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        CircularProgressIndicator(color = Green, modifier = Modifier.size(46.dp))
    }
}

/** Hidden operator access: fires only after holding for [holdMillis]; a shorter tap does nothing. */
fun Modifier.holdToOpen(holdMillis: Long = 5_000, onHold: () -> Unit): Modifier =
    pointerInput(onHold) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var released = false
            withTimeoutOrNull(holdMillis) {
                waitForUpOrCancellation()
                released = true
            }
            if (!released) onHold()
        }
    }

/** Operator PIN pad. Three wrong PINs close it. */
@Composable
internal fun PinDialog(expectedPin: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("OPERATOR PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (entered.isEmpty()) "—" else "●".repeat(entered.length),
                    fontSize = 28.sp,
                    color = Navy
                )
                if (failures > 0) {
                    Text("Wrong PIN", fontSize = 14.sp, color = ErrorRed)
                }
                Spacer(Modifier.height(16.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    when (key) {
                                        "C" -> entered = ""
                                        "OK" -> if (entered == expectedPin) {
                                            onSuccess()
                                        } else {
                                            entered = ""
                                            failures++
                                            if (failures >= 3) onDismiss()
                                        }
                                        else -> if (entered.length < 8) entered += key
                                    }
                                },
                                modifier = Modifier.size(width = 80.dp, height = 64.dp)
                            ) {
                                Text(key, fontSize = 20.sp, color = Navy)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
