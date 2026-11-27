package se.lyzio.lyzlarm.tracking

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import se.lyzio.lyzlarm.MainActivity
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.alarm.AlarmScheduler
import se.lyzio.lyzlarm.alarm.Notifications
import se.lyzio.lyzlarm.alarm.RingingState
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.data.RawSample
import se.lyzio.lyzlarm.engine.EmptyBedReference
import se.lyzio.lyzlarm.engine.Epoch
import se.lyzio.lyzlarm.engine.EpochAccumulator
import se.lyzio.lyzlarm.engine.Gaps
import se.lyzio.lyzlarm.engine.OutOfBed
import se.lyzio.lyzlarm.engine.OutOfBedTracker
import se.lyzio.lyzlarm.engine.Point
import se.lyzio.lyzlarm.engine.Sample
import se.lyzio.lyzlarm.engine.SleepEngine
import se.lyzio.lyzlarm.engine.WakeRules
import se.lyzio.lyzlarm.ui.Format

/**
 * Records movement all night. Foreground service of type `health`, so Android keeps it
 * running; START_STICKY plus a watchdog alarm and the boot receiver bring it back if it dies.
 *
 * Battery: the accelerometer collects samples in its hardware FIFO while the phone sleeps
 * ("batching") and delivers them every few minutes. We hold a wake lock only while
 * draining a batch into the database. Phones without a wake-up accelerometer with a FIFO
 * fall back to holding a wake lock all night (logged; tracking still works).
 *
 * The alarm itself (setAlarmClock) is independent: tracking never delays or blocks it.
 *
 * Smart wake (v0.3): from the start of the wake window, during a snooze and during the
 * stay-awake check, the service "listens closely": samples are delivered at once instead of
 * in batches (with a wake lock, at most about an hour), and after every epoch the sleep
 * engine decides whether to ring now. If it says so, the service sets an early alarm
 * ([AlarmScheduler.Kind.SMART]); the alarm at the window end stays as the fallback.
 */
class TrackingService : Service(), SensorEventListener2 {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val db = Dispatchers.IO.limitedParallelism(1)
    private val lifecycle = Mutex()

    private var sensorThread: HandlerThread? = null
    private var handler: Handler? = null
    private var sensorManager: SensorManager? = null
    private var registered = false

    private var drainLock: PowerManager.WakeLock? = null
    private var fallbackLock: PowerManager.WakeLock? = null

    // Sensor-thread state.
    private val accumulator = EpochAccumulator()
    private val pendingEpochs = ArrayList<Epoch>()
    private val pendingRaw = ArrayList<RawSample>()
    private var sampleCounter = 0
    private var drainScheduled = false
    private var flushDone: CompletableDeferred<Unit>? = null

    // Set at begin(); read on the sensor thread.
    @Volatile private var nightId = 0L
    @Volatile private var rawOn = false
    @Volatile private var coveredUntil = 0L

    // Smart wake state; used on the db dispatcher only.
    private val recent = ArrayDeque<Point>()
    private var outOfBed: OutOfBedTracker? = null
    private var reference: EmptyBedReference? = null
    private var ringRequestedAt = 0L

