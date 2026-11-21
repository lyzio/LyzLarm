package se.lyzio.lyzlarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import se.lyzio.lyzlarm.MainActivity
import se.lyzio.lyzlarm.data.Alarm
import se.lyzio.lyzlarm.data.AlarmDao
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Schedules alarms with AlarmManager.setAlarmClock(). This is the most reliable
 * alarm type on Android: it is exact, wakes the phone from Doze, is exempt from
 * battery restrictions, and shows the alarm icon in the status bar.
 *
 * Android forgets all alarms on restart, so [rescheduleAll] is called after boot,
 * app updates and time / time-zone changes (see RescheduleReceiver).
 */
class AlarmScheduler(private val context: Context, private val dao: AlarmDao) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("scheduler", Context.MODE_PRIVATE)

    enum class Kind { ALARM, SNOOZE, TEST }

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

    fun nextRing(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long =
        NextAlarm.next(alarm.hour, alarm.minute, alarm.repeatDays, now).toInstant().toEpochMilli()

    fun cancel(alarm: Alarm) {
        alarmManager.cancel(pendingIntent(alarm.id, Kind.ALARM))
        cancelSnooze(alarm.id)
    }

    fun snooze(alarmId: Long, minutes: Int): Long {
        val at = System.currentTimeMillis() + minutes * 60_000L
        prefs.edit().putLong(snoozeKey(alarmId), at).apply()
        setAlarmClock(at, alarmId, Kind.SNOOZE)
        return at
    }

    fun cancelSnooze(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Kind.SNOOZE))
        prefs.edit().remove(snoozeKey(alarmId)).apply()
    }

    fun snoozedUntil(alarmId: Long): Long? =
        prefs.getLong(snoozeKey(alarmId), 0L).takeIf { it > System.currentTimeMillis() }

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

    private fun setAlarmClock(atMillis: Long, alarmId: Long, kind: Kind) {
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(atMillis, show),
                pendingIntent(alarmId, kind),
            )
            Log.i(TAG, "Scheduled $kind for alarm $alarmId at ${Instant.ofEpochMilli(atMillis)}")
        } catch (e: SecurityException) {
            // Exact alarms not allowed. System check shows this as needing a fix.
            Log.e(TAG, "Cannot schedule exact alarm", e)
        }
    }

    private fun pendingIntent(alarmId: Long, kind: Kind): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("se.lyzio.lyzlarm.RING.$kind.$alarmId")
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
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
    }

    private fun snoozeKey(alarmId: Long) = "snooze_$alarmId"

    companion object {
        const val TEST_ALARM_ID = -1L
        private const val KEY_TEST_AT = "test_at"
        private const val TAG = "AlarmScheduler"
    }
}
