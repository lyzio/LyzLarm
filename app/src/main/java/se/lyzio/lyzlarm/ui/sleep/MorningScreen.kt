package se.lyzio.lyzlarm.ui.sleep

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.data.Night
import se.lyzio.lyzlarm.engine.Bars
import se.lyzio.lyzlarm.engine.Gap
import se.lyzio.lyzlarm.engine.NightAnalysis
import se.lyzio.lyzlarm.engine.NightResult
import se.lyzio.lyzlarm.engine.SleepEngine
import se.lyzio.lyzlarm.engine.Stage
import se.lyzio.lyzlarm.engine.StagePoint
import se.lyzio.lyzlarm.tracking.NightCsv
import se.lyzio.lyzlarm.tracking.NightExport
import se.lyzio.lyzlarm.tracking.NightStats
import se.lyzio.lyzlarm.tracking.NightSummary
import se.lyzio.lyzlarm.ui.Format
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.components.SoonBadge
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

private val BarColor = Color(0xFF4FB3A9)
private val WindowColor = Lz.Accent.copy(alpha = 0.22f)
private val OutOfBedColor = Lz.Warning.copy(alpha = 0.35f)

/** "at light sleep" etc., under the Woke tile. */
fun wakeReasonText(reason: String?): String? = when (SleepEngine.WakeReason.parse(reason)) {
    SleepEngine.WakeReason.LIGHT_SLEEP -> "at light sleep"
    SleepEngine.WakeReason.ALREADY_AWAKE -> "already awake"
    SleepEngine.WakeReason.WINDOW_END -> "end of window"
    SleepEngine.WakeReason.FIXED -> "fixed time"
    SleepEngine.WakeReason.MANUAL -> "stopped by hand"
    null -> null
}

private data class NightData(val summary: NightSummary, val epochs: List<EpochRow>)

/**
 * The night's movement graph, gaps and timeline. [nightId] 0 = the latest finished night
 * (opened right after the alarm is dismissed).
 */
