package com.autotennisclub.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotennisclub.app.debug.DebugPanel
import com.autotennisclub.app.kiosk.Kiosk
import com.autotennisclub.app.kiosk.KioskStatus
import com.autotennisclub.app.kiosk.bluetoothPermissions
import com.autotennisclub.app.machine.MachineState
import com.autotennisclub.app.machine.MockPusunMachine
import com.autotennisclub.app.payment.MockPaymentGateway
import com.autotennisclub.app.session.CustomConfig
import com.autotennisclub.app.session.ErrorEntry
import com.autotennisclub.app.session.ErrorLog
import com.autotennisclub.app.session.PaymentState
import com.autotennisclub.app.session.RecoveryStep
import com.autotennisclub.app.session.SessionController
import com.autotennisclub.app.session.SessionDiagnostics
import com.autotennisclub.app.session.SessionState
import com.autotennisclub.app.session.TrainingMode
import com.autotennisclub.app.session.TrainingOverlay
import com.autotennisclub.app.session.UnavailableReason
import com.autotennisclub.app.session.trainingOverlay
import com.autotennisclub.app.station.PrefsErrorLogStore
import com.autotennisclub.app.station.PrefsSessionStore
import com.autotennisclub.app.station.DeviceStatus
import com.autotennisclub.app.station.StationState
import com.autotennisclub.app.station.deviceStatusFlow
import com.autotennisclub.app.station.stationState
import com.autotennisclub.app.ui.AppLanguage
import com.autotennisclub.app.ui.LanguagePicker
import com.autotennisclub.app.ui.LocalizedContent
import com.autotennisclub.app.ui.MaintenanceActions
import com.autotennisclub.app.ui.MaintenanceScreen
import com.autotennisclub.app.ui.PinDialog
import com.autotennisclub.app.ui.StatusOverlay
import com.autotennisclub.app.ui.StatusScreen
import com.autotennisclub.app.ui.holdToOpen
import com.autotennisclub.app.ui.theme.AutoTennisClubTheme
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.ErrorRed
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.GreenDark
import com.autotennisclub.app.ui.theme.GreenPale
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Currency

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before any UI: on the station tablet this also grants the Bluetooth permissions.
        Kiosk.configure(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AutoTennisClubTheme {
                AutoTennisClubApp(
                    onAndroidSettings = { Kiosk.openAndroidSettings(this) },
                    onRemoveKiosk = { Kiosk.remove(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        Kiosk.lock(this)
    }

    /** Kiosk look: status and navigation bars stay hidden; a swipe shows them briefly. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Everything the customer screens can do; the defaults keep previews short. */
data class SessionActions(
    val onStart: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onTraining: (TrainingMode) -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onCustomConfig: (CustomConfig) -> Unit = {},
    val onConfirmCustom: () -> Unit = {},
    val onPayment: () -> Unit = {},
    val onPay: () -> Unit = {},
    val onRetryPayment: () -> Unit = {},
    val onCancelPayment: () -> Unit = {},
    val onStartPaid: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onBallsReturned: () -> Unit = {},
    val onSelfCheck: () -> Unit = {},
    /** Hidden 5-second hold on the logo or a status heading: opens the operator PIN. */
    val onOperatorAccess: () -> Unit = {}
)

@Composable
fun AutoTennisClubApp(onAndroidSettings: () -> Unit = {}, onRemoveKiosk: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val machine = remember { MockPusunMachine(scope) }
    val payments = remember { MockPaymentGateway() }
    val errors = remember { ErrorLog(PrefsErrorLogStore(context)) }
    val session = remember { SessionController(scope, machine, payments, PrefsSessionStore(context), errors) }
    val station = remember {
        stationState(scope, session, machine, payments.providerName, errors, context.deviceStatusFlow())
    }
    val state by station.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(AppLanguage.DEFAULT) }

    // Every customer starts in the default language: reset whenever the station is back at Home.
    val atHome = state.session == SessionState.Idle
    LaunchedEffect(atHome) {
        if (atHome) language = AppLanguage.DEFAULT
    }

    // The real MAX B needs these; on the station tablet the kiosk setup has already granted them.
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val current = state.session
        if (granted.values.any { it } && (current == SessionState.StartingUp || current is SessionState.Unavailable)) {
            session.runSelfCheck()
        }
    }
    LaunchedEffect(Unit) {
        val missing = bluetoothPermissions().filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose {
            session.dispose()
            machine.shutdown()
        }
    }

    val actions = remember(session) {
        SessionActions(
            onStart = session::start,
            onLanguage = { language = it },
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
            onOperatorAccess = { askPin = true }
        )
    }
    val maintenanceActions = remember(session) {
        MaintenanceActions(
            onClose = session::exitMaintenance,
            onResetStation = session::resetStation,
            onReconnect = session::reconnectMachine,
            onTestPayment = session::testPayment,
            onEndSession = session::endSavedSession,
            onAndroidSettings = onAndroidSettings,
            onMachineTest = session::runMachineTest,
            onSimulateFault = { machine.simulateFault(1) }
        )
    }

    LocalizedContent(language) {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(session) {
                        // Watches touches without consuming them: restarts the return-to-Home countdown.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) session.userActivity()
                            }
                        }
                    }
            ) {
                val content = Modifier.fillMaxSize().padding(padding)
                if (state.maintenance) {
                    MaintenanceScreen(state, maintenanceActions, content)
                } else {
                    SessionUi(state.session, actions, BuildConfig.SUPPORT_CONTACT, content, language)
                }
                if (BuildConfig.DEBUG) {
                    DebugPanel(
                        state = state.session,
                        session = session,
                        machine = machine,
                        payments = payments,
                        kioskSetUp = state.device.kiosk != KioskStatus.NOT_SET_UP,
                        onRemoveKiosk = onRemoveKiosk,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp)
                    )
                }
            }
        }
    }

    if (askPin) {
        PinDialog(
            expectedPin = BuildConfig.OPERATOR_PIN,
            onDismiss = { askPin = false },
            onSuccess = {
                askPin = false
                session.enterMaintenance()
            }
        )
    }
}

