package se.lyzio.anatole.tracking

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import se.lyzio.anatole.app
import se.lyzio.anatole.data.Night
import se.lyzio.anatole.engine.NightAnalysis

/**
 * Settings › Data › Import night (CSV), v0.4: reads night exports from v0.2/v0.3 (and later)
 * so history survives the one-time reinstall for the new package name. Epochs and events
 * are imported; raw sensor rows are ignored. A night whose start is already saved is skipped,
 * so importing the same file twice does nothing.
 */
object NightImport {

    data class Result(val imported: Int, val skipped: Int, val failed: Int) {
        val message: String
            get() = buildList {
                add(if (imported == 1) "1 night imported" else "$imported nights imported")
                if (skipped > 0) add("$skipped already saved")
                if (failed > 0) add(if (failed == 1) "1 file not readable" else "$failed files not readable")
            }.joinToString(" · ")
    }

    enum class Outcome { IMPORTED, SKIPPED }

    suspend fun importAll(context: Context, uris: List<Uri>): Result {
        var imported = 0
        var skipped = 0
        var failed = 0
        for (uri in uris) {
            val outcome = runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("Cannot open $uri")
                import(context, NightCsv.parse(text))
            }.getOrNull()
            when (outcome) {
                Outcome.IMPORTED -> imported++
                Outcome.SKIPPED -> skipped++
                null -> failed++
            }
        }
        if (imported > 0) Planner.refresh(context)
        return Result(imported, skipped, failed)
    }

    suspend fun import(context: Context, parsed: NightCsv.Parsed): Outcome {
        val db = context.app.database
        val dao = db.nightDao()
        return db.withTransaction {
            if (dao.countStartingAt(parsed.startTime) > 0) return@withTransaction Outcome.SKIPPED
            val points = parsed.epochs.map { it.toPoint() }
            val id = dao.insertNight(
                Night(
                    alarmId = null,
                    startTime = parsed.startTime,
                    endTime = parsed.endTime,
                    wakeTime = parsed.wakeTime ?: parsed.endTime,
                    wakeReason = parsed.wakeReason,
                    batteryStart = parsed.batteryStart,
                    batteryEnd = parsed.batteryEnd,
                    engineVersion = parsed.engineVersion,
                    timeZone = parsed.timeZone,
                    onsetEstimate = NightAnalysis.onsetIndex(points)?.let { points[it].timeMs },
                ),
            )
            dao.insertEpochs(parsed.epochs.map { it.copy(nightId = id) })
            dao.insertEvents(parsed.events.map { it.copy(id = 0, nightId = id) })
            Outcome.IMPORTED
        }
    }
}
