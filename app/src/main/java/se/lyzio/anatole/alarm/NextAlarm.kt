package se.lyzio.anatole.alarm

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure time calculation for when an alarm rings next. No Android types, so it is
 * unit-tested on the JVM (see NextAlarmTest).
 */
object NextAlarm {

    /** Bit for a weekday in the repeat mask: Monday = bit 0 … Sunday = bit 6. */
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun repeatsOn(repeatDays: Int, day: DayOfWeek): Boolean = repeatDays and bit(day) != 0

    /**
     * The next moment strictly after [now] when an alarm set to [hour]:[minute]
     * with [repeatDays] should ring, in [now]'s time zone.
     *
     * A one-time alarm (repeatDays = 0) rings at the next occurrence of the time.
     * If the time falls in a daylight-saving gap (e.g. 02:30 on the spring-forward
     * night), it is shifted later by the length of the gap (02:30 → 03:30).
     */
    fun next(hour: Int, minute: Int, repeatDays: Int, now: ZonedDateTime): ZonedDateTime {
        require(hour in 0..23 && minute in 0..59) { "Invalid time $hour:$minute" }
        val time = LocalTime.of(hour, minute)
        val startDate = now.toLocalDate()
        // Today plus the next 7 days covers every weekday at least once.
        for (offset in 0L..7L) {
            val date = startDate.plusDays(offset)
            if (repeatDays != 0 && !repeatsOn(repeatDays, date.dayOfWeek)) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No next alarm found for mask $repeatDays") // unreachable for a valid mask
    }

    /** Human-readable repeat summary, e.g. "Mon – Fri", "Sat, Sun", "Once". */
    fun describeRepeat(repeatDays: Int): String {
        val weekdays = 0b0011111
        val weekend = 0b1100000
        return when (repeatDays and 0b1111111) {
            0 -> "Once"
            0b1111111 -> "Every day"
            weekdays -> "Mon – Fri"
            weekend -> "Sat, Sun"
            else -> DayOfWeek.entries
                .filter { repeatsOn(repeatDays, it) }
                .joinToString(", ") { SHORT_NAMES[it.value - 1] }
        }
    }

    /** Label used on the ringing screen, derived from the repeat days. */
    fun label(repeatDays: Int): String = when (repeatDays and 0b1111111) {
        0 -> "One-time alarm"
        0b0011111 -> "Weekday alarm"
        0b1100000 -> "Weekend alarm"
        0b1111111 -> "Daily alarm"
        else -> "Alarm"
    }

    private val SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
