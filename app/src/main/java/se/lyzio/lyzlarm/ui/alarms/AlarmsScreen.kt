package se.lyzio.lyzlarm.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.alarm.NextAlarm
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.Alarm
import se.lyzio.lyzlarm.ui.Format
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.components.LzSwitch
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

@Composable
fun AlarmsScreen(onEditAlarm: (Long) -> Unit) {
    val context = LocalContext.current
    val container = context.app
    val alarms by remember { container.database.alarmDao().observeAll() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // Refresh the countdown every 30 seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Alarms", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Lz.Text)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Lz.Surface)
                    .border(1.dp, Lz.Outline, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "New alarm") { onEditAlarm(0L) },
                contentAlignment = Alignment.Center,
            ) { LzIcon(R.drawable.ic_plus, "New alarm", size = 20.dp) }
        }

        val list = alarms ?: return@Column
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { NextWakeCard(list, now) }
            if (list.isEmpty()) {
                item {
                    Text(
                        "No alarms yet. Tap + to add one.",
                        color = Lz.TextMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(list, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onEditAlarm(alarm.id) },
                    onToggle = { enabled ->
                        scope.launch {
                            val updated = alarm.copy(enabled = enabled)
                            withContext(Dispatchers.IO) { container.database.alarmDao().update(updated) }
                            if (enabled) container.scheduler.schedule(updated) else container.scheduler.cancel(updated)
                            now = System.currentTimeMillis()
                        }
                    },
                )
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

@Composable
private fun NextWakeCard(alarms: List<Alarm>, now: Long) {
    val context = LocalContext.current
    val scheduler = context.app.scheduler
    val next = alarms
        .filter { it.enabled }
        .map { alarm ->
            val ring = scheduler.snoozedUntil(alarm.id) ?: scheduler.nextRing(alarm)
            alarm to ring
        }
        .minByOrNull { it.second }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Lz.Highlight)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (next == null) {
            Text("No alarm set", color = Lz.HighlightText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Turn on an alarm to wake up on time.", color = Lz.HighlightSoft, fontSize = 14.sp)
        } else {
            val (alarm, ringAt) = next
            val snoozed = scheduler.snoozedUntil(alarm.id) != null
            Text(
                "Next wake-up · in ${Format.duration(ringAt - now)}",
                color = Lz.HighlightText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                Format.time(context, ringAt),
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                letterSpacing = (-1).sp,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Text(
                if (snoozed) "Snoozed" else "Rings at exactly this time · smart wake arrives in v0.3",
                color = Lz.HighlightSoft,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val time = Format.time(context, alarm.hour, alarm.minute)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (alarm.enabled) Lz.Surface else Lz.SurfaceDim)
            .clickable(onClick = onClick, onClickLabel = "Edit alarm")
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                time,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                color = if (alarm.enabled) Lz.Text else Lz.TextMuted,
            )
            Text(
                "${NextAlarm.describeRepeat(alarm.repeatDays)} · fixed time",
                fontSize = 13.sp,
                color = if (alarm.enabled) Lz.TextMuted else Lz.TextDim,
            )
        }
        LzSwitch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            contentDescription = "Alarm at $time",
        )
    }
}
