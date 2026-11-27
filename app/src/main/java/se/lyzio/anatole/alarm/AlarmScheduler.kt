package se.lyzio.anatole.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import se.lyzio.anatole.AppClock
import se.lyzio.anatole.MainActivity
import se.lyzio.anatole.data.Alarm
import se.lyzio.anatole.data.AlarmDao
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Schedules alarms with AlarmManager.setAlarmClock(). This is the most reliable
 * alarm type on Android: it is exact, wakes the phone from Doze, is exempt from
 * battery restrictions, and shows the alarm icon in the status bar.
 *
 * Android forgets all alarms on restart, so [rescheduleAll] is called after boot,
 * app updates and time / time-zone changes (see RescheduleReceiver).
 *
 * Smart wake (v0.3): the [Kind.ALARM] alarm always stays at the latest wake time (the end
 * of the wake window). It is the fallback and is only moved on once the alarm has rung or
 * tracking was stopped early (#71). An early ring at light sleep is a separate
 * [Kind.SMART] alarm set a second ahead by the tracking service, so it goes through the
 * same reliable alarm-clock path as every other ring.
 */
class AlarmScheduler(private val context: Context, private val dao: AlarmDao) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("scheduler", Context.MODE_PRIVATE)

    enum class Kind {
        /** The alarm at its latest wake time (end of the window). */
        ALARM,
        /** End of a snooze. */
        SNOOZE,
        TEST,
        /** Early ring decided by the tracking service (light sleep, already awake). */
        SMART,
        /** Stay-awake check found the user back in bed. */
        STAY_AWAKE,
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-arms every enabled alarm, pending snoozes and a pending test alarm. */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (alarm in dao.getAll()) {
            if (alarm.enabled) schedule(alarm) else cancel(alarm)
            val snoozeAt = prefs.getLong(snoozeKey(alarm.id), 0L)
            if (snoozeAt > now) setAlarmClock(snoozeAt, alarm.id, Kind.SNOOZE)
            else if (snoozeAt != 0L) prefs.edit().remove(snoozeKey(alarm.id)).apply()
        }
        val testAt = prefs.getLong(KEY_TEST_AT, 0L)
        if (testAt > now) setAlarmClock(testAt, TEST_ALARM_ID, Kind.TEST)
    }

    /** Schedules the next ring of [alarm]; returns its time in epoch millis, or null. */
    fun schedule(alarm: Alarm): Long? {
        if (!alarm.enabled) {
            cancel(alarm)
            return null
        }
        val next = nextRing(alarm)
        setAlarmClock(next, alarm.id, Kind.ALARM)
        return next
    }

    /** Next latest-wake time of [alarm], after any ring skipped with [skip]. */
    fun nextRing(alarm: Alarm, now: ZonedDateTime = AppClock.zoned()): Long {
        val next = NextAlarm.next(alarm.hour, alarm.minute, alarm.repeatDays, now).toInstant().toEpochMilli()
        val skipped = prefs.getLong(skipKey(alarm.id), 0L)
        if (skipped < next) return next
        val after = Instant.ofEpochMilli(skipped).atZone(now.zone)
        return NextAlarm.next(alarm.hour, alarm.minute, alarm.repeatDays, after).toInstant().toEpochMilli()
    }

    /**
     * Turns off the ring at [ringAt] only: a repeating alarm moves on to its next day,
     * a one-time alarm is left for the caller to disable. Survives restarts (the skip is
     * remembered until that time has passed). Used when tracking stops early (#71) and
     * after an early smart ring.
     */
    fun skip(alarm: Alarm, ringAt: Long) {
        prefs.edit().putLong(skipKey(alarm.id), ringAt).apply()
        alarmManager.cancel(pendingIntent(alarm.id, Kind.ALARM))
        if (alarm.enabled && alarm.isRepeating) schedule(alarm)
    }

    fun cancel(alarm: Alarm) {
        alarmManager.cancel(pendingIntent(alarm.id, Kind.ALARM))
        cancelSnooze(alarm.id)
        cancelEarly(alarm.id)
    }

    fun snooze(alarmId: Long, minutes: Int): Long {
        val now = System.currentTimeMillis()
        val at = now + minutes * 60_000L
        prefs.edit().putLong(snoozeKey(alarmId), at).putLong(snoozeFromKey(alarmId), now).apply()
        setAlarmClock(at, alarmId, Kind.SNOOZE)
        return at
    }

    fun cancelSnooze(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Kind.SNOOZE))
        prefs.edit().remove(snoozeKey(alarmId)).remove(snoozeFromKey(alarmId)).apply()
    }

    fun snoozedUntil(alarmId: Long): Long? =
        prefs.getLong(snoozeKey(alarmId), 0L).takeIf { it > System.currentTimeMillis() }

    /** When the current snooze of [alarmId] started, or null. */
    fun snoozedAt(alarmId: Long): Long? =
        prefs.getLong(snoozeFromKey(alarmId), 0L).takeIf { it > 0L && snoozedUntil(alarmId) != null }

    /**
     * Rings [alarmId] now ([Kind.SMART] or [Kind.STAY_AWAKE]) via an alarm clock a second
     * ahead. [reason] is a SleepEngine.WakeReason name shown on the ringing screen.
     */
    fun ringNow(alarmId: Long, kind: Kind, reason: String?) {
        setAlarmClock(System.currentTimeMillis() + 1_000L, alarmId, kind, reason)
    }

    /** Cancels a pending early or stay-awake ring. */
    fun cancelEarly(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Kind.SMART))
        alarmManager.cancel(pendingIntent(alarmId, Kind.STAY_AWAKE))
    }

    /** Test alarm in [delayMs] (default one minute). Returns the ring time. */
    fun scheduleTest(delayMs: Long = 60_000L): Long {
        val at = System.currentTimeMillis() + delayMs
        prefs.edit().putLong(KEY_TEST_AT, at).apply()
        setAlarmClock(at, TEST_ALARM_ID, Kind.TEST)
        return at
    }

    fun cancelTest() {
        alarmManager.cancel(pendingIntent(TEST_ALARM_ID, Kind.TEST))
        prefs.edit().remove(KEY_TEST_AT).apply()
    }

    fun clearTestMarker() {
        prefs.edit().remove(KEY_TEST_AT).apply()
    }

    fun pendingTestAt(): Long? = prefs.getLong(KEY_TEST_AT, 0L).takeIf { it > 0L }

    private fun setAlarmClock(atMillis: Long, alarmId: Long, kind: Kind, reason: String? = null) {
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(atMillis, show),
                pendingIntent(alarmId, kind, reason),
            )
            Log.i(TAG, "Scheduled $kind for alarm $alarmId at ${Instant.ofEpochMilli(atMillis)}")
        } catch (e: SecurityException) {
            // Exact alarms not allowed. System check shows this as needing a fix.
            Log.e(TAG, "Cannot schedule exact alarm", e)
        }
    }

    private fun pendingIntent(alarmId: Long, kind: Kind, reason: String? = null): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("se.lyzio.anatole.RING.$kind.$alarmId")
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
        if (reason != null) intent.putExtra(AlarmReceiver.EXTRA_REASON, reason)
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(alarmId: Long, kind: Kind): Int = when (kind) {
        Kind.ALARM -> alarmId.toInt()
        Kind.SNOOZE -> alarmId.toInt() or 0x40000000
        Kind.TEST -> 0x7FFFFFF0
        Kind.SMART -> alarmId.toInt() or 0x20000000
        Kind.STAY_AWAKE -> alarmId.toInt() or 0x10000000
    }

    private fun snoozeKey(alarmId: Long) = "snooze_$alarmId"
    private fun snoozeFromKey(alarmId: Long) = "snooze_from_$alarmId"
    private fun skipKey(alarmId: Long) = "skip_$alarmId"

    companion object {
        const val TEST_ALARM_ID = -1L
        private const val KEY_TEST_AT = "test_at"
        private const val TAG = "AlarmScheduler"
    }
}
