package se.lyzio.lyzlarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.Alarm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plays the alarm: vibration first, then the sound fades in. Runs as a foreground
 * service so Android keeps it alive, and shows the full-screen ringing screen
 * over the lock screen.
 */
class RingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ringJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, AlarmScheduler.TEST_ALARM_ID)
                val kind = runCatching {
                    AlarmScheduler.Kind.valueOf(intent.getStringExtra(AlarmReceiver.EXTRA_KIND) ?: "")
                }.getOrDefault(AlarmScheduler.Kind.ALARM)
                // Placeholder state first, so the ringing screen has something to show
                // if it opens before the alarm has loaded.
                RingingState.set(
                    Ringing(alarmId, kind, placeholderLabel(kind), System.currentTimeMillis(), 0, 0f, true),
                )
                // Must be in the foreground within seconds of starting.
                goForeground(placeholderLabel(kind), System.currentTimeMillis(), snoozeMinutes = 0)
                start(alarmId, kind)
            }
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> if (RingingState.current.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(alarmId: Long, kind: AlarmScheduler.Kind) {
        stopPlayback()
        acquireWakeLock()
        ringJob = scope.launch {
            val scheduler = app.scheduler
            val alarm: Alarm? = if (kind == AlarmScheduler.Kind.TEST) {
                scheduler.clearTestMarker()
                Alarm(id = AlarmScheduler.TEST_ALARM_ID, snoozeMinutes = 0)
            } else {
                withContext(Dispatchers.IO) { loadAndAdvance(alarmId, kind) }
            }
            if (alarm == null) {
                Log.w(TAG, "Alarm $alarmId no longer exists; not ringing")
                finish()
                return@launch
            }
            val isTest = kind == AlarmScheduler.Kind.TEST
            val label = if (isTest) "Test alarm" else NextAlarm.label(alarm.repeatDays)
            val now = System.currentTimeMillis()
            RingingState.set(
                Ringing(
                    alarmId = alarm.id,
                    kind = kind,
                    label = label,
                    ringTimeMillis = now,
                    snoozeMinutes = alarm.snoozeMinutes,
                    volume = 0f,
                    vibratingOnly = alarm.vibrateFirst,
                ),
            )
            goForeground(label, now, alarm.snoozeMinutes)
            ring(alarm, isTest)
        }
    }

    /** Loads the alarm and moves it on: repeating alarms get their next ring, one-time alarms turn off. */
    private suspend fun loadAndAdvance(alarmId: Long, kind: AlarmScheduler.Kind): Alarm? {
        val dao = app.database.alarmDao()
        val alarm = dao.get(alarmId) ?: return null
        if (kind == AlarmScheduler.Kind.SNOOZE) {
            app.scheduler.cancelSnooze(alarmId)
            return alarm
        }
        if (alarm.isRepeating) {
            app.scheduler.schedule(alarm)
        } else {
            dao.update(alarm.copy(enabled = false))
        }
        return alarm
    }

    private suspend fun ring(alarm: Alarm, isTest: Boolean) {
        val vibrateFirstMs = if (isTest) 10_000L else VIBRATE_FIRST_MS
        val fadeMs = when {
            !alarm.gradualVolume -> 0L
            isTest -> 20_000L
            else -> alarm.fadeMinutes * 60_000L
        }
        val mediaPlayer = createPlayer(alarm.soundUri)
        // If no sound can be played at all, always vibrate.
        if (alarm.vibration || mediaPlayer == null) startVibration()

        if (alarm.vibrateFirst) delay(vibrateFirstMs)
        RingingState.update { it.copy(vibratingOnly = false) }

        if (mediaPlayer == null) return
        player = mediaPlayer
        mediaPlayer.setVolume(if (fadeMs == 0L) 1f else MIN_VOLUME, if (fadeMs == 0L) 1f else MIN_VOLUME)
        mediaPlayer.start()

        val started = System.currentTimeMillis()
        val endAt = started + MAX_RING_MS
        while (scope.isActive) {
            val elapsed = System.currentTimeMillis() - started
            val fraction = if (fadeMs == 0L) 1f else (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)
            // Squared curve: sounds like an even rise to the ear.
            val volume = (MIN_VOLUME + (1f - MIN_VOLUME) * fraction * fraction).coerceAtMost(1f)
            runCatching { mediaPlayer.setVolume(volume, volume) }
            RingingState.update { it.copy(volume = fraction) }
            if (System.currentTimeMillis() > endAt) {
                Log.i(TAG, "Alarm rang for ${MAX_RING_MS / 60_000} min without response; stopping")
                finish()
                return
            }
            delay(1_000L)
        }
    }

    private fun createPlayer(soundUri: String?): MediaPlayer? {
        val candidates = listOfNotNull(
            soundUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                return MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@RingingService, uri)
                    isLooping = true
                    prepare()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $uri", e)
            }
        }
        return null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun dismiss() {
        val current = RingingState.current.value
        if (current?.kind == AlarmScheduler.Kind.TEST) {
            // Own scope: the service scope is cancelled as soon as the service stops.
            val settings = app.settings
            CoroutineScope(Dispatchers.IO).launch { settings.markTestAlarmPassed(System.currentTimeMillis()) }
        }
        finish()
    }

    private fun snooze() {
        val current = RingingState.current.value
        if (current != null && current.kind != AlarmScheduler.Kind.TEST && current.snoozeMinutes > 0) {
            app.scheduler.snooze(current.alarmId, current.snoozeMinutes)
        }
        finish()
    }

    private fun finish() {
        stopPlayback()
        RingingState.set(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    private fun stopPlayback() {
        ringJob?.cancel()
        ringJob = null
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun goForeground(label: String, ringTime: Long, snoozeMinutes: Int) {
        val notification = buildNotification(label, ringTime, snoozeMinutes)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, Notifications.ID_RINGING, notification, type)
    }

    private fun buildNotification(label: String, ringTime: Long, snoozeMinutes: Int): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingingActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ringTime))
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$time · $label")
            .setContentText("Alarm ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        if (snoozeMinutes > 0) {
            builder.addAction(0, "Snooze $snoozeMinutes min", serviceIntent(ACTION_SNOOZE))
        }
        builder.addAction(0, "I'm up", serviceIntent(ACTION_DISMISS))
        return builder.build()
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, RingingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun placeholderLabel(kind: AlarmScheduler.Kind) =
        if (kind == AlarmScheduler.Kind.TEST) "Test alarm" else "Alarm"

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LyzLarm:ringing")
            .apply { acquire(MAX_RING_MS + 60_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopPlayback()
        RingingState.set(null)
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "se.lyzio.lyzlarm.action.START"
        const val ACTION_DISMISS = "se.lyzio.lyzlarm.action.DISMISS"
        const val ACTION_SNOOZE = "se.lyzio.lyzlarm.action.SNOOZE"

        /** Vibration alone before the sound starts (design: ~30–60 s). */
        private const val VIBRATE_FIRST_MS = 45_000L
        /** Stop ringing after this long without a response. */
        private const val MAX_RING_MS = 30 * 60_000L
        private const val MIN_VOLUME = 0.05f
        private const val TAG = "RingingService"

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RingingService::class.java).setAction(action))
        }
    }
}
