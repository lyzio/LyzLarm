package se.lyzio.anatole.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.lyzio.anatole.app
import se.lyzio.anatole.tracking.Planner
import se.lyzio.anatole.tracking.Tracker

/**
 * Re-arms all alarms after a restart, an app update, a clock or time-zone change,
 * or when the exact-alarm permission is granted again. Also resumes an open night's
 * tracking after a restart and re-plans the bedtime reminder.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RescheduleReceiver", "Rescheduling after ${intent.action}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.app.scheduler.rescheduleAll()
                // After a restart or app update: continue an open night (boot and update
                // broadcasts may start a foreground service; the others may not).
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    Tracker.resumeIfNeeded(context)
                }
                Planner.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
