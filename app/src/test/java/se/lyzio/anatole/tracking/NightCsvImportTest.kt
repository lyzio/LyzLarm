package se.lyzio.anatole.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.lyzio.anatole.data.EpochRow
import se.lyzio.anatole.data.EventRow
import se.lyzio.anatole.data.EventType
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.data.RawSample
import java.io.File

/** Night import (v0.4): reads back what v0.2 and v0.3 exported. */
class NightCsvImportTest {
    // 2026-11-19 23:00:00 in Stockholm (UTC+1).
    private val start = 1_795_125_600_000L

    private fun night(epochCount: Int) = Night(
        id = 7,
        alarmId = 1,
        startTime = start,
        endTime = start + epochCount * 30_000L,
        wakeTime = start + epochCount * 30_000L,
        wakeReason = "LIGHT_SLEEP",
        batteryStart = 80,
        batteryEnd = 71,
        engineVersion = 3,
        timeZone = "Europe/Stockholm",
    )

    /** The owner's test night A (movement per 30 s, no dates), as the app would export it. */
    private fun testNightA(): List<EpochRow> {
        val file = listOf("../sleep-engine/src/test/resources/nights/night-a.csv", "sleep-engine/src/test/resources/nights/night-a.csv")
            .map(::File).first { it.exists() }
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("second") }
            .map { line ->
                val (second, movement) = line.split(',')
                EpochRow(nightId = 7, time = start + second.toLong() * 1000L, movement = movement.toFloat())
            }
    }

    private fun export(n: Night, epochs: List<EpochRow>, events: List<EventRow>, raw: List<RawSample> = emptyList()) =
        StringBuilder().also { NightCsv.write(it, n, epochs, events, raw, appVersion = "0.3.0") }.toString()

    @Test fun roundTripOfTestNight() {
        val epochs = testNightA()
        assertTrue("test night has data", epochs.size > 600)
        val n = night(epochs.size)
        val events = listOf(
            EventRow(nightId = 7, time = start, type = EventType.TRACKING_STARTED, detail = "80"),
            EventRow(nightId = 7, time = start + 3_600_000L, type = EventType.TRACKING_INTERRUPTED, detail = "say \"hi\", ok"),
            EventRow(nightId = 7, time = n.endTime!!, type = EventType.DISMISSED),
        )
        val raw = listOf(RawSample(nightId = 7, time = start + 100, x = 0.1f, y = 0.2f, z = 9.8f))

        val parsed = NightCsv.parse(export(n, epochs, events, raw))

        assertEquals(start, parsed.startTime)
        assertEquals(n.endTime, parsed.endTime)
        assertEquals(n.wakeTime, parsed.wakeTime)
        assertEquals("LIGHT_SLEEP", parsed.wakeReason)
        assertEquals(80, parsed.batteryStart)
        assertEquals(71, parsed.batteryEnd)
        assertEquals(3, parsed.engineVersion)
        assertEquals("Europe/Stockholm", parsed.timeZone)
        assertEquals(epochs.size, parsed.epochs.size)
        epochs.zip(parsed.epochs).forEach { (a, b) ->
            assertEquals(a.time, b.time)
            assertEquals(a.movement, b.movement, 0.0001f)
        }
        assertEquals(listOf("tracking_started", "tracking_interrupted", "dismissed"), parsed.events.map { it.type })
        assertEquals("say \"hi\", ok", parsed.events[1].detail)
        assertNull(parsed.events[2].detail)
    }

    @Test fun readsNoiseFromV03() {
        val epochs = listOf(EpochRow(7, start, 12.5f), EpochRow(7, start + 30_000, 0f, noise = 0.0031f))
        val parsed = NightCsv.parse(export(night(2), epochs, emptyList()))
        assertNull(parsed.epochs[0].noise)
        assertEquals(0.0031f, parsed.epochs[1].noise!!, 0.000001f)
    }

    @Test fun readsV02FileWithoutNoiseColumn() {
        val v02 = """
            # LyzLarm night export
            # app_version,0.2.0
            # night_id,3
            # start,2026-11-19T23:00:00+01:00
            # end,2026-11-20T06:30:00+01:00
            # wake_time,2026-11-20T06:30:00+01:00
            # wake_reason,fixed
            # battery_start,90
            # battery_end,
            # engine_version,1
            # time_zone,Europe/Stockholm
            # raw_samples,1
            record,time_ms,time_local,movement,type,detail,x,y,z
            epoch,$start,2026-11-19T23:00:00+01:00,4.25,,,,,
            epoch,${start + 30_000},2026-11-19T23:00:30+01:00,0,,,,,
            event,$start,2026-11-19T23:00:00+01:00,,tracking_started,90,,,
            raw,${start + 100},2026-11-19T23:00:00.1+01:00,,,,0.1,0.2,9.8
        """.trimIndent().replace("\n", "\r\n")

        val parsed = NightCsv.parse(v02)

        assertEquals(start, parsed.startTime)
        assertEquals(start + 7 * 3_600_000L + 30 * 60_000L, parsed.endTime)
        assertEquals("FIXED", parsed.wakeReason)
        assertEquals(90, parsed.batteryStart)
        assertNull(parsed.batteryEnd)
        assertEquals(1, parsed.engineVersion)
        assertEquals(listOf(4.25f, 0f), parsed.epochs.map { it.movement })
        assertTrue(parsed.epochs.all { it.noise == null })
        assertEquals(1, parsed.events.size)
    }

    @Test fun missingTimesFallBackToEpochs() {
        val text = """
            # Anatole night export
            record,time_ms,time_local,movement,type,detail,x,y,z,noise
            epoch,${start + 30_000},x,1,,,,,,
            epoch,$start,x,2,,,,,,
        """.trimIndent()
        val parsed = NightCsv.parse(text)
        assertEquals(start, parsed.startTime)
        assertEquals(start + 60_000, parsed.endTime)
        assertEquals(listOf(start, start + 30_000), parsed.epochs.map { it.time })
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsOtherCsv() {
        NightCsv.parse("second,movement\n0,1.0\n30,2.0\n")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNightWithoutEpochs() {
        NightCsv.parse(export(night(0), emptyList(), listOf(EventRow(nightId = 7, time = start, type = "rang"))))
    }

    @Test fun splitHandlesQuotes() {
        assertEquals(listOf("a", "b, c", "say \"hi\"", ""), NightCsv.split("a,\"b, c\",\"say \"\"hi\"\"\","))
    }
}