@Composable
private fun SessionUi(
    state: SessionState,
    actions: SessionActions,
    supportContact: String,
    modifier: Modifier,
    language: AppLanguage = AppLanguage.DEFAULT
) {
    val overlay = state.trainingOverlay
    if (overlay != null) {
        TrainingWithOverlay(state, overlay, actions, supportContact, modifier)
        return
    }
    when (state) {
        SessionState.StartingUp -> StatusScreen(
            icon = "✓",
            heading = stringResource(R.string.s01_heading),
            body = stringResource(R.string.s01_body),
            onHeadingHold = actions.onOperatorAccess,
            modifier = modifier
        )
        is SessionState.Unavailable -> StatusScreen(
            icon = "!",
            heading = stringResource(R.string.s02_heading),
            body = stringResource(R.string.s02_body),
            action = stringResource(R.string.try_again) to actions.onSelfCheck,
            onHeadingHold = actions.onOperatorAccess,
            modifier = modifier
        )
        is SessionState.MachineFault -> StatusScreen(
            icon = "!",
            heading = stringResource(R.string.s03_heading),
            body = stringResource(R.string.s03_body),
            action = stringResource(R.string.try_again) to actions.onSelfCheck,
            footnote = faultFootnote(state.reference, supportContact),
            onHeadingHold = actions.onOperatorAccess,
            modifier = modifier
        )
        // Shown over the timer by TrainingWithOverlay; full screens kept as a fallback.
        is SessionState.BallsRequired -> StatusScreen(
            icon = "●",
            heading = stringResource(R.string.s04_heading),
            body = stringResource(R.string.s04_body),
            action = stringResource(R.string.s04_action) to actions.onBallsReturned,
            modifier = modifier
        )
        is SessionState.Reconnecting -> StatusScreen(
            icon = "●",
            heading = stringResource(R.string.s05_heading),
            body = stringResource(R.string.s05_body),
            modifier = modifier
        )
        is SessionState.Recovering -> StatusScreen(
            icon = null,
            heading = stringResource(R.string.s09_heading),
            body = stringResource(R.string.s09_body),
            footnote = recoveryProgress(state.step),
            onHeadingHold = actions.onOperatorAccess,
            modifier = modifier
        )
        SessionState.Maintenance -> Unit
        SessionState.Idle -> HomeScreen(language, actions.onLanguage, actions.onStart, actions.onOperatorAccess, modifier)
        SessionState.TrainingSelection -> TrainingSelectionScreen(actions.onTraining, modifier)
        is SessionState.DurationSelection -> DurationSelectionScreen(actions.onDuration, modifier)
        is SessionState.CustomConfigState ->
            CustomConfigScreen(state, actions.onCustomConfig, actions.onConfirmCustom, modifier)
        is SessionState.Summary -> SummaryScreen(state, actions.onPayment, modifier)
        is SessionState.Payment -> when (state.status) {
            PaymentState.VERIFYING -> StatusScreen(
                icon = "●",
                heading = stringResource(R.string.s08_heading),
                body = stringResource(R.string.s08_body),
                modifier = modifier
            )
            PaymentState.TIMEOUT -> StatusScreen(
                icon = "!",
                heading = stringResource(R.string.s06_heading),
                body = stringResource(R.string.s06_body),
                action = stringResource(R.string.try_again) to actions.onRetryPayment,
                modifier = modifier
            )
            PaymentState.CANCELLED -> StatusScreen(
                icon = "!",
                heading = stringResource(R.string.s07_heading),
                body = stringResource(R.string.s07_body),
                action = stringResource(R.string.try_again) to actions.onRetryPayment,
                modifier = modifier
            )
            else -> PaymentScreen(
                state, actions.onPay, actions.onRetryPayment, actions.onCancelPayment, actions.onStartPaid, modifier
            )
        }
        SessionState.Preparing -> PreparingScreen(modifier)
        is SessionState.Countdown -> CountdownScreen(state.seconds, modifier)
        is SessionState.Running -> TrainingScreen(state.remainingSeconds, state.mode, actions.onStop, modifier)
        is SessionState.Complete -> CompleteScreen(state.minutes, state.mode, actions.onFinish, modifier)
    }
}

