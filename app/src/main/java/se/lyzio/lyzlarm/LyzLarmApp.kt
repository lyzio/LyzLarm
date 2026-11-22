package se.lyzio.lyzlarm

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import se.lyzio.lyzlarm.alarm.AlarmScheduler
import se.lyzio.lyzlarm.alarm.Notifications
import se.lyzio.lyzlarm.data.AppDatabase
import se.lyzio.lyzlarm.data.Settings
import se.lyzio.lyzlarm.tracking.Planner
import se.lyzio.lyzlarm.tracking.Tracker

/** Manual dependency injection: one container for the whole app. */
class LyzLarmApp : Application() {
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
        // Re-plan the bedtime reminder and battery warning whenever alarms, settings or
        // the open night change.
        scope.launch {
            combine(
                database.alarmDao().observeAll(),
                settings.tracking,
                database.nightDao().observeOpenNight(),
            ) { _, _, _ -> Unit }
                .debounce(1_000L)
                .collect { runCatching { Planner.refresh(this@LyzLarmApp) } }
        }
    }
}

val Context.app: LyzLarmApp get() = applicationContext as LyzLarmApp
