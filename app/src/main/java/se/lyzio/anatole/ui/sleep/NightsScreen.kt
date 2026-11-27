package se.lyzio.anatole.ui.sleep

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.anatole.R
import se.lyzio.anatole.app
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.tracking.NightStats
import se.lyzio.anatole.tracking.NightSummary
import se.lyzio.anatole.ui.Format
import se.lyzio.anatole.ui.components.LzIcon
import se.lyzio.anatole.ui.theme.Lz
import se.lyzio.anatole.ui.theme.SpaceGrotesk

/** "Sleep" tab: finished nights, newest first. Tap opens the graph, long press deletes. */
@Composable
fun NightsScreen(onOpenNight: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val nights by remember { app.database.nightDao().observeFinished() }.collectAsState(initial = null)
    val summaries by produceState<List<NightSummary>?>(null, nights) {
        val list = nights ?: return@produceState
        value = withContext(Dispatchers.IO) { list.map { NightStats.summary(context, it) } }
    }
    var toDelete by remember { mutableStateOf<Night?>(null) }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        Text("Past nights", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Lz.Text)
        Text(
            "Newest first · tap a night for its graph · long press to delete",
            color = Lz.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        val list = summaries ?: return@Column
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (list.isEmpty()) {
                item {
                    Text(
                        "No nights yet. At bedtime, tap Start sleep on the Alarms tab and put the phone on the mattress.",
                        color = Lz.TextMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
            items(list, key = { it.night.id }) { s ->
                NightRow(s, onClick = { onOpenNight(s.night.id) }, onLongClick = { toDelete = s.night })
            }
            item {
                Text(
                    "Trends and averages arrive in v0.5",
                    color = Lz.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }
    }

    toDelete?.let { night ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Lz.Surface,
            title = { Text("Delete this night?", color = Lz.Text) },
            text = {
                Text(
                    "${Format.nightRange(night.startTime, night.endTime ?: night.startTime)}: its graph, events and raw data are removed from this phone.",
                    color = Lz.TextSoft,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.scope.launch { app.database.nightDao().deleteNight(night.id) }
                    toDelete = null
                }) { Text("Delete", color = Lz.AccentText, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel", color = Lz.TextSoft) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NightRow(s: NightSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val n = s.night
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Lz.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Delete night")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Format.nightRange(n.startTime, s.endOrNow), color = Lz.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${Format.time(context, n.startTime)} – ${Format.time(context, s.endOrNow)}", color = Lz.TextMuted, fontSize = 13.sp)
                if (s.gaps.isNotEmpty()) {
                    Text(
                        "Gap",
                        color = Lz.OnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Lz.Warning).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val r = s.result
            Text(
                (r?.let { Format.measured(it.asleepMs) } ?: Format.measured(s.trackedMs)) + " asleep",
                color = Lz.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                (r?.let { Format.measured(it.inBedMs) } ?: Format.measured(s.trackedMs)) + " in bed",
                color = Lz.TextMuted,
                fontSize = 12.sp,
            )
        }
        LzIcon(R.drawable.ic_chevron, null, tint = Lz.TextMuted, size = 18.dp)
    }
}
