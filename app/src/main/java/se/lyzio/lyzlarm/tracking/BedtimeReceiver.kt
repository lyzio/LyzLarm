package se.lyzio.lyzlarm.tracking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.lyzio.lyzlarm.MainActivity
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.alarm.Notifications
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.ui.Format

/** Bedtime reminder, low-battery warning, their actions, "Stop tracking", and the tracking watchdog. */
class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent.action)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        when (action) {
            ACTION_REMINDER -> {
                if (context.app.database.nightDao().openNight() == null) {
                    val next = Tracker.nextAlarm(context)
                    if (next != null && next.second - System.currentTimeMillis() <= Tracker.MAX_AHEAD_MS) {
                        showReminder(context, Format.time(context, next.second))
                    }
                }
            }
            ACTION_BATTERY_WARNING -> {
                val settings = context.app.settings.tracking()
                val level = Tracker.battery(context)
                val tracking = context.app.database.nightDao().openNight() != null
                if (!tracking && level < settings.warningThreshold && !Tracker.isCharging(context)) {
                    showBatteryWarning(context, level)
                }
            }
            ACTION_NOT_TONIGHT -> manager.cancel(Notifications.ID_REMINDER)
            ACTION_BATTERY_OK -> manager.cancel(Notifications.ID_BATTERY)
            ACTION_STOP_TRACKING -> Tracker.stopManually(context)
            ACTION_WATCHDOG -> {
                if (Tracker.resumeIfNeeded(context)) Planner.scheduleWatchdog(context)
                return
            }
        }
    }

    private fun showReminder(context: Context, alarmTime: String) {
        val start = PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_SLEEP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notTonight = broadcast(context, ACTION_NOT_TONIGHT, 21)
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_BEDTIME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for bed?")
            .setContentText("Your alarm is at $alarmTime. Start sleep tracking when you lie down.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your alarm is at $alarmTime. Start sleep tracking when you lie down."))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(start)
            .addAction(0, "Start sleep", start)
            .addAction(0, "Not tonight", notTonight)
            .build()
        notify(context, Notifications.ID_REMINDER, n)
    }

    private fun showBatteryWarning(context: Context, level: Int) {
        val ok = broadcast(context, ACTION_BATTERY_OK, 22)
        val text = "Charge before bedtime so tracking lasts the night. Never charge in the bed."
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_BEDTIME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Battery at $level%")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, "OK", ok)
            .build()
        notify(context, Notifications.ID_BATTERY, n)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notifications not allowed", e)
        }
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, BedtimeReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMINDER = "se.lyzio.lyzlarm.bedtime.REMINDER"
        const val ACTION_BATTERY_WARNING = "se.lyzio.lyzlarm.bedtime.BATTERY_WARNING"
        const val ACTION_NOT_TONIGHT = "se.lyzio.lyzlarm.bedtime.NOT_TONIGHT"
        const val ACTION_BATTERY_OK = "se.lyzio.lyzlarm.bedtime.BATTERY_OK"
        const val ACTION_STOP_TRACKING = "se.lyzio.lyzlarm.bedtime.STOP_TRACKING"
        const val ACTION_WATCHDOG = "se.lyzio.lyzlarm.bedtime.WATCHDOG"
        private const val TAG = "BedtimeReceiver"
    }
}
