package se.lyzio.lyzlarm

import android.app.Application
import android.content.Context
import se.lyzio.lyzlarm.alarm.AlarmScheduler
import se.lyzio.lyzlarm.alarm.Notifications
import se.lyzio.lyzlarm.data.AppDatabase
import se.lyzio.lyzlarm.data.Settings

/** Manual dependency injection: one container for the whole app. */
class LyzLarmApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { Settings(this) }
    val scheduler by lazy { AlarmScheduler(this, database.alarmDao()) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val Context.app: LyzLarmApp get() = applicationContext as LyzLarmApp
