package se.lyzio.anatole.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.lyzio.anatole.AppClock
import se.lyzio.anatole.R
import se.lyzio.anatole.alarm.AlarmScheduler
import se.lyzio.anatole.alarm.Ringing
import se.lyzio.anatole.alarm.RingingScreen
import se.lyzio.anatole.app
import se.lyzio.anatole.data.Alarm
import se.lyzio.anatole.data.EmptyBedTest
import se.lyzio.anatole.data.EpochRow
import se.lyzio.anatole.data.EventRow
import se.lyzio.anatole.data.EventType
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.engine.NightAnalysis
import se.lyzio.anatole.engine.SleepEngine
import se.lyzio.anatole.tracking.TrackingService
import se.lyzio.anatole.tracking.toPoint
import se.lyzio.anatole.ui.alarms.EditAlarmScreen
import se.lyzio.anatole.ui.home.HomeScreen
import se.lyzio.anatole.ui.sleep.BedtimeScreen
import se.lyzio.anatole.ui.sleep.MorningScreen
import se.lyzio.anatole.ui.theme.AnatoleTheme
import se.lyzio.anatole.ui.theme.Lz
import se.lyzio.anatole.ui.theme.SpaceGrotesk
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.imageio.ImageIO

/**
 * Google Play / F-Droid store images (v0.4), rendered from the real screens on the JVM
 * (Robolectric, no emulator) with sample nights: the owner's three test nights.
 *
 * Output: app/build/store-images/ — icon.png (512×512), featureGraphic.png (1024×500) and
 * phoneScreenshots/1_alarms.png … 6_ringing.png (1080×1920). The release workflow copies
 * them into fastlane/metadata/android/en-US/images/. All are 24-bit PNG (no alpha), as Play asks.
 *
 * One test renders everything in one activity, switching screens, so there is one app
 * instance and one database.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshots {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Europe/Stockholm")
    private val out = File("build/store-images")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @After fun reset() {
        AppClock.fixed = null
        TrackingService._running.value = false
    }

    private enum class Screen { NONE, ICON, FEATURE, ALARMS, NIGHTS, MORNING, EDIT, BEDTIME, RINGING }

    private var screen by mutableStateOf(Screen.NONE)

    @Test fun renderStoreImages() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Locale.setDefault(Locale.UK)
        val app = compose.activity.app
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        // Tuesday 24 November 2026, 23:40: bedtime.
        AppClock.fixed = at(24, 23, 40)

        val ids = runBlocking { seed() }

        compose.mainClock.autoAdvance = false
        compose.setContent {
            AnatoleTheme {
                when (screen) {
                    Screen.NONE -> Unit
                    Screen.ICON -> Icon()
                    Screen.FEATURE -> FeatureGraphic()
                    else -> Box(Modifier.fillMaxSize().background(Lz.Base).testTag(ROOT)) {
                        when (screen) {
                            Screen.ALARMS -> Home(tab = 0)
                            Screen.NIGHTS -> Home(tab = 1)
                            Screen.MORNING -> MorningScreen(nightId = ids.lastNight, onBack = {}, onOpenNights = {})
                            Screen.EDIT -> EditAlarmScreen(alarmId = ids.alarm, onDone = {})
                            Screen.BEDTIME -> BedtimeScreen(onFinished = {}, onBack = {})
                            Screen.RINGING -> RingingScreen(ringing(ids.alarm), onDismiss = {}, onSnooze = {})
                            else -> Unit
                        }
                    }
                }
            }
        }

        screen = Screen.ICON
        settle("Icon") { exists(ICON) }
        save(compose.onNodeWithTag(ICON), File(out, "icon.png"), 512, 512)

        screen = Screen.FEATURE
        settle("Feature graphic") { text("Anatole") }
        save(compose.onNodeWithTag(FEATURE), File(out, "featureGraphic.png"), 1024, 500)

        shot(Screen.ALARMS, "1_alarms") { text("Start sleep") && text("Last night") }
        shot(Screen.EDIT, "2_edit_alarm") { text("Edit alarm") && text("Rings between") }
        runBlocking { openTonight(ids.alarm) }
        TrackingService._running.value = true
        shot(Screen.BEDTIME, "3_bedtime") { text("Tracking your sleep") && text("Rings in") }
        runBlocking { closeTonight() }
        TrackingService._running.value = false
        AppClock.fixed = at(25, 6, 52)
        shot(Screen.RINGING, "4_ringing") { text("Good morning") }
        shot(Screen.MORNING, "5_morning") { text("Time asleep") && text("Estimate") }
        shot(Screen.NIGHTS, "6_past_nights") { text("Past nights") && text("asleep") }
    }

    // Screens

    @androidx.compose.runtime.Composable
    private fun Home(tab: Int) = HomeScreen(
        onEditAlarm = {}, onOpenAbout = {}, onRunTest = {}, onStartSleep = {}, onOpenBedtime = {},
        onOpenNight = {}, onOpenTracking = {}, onOpenAlarmDefaults = {}, onOpenEmptyBed = {},
        initialTab = tab,
    )

    /** Play icon: the launcher icon (logo C on the logo background), full square; Play rounds it. */
    @androidx.compose.runtime.Composable
    private fun Icon() {
        val side = with(LocalDensity.current) { 512.toDp() }
        Box(Modifier.size(side).background(Color(0xFF0F1C1F)).testTag(ICON)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
    }

    /** Feature graphic: base color, logo, name and tagline. */
    @androidx.compose.runtime.Composable
    private fun FeatureGraphic() {
        val d = LocalDensity.current
        val w = with(d) { 1024.toDp() }
        val h = with(d) { 500.toDp() }
        val u = with(d) { 1.toDp() } // one pixel, so the layout is in feature-graphic pixels
        Box(Modifier.size(w, h).background(Lz.Base).testTag(FEATURE)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = u * 96),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u * 56),
            ) {
                Image(painterResource(R.drawable.logo_mark), null, Modifier.size(u * 260))
                Column(verticalArrangement = Arrangement.spacedBy(u * 14)) {
                    Text(
                        "Anatole",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = with(d) { 124.toSp() },
                        letterSpacing = with(d) { (-3).toSp() },
                        color = Lz.Text,
                    )
                    Text("Sleep tracker & smart alarm", color = Lz.AccentText, fontSize = with(d) { 42.toSp() }, fontWeight = FontWeight.Medium)
                    Text("Fully offline · no ads · open source", color = Lz.TextMuted, fontSize = with(d) { 34.toSp() })
                }
            }
        }
    }

    private fun ringing(alarmId: Long) = Ringing(
        alarmId = alarmId,
        kind = AlarmScheduler.Kind.SMART,
        label = "Weekday alarm",
        ringTimeMillis = at(25, 6, 52),
        snoozeMinutes = 9,
        volume = 0.45f,
        vibratingOnly = false,
        reason = SleepEngine.WakeReason.LIGHT_SLEEP,
    )

    // Sample data

    private data class Ids(val alarm: Long, val lastNight: Long)

    private suspend fun seed(): Ids {
        val db = compose.activity.app.database
        val alarm = db.alarmDao().insert(Alarm(hour = 7, minute = 0, windowMinutes = 30, repeatDays = 0b0011111))
        db.alarmDao().insert(Alarm(hour = 9, minute = 0, windowMinutes = 20, repeatDays = 0b1100000))
        db.alarmDao().insert(Alarm(hour = 5, minute = 45, repeatDays = 0, enabled = false))
        db.extrasDao().insertEmptyBedTest(
            EmptyBedTest(
                startTime = at(21, 20, 0), endTime = at(21, 20, 10), noiseP50 = 0.002f, noiseP90 = 0.004f,
                movementMax = 0.1f, epochCount = 20, epochs = "",
            ),
        )
        // The owner's test nights, oldest first, ending on the morning of 24 November.
        insertNight("night-c.csv", start = at(21, 23, 25), alarmId = alarm, smart = false)
        insertNight("night-a.csv", start = at(22, 23, 5), alarmId = alarm, smart = true)
        val last = insertNight("night-b.csv", start = at(23, 23, 18), alarmId = alarm, smart = true, wakeAt = at(24, 6, 52))
        return Ids(alarm, last)
    }

    private fun testNight(name: String): List<Pair<Long, Float>> {
        val file = listOf("../sleep-engine/src/test/resources/nights/$name", "sleep-engine/src/test/resources/nights/$name")
            .map(::File).first { it.exists() }
        return file.readLines()
            .filter { it.isNotBlank() && it.first().isDigit() }
            .map { line -> line.split(',').let { it[0].toLong() * 1000L to it[1].toFloat() } }
    }

    private suspend fun insertNight(name: String, start: Long, alarmId: Long, smart: Boolean, wakeAt: Long? = null): Long {
        val dao = compose.activity.app.database.nightDao()
        val rows = testNight(name)
        val dataEnd = start + rows.last().first + SleepEngine.EPOCH_MS
        val end = wakeAt?.coerceAtMost(dataEnd) ?: dataEnd
        val epochs = rows.map { (offset, movement) -> EpochRow(0, start + offset, movement) }.filter { it.time < end }
        val points = epochs.map { it.toPoint() }
        val id = dao.insertNight(
            Night(
                alarmId = alarmId, startTime = start, endTime = end, wakeTime = end,
                wakeReason = if (smart) SleepEngine.WakeReason.LIGHT_SLEEP.name else SleepEngine.WakeReason.FIXED.name,
                batteryStart = 86, batteryEnd = 78, engineVersion = SleepEngine.VERSION, timeZone = zone.id,
                onsetEstimate = NightAnalysis.onsetIndex(points)?.let { points[it].timeMs },
            ),
        )
        dao.insertEpochs(epochs.map { it.copy(nightId = id) })
        val windowEnd = end + 8 * 60_000L
        val events = buildList {
            add(EventRow(nightId = id, time = start, type = EventType.TRACKING_STARTED, detail = "86"))
            if (smart) {
                add(EventRow(nightId = id, time = windowEnd - 30 * 60_000L, type = EventType.WINDOW_OPENED, detail = "30;${windowEnd - 30 * 60_000L};$windowEnd"))
                add(EventRow(nightId = id, time = end - 20_000L, type = EventType.LIGHT_SLEEP_DETECTED, detail = SleepEngine.WakeReason.LIGHT_SLEEP.key))
                add(EventRow(nightId = id, time = end - 15_000L, type = EventType.RANG, detail = SleepEngine.WakeReason.LIGHT_SLEEP.key))
            } else {
                add(EventRow(nightId = id, time = end - 15_000L, type = EventType.RANG, detail = SleepEngine.WakeReason.FIXED.key))
            }
            add(EventRow(nightId = id, time = end, type = EventType.DISMISSED))
            add(EventRow(nightId = id, time = end, type = EventType.TRACKING_STOPPED, detail = "78"))
        }
        dao.insertEvents(events)
        return id
    }

    /** Tonight, started at 23:32 with a few minutes of movement. */
    private suspend fun openTonight(alarmId: Long) {
        val dao = compose.activity.app.database.nightDao()
        val start = at(24, 23, 32)
        val id = dao.insertNight(Night(alarmId = alarmId, startTime = start, batteryStart = 91, engineVersion = SleepEngine.VERSION, timeZone = zone.id))
        dao.insertEvent(EventRow(nightId = id, time = start, type = EventType.TRACKING_STARTED, detail = "91"))
        dao.insertEpochs(testNight("night-a.csv").take(16).map { (o, m) -> EpochRow(id, start + o, m) })
    }

    private suspend fun closeTonight() {
        val dao = compose.activity.app.database.nightDao()
        dao.openNight()?.let { dao.deleteNight(it.id) }
    }

    // Rendering

    private fun exists(tag: String) = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun text(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Lets coroutines on other threads finish and the screen recompose until [ready]. */
    private fun settle(what: String, ready: () -> Boolean) {
        repeat(200) {
            frames(2)
            if (ready()) {
                // A little longer for values computed in the background (plans, summaries).
                repeat(15) { frames(2); Thread.sleep(40) }
                return
            }
            Thread.sleep(50)
        }
        error("$what did not appear")
    }

    private fun frames(n: Int) {
        repeat(n) {
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(s: Screen, name: String, ready: () -> Boolean) {
        screen = Screen.NONE
        frames(2)
        screen = s
        settle(name, ready)
        save(compose.onNodeWithTag(ROOT), File(out, "phoneScreenshots/$name.png"), 1080, 1920)
    }

    /**
     * Captures [node] and writes it as a 24-bit PNG of exactly [w]×[h] pixels (scaled if the
     * window is a few pixels off, letterboxed with the base color if the shape differs).
     */
    private fun save(node: SemanticsNodeInteraction, file: File, w: Int, h: Int) {
        frames(2)
        val captured = runCatching { node.captureToImage().asAndroidBitmap() }.getOrElse { drawRoot() }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(0xFF1B2F33.toInt())
            val scale = minOf(w.toFloat() / captured.width, h.toFloat() / captured.height)
            val sw = (captured.width * scale).toInt()
            val sh = (captured.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(captured, sw, sh, true)
            drawBitmap(scaled, ((w - sw) / 2).toFloat(), 0f, null)
        }
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        rgb.setRGB(0, 0, w, h, pixels, 0, w)
        file.parentFile?.mkdirs()
        check(ImageIO.write(rgb, "png", file)) { "No PNG writer" }
        println("Store image: ${file.path} ($w×$h, captured ${captured.width}×${captured.height})")
    }

    /** Fallback capture: draws the activity's view tree into a bitmap. */
    private fun drawRoot(): Bitmap {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private companion object {
        const val ROOT = "store-root"
        const val ICON = "store-icon"
        const val FEATURE = "store-feature"
    }
}