@Composable
fun MorningScreen(nightId: Long, onBack: () -> Unit, onOpenNights: () -> Unit) {
    val context = LocalContext.current
    val dao = context.app.database.nightDao()
    val night by remember(nightId) {
        if (nightId == 0L) dao.observeLatestFinished() else dao.observeNight(nightId)
    }.collectAsState(initial = null)
    val open by remember { dao.observeOpenNight() }.collectAsState(initial = null)
    val openEvents by remember(open?.id) {
        open?.let { dao.observeEvents(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    // Newest first: a dismiss after the last ring means the stay-awake check is running.
    val stayAwakeRunning = openEvents.firstOrNull { it.type == EventType.RANG || it.type == EventType.DISMISSED }?.type == EventType.DISMISSED
    val events by remember(night?.id) {
        night?.let { dao.observeEvents(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val data by produceState<NightData?>(null, night?.id, night?.endTime) {
        val n = night
        value = n?.let {
            withContext(Dispatchers.IO) { NightData(NightStats.summary(context, it), NightStats.epochs(context, it.id)) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        val n = night
        Header(
            title = n?.let { Format.nightRange(it.startTime, it.endTime ?: System.currentTimeMillis()) } ?: "",
            onBack = onBack,
            onOpenNights = onOpenNights,
        )
        val d = data
        when {
            // Just dismissed: the night is still being saved (or the stay-awake check runs).
            nightId == 0L && open != null -> Message(
                if (stayAwakeRunning) "Stay-awake check: tracking runs 5 more minutes. Last night is saved after that." else "Saving last night…",
            )
            n == null || d == null -> if (n == null) Message("No tracked nights yet.") else Unit
            else -> NightContent(n, d, events)
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, onOpenNights: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(R.drawable.ic_back, "Back", onBack)
        Text(title, color = Lz.TextSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundIcon(R.drawable.ic_list, "Past nights", onOpenNights)
    }
}

@Composable
private fun RoundIcon(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LzIcon(icon, label) }
}

@Composable
private fun Message(text: String) {
    Text(text, color = Lz.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NightContent(night: Night, data: NightData, events: List<EventRow>) {
    val context = LocalContext.current
    val latest by remember { context.app.database.nightDao().observeLatestFinished() }.collectAsState(initial = null)
    val isLatest = latest == null || latest?.id == night.id
    var showExport by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (isLatest) "Last night" else "Night",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = Lz.Text,
        )
        val r = data.summary.result
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Time asleep", r?.let { Format.measured(it.asleepMs) } ?: "–", Lz.Text, Modifier.weight(1f))
            Tile("In bed", r?.let { Format.measured(it.inBedMs) } ?: Format.measured(data.summary.trackedMs), Lz.Text, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Fell asleep in", r?.fellAsleepMs?.let { Format.measured(it) } ?: "–", Lz.Text, Modifier.weight(1f))
            Tile(
                "Woke",
                night.wakeTime?.let { Format.time(context, it) } ?: "–",
                Lz.AccentText,
                Modifier.weight(1f),
                sub = wakeReasonText(night.wakeReason),
            )
        }

        val alarmAt = events.filter { it.type == EventType.RANG }.minOfOrNull { it.time }
            ?: night.wakeTime.takeIf { night.wakeReason == SleepEngine.WakeReason.FIXED.name }
        val window = events.firstOrNull { it.type == EventType.WINDOW_OPENED }?.detail?.split(';')
            ?.takeIf { it.size == 3 }?.let { parts -> parts[1].toLongOrNull()?.let { a -> parts[2].toLongOrNull()?.let { b -> a to b } } }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (r != null && r.onsetMs != null) {
                StageGraph(start = night.startTime, end = data.summary.endOrNow, result = r)
            }
            MovementGraph(
                start = night.startTime,
                end = data.summary.endOrNow,
                epochs = data.epochs,
                gaps = data.summary.gaps,
                alarmAt = alarmAt,
                window = window,
                outOfBed = r?.outOfBed.orEmpty().map { it.startMs to it.endMs },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendItem("Movement (5 min)") { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(BarColor)) }
                LegendItem("Not tracked") { HatchSwatch() }
                if (window != null) LegendItem("Wake window") { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(WindowColor)) }
                if (r?.outOfBed?.isNotEmpty() == true) LegendItem("Out of bed") { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(OutOfBedColor)) }
                LegendItem("Alarm") { Box(Modifier.size(8.dp).clip(CircleShape).background(Lz.Accent)) }
            }
            if (r != null && !r.outOfBedReliable) {
                Text(
                    "Out-of-bed detection was not reliable tonight: lying still looked like the empty bed. Nothing was counted as out of bed.",
                    color = Lz.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            val bStart = night.batteryStart
            val bEnd = night.batteryEnd
            if (bStart != null && bEnd != null) {
                Text(
                    "Battery $bStart% → $bEnd% (${(bStart - bEnd).coerceAtLeast(0)}% used)",
                    color = Lz.TextSoft,
                    fontSize = 13.sp,
                )
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
            val list = events.sortedByDescending { it.time }.filter { it.type != EventType.TRACKING_STOPPED || night.wakeReason == SleepEngine.WakeReason.MANUAL.name }
            list.forEachIndexed { i, e ->
                EventLine(e)
                if (i < list.lastIndex) HorizontalDivider(color = Lz.Divider)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface).alpha(0.5f)
                .heightIn(min = 56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How did you wake up?", color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            SoonBadge("Arrives in v0.5")
        }

        Row(
            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { showExport = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LzIcon(R.drawable.ic_export, null, tint = Lz.AccentText, size = 20.dp)
            Text("Export this night (CSV)", color = Lz.AccentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showExport) ExportDialog(night) { showExport = false }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) { swatch() }
        Text(label, color = Lz.TextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun HatchSwatch() {
    Canvas(Modifier.size(12.dp)) { hatch(Offset.Zero, size) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.hatch(topLeft: Offset, size: Size) {
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        drawRect(Lz.Divider, topLeft, size)
        val step = 6.dp.toPx()
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(
                Lz.TextDim.copy(alpha = 0.6f),
                Offset(x, topLeft.y + size.height),
                Offset(x + size.height, topLeft.y),
                strokeWidth = 1.dp.toPx(),
            )
            x += step
        }
    }
}

@Composable
private fun Tile(label: String, value: String, valueColor: Color, modifier: Modifier, sub: String? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Lz.Surface).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Lz.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        BasicText(
            value,
            style = TextStyle(color = valueColor, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 18.sp),
        )
        if (sub != null) Text(sub, color = Lz.TextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

/** Movement bars per 5 min, gaps hatched, alarm as an accent line with a dot, time axis every 2 h. */
@Composable
private fun MovementGraph(
    start: Long,
    end: Long,
    epochs: List<EpochRow>,
    gaps: List<Gap>,
    alarmAt: Long?,
    window: Pair<Long, Long>?,
    outOfBed: List<Pair<Long, Long>>,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val bars = remember(epochs) { Bars.of(epochs.map { it.time to it.movement }) }
    val labelStyle = TextStyle(color = Lz.TextMuted, fontSize = 11.sp)
    val ticks = remember(start, end) { twoHourTicks(start, end) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Movement graph from ${Format.time(context, start)} to ${Format.time(context, end)}, ${gaps.size} tracking gaps" },
    ) {
        val axisW = 44.dp.toPx()
        val axisH = 22.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - axisW
        val plotH = size.height - axisH - top
        val span = (end - start).coerceAtLeast(60_000L).toFloat()
        fun x(t: Long) = axisW + ((t - start) / span).coerceIn(0f, 1f) * plotW

        // Y labels.
        drawText(measurer, "More", Offset(0f, top - 2.dp.toPx()), labelStyle)
        drawText(measurer, "Still", Offset(0f, top + plotH - 14.dp.toPx()), labelStyle)
        drawLine(Lz.Divider, Offset(axisW, top + plotH), Offset(size.width, top + plotH), 1.dp.toPx())

        // Wake window and out-of-bed bands, behind everything else.
        if (window != null) {
            val l = x(window.first)
            val r = x(window.second)
            if (r - l > 0.5f) drawRect(WindowColor, Offset(l, top), Size(r - l, plotH))
        }
        for ((a, b) in outOfBed) {
            val l = x(a)
            val r = x(b)
            if (r - l > 0.5f) drawRect(OutOfBedColor, Offset(l, top), Size(maxOf(r - l, 2f), plotH))
        }

        // Gaps.
        for (g in gaps) {
            val l = x(g.startMs)
            val r = x(g.endMs)
            if (r - l > 0.5f) hatch(Offset(l, top), Size(r - l, plotH))
        }

        // Bars: square-root scale so small movements stay visible next to big ones.
        val max = bars.maxOfOrNull { it.movement }?.takeIf { it > 0f } ?: 1f
        val barW = (Bars.BAR_MS / span * plotW).coerceAtLeast(1f)
        clipRect(axisW, top, size.width, top + plotH) {
            for (b in bars) {
                val h = sqrt(b.movement / max) * plotH
                if (h <= 0f) continue
                val l = axisW + (b.startMs - start) / span * plotW
                drawRect(BarColor, Offset(l + barW * 0.1f, top + plotH - h), Size(barW * 0.8f, h))
            }
        }

        // Alarm.
        if (alarmAt != null && alarmAt in start..end) {
            val ax = x(alarmAt)
            drawLine(Lz.Accent, Offset(ax, top), Offset(ax, top + plotH), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Lz.Accent, 4.dp.toPx(), Offset(ax, top))
        }

        // Time axis.
        for (t in ticks) {
            val tx = x(t)
            val text = Format.time(context, t)
            val layout = measurer.measure(text, labelStyle)
            val lx = (tx - layout.size.width / 2f).coerceIn(axisW, size.width - layout.size.width)
            drawLine(Lz.Divider, Offset(tx, top + plotH), Offset(tx, top + plotH + 4.dp.toPx()), 1.dp.toPx())
            drawText(layout, topLeft = Offset(lx, top + plotH + 6.dp.toPx()))
        }
    }
}

/** Estimated stages (Awake / Light / Deep) per 5 minutes as a step line, labelled as an estimate. */
@Composable
private fun StageGraph(start: Long, end: Long, result: NightResult) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Lz.TextMuted, fontSize = 11.sp)
    val blocks = remember(result) { NightAnalysis.blocks(result.stages) }
    val deep = result.stages.count { it.stage == Stage.DEEP } * SleepEngine.EPOCH_MS
    val light = result.stages.count { it.stage == Stage.LIGHT } * SleepEngine.EPOCH_MS
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep stages", color = Lz.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Estimate", color = Lz.TextMuted, fontSize = 12.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .semantics {
                    contentDescription = "Estimated sleep stages: deep ${Format.measured(deep)}, light ${Format.measured(light)}"
                },
        ) {
            val axisW = 44.dp.toPx()
            val plotW = size.width - axisW
            val rowH = size.height / 3f
            val span = (end - start).coerceAtLeast(60_000L).toFloat()
            fun x(t: Long) = axisW + ((t - start) / span).coerceIn(0f, 1f) * plotW
            fun y(stage: Stage) = rowH * stage.ordinal + rowH / 2f
            listOf(Stage.AWAKE to "Awake", Stage.LIGHT to "Light", Stage.DEEP to "Deep").forEach { (st, label) ->
                val layout = measurer.measure(label, labelStyle)
                drawText(layout, topLeft = Offset(0f, y(st) - layout.size.height / 2f))
                drawLine(Lz.Divider, Offset(axisW, y(st)), Offset(size.width, y(st)), 1.dp.toPx())
            }
            val blockW = Bars.BAR_MS / span * plotW
            var prev: StagePoint? = null
            for (b in blocks) {
                val l = x(b.timeMs)
                val color = when (b.stage) {
                    Stage.AWAKE -> Lz.AccentSoft
                    Stage.LIGHT -> BarColor
                    Stage.DEEP -> Lz.Highlight
                }
                val p = prev
                // Vertical step from the previous block, unless there was a gap.
                if (p != null && b.timeMs - p.timeMs == Bars.BAR_MS && p.stage != b.stage) {
                    drawLine(Lz.TextDim, Offset(l, y(p.stage)), Offset(l, y(b.stage)), 1.5.dp.toPx())
                }
                drawLine(color, Offset(l, y(b.stage)), Offset(l + blockW, y(b.stage)), 4.dp.toPx(), StrokeCap.Butt)
                prev = b
            }
        }
        Text("Deep ${Format.measured(deep)} · Light ${Format.measured(light)}", color = Lz.TextSoft, fontSize = 13.sp)
    }
}

/** Even hours (…22:00, 00:00, 02:00…) between start and end. */
private fun twoHourTicks(start: Long, end: Long): List<Long> {
    val zone = ZoneId.systemDefault()
    var t = Instant.ofEpochMilli(start).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
    if (t.hour % 2 == 1) t = t.plusHours(1)
    val out = ArrayList<Long>()
    while (t.toInstant().toEpochMilli() <= end && out.size < 13) {
        out.add(t.toInstant().toEpochMilli())
        t = t.plusHours(2)
    }
    return out
}

private fun challengeName(key: String?): String =
    se.lyzio.lyzlarm.challenge.ChallengeType.entries.firstOrNull { it.key == key }?.label?.lowercase() ?: (key ?: "")

@Composable
private fun EventLine(e: EventRow) {
    val context = LocalContext.current
    val (icon, text) = when (e.type) {
        EventType.RANG -> R.drawable.ic_alarm to when {
            e.detail == "stay_awake" -> "Alarm rang again (back in bed)"
            e.detail?.startsWith("snooze") == true -> if (e.detail.contains("light_sleep")) "Snooze ended at light sleep" else "Snooze ended"
            else -> "Alarm rang" + (wakeReasonText(e.detail)?.let { " · $it" } ?: "")
        }
        EventType.WINDOW_OPENED -> R.drawable.ic_clock to (e.detail?.split(';')?.firstOrNull()?.let { "Wake window opened · $it min" } ?: "Listening closely")
        EventType.LIGHT_SLEEP_DETECTED -> R.drawable.ic_wave to if (e.detail == "already_awake") "Already awake detected" else "Light sleep detected"
        EventType.OUT_OF_BED -> R.drawable.ic_sleep to "Out of bed"
        EventType.BACK_IN_BED -> R.drawable.ic_sleep to "Back in bed"
        EventType.CHALLENGE_SHOWN -> R.drawable.ic_lock to "Challenge shown · ${challengeName(e.detail)}"
        EventType.CHALLENGE_SOLVED -> R.drawable.ic_check to "Challenge solved · ${challengeName(e.detail)}"
        EventType.CHALLENGE_FAILED -> R.drawable.ic_lock to "Wrong answer · ${challengeName(e.detail)}"
        EventType.STAY_AWAKE_RANG -> R.drawable.ic_warning to "Stay-awake check: back in bed"
        EventType.SNOOZED -> R.drawable.ic_clock to "Snoozed ${e.detail ?: ""} min"
        EventType.DISMISSED -> R.drawable.ic_check to if (e.detail == "no response") "Alarm stopped, no response" else "Alarm dismissed"
        EventType.TRACKING_INTERRUPTED -> R.drawable.ic_warning to "Tracking interrupted for ${e.detail ?: "?"} min"
        EventType.TRACKING_STARTED -> R.drawable.ic_moon to ("Tracking started" + (e.detail?.let { " · battery $it%" } ?: ""))
        EventType.TRACKING_STOPPED -> R.drawable.ic_moon to (
            "Tracking stopped" + (e.detail?.split(';')?.getOrNull(1)?.let { " · battery $it%" } ?: "") +
                (if (e.detail?.contains("alarm_off") == true) " · alarm turned off" else "")
            )
        else -> R.drawable.ic_info to e.type
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LzIcon(icon, null, tint = if (e.type == EventType.TRACKING_INTERRUPTED) Lz.Warning else Lz.TextSoft, size = 20.dp)
        Text(text, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(Format.time(context, e.time), color = Lz.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ExportDialog(night: Night, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val appContext = context.applicationContext
    var preparing by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            app.scope.launch {
                runCatching { NightExport.saveTo(appContext, night, uri) }
                    .onSuccess { withContext(Dispatchers.Main) { Toast.makeText(appContext, "Night saved", Toast.LENGTH_SHORT).show() } }
                    .onFailure { withContext(Dispatchers.Main) { Toast.makeText(appContext, "Could not save the file", Toast.LENGTH_SHORT).show() } }
            }
        }
        onDismiss()
    }
    fun share() {
        preparing = true
        // App scope: a large night (raw data) takes a few seconds and must not be
        // cancelled if the dialog closes meanwhile.
        app.scope.launch {
            val intent = runCatching { NightExport.shareIntent(appContext, night) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (intent != null) {
                    runCatching { appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(appContext, "No app to share with", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(appContext, "Could not prepare the export", Toast.LENGTH_SHORT).show()
                }
                preparing = false
                onDismiss()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lz.Surface,
        title = { Text("Export this night", color = Lz.Text) },
        text = {
            if (preparing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Lz.Accent, strokeWidth = 2.5.dp)
                    Text("Preparing export…", color = Lz.TextSoft, fontSize = 14.sp)
                }
            } else {
                Text(
                    "A CSV file with the movement per 30 s and the night's events" +
                        (if (night.endTime != null) ", plus raw sensor data if it was recorded and is still kept." else "."),
                    color = Lz.TextSoft,
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::share, enabled = !preparing) {
                Text("Share…", color = if (preparing) Lz.TextDim else Lz.AccentText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { save.launch(NightCsv.fileName(night)) }, enabled = !preparing) {
                Text("Save to file…", color = if (preparing) Lz.TextDim else Lz.TextSoft)
            }
        },
    )
}