/** Figma 09 overlay model: the paused timer stays visible under S03 / S04 / S05 / S09. */
@Composable
private fun TrainingWithOverlay(
    state: SessionState,
    overlay: TrainingOverlay,
    actions: SessionActions,
    supportContact: String,
    modifier: Modifier
) {
    val (remaining, mode) = when (state) {
        is SessionState.BallsRequired -> state.remainingSeconds to state.mode
        is SessionState.Reconnecting -> state.remainingSeconds to state.mode
        is SessionState.Recovering -> state.remainingSeconds to state.mode
        is SessionState.MachineFault -> (state.remainingSeconds ?: 0L) to (state.mode ?: TrainingMode.BASIC)
        else -> 0L to TrainingMode.BASIC
    }
    Box(modifier.fillMaxSize()) {
        TrainingScreen(remaining, mode, onStop = {}, modifier = Modifier.fillMaxSize(), paused = true)
        when (overlay) {
            TrainingOverlay.OUT_OF_BALLS -> StatusOverlay(
                icon = "●",
                heading = stringResource(R.string.s04_heading),
                body = stringResource(R.string.s04_body),
                action = stringResource(R.string.s04_action) to actions.onBallsReturned
            )
            TrainingOverlay.CONNECTION_LOST -> StatusOverlay(
                icon = "●",
                heading = stringResource(R.string.s05_heading),
                body = stringResource(R.string.s05_body),
                footnote = (state as? SessionState.Reconnecting)?.let { stringResource(R.string.s05_attempt, it.attempt) }
            )
            TrainingOverlay.MACHINE_FAULT -> StatusOverlay(
                icon = "!",
                heading = stringResource(R.string.s03_heading),
                body = stringResource(R.string.s03_body),
                action = stringResource(R.string.try_again) to actions.onSelfCheck,
                footnote = faultFootnote((state as? SessionState.MachineFault)?.reference, supportContact)
            )
            TrainingOverlay.RECOVERING -> StatusOverlay(
                icon = null,
                heading = stringResource(R.string.s09_heading),
                body = stringResource(R.string.s09_body),
                footnote = (state as? SessionState.Recovering)?.let { recoveryProgress(it.step) }
            )
        }
    }
}

