package se.lyzio.anatole.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Receives the setAlarmClock() broadcast and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.TEST_ALARM_ID)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: AlarmScheduler.Kind.ALARM.name
        val service = Intent(context, RingingService::class.java)
            .setAction(RingingService.ACTION_START)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_REASON, intent.getStringExtra(EXTRA_REASON))
        // Alarm-clock broadcasts are allowed to start a foreground service from the background.
        ContextCompat.startForegroundService(context, service)
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_REASON = "reason"
    }
}
