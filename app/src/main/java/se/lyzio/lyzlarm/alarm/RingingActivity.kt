package se.lyzio.lyzlarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.MainActivity
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.engine.SleepEngine
import se.lyzio.lyzlarm.tracking.Tracker
import se.lyzio.lyzlarm.tracking.TrackingService
import se.lyzio.lyzlarm.ui.challenge.ChallengeScreen
import se.lyzio.lyzlarm.ui.challenge.LockedAction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.components.PrimaryButton
import se.lyzio.lyzlarm.ui.components.SecondaryButton
import se.lyzio.lyzlarm.ui.theme.LyzLarmTheme
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen ringing screen, shown over the lock screen. */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Back must not silence the alarm by accident.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            LyzLarmTheme {
                val ringing by RingingState.current.collectAsState()
                val current = ringing
                LaunchedEffect(current == null) {
                    if (current == null) finish()
                }
                var locked by rememberSaveable { mutableStateOf<LockedAction?>(null) }
                if (current != null) {
                    val dismiss = {
                        val tracking = TrackingService.running.value && current.kind != AlarmScheduler.Kind.TEST
                        RingingService.send(this, RingingService.ACTION_DISMISS)
                        // Show last night's graph (after unlocking, if the phone is locked).
                        if (tracking) {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.ACTION_OPEN_MORNING)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            )
                        }
                    }
                    val snooze = { RingingService.send(this, RingingService.ACTION_SNOOZE) }
                    val action = locked
                    if (action != null && current.snoozeLock) {
                        ChallengeScreen(
                            action = action,
                            types = current.challengeTypes,
                            mathDifficulty = current.mathDifficulty,
                            onSolved = {
                                locked = null
                                if (action == LockedAction.SNOOZE) snooze() else dismiss()
                            },
                            onBack = { locked = null },
                            onEvent = { type, detail ->
                                val context = applicationContext
                                app.scope.launch { Tracker.event(context, type, detail) }
                            },
                        )
                    } else {
                        RingingScreen(
                            ringing = current,
                            onDismiss = { if (current.snoozeLock) locked = LockedAction.DISMISS else dismiss() },
                            onSnooze = { if (current.snoozeLock) locked = LockedAction.SNOOZE else snooze() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RingingScreen(ringing: Ringing, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ringing.ringTimeMillis))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(ringing.ringTimeMillis))
    val isTest = ringing.kind == AlarmScheduler.Kind.TEST
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ringing.ringTimeMillis))
    val (headline, explanation) = when {
        isTest -> "Test alarm" to "It works. Tap \"I'm up\" to finish the test."
        ringing.kind == AlarmScheduler.Kind.STAY_AWAKE -> "You went back to bed" to "The stay-awake check found you lying still again."
        ringing.reason == SleepEngine.WakeReason.LIGHT_SLEEP -> "Light sleep at $clock" to "You were moving, a good moment to wake up."
        ringing.reason == SleepEngine.WakeReason.ALREADY_AWAKE -> "You were already awake" to "You kept moving for a few minutes, so the alarm rang now."
        ringing.reason == SleepEngine.WakeReason.WINDOW_END -> "End of your window" to "No light sleep found in the window, so it rang at your latest time."
        ringing.reason == SleepEngine.WakeReason.FIXED -> "Fixed time" to "This alarm has no wake window."
        else -> "Snooze over" to "Time to get up."
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lz.Ringing)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LzIcon(R.drawable.ic_wave, null, tint = Lz.AccentSoft, size = 56.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isTest) "Test alarm" else "Good morning", color = Lz.RingingTextSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(time, color = Lz.RingingText, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 96.sp, letterSpacing = (-3).sp)
            Text(date.replaceFirstChar { it.titlecase(Locale.getDefault()) }, color = Lz.RingingTextSoft, fontSize = 15.sp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Lz.RingingCard)
                .border(1.dp, Lz.RingingBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LzIcon(R.drawable.ic_info, null, tint = Lz.RingingTextSoft, size = 22.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline, color = Lz.RingingText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isTest) explanation else "$explanation ${ringing.label}.",
                    color = Lz.RingingTextSoft,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LzIcon(R.drawable.ic_volume, null, tint = Lz.RingingTextSoft, size = 20.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Lz.RingingBorder),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ringing.volume.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Lz.Accent),
                )
            }
            Text(
                when {
                    ringing.vibratingOnly -> "Vibrating"
                    ringing.volume < 1f -> "Volume rising"
                    else -> "Full volume"
                },
                color = Lz.RingingTextSoft,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ringing.snoozeLock) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LzIcon(R.drawable.ic_lock, null, tint = Lz.RingingTextSoft, size = 18.dp)
                    Text("Snooze lock: both buttons ask for a challenge", color = Lz.RingingTextSoft, fontSize = 13.sp)
                }
            }
            PrimaryButton("I'm up", onDismiss, height = 64.dp)
            if (!isTest && ringing.snoozeMinutes > 0) {
                SecondaryButton(
                    "Snooze ${ringing.snoozeMinutes} min",
                    onSnooze,
                    border = Lz.RingingButtonBorder,
                    textColor = Lz.RingingText,
                )
            }
        }
    }
}