/** Operator notice on S03: the unused time is logged under this reference. */
@Composable
private fun faultFootnote(reference: String?, supportContact: String): String? =
    listOfNotNull(
        reference?.let { stringResource(R.string.s03_reference, it) },
        supportContact.takeIf { it.isNotBlank() }?.let { stringResource(R.string.staff_contact, it) }
    ).joinToString(" · ").ifEmpty { null }

@Composable
private fun recoveryProgress(step: RecoveryStep): String {
    fun mark(done: Boolean) = if (done) "✓" else "…"
    return stringResource(R.string.recovery_payment) + " " + mark(step >= RecoveryStep.PAYMENT_VERIFIED) + "   " +
        stringResource(R.string.recovery_machine) + " " + mark(step >= RecoveryStep.MACHINE_VERIFIED) + "   " +
        stringResource(R.string.recovery_timer) + " " + mark(step >= RecoveryStep.TIMER_VERIFIED)
}

@Composable
private fun HomeScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onStart: () -> Unit,
    onOperatorAccess: () -> Unit,
    modifier: Modifier
) {
    Box(modifier.fillMaxSize()) {
        Centered(Modifier.fillMaxSize()) {
            Text(
                "AUTO TENNIS CLUB",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GreenDark,
                modifier = Modifier.holdToOpen(onHold = onOperatorAccess)
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_tagline), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart, modifier = Modifier.width(300.dp).height(90.dp)) {
                Text(stringResource(R.string.start), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            LanguagePicker(language, onLanguage)
        }
        StatusBadge(stringResource(R.string.station_ready), modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
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
        Text(stringResource(R.string.choose_training), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            Text(stringResource(R.string.continue_button))
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
        Text(stringResource(R.string.choose_duration), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                SelectableCard(
                    label = stringResource(R.string.minutes_short, minutes),
                    subLabel = priceText(priceFor(minutes)),
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
            Text(stringResource(R.string.continue_button))
        }
    }
}

private fun priceFor(minutes: Int): Double = when (minutes) {
    15 -> 6.90
    30 -> 12.0
    60 -> 20.0
    else -> 0.0
}

/** Euros in the customer's language: "€6.90" in English, "6,90 €" in Spanish and Catalan. */
@Composable
private fun priceText(price: Double): String {
    val format = NumberFormat.getCurrencyInstance(LocalConfiguration.current.locales[0]).apply {
        currency = Currency.getInstance("EUR")
        if (price % 1.0 == 0.0) maximumFractionDigits = 0
    }
    return format.format(price)
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
        Text(stringResource(R.string.custom_training), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.minutes_short, state.minutes), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Column(Modifier.padding(24.dp)) {
                ConfigSlider(
                    label = stringResource(R.string.speed),
                    valueLabel = "${config.velocity} km/h",
                    value = config.velocity.toFloat(),
                    range = 40f..120f,
                    onChange = { onConfig(config.copy(velocity = it.toInt())) }
                )
                Spacer(Modifier.height(20.dp))
                ConfigSlider(
                    label = stringResource(R.string.frequency),
                    valueLabel = frequencyLabel(config.frequencyGrade),
                    value = config.frequencyGrade.toFloat(),
                    range = 10f..50f,
                    onChange = { onConfig(config.copy(frequencyGrade = it.toInt())) }
                )
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.spin), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NONE", "TOPSPIN", "BACKSPIN").forEach { spin ->
                        OptionPill(
                            label = spinLabel(spin),
                            selected = config.spin == spin,
                            onClick = { onConfig(config.copy(spin = spin)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.sequence_mode), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FIXED POINT", "ROTATE POINTS").forEach { seq ->
                        OptionPill(
                            label = sequenceLabel(seq),
                            selected = config.sequence == seq,
                            onClick = { onConfig(config.copy(sequence = seq)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.config_not_paid),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirm, modifier = Modifier.width(280.dp).height(70.dp)) {
            Text(stringResource(R.string.confirm))
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

@Composable
private fun frequencyLabel(grade: Int): String =
    String.format(LocalConfiguration.current.locales[0], "%.1f s", grade / 10.0)

/** CustomConfig keeps English keys ("TOPSPIN"); these are what the customer reads. */
@Composable
private fun spinLabel(spin: String): String = stringResource(
    when (spin) {
        "NONE", "NO SPIN" -> R.string.spin_none
        "BACKSPIN" -> R.string.spin_backspin
        else -> R.string.spin_topspin
    }
)

@Composable
private fun sequenceLabel(sequence: String): String =
    stringResource(if (sequence == "FIXED POINT") R.string.sequence_fixed else R.string.sequence_rotate)

@Composable
private fun SummaryScreen(state: SessionState.Summary, onPayment: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Text(
            stringResource(if (state.mode == TrainingMode.CUSTOM) R.string.custom_summary else R.string.session_summary),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryCard(label = stringResource(R.string.label_training), value = modeLabel(state.mode), modifier = Modifier.weight(1f))
            SummaryCard(label = stringResource(R.string.label_duration), value = stringResource(R.string.minutes_short, state.minutes), modifier = Modifier.weight(1f))
            SummaryCard(
                label = stringResource(R.string.label_price),
                value = priceText(state.price),
                valueColor = GreenDark,
                modifier = Modifier.weight(1f)
            )
        }
        if (state.config != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.summary_config, state.config.velocity, spinLabel(state.config.spin)),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPayment, modifier = Modifier.width(300.dp).height(75.dp)) {
            Text(stringResource(R.string.proceed_to_payment))
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
                    Text(stringResource(R.string.tap_card), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.cancel_payment),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onCancel).padding(8.dp)
                )
            }
            PaymentState.PROCESSING -> {
                Text(stringResource(R.string.processing_payment), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            PaymentState.SUCCESS -> {
                Text(stringResource(R.string.payment_successful), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenDark)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.preparing_training), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            PaymentState.FAILED -> {
                Text(stringResource(R.string.payment_not_completed), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.try_again_sentence), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.width(240.dp).height(70.dp)) {
                    Text(stringResource(R.string.try_again))
                }
            }
            // Rendered as full-screen Phase 4.5 states in SessionUi.
            PaymentState.VERIFYING, PaymentState.CANCELLED, PaymentState.TIMEOUT -> Unit
        }
    }
}

@Composable
private fun PreparingScreen(modifier: Modifier) {
    Centered(modifier) {
        Text(stringResource(R.string.preparing_training), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connecting_machine), fontSize = 17.sp, color = TextSecondary)
    }
}

@Composable
private fun CountdownScreen(seconds: Int, modifier: Modifier) {
    Centered(modifier) {
        Text(stringResource(R.string.get_ready), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        Spacer(Modifier.height(24.dp))
        Text("$seconds", fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.starts_automatically), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

@Composable
private fun TrainingScreen(
    remainingSeconds: Long,
    mode: TrainingMode,
    onStop: () -> Unit,
    modifier: Modifier,
    paused: Boolean = false
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Box(modifier.fillMaxSize()) {
        Centered(Modifier.fillMaxSize()) {
            Text(stringResource(R.string.training_session), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (paused) R.string.paused else R.string.time_remaining),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            Text(modeLabel(mode), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GreenDark)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStop, modifier = Modifier.width(240.dp).height(75.dp)) {
                Text(stringResource(R.string.stop))
            }
        }
        StatusBadge(
            stringResource(if (paused) R.string.machine_paused else R.string.machine_active),
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
        )
    }
}

@Composable
private fun CompleteScreen(minutes: Int, mode: TrainingMode, onFinish: () -> Unit, modifier: Modifier) {
    Centered(modifier) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = GreenDark, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.session_complete), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.great_training), fontSize = 22.sp, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.minutes_short, minutes) + " · " + modeLabel(mode), style = MaterialTheme.typography.titleLarge, color = Navy)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.width(240.dp).height(75.dp)) {
            Text(stringResource(R.string.finish))
        }
    }
}

