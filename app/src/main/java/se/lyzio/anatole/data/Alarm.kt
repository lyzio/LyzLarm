package se.lyzio.anatole.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One configured alarm. Columns follow the design doc's data model.
 *
 * [hour]:[minute] is the latest wake time: the alarm always rings by then. With a wake
 * window (v0.3) it may ring up to [windowMinutes] earlier, at light sleep.
 * [soundTrackingMode] is stored but not used until v0.4.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Latest wake time, local time of day. */
    val hour: Int = 7,
    val minute: Int = 0,
    /** Wake window length in minutes; 0 = fixed time. */
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
    /** Snooze lock: snooze and "I'm up" need a solved challenge (v0.3). */
    @ColumnInfo(name = "snooze_lock", defaultValue = "0") val snoozeLock: Boolean = false,
    /** Allowed challenge types, bits from [Challenges]; math, memory and typing by default. */
    @ColumnInfo(name = "challenge_types", defaultValue = "7") val challengeTypes: Int = Challenges.DEFAULT,
    /** 0 = Easy, 1 = Normal, 2 = Hard. */
    @ColumnInfo(name = "math_difficulty", defaultValue = "1") val mathDifficulty: Int = 1,
    /** After the challenge, check for 5 min that the user stays up; ring again if not. */
    @ColumnInfo(name = "stay_awake_check", defaultValue = "0") val stayAwakeCheck: Boolean = false,
) {
    val isRepeating: Boolean get() = repeatDays != 0
    val isSmart: Boolean get() = windowMinutes > 0
}

/** Bits of [Alarm.challengeTypes]. */
object Challenges {
    const val MATH = 1
    const val MEMORY = 2
    const val TYPING = 4
    const val BARCODE = 8
    const val DEFAULT = MATH or MEMORY or TYPING
}
