package se.lyzio.anatole.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.lyzio.anatole.R
import se.lyzio.anatole.alarm.AlarmScheduler
import se.lyzio.anatole.alarm.RingingState
import se.lyzio.anatole.app
import se.lyzio.anatole.ui.Format
import se.lyzio.anatole.ui.components.IconBadge
import se.lyzio.anatole.ui.components.LzCard
import se.lyzio.anatole.ui.components.LzIcon
import se.lyzio.anatole.ui.components.PrimaryButton
import se.lyzio.anatole.ui.components.QuietButton
import se.lyzio.anatole.ui.components.SecondaryButton
import se.lyzio.anatole.ui.theme.Lz
import se.lyzio.anatole.ui.theme.SpaceGrotesk

/**
 * Test alarm: rings in one minute with the screen locked, proving the alarm works on
 * this phone. Used as the last setup step ([step] = 5) and from System check ([step] = null).
 */
@Composable
fun TestAlarmScreen(step: Int?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scheduler = context.app.scheduler
    val passedAt by context.app.settings.testAlarmPassedAt.collectAsState(initial = 0L)
    val status = rememberSystemStatus()

    var ringAt by remember { mutableStateOf(scheduler.pendingTestAt()) }
    var startedAt by remember { mutableLongStateOf(ringAt?.minus(60_000L) ?: 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500L)
        }
    }

    val passed = startedAt > 0 && passedAt >= startedAt
    val target = ringAt
    // Give Android a minute of slack before saying it didn't ring.
    val ringingNow by RingingState.current.collectAsState()
    val overdue = !passed && target != null && now > target + 60_000L &&
        ringingNow?.kind != AlarmScheduler.Kind.TEST

    SetupScaffold(
        step = step,
        content = {
            IconBadge(R.drawable.ic_alarm, background = if (passed) Lz.Highlight else Lz.Accent, tint = if (passed) androidx.compose.ui.graphics.Color.White else Lz.OnAccent)
            when {
                passed -> {
                    SetupTitle("Your alarm works")
                    SetupBody("The test alarm rang on this phone. Anatole is ready to wake you.")
                }
                overdue -> {
                    SetupTitle("It didn't ring")
                    SetupBody("Something on this phone stopped the alarm. Fix the items below, then run the test again.")
                    LzCard {
                        FixLine("Notifications", status.notifications)
                        FixLine("Exact alarms", status.exactAlarms)
                        FixLine("Full-screen alarms", status.fullScreen)
                        FixLine("Battery optimization", status.batteryUnrestricted)
                    }
                }
                else -> {
                    SetupTitle("Let's test the alarm")
                    SetupBody(
                        if (target == null) "Anatole will ring in one minute, just like a real morning. Start the test, then lock your screen."
                        else "Lock your screen now. Anatole will ring in one minute, just like a real morning. This proves the alarm works on your phone.",
                    )
                    if (target != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Lz.Surface)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LzIcon(R.drawable.ic_lock, null, tint = Lz.AccentSoft, size = 28.dp)
                            Text(
                                Format.countdown(target - now),
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 56.sp,
                                color = Lz.Text,
                            )
                            Text(if (target > now) "until test alarm" else "ringing…", color = Lz.TextMuted, fontSize = 14.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LzIcon(R.drawable.ic_info, null, tint = Lz.TextMuted, size = 20.dp)
                        Text(
                            "If it doesn't ring, Anatole shows which setting to fix. You can run this test any time from System check.",
                            color = Lz.TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        },
        actions = {
            fun start() {
                startedAt = System.currentTimeMillis()
                ringAt = scheduler.scheduleTest()
            }
            when {
                passed -> PrimaryButton(if (step != null) "Finish setup" else "Done", onDone)
                overdue -> {
                    PrimaryButton("Run test again", ::start)
                    QuietButton(if (step != null) "Skip for now" else "Close", onDone)
                }
                target == null -> {
                    PrimaryButton("Start test alarm", ::start)
                    QuietButton(if (step != null) "Skip for now" else "Close", onDone)
                }
                else -> {
                    SecondaryButton("Cancel test", {
                        scheduler.cancelTest()
                        ringAt = null
                        startedAt = 0L
                    })
                    if (step != null) QuietButton("Skip for now", {
                        scheduler.cancelTest()
                        onDone()
                    })
                }
            }
        },
    )
}

@Composable
private fun FixLine(label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LzIcon(if (ok) R.drawable.ic_check else R.drawable.ic_warning, null, tint = if (ok) Lz.Ok else Lz.AccentSoft, size = 20.dp)
        Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (ok) "OK" else "Needs fixing", color = if (ok) Lz.Ok else Lz.AccentText, fontSize = 13.sp)
    }
}
