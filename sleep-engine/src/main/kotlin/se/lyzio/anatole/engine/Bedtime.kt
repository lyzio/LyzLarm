package se.lyzio.anatole.engine

/**
 * Learns the usual tracking start time from recent nights, for the bedtime reminder
 * and the low-battery warning. Times are minutes after local midnight (0..1439).
 */
object Bedtime {
    /** Nights needed before the learned time is used. */
    const val MIN_NIGHTS = 3

    /** Until learned: alarm time minus this many minutes (9 h). */
    const val FALLBACK_MINUTES_BEFORE_ALARM = 9 * 60

    /** The battery warning comes this long before the usual start. */
    const val WARNING_MINUTES_BEFORE_START = 60

    /**
     * Median start time of recent nights, or null if fewer than [MIN_NIGHTS].
     * Bedtimes wrap around midnight (23:30 and 00:30 are an hour apart, not 23 hours),
     * so the median is taken on a clock that starts at noon.
     */
    fun usualStartMinute(startMinutes: List<Int>): Int? {
        if (startMinutes.size < MIN_NIGHTS) return null
        val shifted = startMinutes.map { Math.floorMod(it - NOON, DAY) }.sorted()
        val n = shifted.size
        val median = if (n % 2 == 1) shifted[n / 2] else (shifted[n / 2 - 1] + shifted[n / 2]) / 2
        return Math.floorMod(median + NOON, DAY)
    }

    /** Minute of day of the reminder: learned start, or alarm minus 9 h. */
    fun reminderMinute(alarmMinute: Int, learnedStart: Int?): Int =
        learnedStart ?: Math.floorMod(alarmMinute - FALLBACK_MINUTES_BEFORE_ALARM, DAY)

    /**
     * How many minutes before the alarm a daily time [targetMinute] falls, looking back
     * at most 24 h. Same minute as the alarm counts as a full day before.
     */
    fun minutesBeforeAlarm(alarmMinute: Int, targetMinute: Int): Int {
        val d = Math.floorMod(alarmMinute - targetMinute, DAY)
        return if (d == 0) DAY else d
    }

    private const val NOON = 12 * 60
    private const val DAY = 24 * 60
}