@Composable
private fun modeLabel(mode: TrainingMode): String = stringResource(
    when (mode) {
        TrainingMode.BASIC -> R.string.mode_basic
        TrainingMode.TRAINING -> R.string.mode_training
        TrainingMode.CUSTOM -> R.string.mode_custom
    }
)

@Composable
private fun Centered(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/** Phase 4.5 production / error states (Figma S01–S09), full screen and over the timer. */
private class PhaseStatesProvider : PreviewParameterProvider<SessionState> {
    override val values = sequenceOf(
        SessionState.StartingUp,
        SessionState.Unavailable(UnavailableReason.MACHINE_FAULT),
        SessionState.MachineFault("WHEEL_PROTECTION", 1, reference = "3F2A9C1E"),
        SessionState.MachineFault("WHEEL_PROTECTION", 1, "3F2A9C1E", remainingSeconds = 754, mode = TrainingMode.BASIC),
        SessionState.BallsRequired(754, TrainingMode.BASIC),
        SessionState.Reconnecting(754, TrainingMode.BASIC, attempt = 2),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.TIMEOUT),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.CANCELLED),
        SessionState.Payment(TrainingMode.BASIC, 30, 12.0, PaymentState.VERIFYING),
        SessionState.Recovering(RecoveryStep.PAYMENT_VERIFIED, 754, TrainingMode.BASIC, afterRestart = true),
        SessionState.Recovering(RecoveryStep.MACHINE_VERIFIED, 754, TrainingMode.BASIC)
    )
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PhaseStatesPreview(@PreviewParameter(PhaseStatesProvider::class) state: SessionState) {
    AutoTennisClubTheme {
        LocalizedContent(AppLanguage.DEFAULT) {
            SessionUi(state, SessionActions(), supportContact = "+34 600 000 000", modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun MaintenancePreview() {
    AutoTennisClubTheme {
        MaintenanceScreen(
            StationState(
                session = SessionState.Maintenance,
                machine = MachineState.Ready,
                paymentProvider = "MOCK",
                sessionDiagnostics = SessionDiagnostics(paymentReady = true, lastPayment = PaymentState.SUCCESS),
                device = DeviceStatus(online = true, bluetoothOn = true, bluetoothPermission = true, kiosk = KioskStatus.LOCKED),
                errors = listOf(
                    ErrorEntry(1_790_000_000_000, "MACHINE_FAULT", "WHEEL_PROTECTION ref=3f2a9c1e paid=true unused=12:34")
                )
            ),
            MaintenanceActions(),
            Modifier.fillMaxSize()
        )
    }
}

private class LanguageProvider : PreviewParameterProvider<AppLanguage> {
    override val values = AppLanguage.entries.asSequence()
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun HomeLanguagesPreview(@PreviewParameter(LanguageProvider::class) language: AppLanguage) {
    AutoTennisClubTheme {
        LocalizedContent(language) {
            SessionUi(SessionState.Idle, SessionActions(), "", Modifier.fillMaxSize(), language)
        }
    }
}
