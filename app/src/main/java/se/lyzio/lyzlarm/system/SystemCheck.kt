package se.lyzio.lyzlarm.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import se.lyzio.lyzlarm.app

/** Status of everything an alarm needs to ring reliably. Used by setup and System check. */
data class SystemStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** Number of items that need attention (battery is advice, but still counted). */
    val issues: Int get() = listOf(notifications, exactAlarms, fullScreen, batteryUnrestricted).count { !it }
}

object SystemCheck {

    fun status(context: Context): SystemStatus = SystemStatus(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarms = context.app.scheduler.canScheduleExact(),
        fullScreen = canUseFullScreen(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
    )

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    private fun pkg(context: Context) = Uri.parse("package:${context.packageName}")

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(context))
        } else null

    fun fullScreenIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context))
        } else null

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(context))

    fun batterySettingsFallback(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Brand name for display, e.g. "Samsung". */
    fun brand(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** Extra steps for phone brands known to stop background apps. Null = none needed. */
    fun brandTip(): String? = when (Build.MANUFACTURER.lowercase()) {
        "samsung" -> "Also add Anatole under Settings › Battery › Background usage limits › Never sleeping apps."
        "xiaomi", "redmi", "poco" -> "Also open Settings › Apps › Anatole and set Battery saver to No restrictions, and turn on Autostart."
        "huawei", "honor" -> "Also open Settings › Battery › App launch, find Anatole and set it to Manage manually with all switches on."
        "oneplus", "oppo", "realme" -> "Also open Settings › Battery › Anatole and allow background activity."
        "vivo" -> "Also allow Anatole under Settings › Battery › Background power consumption."
        else -> null
    }
}
