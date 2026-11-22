package se.lyzio.lyzlarm.tracking

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.Alarm
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.data.Night
import se.lyzio.lyzlarm.engine.SleepEngine
import java.time.ZoneId

/**
 * Starts, stops and records sleep tracking. The night itself lives in the database
 * (an open night has no end time), so tracking survives the app being killed or the
 * phone restarting: [resumeIfNeeded] picks it up again.
 */
object Tracker {
    private const val TAG = "Tracker"

    /** "Start sleep" needs an alarm ringing within this time. */
    const val MAX_AHEAD_MS = 16 * 3_600_000L

    /** An open night older than this is closed instead of resumed (safety net). */
    const val MAX_NIGHT_MS = 18 * 3_600_000L

    /** Tracking stopped sooner than this is not kept as a night (accidental or test start). */
    const val MIN_NIGHT_MS = 15 * 60_000L

    /** Raw samples are deleted after this long. */
    const val RAW_KEEP_MS = 3 * 24 * 3_600_000L

    fun battery(context: Context): Int =
        context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)

    fun isCharging(context: Context): Boolean =
        context.getSystemService(BatteryManager::class.java).isCharging

    /** The next alarm to ring (snoozes included), with its ring time. */
    suspend fun nextAlarm(context: Context, now: Long = System.currentTimeMillis()): Pair<Alarm, Long>? {
        val scheduler = context.app.scheduler
        return context.app.database.alarmDao().getAll()
            .filter { it.enabled || scheduler.snoozedUntil(it.id) != null }
            .map { it to (scheduler.snoozedUntil(it.id) ?: scheduler.nextRing(it)) }
            .filter { it.second > now }
            .minByOrNull { it.second }
    }

    /** When the alarm this night tracks towards rings, or null if it no longer will. */
    suspend fun alarmTimeFor(context: Context, night: Night): Long? {
        val id = night.alarmId ?: return null
        val alarm = context.app.database.alarmDao().get(id) ?: return null
        val scheduler = context.app.scheduler
        return scheduler.snoozedUntil(id) ?: if (alarm.enabled) scheduler.nextRing(alarm) else null
    }

    /** Starts tracking now. Call while the app is in the foreground (button or notification tap). */
    suspend fun start(context: Context): Long? {
        val app = context.app
        val dao = app.database.nightDao()
        dao.openNight()?.let {
            startService(context, TrackingService.ACTION_RESUME)
            return it.id
        }
        val (alarm, _) = nextAlarm(context)?.takeIf { it.second - System.currentTimeMillis() <= MAX_AHEAD_MS }
            ?: return null
        val now = System.currentTimeMillis()
        val battery = battery(context)
        val id = dao.insertNight(
            Night(
                alarmId = alarm.id,
                startTime = now,
                batteryStart = battery,
                engineVersion = SleepEngine.VERSION,
                timeZone = ZoneId.systemDefault().id,
            ),
        )
        dao.insertEvent(EventRow(nightId = id, time = now, type = EventType.TRACKING_STARTED, detail = "$battery"))
        dao.deleteRawBefore(now - RAW_KEEP_MS)
        startService(context, TrackingService.ACTION_START)
        Planner.cancelAll(context)
        return id
    }

    /** Stops tracking by hand ("Hold to stop", notification action). */
    suspend fun stopManually(context: Context) = stop(context, SleepEngine.WakeReason.MANUAL, null)

    suspend fun onAlarmRang(context: Context) = event(context, EventType.RANG, null)

    suspend fun onSnoozed(context: Context, minutes: Int) = event(context, EventType.SNOOZED, "$minutes")

    /** The alarm was dismissed ("I'm up"): the night ends. [detail] is set when it timed out. */
    suspend fun onAlarmDismissed(context: Context, detail: String? = null) {
        event(context, EventType.DISMISSED, detail)
        stop(context, SleepEngine.WakeReason.FIXED, detail)
    }

    private suspend fun event(context: Context, type: String, detail: String?) {
        val dao = context.app.database.nightDao()
        val night = dao.openNight() ?: return
        dao.insertEvent(EventRow(nightId = night.id, time = System.currentTimeMillis(), type = type, detail = detail))
    }

    private suspend fun stop(context: Context, reason: SleepEngine.WakeReason, detail: String?) {
        val night = context.app.database.nightDao().openNight() ?: return
        if (TrackingService.running.value) {
            // The service flushes the last sensor data first, then closes the night.
            // It is already in the foreground, so a plain start is allowed.
            context.startService(
                Intent(context, TrackingService::class.java)
                    .setAction(TrackingService.ACTION_STOP)
                    .putExtra(TrackingService.EXTRA_REASON, reason.name)
                    .putExtra(TrackingService.EXTRA_DETAIL, detail),
            )
        } else {
            close(context, night, reason, System.currentTimeMillis(), detail)
        }
    }

    /** Writes the end of the night. Called by the service, or directly if it is not running. */
    suspend fun close(context: Context, night: Night, reason: SleepEngine.WakeReason, at: Long, detail: String?) {
        val dao = context.app.database.nightDao()
        val current = dao.night(night.id) ?: return
        if (!current.isOpen) return
        if (at - current.startTime < MIN_NIGHT_MS) {
            dao.deleteNight(current.id)
            Planner.refresh(context)
            Log.i(TAG, "Night ${night.id} discarded: shorter than ${MIN_NIGHT_MS / 60_000} min")
            return
        }
        val battery = battery(context)
        dao.updateNight(
            current.copy(endTime = at, wakeTime = at, wakeReason = reason.name, batteryEnd = battery),
        )
        val what = if (reason == SleepEngine.WakeReason.MANUAL) "manual" else "fixed"
        dao.insertEvent(
            EventRow(
                nightId = night.id,
                time = at,
                type = EventType.TRACKING_STOPPED,
                detail = listOfNotNull(what, "$battery", detail).joinToString(";"),
            ),
        )
        dao.deleteRawBefore(at - RAW_KEEP_MS)
        Planner.refresh(context)
        Log.i(TAG, "Night ${night.id} closed ($reason)")
    }

    /**
     * After a restart, app update or app kill: continue an open night, or close it if
     * it is far too old. Returns true if tracking was (re)started.
     */
    suspend fun resumeIfNeeded(context: Context): Boolean {
        val dao = context.app.database.nightDao()
        val night = dao.openNight() ?: return false
        val now = System.currentTimeMillis()
        if (now - night.startTime > MAX_NIGHT_MS) {
            val last = dao.lastEpochTime(night.id)?.plus(SleepEngine.EPOCH_MS) ?: night.startTime
            close(context, night, SleepEngine.WakeReason.MANUAL, last, "expired")
            return false
        }
        if (TrackingService.running.value) return true
        return runCatching {
            startService(context, TrackingService.ACTION_RESUME)
            true
        }.getOrElse {
            Log.w(TAG, "Cannot resume tracking from here", it)
            false
        }
    }

    private fun startService(context: Context, action: String) {
        ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).setAction(action))
    }
}
