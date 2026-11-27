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
import se.lyzio.lyzlarm.tracking.TrackingService
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
                if (current != null) {
                    RingingScreen(
                        ringing = current,
                        onDismiss = {
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
                        },
                        onSnooze = { RingingService.send(this, RingingService.ACTION_SNOOZE) },
                    )
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
                Text(ringing.label, color = Lz.RingingText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isTest) "It works. Tap \"I'm up\" to finish the test."
                    else "Fixed time. Smart wake at light sleep arrives in v0.3.",
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
