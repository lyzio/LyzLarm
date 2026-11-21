package se.lyzio.lyzlarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val ID_RINGING = 1

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
        manager.createNotificationChannel(alarm)
    }
}
