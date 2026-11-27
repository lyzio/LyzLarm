package se.lyzio.anatole.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.lyzio.anatole.app
import se.lyzio.anatole.data.TrackingSettings
import se.lyzio.anatole.engine.Bedtime
import se.lyzio.anatole.tracking.Planner
import se.lyzio.anatole.tracking.Tracker
import se.lyzio.anatole.ui.Format
import se.lyzio.anatole.ui.about.BackHeader
import se.lyzio.anatole.ui.components.ChoiceDialog
import se.lyzio.anatole.ui.components.LzSwitch
import se.lyzio.anatole.ui.components.SoonBadge
import se.lyzio.anatole.ui.components.TimeDialog
import se.lyzio.anatole.ui.theme.Lz
import java.time.Instant
import java.time.ZoneId

private val THRESHOLDS = listOf(20, 30, 40, 50, 60)

private enum class Picker { REMINDER_MODE, REMINDER_TIME, THRESHOLD, WARNING_MODE, WARNING_TIME }

/** Settings › Tracking: bedtime reminder, low-battery warning, raw sensor data. */
@Composable
fun TrackingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val settings by remember { app.settings.tracking }.collectAsState(initial = null)
    val s = settings ?: TrackingSettings()
    // The automatic reminder time for the next alarm (learned, or alarm − 9 h).
    val usualMinute by produceState<Int?>(null, settings) {
        val next = Tracker.nextAlarm(context)
        value = next?.let { (_, at) ->
            val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            Bedtime.reminderMinute(t.hour * 60 + t.minute, Planner.learnedStartMinute(context))
        }
    }
    var picker by remember { mutableStateOf<Picker?>(null) }
    fun save(block: suspend () -> Unit) {
        app.scope.launch { block() }
    }

    fun auto(minute: Int?) = minute?.let { "Automatic, around ${Format.minuteOfDay(context, it)}" } ?: "Automatic"
    val autoWarning = usualMinute?.let { Math.floorMod(it - Bedtime.WARNING_MINUTES_BEFORE_START, 24 * 60) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackHeader("Tracking", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Group("Bedtime") {
                ToggleRow("Bedtime reminder", "A notification when it's time to start tracking", s.reminderOn, divider = true) { on ->
                    save { app.settings.setReminderOn(on) }
                }
                ValueLine(
                    "Reminder time",
                    s.reminderFixedMinute?.let { Format.minuteOfDay(context, it) } ?: auto(usualMinute),
                    enabled = s.reminderOn,
                    divider = false,
                ) { picker = Picker.REMINDER_MODE }
            }

            Group("Battery") {
                ToggleRow("Low-battery warning", "An evening notification if the battery is low", s.warningOn, divider = true) { on ->
                    save { app.settings.setWarningOn(on) }
                }
                ValueLine("Warn below", "${s.warningThreshold}%", enabled = s.warningOn, divider = true) { picker = Picker.THRESHOLD }
                ValueLine(
                    "Warning time",
                    s.warningFixedMinute?.let { Format.minuteOfDay(context, it) } ?: auto(autoWarning),
                    enabled = s.warningOn,
                    divider = false,
                ) { picker = Picker.WARNING_MODE }
            }

            Group("For tuning") {
                ToggleRow(
                    "Record raw sensor data",
                    "Extra detail for improving smart wake. Up to ~30 MB a night, deleted after 3 days. Included in night exports.",
                    s.rawOn,
                    divider = false,
                ) { on -> save { app.settings.setRawOn(on) } }
            }
            Text(
                "A change to raw recording applies from the next Start sleep.",
                color = Lz.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface).alpha(0.45f)
                    .heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sound tracking · Shared bed", color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                SoonBadge("Arrives in v0.4")
            }
        }
    }

    when (picker) {
        Picker.REMINDER_MODE -> ChoiceDialog(
            "Reminder time",
            listOf(auto(usualMinute), "Fixed time…"),
            if (s.reminderFixedMinute == null) 0 else 1,
            onSelect = { i ->
                if (i == 0) {
                    save { app.settings.setReminderFixedMinute(null) }
                    picker = null
                } else picker = Picker.REMINDER_TIME
            },
            onDismiss = { picker = null },
        )
        Picker.REMINDER_TIME -> TimeDialog("Reminder time", s.reminderFixedMinute ?: usualMinute ?: 22 * 60, onConfirm = { m ->
            save { app.settings.setReminderFixedMinute(m) }
            picker = null
        }, onDismiss = { picker = null })
        Picker.THRESHOLD -> ChoiceDialog(
            "Warn below",
            THRESHOLDS.map { "$it%" },
            THRESHOLDS.indexOf(s.warningThreshold).coerceAtLeast(0),
            onSelect = { i ->
                save { app.settings.setWarningThreshold(THRESHOLDS[i]) }
                picker = null
            },
            onDismiss = { picker = null },
        )
        Picker.WARNING_MODE -> ChoiceDialog(
            "Warning time",
            listOf(auto(autoWarning), "Fixed time…"),
            if (s.warningFixedMinute == null) 0 else 1,
            onSelect = { i ->
                if (i == 0) {
                    save { app.settings.setWarningFixedMinute(null) }
                    picker = null
                } else picker = Picker.WARNING_TIME
            },
            onDismiss = { picker = null },
        )
        Picker.WARNING_TIME -> TimeDialog("Warning time", s.warningFixedMinute ?: autoWarning ?: 21 * 60, onConfirm = { m ->
            save { app.settings.setWarningFixedMinute(m) }
            picker = null
        }, onDismiss = { picker = null })
        null -> Unit
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Lz.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, divider: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Lz.Text, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = Lz.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            LzSwitch(checked = checked, onCheckedChange = onChange, contentDescription = title)
        }
        if (divider) HorizontalDivider(color = Lz.Divider)
    }
}

@Composable
private fun ValueLine(label: String, value: String, enabled: Boolean, divider: Boolean, onClick: () -> Unit) {
    Column(Modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = Lz.AccentText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (divider) HorizontalDivider(color = Lz.Divider)
    }
}
