package se.lyzio.lyzlarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmTest {
    private val stockholm = ZoneId.of("Europe/Stockholm")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = stockholm) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    private val weekdays = 0b0011111
    private val weekend = 0b1100000

    @Test
    fun onceLaterToday() {
        // Fri 20 Nov 2026, 22:00 → 23:30 same day
        assertEquals(at(2026, 11, 20, 23, 30), NextAlarm.next(23, 30, 0, at(2026, 11, 20, 22, 0)))
    }

    @Test
    fun onceTomorrowWhenTimeHasPassed() {
        assertEquals(at(2026, 11, 21, 7, 0), NextAlarm.next(7, 0, 0, at(2026, 11, 20, 22, 0)))
    }

    @Test
    fun exactlyNowMeansTomorrow() {
        assertEquals(at(2026, 11, 21, 7, 0), NextAlarm.next(7, 0, 0, at(2026, 11, 20, 7, 0)))
    }

    @Test
    fun weekdayAlarmOnFridayEveningSkipsToMonday() {
        // Fri 20 Nov 2026 → Mon 23 Nov
        assertEquals(at(2026, 11, 23, 6, 30), NextAlarm.next(6, 30, weekdays, at(2026, 11, 20, 22, 0)))
    }

    @Test
    fun weekendAlarmOnSaturdayMorningBeforeTime() {
        assertEquals(at(2026, 11, 21, 8, 0), NextAlarm.next(8, 0, weekend, at(2026, 11, 21, 7, 0)))
    }

    @Test
    fun singleDayRepeatOneWeekLater() {
        val mondayOnly = NextAlarm.bit(DayOfWeek.MONDAY)
        // Mon 23 Nov 07:01 → Mon 30 Nov 07:00
        assertEquals(at(2026, 11, 30, 7, 0), NextAlarm.next(7, 0, mondayOnly, at(2026, 11, 23, 7, 1)))
    }

    @Test
    fun springForwardGapRingsAfterGap() {
        // 29 Mar 2026: Stockholm clocks jump 02:00 → 03:00. 02:30 doesn't exist.
        val result = NextAlarm.next(2, 30, 0, at(2026, 3, 28, 23, 0))
        assertEquals(at(2026, 3, 29, 3, 30), result)
    }

    @Test
    fun fallBackKeepsLocalTime() {
        // 25 Oct 2026: clocks go 03:00 → 02:00. 07:00 local is still 07:00.
        val result = NextAlarm.next(7, 0, 0, at(2026, 10, 24, 23, 0))
        assertEquals(7, result.hour)
        assertEquals(25, result.dayOfMonth)
    }

    @Test
    fun timeZoneChangeUsesNewZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val result = NextAlarm.next(7, 0, 0, at(2026, 11, 20, 22, 0, tokyo))
        assertEquals(tokyo, result.zone)
        assertEquals(at(2026, 11, 21, 7, 0, tokyo), result)
    }

    @Test
    fun describeRepeat() {
        assertEquals("Once", NextAlarm.describeRepeat(0))
        assertEquals("Mon – Fri", NextAlarm.describeRepeat(weekdays))
        assertEquals("Sat, Sun", NextAlarm.describeRepeat(weekend))
        assertEquals("Every day", NextAlarm.describeRepeat(0b1111111))
        assertEquals("Mon, Wed", NextAlarm.describeRepeat(0b0000101))
    }

    @Test
    fun label() {
        assertEquals("Weekday alarm", NextAlarm.label(weekdays))
        assertEquals("One-time alarm", NextAlarm.label(0))
    }
}
