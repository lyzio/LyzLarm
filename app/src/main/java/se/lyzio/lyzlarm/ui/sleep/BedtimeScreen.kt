package se.lyzio.lyzlarm.ui.sleep

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.engine.Sensitivity
import se.lyzio.lyzlarm.engine.WakeRules
import se.lyzio.lyzlarm.tracking.Tracker
import se.lyzio.lyzlarm.tracking.TrackingService
import se.lyzio.lyzlarm.ui.Format
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.components.SoonBadge
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

private const val DIM_AFTER_MS = 30_000L
private const val HOLD_MS = 1_500L

/**
 * Shown while tracking. Very dark, dims itself after 30 s; a long press stops tracking.
 * When the night ends (alarm dismissed or stopped), [onFinished] gets the night's id.
 */
@Composable
fun BedtimeScreen(onFinished: (Long) -> Unit, onBack: () -> Unit, startOnOpen: Boolean = false) {
    val context = LocalContext.current
    val app = context.app
    val dao = app.database.nightDao()
    val night by remember { dao.observeOpenNight() }.collectAsState(initial = null)
    val running by TrackingService.running.collectAsState()

    var seenNightId by remember { mutableLongStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(night) {
        val n = night
        if (n != null) {
            seenNightId = n.id
        } else if (seenNightId != 0L) {
            onFinished(seenNightId)
        }
    }
    var startFailed by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (startOnOpen && dao.openNight() == null) {
            // From the bedtime reminder (#26): start once the screen is really showing.
            lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            val id = runCatching { Tracker.start(context) }.getOrNull()
            if (id == null) startFailed = true
        }
        delay(1_500L)
        loaded = true
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000L)
        }
    }

    // Dim after 30 s without touch; any touch brightens again.
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dimmed = now - lastTouch >= DIM_AFTER_MS
    val activity = context as? Activity
    LaunchedEffect(lastTouch) {
        delay(DIM_AFTER_MS)
        now = System.currentTimeMillis()
    }
    LaunchedEffect(dimmed) {
        activity?.window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = if (dimmed) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    val current = night
    val alarmAt by produceState<Long?>(null, current?.id, now / 60_000) {
        value = current?.let { withContext(Dispatchers.IO) { Tracker.alarmTimeFor(context, it) } }
    }
    val events by remember(current?.id) {
        if (current != null) dao.observeEvents(current.id) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val plan by produceState<Tracker.WakePlan?>(null, current?.id, now / 60_000) {
        value = current?.let { withContext(Dispatchers.IO) { Tracker.wakePlan(context, it) } }
    }
    val emptyBedTest by remember { app.database.extrasDao().observeLatestEmptyBedTest() }.collectAsState(initial = null)
    val threshold by produceState(40) { value = app.settings.tracking().warningThreshold }
    val battery = remember(now / 60_000) { Tracker.battery(context) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Lz.Night)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    lastTouch = System.currentTimeMillis()
                    now = lastTouch
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .alpha(if (dimmed) 0.35f else 1f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (current == null) {
                if (loaded && seenNightId == 0L) NotTracking(onBack, if (startFailed) "No alarm in the next 16 hours. Set an alarm first." else null)
                return@Column
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tracking your sleep", color = Lz.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    Format.time(context, now),
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 84.sp,
                    letterSpacing = (-3).sp,
                    color = Lz.Text,
                )
                val p = plan
                val ringAt = alarmAt
                Text(
                    when {
                        ringAt == null -> "No alarm set"
                        p != null && p.isSmart && p.endMs == ringAt ->
                            "Wake ${Format.time(context, p.startMs)}–${Format.time(context, p.endMs)}"
                        else -> "Alarm ${Format.time(context, ringAt)}"
                    },
                    color = Lz.AccentText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                p?.learningNight?.takeIf { p.isSmart }?.let { n ->
                    Text(
                        "Learning your sleep · night $n of ${WakeRules.LEARNING_NIGHTS}",
                        color = Lz.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val interrupted = !running || events.any { it.type == EventType.TRACKING_INTERRUPTED }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.NightCard)) {
                StatusRow(R.drawable.ic_wave, "Movement", if (interrupted) "Interrupted" else "Active, all night", warn = interrupted)
                HorizontalDivider(color = Lz.Divider)
                StatusRow(
                    R.drawable.ic_alarm,
                    "Alarm",
                    alarmAt?.let { "Rings in ${Format.duration(it - now)}" } ?: "Not set",
                    warn = alarmAt == null,
                )
                HorizontalDivider(color = Lz.Divider)
                StatusRow(
                    R.drawable.ic_battery,
                    "Battery",
                    if (battery >= threshold) "$battery%, enough for tonight" else "$battery%, may not last the night",
                    warn = battery < threshold,
                )
                HorizontalDivider(color = Lz.Divider)
                val p = plan
                StatusRow(
                    R.drawable.ic_clock,
                    "Smart wake window",
                    when {
                        p == null -> "Off"
                        !p.isSmart -> "Off · fixed time"
                        else -> "${Format.time(context, p.startMs)}–${Format.time(context, p.endMs)} · ${sensitivityLabel(p.sensitivity)}"
                    },
                    warn = false,
                )
                HorizontalDivider(color = Lz.Divider)
                StatusRow(
                    R.drawable.ic_sleep,
                    "Out of bed",
                    if (emptyBedTest != null) "Active" else "Run the empty bed test",
                    warn = emptyBedTest == null,
                )
                HorizontalDivider(color = Lz.Divider)
                SoonRow(R.drawable.ic_volume, "Sound", "Arrives in v0.4")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LzIcon(R.drawable.ic_info, null, tint = Lz.TextMuted, size = 20.dp)
                Text(
                    "Put the phone on the mattress near your pillow, face down. Never charge it in bed.",
                    color = Lz.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.weight(1f))
            HoldToStop {
                // App scope: the screen closes as soon as the night ends.
                val appContext = context.applicationContext
                app.scope.launch { Tracker.stopManually(appContext) }
            }
        }
    }
}

fun sensitivityLabel(s: Sensitivity): String = when (s) {
    Sensitivity.CAUTIOUS -> "Cautious"
    Sensitivity.BALANCED -> "Balanced"
    Sensitivity.EAGER -> "Eager"
}

@Composable
private fun NotTracking(onBack: () -> Unit, reason: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Not tracking", color = Lz.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(reason ?: "No night is being tracked. Use Start sleep on the Alarms tab.", color = Lz.TextMuted, fontSize = 15.sp)
        Text(
            "Back",
            color = Lz.AccentText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.heightIn(min = 48.dp).pointerInput(Unit) { detectTapGestures { onBack() } }.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun StatusRow(@DrawableRes icon: Int, label: String, value: String, warn: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LzIcon(icon, null, tint = Lz.TextSoft, size = 20.dp)
        Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (warn) Lz.Warning else Lz.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun SoonRow(@DrawableRes icon: Int, label: String, badge: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(0.45f).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LzIcon(icon, null, tint = Lz.TextSoft, size = 20.dp)
        Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        SoonBadge(badge)
    }
}

/** Press and hold for 1.5 s to stop; the bar fills while holding. */
@Composable
private fun HoldToStop(onStop: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Lz.NightCard)
            .semantics {
                contentDescription = "Hold to stop tracking"
                onLongClick(label = "Stop tracking") { onStop(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_MS.toInt(), easing = LinearEasing))
                        onStop()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                .background(Lz.Accent.copy(alpha = 0.6f)),
        )
        Text("Hold to stop tracking", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
