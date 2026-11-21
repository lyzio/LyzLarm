package se.lyzio.lyzlarm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One configured alarm. Columns follow the design doc's data model.
 *
 * v0.1 rings at [hour]:[minute] (the latest wake time). [windowMinutes] and
 * [soundTrackingMode] are stored but not used until v0.3 / v0.4.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Latest wake time, local time of day. */
    val hour: Int = 7,
    val minute: Int = 0,
    /** Wake window length in minutes; 0 = fixed time. Unused in v0.1. */
    @ColumnInfo(name = "window_minutes") val windowMinutes: Int = 0,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 = rings once. */
    @ColumnInfo(name = "repeat_days") val repeatDays: Int = 0,
    val enabled: Boolean = true,
    /** Alarm sound as a content URI; null = the phone's default alarm sound. */
    @ColumnInfo(name = "sound_uri") val soundUri: String? = null,
    @ColumnInfo(name = "gradual_volume") val gradualVolume: Boolean = true,
    @ColumnInfo(name = "fade_minutes") val fadeMinutes: Int = 3,
    val vibration: Boolean = true,
    /** Vibration alone for the first ~45 s, then the sound fades in. */
    @ColumnInfo(name = "vibrate_first") val vibrateFirst: Boolean = true,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int = 9,
    /** 0 = off, 1 = last 1 h, 2 = last 2 h, 3 = all night. Unused in v0.1. */
    @ColumnInfo(name = "sound_tracking_mode") val soundTrackingMode: Int = 0,
) {
    val isRepeating: Boolean get() = repeatDays != 0
}
