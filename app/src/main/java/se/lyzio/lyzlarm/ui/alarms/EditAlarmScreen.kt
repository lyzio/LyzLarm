package se.lyzio.lyzlarm.ui.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.alarm.NextAlarm
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.Alarm
import se.lyzio.lyzlarm.ui.Format
import se.lyzio.lyzlarm.ui.components.ChipRow
import se.lyzio.lyzlarm.ui.components.SectionLabel
import se.lyzio.lyzlarm.ui.components.Segmented
import se.lyzio.lyzlarm.ui.components.ValueRow
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk
import java.time.DayOfWeek

private enum class WakeUpStyle(val label: String, val vibrateFirst: Boolean, val gradual: Boolean) {
    VibrateThenFade("Vibrate, then fade in 3 min", true, true),
    Fade("Fade in 3 min", false, true),
    VibrateThenFull("Vibrate, then full volume", true, false),
    Full("Full volume at once", false, false);

    companion object {
        fun of(alarm: Alarm) = entries.first { it.vibrateFirst == alarm.vibrateFirst && it.gradual == alarm.gradualVolume }
    }
}

private val SNOOZE_OPTIONS = listOf(0, 5, 9, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = context.app
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(alarmId == 0L) }
    var alarm by remember { mutableStateOf(Alarm(repeatDays = 0b0011111)) }
    LaunchedEffect(alarmId) {
        if (alarmId != 0L) {
            withContext(Dispatchers.IO) { container.database.alarmDao().get(alarmId) }?.let { alarm = it }
            loaded = true
        }
    }
    if (!loaded) return

    var showTimePicker by remember { mutableStateOf(alarmId == 0L) }
    var showWakeUp by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            alarm = alarm.copy(soundUri = uri?.toString())
        }
    }

    val time = Format.time(context, alarm.hour, alarm.minute)

    fun save() {
        scope.launch {
            val toSave = alarm.copy(enabled = true)
            val saved = withContext(Dispatchers.IO) {
                val dao = container.database.alarmDao()
                if (toSave.id == 0L) toSave.copy(id = dao.insert(toSave)) else toSave.also { dao.update(it) }
            }
            container.scheduler.schedule(saved)
            onDone()
        }
    }

    fun delete() {
        scope.launch {
            container.scheduler.cancel(alarm)
            withContext(Dispatchers.IO) { container.database.alarmDao().delete(alarm) }
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) { Text("Cancel", color = Lz.TextSoft, fontSize = 16.sp) }
            Text(
                if (alarmId == 0L) "New alarm" else "Edit alarm",
                color = Lz.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::save) {
                Text("Save", color = Lz.AccentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Change time") { showTimePicker = true },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Wake me by", color = Lz.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(time, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 60.sp, letterSpacing = (-2).sp, color = Lz.Text)
                Text("Rings at exactly $time", color = Lz.TextSoft, fontSize = 14.sp)
            }

            // Shown but disabled until v0.3 (design decision 21 Nov 2026).
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Smart wake window", badge = "Arrives in v0.3")
                ChipRow(listOf("Off", "10 min", "20 min", "30 min", "45 min"), selected = 3, enabled = false) {}
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Sensitivity", badge = "Arrives in v0.3")
                Segmented(listOf("Cautious", "Balanced", "Eager"), selected = 1, onSelect = {}, enabled = false)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Repeat")
                RepeatDays(alarm.repeatDays) { alarm = alarm.copy(repeatDays = it) }
                Text(NextAlarm.describeRepeat(alarm.repeatDays), color = Lz.TextMuted, fontSize = 12.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Sound tracking", badge = "Arrives in v0.4")
                Segmented(
                    listOf("Off", "Last 1 h", "Last 2 h", "All night"),
                    selected = 2,
                    onSelect = {},
                    selectedColor = Lz.Sound,
                    enabled = false,
                )
                Text("Sleep tracking arrives in v0.2, sound in v0.4.", color = Lz.TextMuted, fontSize = 12.sp)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
                ValueRow("Sound", soundTitle(alarm.soundUri), onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            alarm.soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        )
                    ringtonePicker.launch(intent)
                }, showDivider = true)
                ValueRow("Wake-up", WakeUpStyle.of(alarm).label, onClick = { showWakeUp = true }, showDivider = true)
                ValueRow(
                    "Snooze",
                    if (alarm.snoozeMinutes == 0) "Off" else "${alarm.snoozeMinutes} min",
                    onClick = { showSnooze = true },
                    showDivider = false,
                )
            }

            Text(
                "Rings at $time. Smart wake arrives in v0.3.",
                color = Lz.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (alarmId != 0L) {
                TextButton(onClick = ::delete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Delete alarm", color = Lz.AccentText, fontSize = 15.sp)
                }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = alarm.hour,
            initialMinute = alarm.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Lz.Base,
            title = { Text("Wake me by", color = Lz.Text) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Lz.Surface,
                        selectorColor = Lz.Accent,
                        timeSelectorSelectedContainerColor = Lz.Highlight,
                        timeSelectorUnselectedContainerColor = Lz.Surface,
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContentColor = Lz.Text,
                        periodSelectorSelectedContainerColor = Lz.Highlight,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    alarm = alarm.copy(hour = state.hour, minute = state.minute)
                    showTimePicker = false
                }) { Text("OK", color = Lz.AccentText, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = Lz.TextSoft) }
            },
        )
    }

    if (showWakeUp) {
        OptionsDialog(
            title = "Wake-up",
            options = WakeUpStyle.entries.map { it.label },
            selected = WakeUpStyle.of(alarm).ordinal,
            onSelect = { i ->
                val style = WakeUpStyle.entries[i]
                alarm = alarm.copy(vibrateFirst = style.vibrateFirst, gradualVolume = style.gradual, vibration = true)
                showWakeUp = false
            },
            onDismiss = { showWakeUp = false },
        )
    }

    if (showSnooze) {
        OptionsDialog(
            title = "Snooze",
            options = SNOOZE_OPTIONS.map { if (it == 0) "Off" else "$it min" },
            selected = SNOOZE_OPTIONS.indexOf(alarm.snoozeMinutes).coerceAtLeast(0),
            onSelect = { i ->
                alarm = alarm.copy(snoozeMinutes = SNOOZE_OPTIONS[i])
                showSnooze = false
            },
            onDismiss = { showSnooze = false },
        )
    }
}

@Composable
private fun RepeatDays(mask: Int, onChange: (Int) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, day ->
            val on = NextAlarm.repeatsOn(mask, day)
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (on) Lz.Highlight else Color.Transparent)
                    .then(if (on) Modifier else Modifier.border(1.dp, Lz.Outline, CircleShape))
                    .selectable(selected = on, role = Role.Checkbox) { onChange(mask xor NextAlarm.bit(day)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letters[i],
                    color = if (on) Color.White else Lz.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun soundTitle(soundUri: String?): String {
    val context = LocalContext.current
    return remember(soundUri) {
        if (soundUri == null) {
            "Default alarm sound"
        } else {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context) }
                .getOrNull() ?: "Custom sound"
        }
    }
}

@Composable
private fun OptionsDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lz.Surface,
        title = { Text(title, color = Lz.Text) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = i == selected, role = Role.RadioButton) { onSelect(i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = i == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Lz.Accent, unselectedColor = Lz.TextMuted),
                        )
                        Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Lz.TextSoft) }
        },
    )
}
