package se.lyzio.anatole.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.lyzio.anatole.app
import se.lyzio.anatole.data.AlarmDefaults
import se.lyzio.anatole.engine.Sensitivity
import se.lyzio.anatole.engine.WakeRules
import se.lyzio.anatole.ui.about.BackHeader
import se.lyzio.anatole.ui.alarms.WINDOW_LABELS
import se.lyzio.anatole.ui.alarms.WINDOW_OPTIONS
import se.lyzio.anatole.ui.components.ChipRow
import se.lyzio.anatole.ui.components.ChoiceDialog
import se.lyzio.anatole.ui.components.SectionLabel
import se.lyzio.anatole.ui.components.Segmented
import se.lyzio.anatole.ui.components.ValueRow
import se.lyzio.anatole.ui.theme.Lz

private val SNOOZES = listOf(0, 5, 9, 10, 15)

/** Settings › Alarm defaults: wake window and snooze for new alarms, sensitivity for all. */
@Composable
fun AlarmDefaultsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val d by remember { app.settings.alarmDefaults }.collectAsState(initial = AlarmDefaults())
    val finished by remember { app.database.nightDao().observeFinishedCount() }.collectAsState(initial = 0)
    var pickSnooze by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackHeader("Alarm defaults", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Wake window for new alarms")
                ChipRow(WINDOW_LABELS, WINDOW_OPTIONS.indexOf(d.windowMinutes).coerceAtLeast(0), enabled = true) { i ->
                    app.scope.launch { app.settings.setDefaultWindow(WINDOW_OPTIONS[i]) }
                }
                Text(
                    "The alarm rings at the first clear light sleep in the window, and always by the alarm time. Each alarm can change it.",
                    color = Lz.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Sensitivity · all alarms")
                Segmented(listOf("Cautious", "Balanced", "Eager"), d.sensitivity.ordinal, onSelect = { i ->
                    app.scope.launch { app.settings.setSensitivity(Sensitivity.entries[i]) }
                })
                Text(
                    "Cautious waits for clear, longer movement. Eager also wakes you at one strong movement. " +
                        (WakeRules.learningNight(finished)?.let { "Learning your sleep: night $it of ${WakeRules.LEARNING_NIGHTS}, until then Cautious is used." } ?: ""),
                    color = Lz.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
                ValueRow(
                    "Snooze for new alarms",
                    if (d.snoozeMinutes == 0) "Off" else "${d.snoozeMinutes} min",
                    onClick = { pickSnooze = true },
                    showDivider = false,
                )
            }
            Text(
                "Snooze is smart while tracking: it rings at the next light sleep after half the snooze, at the latest when the snooze ends.",
                color = Lz.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }

    if (pickSnooze) {
        ChoiceDialog(
            "Snooze",
            SNOOZES.map { if (it == 0) "Off" else "$it min" },
            SNOOZES.indexOf(d.snoozeMinutes).coerceAtLeast(0),
            onSelect = { i ->
                app.scope.launch { app.settings.setDefaultSnooze(SNOOZES[i]) }
                pickSnooze = false
            },
            onDismiss = { pickSnooze = false },
        )
    }
}
