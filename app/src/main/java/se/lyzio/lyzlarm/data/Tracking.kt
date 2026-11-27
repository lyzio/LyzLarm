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
    /** SleepEngine.WakeReason name; only FIXED or MANUAL in v0.2. */
    @ColumnInfo(name = "wake_reason") val wakeReason: String? = null,
    @ColumnInfo(name = "battery_start") val batteryStart: Int? = null,
    @ColumnInfo(name = "battery_end") val batteryEnd: Int? = null,
    @ColumnInfo(name = "engine_version") val engineVersion: Int,
    /** IANA zone id at the start, e.g. "Europe/Stockholm". */
    @ColumnInfo(name = "time_zone") val timeZone: String,
    /** Sleep-onset estimate; filled from v0.3. */
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
}

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
