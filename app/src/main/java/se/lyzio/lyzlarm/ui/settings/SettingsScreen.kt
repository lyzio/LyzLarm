package se.lyzio.lyzlarm.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.system.SystemCheck
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.setup.rememberSystemStatus
import se.lyzio.lyzlarm.ui.setup.startSafely
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk
import java.text.DateFormat
import java.util.Date

/** Settings: System check, Tracking (v0.2) and About. Other groups arrive with their features. */
@Composable
fun SettingsScreen(onOpenAbout: () -> Unit, onRunTest: () -> Unit, onOpenTracking: () -> Unit) {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    val testPassedAt by context.app.settings.testAlarmPassedAt.collectAsState(initial = 0L)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startSafely(SystemCheck.notificationSettingsIntent(context))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Lz.Text)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("System check", color = Lz.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    when (status.issues) {
                        0 -> "All good"
                        1 -> "1 needs attention"
                        else -> "${status.issues} need attention"
                    },
                    color = if (status.issues == 0) Lz.Ok else Lz.AccentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
                CheckRow(R.drawable.ic_bell, "Notifications", status.notifications, divider = true) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startSafely(SystemCheck.notificationSettingsIntent(context))
                }
                CheckRow(R.drawable.ic_alarm, "Alarms & full screen", status.exactAlarms && status.fullScreen, divider = true) {
                    val intent = if (!status.exactAlarms) SystemCheck.exactAlarmIntent(context) else SystemCheck.fullScreenIntent(context)
                    context.startSafely(intent)
                }
                CheckRow(R.drawable.ic_battery, "Battery optimization", status.batteryUnrestricted, divider = true) {
                    context.startSafely(SystemCheck.batteryIntent(context), SystemCheck.batterySettingsFallback())
                }
                SettingsRow(
                    icon = R.drawable.ic_clock,
                    title = "Test alarm",
                    subtitle = if (testPassedAt > 0) {
                        "Last passed " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(testPassedAt))
                    } else "Rings in one minute",
                    action = "Run test",
                    divider = false,
                    onClick = onRunTest,
                )
            }
            SystemCheck.brandTip()?.takeIf { !status.batteryUnrestricted }?.let { tip ->
                Text("${SystemCheck.brand()}: $tip", color = Lz.TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
            SettingsRow(
                icon = R.drawable.ic_moon,
                title = "Tracking",
                subtitle = "Bedtime reminder, battery warning, raw data",
                action = null,
                divider = true,
                onClick = onOpenTracking,
            )
            SettingsRow(
                icon = R.drawable.ic_info,
                title = "About",
                subtitle = "Version, GPL-3.0 license, source, privacy",
                action = null,
                divider = false,
                onClick = onOpenAbout,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LzIcon(R.drawable.ic_shield, null, tint = Lz.TextMuted, size = 18.dp)
            Text("No internet access · all data stays on this phone", color = Lz.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CheckRow(@DrawableRes icon: Int, title: String, ok: Boolean, divider: Boolean, onFix: () -> Unit) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = null,
        action = if (ok) "OK" else "Fix",
        actionColor = if (ok) Lz.Ok else Lz.AccentText,
        divider = divider,
        onClick = if (ok) null else onFix,
    )
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    action: String?,
    divider: Boolean,
    actionColor: androidx.compose.ui.graphics.Color = Lz.AccentText,
    onClick: (() -> Unit)?,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LzIcon(icon, null, tint = Lz.TextSoft, size = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = Lz.Text, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = Lz.TextMuted, fontSize = 13.sp)
            }
            if (action != null) {
                Text(action, color = actionColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                LzIcon(R.drawable.ic_chevron, null, tint = Lz.TextMuted, size = 18.dp)
            }
        }
        if (divider) HorizontalDivider(color = Lz.Divider)
    }
}
