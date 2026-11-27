package se.lyzio.lyzlarm.tracking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.engine.Bedtime
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules tonight's bedtime reminder and low-battery warning, and the tracking watchdog.
 *
 * Reminder: at the usual tracking start (median of recent nights; until learned, alarm − 9 h),
 * or a fixed time. Warning: 1 h before the usual start, or a fixed time.
 * Called whenever alarms, settings or nights change, after boot, and when the app opens.
 */
object Planner {
    private const val TAG = "Planner"
    private const val LEARN_DAYS = 14L
    private const val WATCHDOG_MS = 15 * 60_000L

    data class Plan(val alarmAt: Long, val reminderAt: Long?, val warningAt: Long?, val learned: Boolean)

    /** Usual tracking start as minutes after midnight, or null while not yet learned. */
    suspend fun learnedStartMinute(context: Context, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val since = System.currentTimeMillis() - LEARN_DAYS * 24 * 3_600_000L
        val starts = context.app.database.nightDao().startTimesSince(since).map {
            val t = Instant.ofEpochMilli(it).atZone(zone)
            t.hour * 60 + t.minute
        }
        return Bedtime.usualStartMinute(starts)
    }

    /** Tonight's plan for the next alarm, or null if no alarm is set. */
    suspend fun plan(context: Context): Plan? {
        val (_, alarmAt) = Tracker.nextAlarm(context) ?: return null
        val settings = context.app.settings.tracking()
        val zone = ZoneId.systemDefault()
        val alarm = Instant.ofEpochMilli(alarmAt).atZone(zone)
        val alarmMinute = alarm.hour * 60 + alarm.minute
        val learned = learnedStartMinute(context, zone)
        val usual = Bedtime.reminderMinute(alarmMinute, learned)
        val now = System.currentTimeMillis()

        fun before(minute: Int) = alarmAt - Bedtime.minutesBeforeAlarm(alarmMinute, minute) * 60_000L

        val reminderAt = before(settings.reminderFixedMinute ?: usual)
            .takeIf { settings.reminderOn && it > now }
        val warningMinute = settings.warningFixedMinute
            ?: Math.floorMod(usual - Bedtime.WARNING_MINUTES_BEFORE_START, 24 * 60)
        val warningAt = before(warningMinute).takeIf { settings.warningOn && it > now }
        return Plan(alarmAt, reminderAt, warningAt, learned != null)
    }

    /** Re-plans the reminder and warning. Nothing is scheduled while a night is being tracked. */
    suspend fun refresh(context: Context) {
        cancelAll(context)
        if (context.app.database.nightDao().openNight() != null) return
        val plan = plan(context) ?: return
        plan.reminderAt?.let { set(context, it, BedtimeReceiver.ACTION_REMINDER) }
        plan.warningAt?.let { set(context, it, BedtimeReceiver.ACTION_BATTERY_WARNING) }
        Log.i(TAG, "Planned reminder=${plan.reminderAt?.let(Instant::ofEpochMilli)} warning=${plan.warningAt?.let(Instant::ofEpochMilli)}")
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, BedtimeReceiver.ACTION_REMINDER))
        am.cancel(pending(context, BedtimeReceiver.ACTION_BATTERY_WARNING))
    }

    /** Wakes the tracking service shortly before the wake window opens. */
    fun scheduleListen(context: Context, at: Long) = set(context, at, BedtimeReceiver.ACTION_LISTEN)

    fun cancelListen(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, BedtimeReceiver.ACTION_LISTEN))
    }

    /** Backup for the stay-awake check, in case no epoch arrives to trigger it. */
    fun scheduleStayAwakeCheck(context: Context, at: Long) = set(context, at, BedtimeReceiver.ACTION_STAY_AWAKE_CHECK)

    fun cancelStayAwakeCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, BedtimeReceiver.ACTION_STAY_AWAKE_CHECK))
    }

    fun scheduleWatchdog(context: Context) {
        set(context, System.currentTimeMillis() + WATCHDOG_MS, BedtimeReceiver.ACTION_WATCHDOG)
    }

    fun cancelWatchdog(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, BedtimeReceiver.ACTION_WATCHDOG))
    }

    private fun set(context: Context, at: Long, action: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, action))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, action))
        }
    }

    private fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, BedtimeReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
