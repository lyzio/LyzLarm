package se.lyzio.anatole.tracking

import se.lyzio.anatole.data.EpochRow
import se.lyzio.anatole.data.EventRow
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.data.RawSample
import se.lyzio.anatole.engine.SleepEngine
import java.time.Instant
import java.time.OffsetDateTime
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

    /**
     * A night read back from an export (v0.2 and later): what [NightImport] needs to recreate
     * it. Raw rows are ignored. Rows keep their time in epoch millis.
     */
    data class Parsed(
        val startTime: Long,
        val endTime: Long,
        val wakeTime: Long?,
        val wakeReason: String?,
        val batteryStart: Int?,
        val batteryEnd: Int?,
        val engineVersion: Int,
        val timeZone: String,
        val epochs: List<EpochRow>,
        val events: List<EventRow>,
    )

    /**
     * Reads a night export. Columns are found by name, so files from v0.2 (no `noise`
     * column) and v0.3 both work. Throws [IllegalArgumentException] for a file that is
     * not a night export or has no epochs. Epoch and event rows get night id 0.
     */
    fun parse(text: String): Parsed {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
        // v0.2 files were written while the app was still called LyzLarm.
        val first = lines.firstOrNull { it.isNotBlank() }?.trim()
        require(first == "# Anatole night export" || first == "# LyzLarm night export") { "Not an Anatole night export" }
        val meta = HashMap<String, String>()
        var header: List<String>? = null
        val epochs = ArrayList<EpochRow>()
        val events = ArrayList<EventRow>()
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                val body = line.removePrefix("#").trim()
                val comma = body.indexOf(',')
                if (comma > 0) meta[body.substring(0, comma)] = body.substring(comma + 1)
                continue
            }
            val fields = split(line)
            if (header == null) {
                header = fields
                require("record" in fields && "time_ms" in fields) { "Missing the record and time_ms columns" }
                continue
            }
            val cols = header
            fun col(name: String): String? = cols.indexOf(name).takeIf { it >= 0 }?.let(fields::getOrNull)?.takeIf { it.isNotEmpty() }
            val time = col("time_ms")?.toLongOrNull() ?: continue
            when (col("record")) {
                "epoch" -> epochs += EpochRow(
                    nightId = 0,
                    time = time,
                    movement = col("movement")?.toFloatOrNull() ?: continue,
                    noise = col("noise")?.toFloatOrNull(),
                )
                "event" -> events += EventRow(nightId = 0, time = time, type = col("type") ?: continue, detail = col("detail"))
                else -> Unit // raw rows are not imported
            }
        }
        require(header != null) { "No data table" }
        require(epochs.isNotEmpty()) { "The night has no movement data" }
        epochs.sortBy { it.time }
        val distinctEpochs = epochs.distinctBy { it.time }
        fun ms(key: String): Long? = meta[key]?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        val start = ms("start") ?: distinctEpochs.first().time
        val end = ms("end") ?: (distinctEpochs.last().time + SleepEngine.EPOCH_MS)
        val zone = meta["time_zone"]?.takeIf { z -> z.isNotBlank() && runCatching { ZoneId.of(z) }.isSuccess } ?: ZoneId.systemDefault().id
        return Parsed(
            startTime = start,
            endTime = maxOf(end, start),
            wakeTime = ms("wake_time"),
            wakeReason = meta["wake_reason"]?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
            batteryStart = meta["battery_start"]?.toIntOrNull(),
            batteryEnd = meta["battery_end"]?.toIntOrNull(),
            engineVersion = meta["engine_version"]?.toIntOrNull() ?: 1,
            timeZone = zone,
            epochs = distinctEpochs,
            events = events.sortedBy { it.time },
        )
    }

    /** Splits one CSV line (RFC 4180 quotes). */
    fun split(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
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