    @Volatile private var listeningClosely = false
    /** Set while the night is being closed: no more wake decisions. */
    @Volatile private var stopping = false
    private var sensor: Sensor? = null
    private var closeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground right away (required after startForegroundService).
        // A restart by Android from the background may not be allowed to; the watchdog
        // alarm then brings tracking back within 15 minutes.
        if (!goForeground(alarmText = null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                // null = from the night's first ring (alarm dismissed).
                val reason = SleepEngine.WakeReason.parse(intent.getStringExtra(EXTRA_REASON))
                val detail = intent.getStringExtra(EXTRA_DETAIL)
                scope.launch { stopTracking(reason, detail) }
            }
            ACTION_LISTEN -> scope.launch {
                begin()
                listenClosely()
            }
            ACTION_STAY_AWAKE -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                scope.launch {
                    begin()
                    startStayAwake(alarmId)
                }
            }
            ACTION_STAY_AWAKE_CHECK -> scope.launch {
                begin()
                kotlinx.coroutines.withContext(db) { checkStayAwake(force = true) }
            }
            // START, RESUME, or null (restarted by Android after being killed).
            else -> scope.launch { begin() }
        }
        return START_STICKY
    }

    private suspend fun begin() = lifecycle.withLock {
        if (registered) return@withLock
        stopping = false
        val app = app
        val dao = app.database.nightDao()
        val night = dao.openNight()
        if (night == null) {
            Log.i(TAG, "No open night; stopping")
            shutDown()
            return@withLock
        }
        if (System.currentTimeMillis() - night.startTime > Tracker.MAX_NIGHT_MS) {
            shutDown()
            Tracker.resumeIfNeeded(this) // closes the expired night
            return@withLock
        }
        nightId = night.id
        // A stay-awake check left over from an earlier night must not touch this one.
        if (stayAwakeFrom() in 1 until night.startTime) {
            prefs.edit().remove(KEY_SA_FROM).remove(KEY_SA_COUNT).remove(KEY_SA_ALARM).apply()
        }
        rawOn = app.settings.tracking().rawOn
        coveredUntil = dao.lastEpochTime(night.id)?.plus(SleepEngine.EPOCH_MS) ?: night.startTime
        _running.value = true

        // Smart wake state, rebuilt from the database (the service may have been restarted).
        kotlinx.coroutines.withContext(db) {
            val points = dao.epochs(night.id).map { it.toPoint() }
            reference = emptyBedReference(this@TrackingService)
            outOfBed = OutOfBedTracker(reference).also { t -> points.forEach { t.add(it) } }
            recent.clear()
            points.takeLast(RECENT_EPOCHS).forEach(recent::addLast)
        }

        goForeground(alarmText = alarmText(night))
        registerSensor()
        Planner.scheduleWatchdog(this)
        planWindow(night)
        Log.i(TAG, "Tracking night ${night.id}, raw=$rawOn, reference=${reference != null}")
    }

    /** Notification text: "06:30–07:00 · smart wake", or the fixed alarm time. */
    private suspend fun alarmText(night: se.lyzio.lyzlarm.data.Night): String? {
        val snoozed = night.alarmId?.let { app.scheduler.snoozedUntil(it) }
        if (snoozed != null) return "snoozed until ${Format.time(this, snoozed)}"
        val plan = Tracker.wakePlan(this, night) ?: return Tracker.alarmTimeFor(this, night)?.let { Format.time(this, it) }
        val end = Format.time(this, plan.endMs)
        return if (plan.isSmart) "${Format.time(this, plan.startMs)}–$end · smart wake" else end
    }

    /** Sets the window-open alarm, or listens closely right away if the window (or a snooze, or the stay-awake check) is on now. */
    private suspend fun planWindow(night: se.lyzio.lyzlarm.data.Night) {
        val now = System.currentTimeMillis()
        val alarmId = night.alarmId
        val snoozed = alarmId != null && app.scheduler.snoozedUntil(alarmId) != null
        val plan = Tracker.wakePlan(this, night)
        when {
            snoozed || stayAwakeFrom() > 0L -> listenClosely()
            plan == null -> Unit
            now >= plan.startMs - LISTEN_LEAD_MS && now < plan.endMs -> listenClosely()
            now < plan.startMs -> Planner.scheduleListen(this, plan.startMs - LISTEN_LEAD_MS)
        }
    }

    private fun registerSensor() {
        val sm = getSystemService(SensorManager::class.java)
        sensorManager = sm
        val wakeUp = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        val sensor = wakeUp ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.e(TAG, "No accelerometer")
            return
        }
        this.sensor = sensor
        val fifo = if (sensor.fifoReservedEventCount > 0) sensor.fifoReservedEventCount else sensor.fifoMaxEventCount
        val batching = wakeUp != null && fifo > 0
        val latencyUs = if (batching) {
            minOf(MAX_LATENCY_US, fifo.toLong() * SAMPLE_PERIOD_US * 8 / 10).toInt()
        } else 0
        if (!batching) {
            // No safe batching: keep the CPU awake so no samples are lost.
            fallbackLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Anatole:tracking")
                .apply { setReferenceCounted(false); acquire(Tracker.MAX_NIGHT_MS) }
        }
        val thread = HandlerThread("Anatole-sensor").also { it.start() }
        sensorThread = thread
        val h = Handler(thread.looper)
        handler = h
        registered = sm.registerListener(this, sensor, SAMPLE_PERIOD_US, latencyUs, h)
        Log.i(TAG, "Accelerometer ${sensor.name} wakeUp=${wakeUp != null} fifo=$fifo latency=${latencyUs / 1000} ms registered=$registered")
    }

    /**
     * From the wake window on: samples at once instead of in batches, so every epoch is
     * decided on within seconds. Holds a wake lock for at most [CLOSE_LOCK_MS].
     */
    private fun listenClosely() {
        if (listeningClosely || !registered) return
        val sm = sensorManager ?: return
        val s = sensor ?: return
        val h = handler ?: return
        listeningClosely = true
        closeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Anatole:listening")
            .apply { setReferenceCounted(false); acquire(CLOSE_LOCK_MS) }
        // Deliver what is still batched first, then switch to no batching.
        sm.flush(this)
        h.post {
            sm.unregisterListener(this)
            registered = sm.registerListener(this, s, SAMPLE_PERIOD_US, 0, h)
            Log.i(TAG, "Listening closely (registered=$registered)")
        }
        Planner.cancelListen(this)
        scope.launch(db) { Tracker.event(this@TrackingService, EventType.WINDOW_OPENED, windowDetail()) }
    }

    private suspend fun windowDetail(): String? {
        val night = app.database.nightDao().night(nightId) ?: return null
        val plan = Tracker.wakePlan(this, night) ?: return null
        // "minutes;start;end" so the Morning graph can draw the window band exactly.
        return if (plan.isSmart) "${plan.alarm.windowMinutes};${plan.startMs};${plan.endMs}" else null
    }

    // Sensor thread.

    override fun onSensorChanged(event: SensorEvent) {
        // Sensor timestamps count from boot; convert to wall-clock time.
        val ageMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
        val time = System.currentTimeMillis() - ageMs.coerceAtLeast(0L)
        val sample = Sample(time, event.values[0], event.values[1], event.values[2])
        accumulator.add(sample)?.let(pendingEpochs::add)
        if (rawOn && sampleCounter++ % RAW_EVERY_NTH == 0) {
            pendingRaw.add(RawSample(nightId = nightId, time = time, x = sample.x, y = sample.y, z = sample.z))
        }
        if (!drainScheduled) {
            drainScheduled = true
            acquireDrainLock()
            // A batch arrives as a burst of events; drain once the burst is over.
            handler?.postDelayed(::drain, DRAIN_DELAY_MS)
        }
    }

    override fun onFlushCompleted(sensor: Sensor) {
        handler?.post {
            drain()
            flushDone?.complete(Unit)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun drain() {
        drainScheduled = false
        if (pendingEpochs.isEmpty() && pendingRaw.isEmpty()) {
            releaseDrainLock()
            return
        }
        val epochs = pendingEpochs.toList()
        val raw = pendingRaw.toList()
        pendingEpochs.clear()
        pendingRaw.clear()
        val id = nightId
        scope.launch(db) {
            try {
                write(id, epochs, raw)
            } finally {
                releaseDrainLock()
            }
        }
    }

    private suspend fun write(id: Long, epochs: List<Epoch>, raw: List<RawSample>) {
        val dao = app.database.nightDao()
        for (e in epochs) {
            Gaps.between(coveredUntil, e.startMs)?.let { gap ->
                dao.insertEvent(
                    EventRow(
                        nightId = id,
                        time = gap.startMs,
                        type = EventType.TRACKING_INTERRUPTED,
                        detail = "${gap.lengthMs / 60_000L}",
                    ),
                )
            }
            coveredUntil = maxOf(coveredUntil, e.startMs + SleepEngine.EPOCH_MS)
            dao.insertEpoch(EpochRow(nightId = id, time = e.startMs, movement = e.movement, noise = e.noise))
            afterEpoch(id, Point(e.startMs, e.movement, e.noise))
        }
        if (raw.isNotEmpty()) dao.insertRaw(raw)
        if (epochs.isNotEmpty()) runCatching { decide(id) }.onFailure { Log.e(TAG, "Wake decision failed", it) }
    }

    // Smart wake (db dispatcher).

    private suspend fun afterEpoch(id: Long, p: Point) {
        if (recent.lastOrNull()?.let { p.timeMs <= it.timeMs } == true) return
        recent.addLast(p)
        while (recent.size > RECENT_EPOCHS) recent.removeFirst()
        val change = outOfBed?.add(p) ?: return
        val type = if (change.first == OutOfBedTracker.Change.OUT_OF_BED) EventType.OUT_OF_BED else EventType.BACK_IN_BED
        app.database.nightDao().insertEvent(EventRow(nightId = id, time = change.second, type = type))
    }

    /**
     * After new epochs: ring now if the engine says so. Inside the wake window before the
     * alarm has rung, and during a snooze once half of it has passed (so pressing snooze
     * is not itself taken for light sleep). Also runs the stay-awake check.
     */
    private suspend fun decide(id: Long) {
        if (stopping) return
        if (checkStayAwake(force = false)) return
        if (RingingState.current.value != null) return
        val now = System.currentTimeMillis()
        if (now - ringRequestedAt < RING_REQUEST_PAUSE_MS) return
        val last = recent.lastOrNull() ?: return
        // Old epochs from a batch: too late to act on.
        if (now - (last.timeMs + SleepEngine.EPOCH_MS) > FRESH_MS) return
        val dao = app.database.nightDao()
        val night = dao.night(id) ?: return
        val alarmId = night.alarmId ?: return
        val scheduler = app.scheduler
        val snoozeFrom = scheduler.snoozedAt(alarmId)
        val snoozeUntil = scheduler.snoozedUntil(alarmId)
        val plan = Tracker.wakePlan(this, night)
        val sensitivity = plan?.sensitivity ?: Tracker.wakePlan(this, app.database.alarmDao().get(alarmId) ?: return).sensitivity
        val points = recent.toList()
        val reason = when {
            snoozeFrom != null && snoozeUntil != null -> {
                if (now < snoozeFrom + (snoozeUntil - snoozeFrom) / 2) null
                else WakeRules.decide(points.filter { it.timeMs >= snoozeFrom }, sensitivity)
            }
            plan != null && plan.isSmart && now >= plan.startMs && now < plan.endMs - END_MARGIN_MS &&
                dao.firstEvent(id, EventType.RANG) == null -> WakeRules.decide(points, sensitivity)
            else -> null
        } ?: return
        ringRequestedAt = now
        dao.insertEvent(EventRow(nightId = id, time = now, type = EventType.LIGHT_SLEEP_DETECTED, detail = reason.key))
        scheduler.ringNow(alarmId, AlarmScheduler.Kind.SMART, reason.name)
        Log.i(TAG, "Ringing early: $reason")
    }

    // Stay-awake check: state in preferences, so it survives a service restart.

    private val prefs by lazy { getSharedPreferences("tracking", Context.MODE_PRIVATE) }

    private fun stayAwakeFrom(): Long = prefs.getLong(KEY_SA_FROM, 0L)

    private suspend fun startStayAwake(alarmId: Long) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_SA_FROM, now).putLong(KEY_SA_ALARM, alarmId).apply()
        listenClosely()
        Planner.scheduleStayAwakeCheck(this, now + OutOfBed.STAY_AWAKE_MS + 60_000L)
        Log.i(TAG, "Stay-awake check started")
    }

    /**
     * 5 minutes after the challenge: back in bed and still → ring again; otherwise the night
     * ends (woke at the dismiss). Returns true if a check was due and handled.
     */
    private suspend fun checkStayAwake(force: Boolean): Boolean {
        val from = stayAwakeFrom()
        if (from == 0L || RingingState.current.value != null) return false
        val now = System.currentTimeMillis()
        if (now < from + OutOfBed.STAY_AWAKE_MS && !(force && now >= from + OutOfBed.STAY_AWAKE_MS - 30_000L)) return false
        val id = nightId.takeIf { it != 0L } ?: app.database.nightDao().openNight()?.id ?: return false
        val dao = app.database.nightDao()
        val points = dao.epochsSince(id, from).map { it.toPoint() }
        val night = dao.epochs(id).map { it.toPoint() }
        val count = prefs.getInt(KEY_SA_COUNT, 0)
        val alarmId = prefs.getLong(KEY_SA_ALARM, 0L)
        prefs.edit().remove(KEY_SA_FROM).apply()
        Planner.cancelStayAwakeCheck(this)
        if (OutOfBed.backInBed(points, reference, night) && count < MAX_STAY_AWAKE_RINGS) {
            prefs.edit().putInt(KEY_SA_COUNT, count + 1).apply()
            dao.insertEvent(EventRow(nightId = id, time = now, type = EventType.STAY_AWAKE_RANG))
            ringRequestedAt = now
            app.scheduler.ringNow(alarmId, AlarmScheduler.Kind.STAY_AWAKE, null)
            Log.i(TAG, "Stay-awake check: back in bed, ringing again")
        } else {
            prefs.edit().remove(KEY_SA_COUNT).remove(KEY_SA_ALARM).apply()
            Log.i(TAG, "Stay-awake check passed; ending the night")
            scope.launch { stopTracking(null, null, wakeAt = from) }
        }
        return true
    }

    // Stopping.

    private suspend fun stopTracking(reason: SleepEngine.WakeReason?, detail: String?, wakeAt: Long? = null) = lifecycle.withLock {
        stopping = true
        val id = nightId
        val night = if (id != 0L) app.database.nightDao().night(id) else app.database.nightDao().openNight()
        if (registered) {
            // Ask the sensor for what is still in its FIFO, then save the epoch in progress.
            val done = CompletableDeferred<Unit>()
            handler?.post { flushDone = done }
            val asked = sensorManager?.flush(this) == true
            if (asked) withTimeoutOrNull(FLUSH_TIMEOUT_MS) { done.await() }
            val last = CompletableDeferred<Pair<List<Epoch>, List<RawSample>>>()
            handler?.post {
                sensorManager?.unregisterListener(this)
                accumulator.flush()?.let(pendingEpochs::add)
                last.complete(pendingEpochs.toList() to pendingRaw.toList())
                pendingEpochs.clear()
                pendingRaw.clear()
            } ?: last.complete(emptyList<Epoch>() to emptyList())
            val (epochs, raw) = withTimeoutOrNull(FLUSH_TIMEOUT_MS) { last.await() } ?: (emptyList<Epoch>() to emptyList())
            registered = false
            if (night != null) scope.launch(db) { write(night.id, epochs, raw) }.join()
        }
        if (night != null) {
            kotlinx.coroutines.withContext(db) {
                val at = System.currentTimeMillis()
                Tracker.close(this@TrackingService, night, reason, at, detail, wakeAt ?: at)
            }
        }
        prefs.edit().remove(KEY_SA_FROM).remove(KEY_SA_COUNT).remove(KEY_SA_ALARM).apply()
        Planner.cancelWatchdog(this)
        Planner.cancelListen(this)
        Planner.cancelStayAwakeCheck(this)
        shutDown()
    }

    private fun shutDown() {
        _running.value = false
        if (registered) sensorManager?.unregisterListener(this)
        registered = false
        sensorThread?.quitSafely()
        sensorThread = null
        handler = null
        fallbackLock?.takeIf { it.isHeld }?.release()
        fallbackLock = null
        closeLock?.takeIf { it.isHeld }?.release()
        closeLock = null
        listeningClosely = false
        releaseDrainLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Wake lock while draining a batch.

    private fun acquireDrainLock() {
        val lock = drainLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Anatole:drain")
            .also { it.setReferenceCounted(false); drainLock = it }
        lock.acquire(DRAIN_LOCK_TIMEOUT_MS)
    }

    private fun releaseDrainLock() {
        drainLock?.takeIf { it.isHeld }?.release()
    }

    // Notification.

    private fun goForeground(alarmText: String?): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else 0
        return try {
            ServiceCompat.startForeground(this, Notifications.ID_TRACKING, notification(alarmText), type)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enter the foreground", e)
            false
        }
    }

    private fun notification(alarmText: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_BEDTIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            this,
            11,
            Intent(this, BedtimeReceiver::class.java).setAction(BedtimeReceiver.ACTION_STOP_TRACKING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (alarmText != null) "Alarm $alarmText · movement tracking on" else "Movement tracking on"
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tracking your sleep")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop tracking", stop)
            .build()
    }

    override fun onDestroy() {
        _running.value = false
        if (registered) sensorManager?.unregisterListener(this)
        registered = false
        sensorThread?.quitSafely()
        fallbackLock?.takeIf { it.isHeld }?.release()
        closeLock?.takeIf { it.isHeld }?.release()
        releaseDrainLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "se.lyzio.lyzlarm.tracking.START"
        const val ACTION_RESUME = "se.lyzio.lyzlarm.tracking.RESUME"
        const val ACTION_STOP = "se.lyzio.lyzlarm.tracking.STOP"
        /** Wake window opening, or a snooze: listen closely. */
        const val ACTION_LISTEN = "se.lyzio.lyzlarm.tracking.LISTEN"
        const val ACTION_STAY_AWAKE = "se.lyzio.lyzlarm.tracking.STAY_AWAKE"
        const val ACTION_STAY_AWAKE_CHECK = "se.lyzio.lyzlarm.tracking.STAY_AWAKE_CHECK"
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_ALARM_ID = "alarm_id"

        /** Epochs kept for the wake decision (5 min, enough for "already awake"). */
        private const val RECENT_EPOCHS = WakeRules.AWAKE_EPOCHS
        /** Start listening closely this long before the window opens. */
        private const val LISTEN_LEAD_MS = 60_000L
        /** Wake lock while listening closely; longer than a 45 min window plus snoozes. */
        private const val CLOSE_LOCK_MS = 90 * 60_000L
        /** An epoch older than this when it arrives is not acted on. */
        private const val FRESH_MS = 90_000L
        /** No early ring in the last seconds of the window: the fallback is about to ring. */
        private const val END_MARGIN_MS = 15_000L
        /** After asking for a ring, wait this long before asking again. */
        private const val RING_REQUEST_PAUSE_MS = 60_000L
        /** The stay-awake check rings again at most this many times. */
        private const val MAX_STAY_AWAKE_RINGS = 6
        private const val KEY_SA_FROM = "stay_awake_from"
        private const val KEY_SA_ALARM = "stay_awake_alarm"
        private const val KEY_SA_COUNT = "stay_awake_count"

        /** 20 Hz for the movement score. */
        private const val SAMPLE_PERIOD_US = 50_000
        /** Raw recording keeps every 2nd sample: 10 Hz, about 15–20 MB for an 8 h night. */
        private const val RAW_EVERY_NTH = 2
        /** Deliver batches at least every 5 minutes. */
        private const val MAX_LATENCY_US = 5L * 60 * 1_000_000
        private const val DRAIN_DELAY_MS = 300L
        private const val DRAIN_LOCK_TIMEOUT_MS = 20_000L
        private const val FLUSH_TIMEOUT_MS = 3_000L
        private const val TAG = "TrackingService"

        private val _running = MutableStateFlow(false)
        /** True while the service is recording in this process. */
        val running: StateFlow<Boolean> = _running

        /** Asks a running tracking service to listen closely (snooze). The app has a foreground service, so this start is allowed. */
        fun listenClosely(context: Context) {
            if (!running.value) return
            runCatching {
                context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_LISTEN))
            }.onFailure { Log.w(TAG, "Cannot reach the tracking service", it) }
        }
    }
}
