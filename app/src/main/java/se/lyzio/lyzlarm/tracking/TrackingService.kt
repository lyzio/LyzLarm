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
import se.lyzio.lyzlarm.MainActivity
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.alarm.Notifications
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.EventType
import se.lyzio.lyzlarm.data.RawSample
import se.lyzio.lyzlarm.engine.Epoch
import se.lyzio.lyzlarm.engine.EpochAccumulator
import se.lyzio.lyzlarm.engine.Gaps
import se.lyzio.lyzlarm.engine.Sample
import se.lyzio.lyzlarm.engine.SleepEngine
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
                val reason = runCatching {
                    SleepEngine.WakeReason.valueOf(intent.getStringExtra(EXTRA_REASON) ?: "")
                }.getOrDefault(SleepEngine.WakeReason.MANUAL)
                val detail = intent.getStringExtra(EXTRA_DETAIL)
                scope.launch { stopTracking(reason, detail) }
            }
            // START, RESUME, or null (restarted by Android after being killed).
            else -> scope.launch { begin() }
        }
        return START_STICKY
    }

    private suspend fun begin() = lifecycle.withLock {
        if (registered) return@withLock
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
        rawOn = app.settings.tracking().rawOn
        coveredUntil = dao.lastEpochTime(night.id)?.plus(SleepEngine.EPOCH_MS) ?: night.startTime
        _running.value = true

        val alarmAt = Tracker.alarmTimeFor(this, night)
        goForeground(alarmText = alarmAt?.let { Format.time(this, it) })
        registerSensor()
        Planner.scheduleWatchdog(this)
        Log.i(TAG, "Tracking night ${night.id}, raw=$rawOn")
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
        val fifo = if (sensor.fifoReservedEventCount > 0) sensor.fifoReservedEventCount else sensor.fifoMaxEventCount
        val batching = wakeUp != null && fifo > 0
        val latencyUs = if (batching) {
            minOf(MAX_LATENCY_US, fifo.toLong() * SAMPLE_PERIOD_US * 8 / 10).toInt()
        } else 0
        if (!batching) {
            // No safe batching: keep the CPU awake so no samples are lost.
            fallbackLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LyzLarm:tracking")
                .apply { setReferenceCounted(false); acquire(Tracker.MAX_NIGHT_MS) }
        }
        val thread = HandlerThread("LyzLarm-sensor").also { it.start() }
        sensorThread = thread
        val h = Handler(thread.looper)
        handler = h
        registered = sm.registerListener(this, sensor, SAMPLE_PERIOD_US, latencyUs, h)
        Log.i(TAG, "Accelerometer ${sensor.name} wakeUp=${wakeUp != null} fifo=$fifo latency=${latencyUs / 1000} ms registered=$registered")
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
            dao.insertEpoch(EpochRow(nightId = id, time = e.startMs, movement = e.movement))
        }
        if (raw.isNotEmpty()) dao.insertRaw(raw)
    }

    // Stopping.

    private suspend fun stopTracking(reason: SleepEngine.WakeReason, detail: String?) = lifecycle.withLock {
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
                Tracker.close(this@TrackingService, night, reason, System.currentTimeMillis(), detail)
            }
        }
        Planner.cancelWatchdog(this)
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
        releaseDrainLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Wake lock while draining a batch.

    private fun acquireDrainLock() {
        val lock = drainLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LyzLarm:drain")
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
        releaseDrainLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "se.lyzio.lyzlarm.tracking.START"
        const val ACTION_RESUME = "se.lyzio.lyzlarm.tracking.RESUME"
        const val ACTION_STOP = "se.lyzio.lyzlarm.tracking.STOP"
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"

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
    }
}
