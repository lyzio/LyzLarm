package se.lyzio.lyzlarm.ui.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.system.SystemCheck
import se.lyzio.lyzlarm.system.SystemStatus
import se.lyzio.lyzlarm.ui.components.IconBadge
import se.lyzio.lyzlarm.ui.components.LogoBadge
import se.lyzio.lyzlarm.ui.components.LzCard
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.components.PrimaryButton
import se.lyzio.lyzlarm.ui.components.QuietButton
import se.lyzio.lyzlarm.ui.components.StepHeader
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

/** v0.1 setup: welcome, notifications, alarms / full screen, battery, test alarm. */
const val SETUP_STEPS = 5

@Composable
fun SetupFlow(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    BackHandler(enabled = step > 1) { step-- }
    val next: () -> Unit = {
        if (step < SETUP_STEPS) step += 1 else onFinished()
    }
    when (step) {
        1 -> WelcomeStep(next)
        2 -> NotificationsStep(next)
        3 -> AlarmsStep(next)
        4 -> BatteryStep(next)
        else -> TestAlarmScreen(step = 5, onDone = onFinished)
    }
}

/** Current system status, refreshed whenever the screen comes back into view. */
@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(SystemCheck.status(context)) }
    LifecycleResumeEffect(Unit) {
        status = SystemCheck.status(context)
        onPauseOrDispose { }
    }
    return status
}

fun Context.startSafely(intent: Intent?, fallback: Intent? = null) {
    if (intent == null) return
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it) } }
    }
}

@Composable
fun SetupScaffold(
    step: Int?,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Lz.Base)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        if (step != null) StepHeader(step, SETUP_STEPS)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun SetupTitle(text: String) {
    Text(text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = Lz.Text)
}

@Composable
fun SetupBody(text: String) {
    Text(text, color = Lz.TextSoft, fontSize = 16.sp, lineHeight = 24.sp)
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    SetupScaffold(
        step = 1,
        content = {
            LogoBadge()
            SetupTitle("Welcome to LyzLarm")
            SetupBody("Wakes you gently at your lightest sleep. Fully offline: no internet access, no accounts, no tracking.")
            Feature(R.drawable.ic_clock, "Smart wake window.", "Rings at light sleep, never after your latest time. Arrives in v0.3.")
            Feature(R.drawable.ic_sleep, "Sleep graph every morning.", "See how much you moved and when, every morning.")
            Feature(R.drawable.ic_shield, "Your data stays here.", "Everything is stored only on this phone.")
        },
        actions = { PrimaryButton("Get started", onNext) },
    )
}

@Composable
private fun Feature(@DrawableRes icon: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        LzIcon(icon, null, tint = Lz.AccentSoft)
        Column {
            Text(title, color = Lz.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Lz.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun NotificationsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onNext()
    }
    SetupScaffold(
        step = 2,
        content = {
            IconBadge(R.drawable.ic_bell)
            SetupTitle("Allow notifications")
            SetupBody("LyzLarm needs notifications to ring the alarm, and shows one quiet notification while it tracks your sleep.")
            LzCard(color = Lz.Surface) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LzIcon(
                        if (status.notifications) R.drawable.ic_check else R.drawable.ic_warning,
                        null,
                        tint = if (status.notifications) Lz.Ok else Lz.AccentSoft,
                    )
                    Text(
                        if (status.notifications) "Notifications are allowed." else "Required: without notifications, alarms can't ring.",
                        color = Lz.TextSoft,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        actions = {
            if (status.notifications) {
                PrimaryButton("Continue", onNext)
            } else {
                PrimaryButton("Allow notifications", {
                    if (Build.VERSION.SDK_INT >= 33 && !asked) {
                        asked = true
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Already asked once (or older Android): Android won't ask again, so open settings.
                        context.startSafely(SystemCheck.notificationSettingsIntent(context))
                    }
                })
            }
        },
    )
}

@Composable
private fun AlarmsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    SetupScaffold(
        step = 3,
        content = {
            IconBadge(R.drawable.ic_clock)
            SetupTitle("Let alarms ring on time")
            SetupBody("Allows LyzLarm to ring at the exact minute and to show the alarm over your lock screen.")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
                PermissionRow(R.drawable.ic_alarm, "Exact alarms", status.exactAlarms, divider = true) {
                    context.startSafely(SystemCheck.exactAlarmIntent(context))
                }
                PermissionRow(R.drawable.ic_lock, "Full-screen alarms", status.fullScreen, divider = false) {
                    context.startSafely(SystemCheck.fullScreenIntent(context))
                }
            }
        },
        actions = {
            PrimaryButton("Continue", onNext)
        },
    )
}

@Composable
private fun BatteryStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    val tip = SystemCheck.brandTip()
    SetupScaffold(
        step = 4,
        content = {
            IconBadge(R.drawable.ic_battery)
            SetupTitle("Keep LyzLarm awake at night")
            SetupBody(
                "Android may pause apps to save battery. For tracking to run all night, allow LyzLarm to run " +
                    "without battery restrictions. The alarm itself always rings either way.",
            )
            if (tip != null) {
                LzCard {
                    Text("Your phone: ${SystemCheck.brand()}", color = Lz.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.padding(2.dp))
                    Text(tip, color = Lz.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LzIcon(
                    if (status.batteryUnrestricted) R.drawable.ic_check else R.drawable.ic_warning,
                    null,
                    tint = if (status.batteryUnrestricted) Lz.Ok else Lz.AccentSoft,
                )
                Text(
                    if (status.batteryUnrestricted) "Status: unrestricted" else "Status: restricted",
                    color = Lz.TextSoft,
                    fontSize = 14.sp,
                )
            }
        },
        actions = {
            if (status.batteryUnrestricted) {
                PrimaryButton("Continue", onNext)
            } else {
                PrimaryButton("Open battery settings", {
                    context.startSafely(SystemCheck.batteryIntent(context), SystemCheck.batterySettingsFallback())
                })
                QuietButton("Skip for now", onNext)
            }
        },
    )
}

@Composable
fun PermissionRow(@DrawableRes icon: Int, label: String, ok: Boolean, divider: Boolean, onFix: () -> Unit) {
    se.lyzio.lyzlarm.ui.components.ValueRow(
        label = label,
        value = "",
        onClick = if (ok) null else onFix,
        showDivider = divider,
        trailing = {
            Text(
                if (ok) "Allowed" else "Allow",
                color = if (ok) Lz.Ok else Lz.AccentText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}
