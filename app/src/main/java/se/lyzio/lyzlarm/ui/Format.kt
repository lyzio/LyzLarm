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

    /** "0:47" countdown. */
    fun countdown(millis: Long): String {
        val s = (millis.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
