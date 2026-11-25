package se.lyzio.lyzlarm.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.data.Night
import se.lyzio.lyzlarm.data.RawSample

class NightCsvTest {
    // 2026-11-19 23:00:00 in Stockholm (UTC+1).
    private val start = 1_795_125_600_000L
    private val night = Night(
        id = 7,
        alarmId = 1,
        startTime = start,
        endTime = start + 8 * 3_600_000L,
        wakeTime = start + 8 * 3_600_000L,
        wakeReason = "FIXED",
        batteryStart = 80,
        batteryEnd = 71,
        engineVersion = 2,
        timeZone = "Europe/Stockholm",
    )

    private fun csv(raw: List<RawSample> = emptyList()): List<String> {
        val sb = StringBuilder()
        NightCsv.write(
            sb,
            night,
            epochs = listOf(EpochRow(7, start + 30_000, 0f, noise = 0.0031f), EpochRow(7, start, 12.5f)),
            events = listOf(
                EventRow(nightId = 7, time = start, type = EventType.TRACKING_STARTED, detail = "80"),
                EventRow(nightId = 7, time = start + 60_000, type = EventType.TRACKING_INTERRUPTED, detail = "say \"hi\", ok"),
            ),
            raw = raw,
            appVersion = "0.2.0",
        )
        return sb.toString().trimEnd('\n').split('\n')
    }

    @Test fun headerAndMetadata() {
        val lines = csv()
        assertEquals("# Anatole night export", lines[0])
        assertTrue(lines.contains("# start,2026-11-19T23:00:00+01:00"))
        assertTrue(lines.contains("# wake_reason,fixed"))
        assertTrue(lines.contains("# battery_end,71"))
        assertTrue(lines.contains("# raw_samples,0"))
        assertEquals(NightCsv.HEADER, lines.first { !it.startsWith("#") })
    }

    @Test fun rowsHaveTenColumnsAndAreSorted() {
        val rows = csv().dropWhile { it.startsWith("#") }.drop(1)
        assertEquals(4, rows.size)
        assertEquals("epoch,$start,2026-11-19T23:00:00+01:00,12.5,,,,,,", rows[0])
        assertEquals("epoch,${start + 30_000},2026-11-19T23:00:30+01:00,0,,,,,,0.00310", rows[1])
        assertEquals("event,$start,2026-11-19T23:00:00+01:00,,tracking_started,80,,,,", rows[2])
        // Commas and quotes in a detail are quoted.
        assertEquals("event,${start + 60_000},2026-11-19T23:01:00+01:00,,tracking_interrupted,\"say \"\"hi\"\", ok\",,,,", rows[3])
        rows.take(3).forEach { assertEquals(10, it.split(',').size) }
    }

    @Test fun rawRowsWhenKept() {
        val rows = csv(listOf(RawSample(nightId = 7, time = start + 100, x = 0.0125f, y = -1f, z = 9.81f)))
        assertTrue(rows.contains("# raw_samples,1"))
        assertEquals("raw,${start + 100},2026-11-19T23:00:00.1+01:00,,,,0.0125,-1,9.81,", rows.last())
    }

    @Test fun fileNameUsesLocalStart() {
        assertEquals("anatole-night-2026-11-19_2300.csv", NightCsv.fileName(night))
    }
}
