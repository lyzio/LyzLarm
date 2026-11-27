package se.lyzio.lyzlarm.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EmptyBedTest
import se.lyzio.lyzlarm.engine.Epoch
import se.lyzio.lyzlarm.engine.EpochAccumulator
import se.lyzio.lyzlarm.engine.OutOfBed
import se.lyzio.lyzlarm.engine.Point
import se.lyzio.lyzlarm.engine.Sample
import se.lyzio.lyzlarm.engine.WakeRules
import se.lyzio.lyzlarm.ui.Format
import se.lyzio.lyzlarm.ui.about.BackHeader
import se.lyzio.lyzlarm.ui.components.PrimaryButton
import se.lyzio.lyzlarm.ui.components.SecondaryButton
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val LEAVE_MS = 30_000L
private const val RECORD_MS = 10 * 60_000L

private enum class Phase { INTRO, LEAVING, RECORDING, DONE, FAILED }

/**
 * Settings › System check › Empty bed test: 10 minutes with the phone where it lies at
 * night and nobody in the bed. Saved as the reference for out-of-bed detection.
 */
@Composable
fun EmptyBedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val tracking by remember { app.database.nightDao().observeOpenNight() }.collectAsState(initial = null)
    val latest by remember { app.database.extrasDao().observeLatestEmptyBedTest() }.collectAsState(initial = null)
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var phaseStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("") }
    val epochs = remember { ArrayList<Epoch>() }

    // Keep the screen on while the test runs, so the sensor keeps delivering.
    val activity = context as? Activity
    DisposableEffect(phase) {
        val on = phase == Phase.LEAVING || phase == Phase.RECORDING
        if (on) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(phase) {
        while (phase == Phase.LEAVING || phase == Phase.RECORDING) {
            now = System.currentTimeMillis()
            if (phase == Phase.LEAVING && now - phaseStart >= LEAVE_MS) {
                phase = Phase.RECORDING
                phaseStart = now
                synchronized(epochs) { epochs.clear() }
            }
            if (phase == Phase.RECORDING && now - phaseStart >= RECORD_MS) {
                val recorded = synchronized(epochs) { epochs.toList() }
                val result = withContext(Dispatchers.IO) { save(context, phaseStart, now, recorded) }
                message = result.second
                phase = if (result.first) Phase.DONE else Phase.FAILED
            }
            delay(500L)
        }
    }

    if (phase == Phase.RECORDING) {
        DisposableEffect(Unit) {
            val sm = context.getSystemService(SensorManager::class.java)
            val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val acc = EpochAccumulator()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
                    val t = System.currentTimeMillis() - ageMs.coerceAtLeast(0L)
                    acc.add(Sample(t, event.values[0], event.values[1], event.values[2]))?.let { synchronized(epochs) { epochs.add(it) } }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) sm.registerListener(listener, sensor, 50_000)
            onDispose { sm.unregisterListener(listener) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackHeader("Empty bed test", onBack)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (phase) {
                Phase.INTRO -> {
                    Text(
                        "This teaches Anatole what your bed looks like with nobody in it, so it can tell when you get up at night.",
                        color = Lz.Text,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    )
                    Steps(
                        listOf(
                            "Put the phone where it lies at night: on the mattress near your pillow, face down.",
                            "Tap Start, then leave the bed within 30 seconds.",
                            "Nobody (pets included) on the bed for 10 minutes. Try not to bump it.",
                        ),
                    )
                    latest?.let {
                        Text(
                            "Last test: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.startTime)) +
                                ". A new test replaces it.",
                            color = Lz.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    if (tracking != null) {
                        Text("Sleep tracking is on. Stop it first.", color = Lz.Warning, fontSize = 14.sp)
                    }
                    PrimaryButton("Start", onClick = {
                        phaseStart = System.currentTimeMillis()
                        now = phaseStart
                        phase = Phase.LEAVING
                    }, enabled = tracking == null)
                }
                Phase.LEAVING, Phase.RECORDING -> {
                    val leaving = phase == Phase.LEAVING
                    val total = if (leaving) LEAVE_MS else RECORD_MS
                    val left = total - (now - phaseStart)
                    Text(if (leaving) "Leave the bed now" else "Recording the empty bed", color = Lz.TextMuted, fontSize = 15.sp)
                    Text(
                        Format.countdown(left),
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 72.sp,
                        color = Lz.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinearProgressIndicator(
                        progress = { ((now - phaseStart).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Lz.Accent,
                        trackColor = Lz.Surface,
                    )
                    Text(
                        if (leaving) "Recording starts when the countdown ends." else "Come back when it is done. The screen stays on.",
                        color = Lz.TextSoft,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Cancel", { phase = Phase.INTRO })
                }
                Phase.DONE, Phase.FAILED -> {
                    Text(
                        if (phase == Phase.DONE) "Saved" else "Not saved",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        color = if (phase == Phase.DONE) Lz.Ok else Lz.Warning,
                    )
                    Text(message, color = Lz.Text, fontSize = 15.sp, lineHeight = 22.sp)
                    PrimaryButton(if (phase == Phase.DONE) "Done" else "Try again", onClick = {
                        if (phase == Phase.DONE) onBack() else phase = Phase.INTRO
                    })
                }
            }
        }
    }
}

@Composable
private fun Steps(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEachIndexed { i, line ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text("${i + 1}", color = Lz.AccentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(line, color = Lz.TextSoft, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** Saves the test as the new reference. Returns success and a message for the screen. */
private suspend fun save(context: Context, start: Long, end: Long, epochs: List<Epoch>): Pair<Boolean, String> {
    val points = epochs.map { Point(it.startMs, it.movement, it.noise) }
    val ref = OutOfBed.reference(points)
        ?: return false to "Too little sensor data was recorded. Keep the app open on this screen during the test."
    if (ref.movementMax > WakeRules.RESTLESS) {
        return false to "The bed moved during the test (someone sat down, or a pet?). Run it again with nobody on the bed."
    }
    val text = points.joinToString(";") { "${it.timeMs},${fmt(it.movement)},${fmt(it.noise ?: 0f)}" }
    context.app.database.extrasDao().insertEmptyBedTest(
        EmptyBedTest(
            startTime = start,
            endTime = end,
            noiseP50 = ref.noiseP50,
            noiseP90 = ref.noiseP90,
            movementMax = ref.movementMax,
            epochCount = ref.epochs,
            epochs = text,
        ),
    )
    return true to "Out-of-bed detection is on from tonight. Empty bed noise: ${fmt(ref.noiseP50)} m/s² " +
        "(90% below ${fmt(ref.noiseP90)}). If lying still looks the same as this, the Morning screen will say detection was not reliable that night."
}

private fun fmt(v: Float) = String.format(Locale.ROOT, "%.5f", v)

/** CSV of the latest empty bed test, shared through the share sheet. */
object EmptyBedExport {
    fun csv(test: EmptyBedTest): String = buildString {
        append("# Anatole empty bed test\n")
        append("# start_ms,").append(test.startTime).append('\n')
        append("# end_ms,").append(test.endTime).append('\n')
        append("# noise_p50,").append(fmt(test.noiseP50)).append('\n')
        append("# noise_p90,").append(fmt(test.noiseP90)).append('\n')
        append("# movement_max,").append(fmt(test.movementMax)).append('\n')
        append("time_ms,movement,noise\n")
        test.epochs.split(';').filter { it.isNotBlank() }.forEach { append(it).append('\n') }
    }

    suspend fun share(context: Context) {
        val test = context.app.database.extrasDao().latestEmptyBedTest()
        if (test == null) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Run the test first", Toast.LENGTH_SHORT).show() }
            return
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "anatole-empty-bed-test.csv")
        file.writeText(csv(test))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            runCatching {
                context.startActivity(Intent.createChooser(send, "Export empty bed test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show() }
        }
    }
}
