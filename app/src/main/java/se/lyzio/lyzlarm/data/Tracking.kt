package se.lyzio.lyzlarm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One tracked night, from "Start sleep" until the alarm is dismissed or tracking is stopped.
 * [endTime] is null while the night is open (tracking in progress).
 */
@Entity(tableName = "nights", indices = [Index("start_time")])
data class Night(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The alarm this night tracks towards; null if it was deleted. */
    @ColumnInfo(name = "alarm_id") val alarmId: Long?,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    /** When the user woke: alarm dismissed or tracking stopped. */
    @ColumnInfo(name = "wake_time") val wakeTime: Long? = null,
    /** SleepEngine.WakeReason name: why the alarm first rang, or MANUAL if stopped before it. */
    @ColumnInfo(name = "wake_reason") val wakeReason: String? = null,
    @ColumnInfo(name = "battery_start") val batteryStart: Int? = null,
    @ColumnInfo(name = "battery_end") val batteryEnd: Int? = null,
    @ColumnInfo(name = "engine_version") val engineVersion: Int,
    /** IANA zone id at the start, e.g. "Europe/Stockholm". */
    @ColumnInfo(name = "time_zone") val timeZone: String,
    /** Sleep-onset estimate, saved when the night ends (v0.3). */
    @ColumnInfo(name = "onset_estimate") val onsetEstimate: Long? = null,
    /** Morning rating; filled from v0.5. */
    val rating: Int? = null,
) {
    val isOpen: Boolean get() = endTime == null
}

/** One 30 s epoch. [time] is the epoch's aligned start. */
@Entity(tableName = "epochs", primaryKeys = ["night_id", "time"])
data class EpochRow(
    @ColumnInfo(name = "night_id") val nightId: Long,
    val time: Long,
    val movement: Float,
    /** Sound level; filled from v0.4. */
    @ColumnInfo(name = "sound_level") val soundLevel: Float? = null,
    /** Sound events in the epoch; filled from v0.4. */
    @ColumnInfo(name = "sound_events") val soundEvents: Int? = null,
    /** Spread of the acceleration length (m/s²), for out-of-bed detection; from v0.3. */
    val noise: Float? = null,
)

/** Something that happened during the night; see [EventType]. */
@Entity(tableName = "events", indices = [Index("night_id")])
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "night_id") val nightId: Long,
    val time: Long,
    val type: String,
    val detail: String? = null,
)

object EventType {
    /** detail = battery % at start. */
    const val TRACKING_STARTED = "tracking_started"
    /** detail = "fixed" (alarm) or "manual" (stopped by hand), plus battery %. */
    const val TRACKING_STOPPED = "tracking_stopped"
    /** time = start of the gap; detail = gap length in minutes. */
    const val TRACKING_INTERRUPTED = "tracking_interrupted"
    const val RANG = "rang"
    /** detail = snooze minutes. */
    const val SNOOZED = "snoozed"
    const val DISMISSED = "dismissed"

    // v0.3
    /** The wake window opened; detail = window length in minutes. */
    const val WINDOW_OPENED = "window_opened"
    /** The engine decided to ring; detail = wake reason key (light_sleep, already_awake). */
    const val LIGHT_SLEEP_DETECTED = "light_sleep_detected"
    const val OUT_OF_BED = "out_of_bed"
    const val BACK_IN_BED = "back_in_bed"
    /** detail = challenge type (math, memory, typing, barcode). */
    const val CHALLENGE_SHOWN = "challenge_shown"
    const val CHALLENGE_SOLVED = "challenge_solved"
    const val CHALLENGE_FAILED = "challenge_failed"
    /** The stay-awake check found the user back in bed and rang again. */
    const val STAY_AWAKE_RANG = "stay_awake_rang"
}

/**
 * One empty bed test (Settings › System check): 10 minutes with the phone where it lies at
 * night and nobody in the bed. The newest one is the reference for out-of-bed detection.
 * [epochs] keeps the recording for export: "time,movement,noise" per epoch, separated by ';'.
 */
@Entity(tableName = "empty_bed_tests")
data class EmptyBedTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "noise_p50") val noiseP50: Float,
    @ColumnInfo(name = "noise_p90") val noiseP90: Float,
    @ColumnInfo(name = "movement_max") val movementMax: Float,
    @ColumnInfo(name = "epoch_count") val epochCount: Int,
    val epochs: String,
)

/**
 * Barcodes used for Snooze lock in the last 7 days. Only a SHA-256 fingerprint is kept,
 * never the code itself, and rows older than 7 days are deleted.
 */
@Entity(tableName = "scanned_codes")
data class ScannedCode(
    @PrimaryKey val hash: String,
    val time: Long,
)

/** Raw accelerometer reading; only stored while the raw toggle is on, kept 3 days. */
@Entity(tableName = "raw_samples", indices = [Index("night_id"), Index("time")])
data class RawSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "night_id") val nightId: Long,
    val time: Long,
    val x: Float,
    val y: Float,
    val z: Float,
)
