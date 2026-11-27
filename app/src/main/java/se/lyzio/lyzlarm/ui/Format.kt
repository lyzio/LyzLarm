package se.lyzio.lyzlarm.ui

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

object Format {
    fun time(context: Context, hour: Int, minute: Int): String =
        if (DateFormat.is24HourFormat(context)) {
            "%02d:%02d".format(hour, minute)
        } else {
            val h = if (hour % 12 == 0) 12 else hour % 12
            "%d:%02d %s".format(h, minute, if (hour < 12) "AM" else "PM")
        }

    fun time(context: Context, epochMillis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return time(context, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** "7 h 16 min", "45 min", "less than a minute". */
    fun duration(millis: Long): String {
        val totalMinutes = (millis + 59_999) / 60_000
        if (millis < 60_000) return "less than a minute"
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return buildList {
            if (days > 0) add("$days d")
            if (hours > 0) add("$hours h")
            if (minutes > 0 && days == 0L) add("$minutes min")
        }.joinToString(" ")
    }

    /** "7 h 12 min" but rounded down, for measured durations. */
    fun measured(millis: Long): String {
        val total = (millis.coerceAtLeast(0) / 60_000)
        val h = total / 60
        val m = total % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** "Thu 19 → Fri 20 Nov" for a night; same day: "Thu 19 Nov". */
    fun nightRange(startMillis: Long, endMillis: Long): String {
        val z = java.time.ZoneId.systemDefault()
        val a = java.time.Instant.ofEpochMilli(startMillis).atZone(z)
        val b = java.time.Instant.ofEpochMilli(endMillis).atZone(z)
        val day = java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.getDefault())
        val month = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.getDefault())
        return if (a.toLocalDate() == b.toLocalDate()) {
            "${day.format(a)} ${month.format(a)}"
        } else if (a.month == b.month) {
            "${day.format(a)} → ${day.format(b)} ${month.format(b)}"
        } else {
            "${day.format(a)} ${month.format(a)} → ${day.format(b)} ${month.format(b)}"
        }
    }

    /** Minutes after midnight as a clock time. */
    fun minuteOfDay(context: Context, minute: Int): String = time(context, minute / 60, minute % 60)

    /** "0:47" countdown. */
    fun countdown(millis: Long): String {
        val s = (millis.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
