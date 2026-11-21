package se.lyzio.lyzlarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.lyzio.lyzlarm.app

/**
 * Re-arms all alarms after a restart, an app update, a clock or time-zone change,
 * or when the exact-alarm permission is granted again.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RescheduleReceiver", "Rescheduling after ${intent.action}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.app.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
