package se.lyzio.lyzlarm.tracking

import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.EventRow
import se.lyzio.lyzlarm.data.Night
import se.lyzio.lyzlarm.data.RawSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One night as a CSV file, for tuning the sleep engine.
 *
 * Lines starting with `#` describe the night. Then one table with a `record` column:
 * `epoch` rows (movement score per 30 s, and from v0.3 the noise used for out-of-bed
 * detection), `event` rows (timeline), and `raw` rows (accelerometer x/y/z in m/s², only
 * while raw recording is on and kept).
 */
object NightCsv {
    const val HEADER = "record,time_ms,time_local,movement,type,detail,x,y,z,noise"

    fun fileName(night: Night): String {
        val zone = zoneOf(night)
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ROOT)
            .format(Instant.ofEpochMilli(night.startTime).atZone(zone))
        return "anatole-night-$date.csv"
    }

    fun write(
        out: Appendable,
        night: Night,
        epochs: List<EpochRow>,
        events: List<EventRow>,
        raw: List<RawSample>,
        appVersion: String,
    ) {
        val zone = zoneOf(night)
        fun local(ms: Long?) = ms?.let { ISO.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: ""

        out.append("# Anatole night export\n")
        out.append("# app_version,").append(appVersion).append('\n')
        out.append("# night_id,").append(night.id.toString()).append('\n')
        out.append("# start,").append(local(night.startTime)).append('\n')
        out.append("# end,").append(local(night.endTime)).append('\n')
        out.append("# wake_time,").append(local(night.wakeTime)).append('\n')
        out.append("# wake_reason,").append(night.wakeReason?.lowercase(Locale.ROOT) ?: "").append('\n')
        out.append("# battery_start,").append(night.batteryStart?.toString() ?: "").append('\n')
        out.append("# battery_end,").append(night.batteryEnd?.toString() ?: "").append('\n')
        out.append("# engine_version,").append(night.engineVersion.toString()).append('\n')
        out.append("# time_zone,").append(night.timeZone).append('\n')
        out.append("# raw_samples,").append(raw.size.toString()).append('\n')
        out.append(HEADER).append('\n')

        for (e in epochs.sortedBy { it.time }) {
            out.append("epoch,").append(e.time.toString()).append(',').append(local(e.time)).append(',')
                .append(num(e.movement)).append(",,,,,,").append(e.noise?.let { String.format(Locale.ROOT, "%.5f", it) } ?: "").append('\n')
        }
        for (e in events.sortedBy { it.time }) {
            out.append("event,").append(e.time.toString()).append(',').append(local(e.time)).append(",,")
                .append(escape(e.type)).append(',').append(escape(e.detail ?: "")).append(",,,,\n")
        }
        for (r in raw) {
            out.append("raw,").append(r.time.toString()).append(',').append(local(r.time)).append(",,,,")
                .append(num(r.x)).append(',').append(num(r.y)).append(',').append(num(r.z)).append(",\n")
        }
    }

    /** Quotes a field if it contains a comma, quote or line break (RFC 4180). */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    private fun num(v: Float): String = String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
        .let { if (it.isEmpty() || it == "-") "0" else it }

    private fun zoneOf(night: Night): ZoneId = runCatching { ZoneId.of(night.timeZone) }.getOrDefault(ZoneId.systemDefault())

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
}
