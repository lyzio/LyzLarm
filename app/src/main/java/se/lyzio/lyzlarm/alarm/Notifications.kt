package se.lyzio.lyzlarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_BEDTIME = "bedtime"

    const val ID_RINGING = 1
    const val ID_TRACKING = 2
    const val ID_REMINDER = 3
    const val ID_BATTERY = 4

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "The ringing alarm"
            // The service plays the sound and vibration itself, with a gentle fade-in.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val tracking = NotificationChannel(
            CHANNEL_TRACKING,
            "Sleep tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Anatole tracks your sleep"
            setShowBadge(false)
        }
        val bedtime = NotificationChannel(
            CHANNEL_BEDTIME,
            "Bedtime",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Bedtime reminder and low-battery warning"
        }
        manager.createNotificationChannels(listOf(alarm, tracking, bedtime))
    }
}
