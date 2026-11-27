package se.lyzio.anatole

import android.app.Application
import android.util.Log
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import se.lyzio.anatole.alarm.AlarmScheduler
import se.lyzio.anatole.alarm.Notifications
import se.lyzio.anatole.data.AppDatabase
import se.lyzio.anatole.data.Settings
import se.lyzio.anatole.tracking.Planner
import se.lyzio.anatole.tracking.Tracker

/** Manual dependency injection: one container for the whole app. */
class AnatoleApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { Settings(this) }
    val scheduler by lazy { AlarmScheduler(this, database.alarmDao()) }

    /** Lives as long as the process; for small background jobs. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        scope.launch {
            val nights = database.nightDao()
            nights.deleteRawBefore(System.currentTimeMillis() - Tracker.RAW_KEEP_MS)
            // Short sessions saved before the 15-minute rule existed.
            nights.shortNightIds(Tracker.MIN_NIGHT_MS).forEach { nights.deleteNight(it) }
        }
        // v0.4: while a night is tracked, it follows the next alarm whenever alarms change
        // (turned off/on, added, deleted, edited). The first emission just re-checks.
        scope.launch {
            database.alarmDao().observeAll()
                .debounce(300L)
                .collect { runCatching { Tracker.followNextAlarm(this@AnatoleApp) }.onFailure { Log.w("AnatoleApp", "Follow alarm failed", it) } }
        }
        // Re-plan the bedtime reminder and battery warning whenever alarms, settings or
        // the open night change.
        scope.launch {
            combine(
                database.alarmDao().observeAll(),
                settings.tracking,
                database.nightDao().observeOpenNight(),
            ) { _, _, _ -> Unit }
                .debounce(1_000L)
                .collect { runCatching { Planner.refresh(this@AnatoleApp) } }
        }
    }
}

val Context.app: AnatoleApp get() = applicationContext as AnatoleApp
