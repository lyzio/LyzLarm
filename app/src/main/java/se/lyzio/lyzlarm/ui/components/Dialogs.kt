package se.lyzio.lyzlarm.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.ui.theme.Lz

/** Pick one option; closes on selection. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
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

/** Time of day picker; [initialMinute] and the result are minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lz.Base,
        title = { Text(title, color = Lz.Text) },
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
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("OK", color = Lz.AccentText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Lz.TextSoft) }
        },
    )
}
