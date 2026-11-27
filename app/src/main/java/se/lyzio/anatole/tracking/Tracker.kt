package se.lyzio.anatole.tracking

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import se.lyzio.anatole.alarm.RingingState
import se.lyzio.anatole.app
import se.lyzio.anatole.data.Alarm
import se.lyzio.anatole.data.EventRow
import se.lyzio.anatole.data.EventType
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.engine.NightAnalysis
import se.lyzio.anatole.engine.Sensitivity
import se.lyzio.anatole.engine.SleepEngine
import se.lyzio.anatole.engine.WakeRules
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

    /**
     * v0.4 fix: the open night follows the next alarm until one has rung (see [AlarmFollow]).
     * Called whenever the alarms change; also tells a running tracking service to re-plan
     * its wake window and notification. Returns the alarm id the night tracks now.
     */
    suspend fun followNextAlarm(context: Context, now: Long = System.currentTimeMillis()): Long? {
        val app = context.app
        val dao = app.database.nightDao()
        val night = dao.openNight() ?: return null
        val scheduler = app.scheduler
        val candidates = app.database.alarmDao().getAll()
            .filter { it.enabled || scheduler.snoozedUntil(it.id) != null }
            .map { AlarmFollow.Candidate(it.id, scheduler.snoozedUntil(it.id) ?: scheduler.nextRing(it)) }
        val target = AlarmFollow.target(
            current = night.alarmId,
            // Ringing now counts too: the ring advances the alarm a moment before `rang` is saved.
            currentRang = RingingState.current.value != null || dao.firstEvent(night.id, EventType.RANG) != null,
            currentSnoozed = night.alarmId?.let { scheduler.snoozedUntil(it) } != null,
            candidates = candidates,
            now = now,
            maxAheadMs = MAX_AHEAD_MS,
        )
        if (target != night.alarmId) {
            // An early ring already asked for the old alarm must not go off.
            night.alarmId?.let { scheduler.cancelEarly(it) }
            dao.updateNight(night.copy(alarmId = target))
            Log.i(TAG, "Night ${night.id} now follows alarm $target (was ${night.alarmId})")
        }
        if (TrackingService.running.value) {
            runCatching {
                context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_RETARGET))
            }.onFailure { Log.w(TAG, "Cannot reach the tracking service", it) }
        }
        return target
    }

    /** When the alarm this night tracks towards rings, or null if it no longer will. */
    suspend fun alarmTimeFor(context: Context, night: Night): Long? {
        val id = night.alarmId ?: return null
        val alarm = context.app.database.alarmDao().get(id) ?: return null
        val scheduler = context.app.scheduler
        return scheduler.snoozedUntil(id) ?: if (alarm.enabled) scheduler.nextRing(alarm) else null
    }

    /**
     * Tonight's wake plan: window start and end (end = latest wake time) and the sensitivity
     * used. Null if the alarm will not ring or is snoozed.
     */
    data class WakePlan(
        val alarm: Alarm,
        val startMs: Long,
        val endMs: Long,
        val sensitivity: Sensitivity,
        /** "night 3 of 7" while learning, else null. */
        val learningNight: Int?,
    ) {
        val isSmart: Boolean get() = alarm.isSmart
    }

    suspend fun wakePlan(context: Context, night: Night): WakePlan? {
        val id = night.alarmId ?: return null
        val alarm = context.app.database.alarmDao().get(id) ?: return null
        if (!alarm.enabled) return null
        return wakePlan(context, alarm)
    }

    suspend fun wakePlan(context: Context, alarm: Alarm, end: Long = context.app.scheduler.nextRing(alarm)): WakePlan {
        val finished = context.app.database.nightDao().finishedCount()
        val chosen = context.app.settings.alarmDefaults().sensitivity
        return WakePlan(
            alarm = alarm,
            startMs = WakeRules.windowStart(end, alarm.windowMinutes),
            endMs = end,
            sensitivity = WakeRules.effective(chosen, finished),
            learningNight = WakeRules.learningNight(finished),
        )
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

    /**
     * Stops tracking by hand ("Hold to stop", notification action). Before the alarm has
     * rung this always turns off that morning's alarm (#71, decided 25 Nov 2026): a
     * repeating alarm moves on to its next day, a one-time alarm is turned off.
     */
    suspend fun stopManually(context: Context) {
        val app = context.app
        val dao = app.database.nightDao()
        val night = dao.openNight() ?: return
        var detail: String? = null
        val alarm = night.alarmId?.let { app.database.alarmDao().get(it) }
        if (alarm != null) {
            val scheduler = app.scheduler
            val rang = dao.firstEvent(night.id, EventType.RANG) != null
            scheduler.cancelSnooze(alarm.id)
            scheduler.cancelEarly(alarm.id)
            if (!rang && alarm.enabled) {
                val ringAt = scheduler.nextRing(alarm)
                if (ringAt - System.currentTimeMillis() <= MAX_AHEAD_MS) {
                    scheduler.skip(alarm, ringAt)
                    if (!alarm.isRepeating) app.database.alarmDao().update(alarm.copy(enabled = false))
                    detail = "alarm_off"
                    Log.i(TAG, "Stopped before the alarm: ring at $ringAt turned off")
                }
            }
        }
        stop(context, SleepEngine.WakeReason.MANUAL, detail)
    }

    /** [detail]: wake reason key, "snooze", "snooze;light_sleep" or "stay_awake". */
    suspend fun onAlarmRang(context: Context, detail: String?) = event(context, EventType.RANG, detail)

    suspend fun onSnoozed(context: Context, minutes: Int) = event(context, EventType.SNOOZED, "$minutes")

    /** The alarm was dismissed ("I'm up"): the night ends. [detail] is set when it timed out. */
    suspend fun onAlarmDismissed(context: Context, detail: String? = null) {
        event(context, EventType.DISMISSED, detail)
        stop(context, null, detail)
    }

    /** Dismissed with the stay-awake check on: tracking runs 5 more minutes first. */
    suspend fun onAlarmDismissedStayAwake(context: Context, alarmId: Long) {
        event(context, EventType.DISMISSED, null)
        context.startService(
            Intent(context, TrackingService::class.java)
                .setAction(TrackingService.ACTION_STAY_AWAKE)
                .putExtra(TrackingService.EXTRA_ALARM_ID, alarmId),
        )
    }

    suspend fun event(context: Context, type: String, detail: String?, at: Long = System.currentTimeMillis()) {
        val dao = context.app.database.nightDao()
        val night = dao.openNight() ?: return
        dao.insertEvent(EventRow(nightId = night.id, time = at, type = type, detail = detail))
    }

    /** [reason] null = from the night's first ring (see [close]). */
    private suspend fun stop(context: Context, reason: SleepEngine.WakeReason?, detail: String?) {
        val night = context.app.database.nightDao().openNight() ?: return
        if (TrackingService.running.value) {
            // The service flushes the last sensor data first, then closes the night.
            // It is already in the foreground, so a plain start is allowed.
            context.startService(
                Intent(context, TrackingService::class.java)
                    .setAction(TrackingService.ACTION_STOP)
                    .putExtra(TrackingService.EXTRA_REASON, reason?.name)
                    .putExtra(TrackingService.EXTRA_DETAIL, detail),
            )
        } else {
            close(context, night, reason, System.currentTimeMillis(), detail)
        }
    }

    /**
     * Writes the end of the night. Called by the service, or directly if it is not running.
     * [reason] null = why the alarm first rang tonight (from the first `rang` event), FIXED
     * if that is unknown. [wakeAt] is when the user got up (the last dismiss); default [at].
     */
    suspend fun close(
        context: Context,
        night: Night,
        reason: SleepEngine.WakeReason?,
        at: Long,
        detail: String?,
        wakeAt: Long = at,
    ) {
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
        val firstRing = dao.firstEvent(current.id, EventType.RANG)
        val why = reason ?: firstRing?.detail?.split(';')?.firstNotNullOfOrNull { SleepEngine.WakeReason.parse(it) }
            ?: SleepEngine.WakeReason.FIXED
        val points = dao.epochs(current.id).map { it.toPoint() }
        val onset = NightAnalysis.onsetIndex(points)?.let { points[it].timeMs }
        dao.updateNight(
            current.copy(endTime = at, wakeTime = wakeAt, wakeReason = why.name, batteryEnd = battery, onsetEstimate = onset),
        )
        val what = why.key
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
        Log.i(TAG, "Night ${night.id} closed ($why)")
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
