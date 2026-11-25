package se.lyzio.lyzlarm.tracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import se.lyzio.lyzlarm.BuildConfig
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.data.Night
import se.lyzio.lyzlarm.engine.Gap
import se.lyzio.lyzlarm.engine.Gaps
import se.lyzio.lyzlarm.engine.NightAnalysis
import se.lyzio.lyzlarm.engine.NightResult
import se.lyzio.lyzlarm.data.EventType
import java.io.File

/**
 * A night with its gaps worked out, for lists and the morning screen. [result] holds time
 * asleep, time in bed, onset, out-of-bed periods and the stage estimate (v0.3); computed
 * when shown, not stored.
 */
data class NightSummary(val night: Night, val gaps: List<Gap>, val trackedMs: Long, val result: NightResult? = null) {
    val endOrNow: Long get() = night.endTime ?: System.currentTimeMillis()
}

object NightStats {
    suspend fun summary(context: Context, night: Night): NightSummary {
        val dao = context.app.database.nightDao()
        val epochs = dao.epochs(night.id)
        val end = night.endTime ?: System.currentTimeMillis()
        val firstRing = dao.firstEvent(night.id, EventType.RANG)?.time
        val result = NightAnalysis.analyze(
            points = epochs.map { it.toPoint() },
            startMs = night.startTime,
            endMs = end,
            awakeFromMs = firstRing,
            ref = emptyBedReference(context),
        )
        return summary(night, epochs.map { it.time }).copy(result = result)
    }

    fun summary(night: Night, epochTimes: List<Long>): NightSummary {
        val end = night.endTime ?: System.currentTimeMillis()
        val gaps = Gaps.find(epochTimes, night.startTime, end)
        return NightSummary(night, gaps, Gaps.trackedMs(night.startTime, end, gaps))
    }

    suspend fun epochs(context: Context, nightId: Long): List<EpochRow> =
        context.app.database.nightDao().epochs(nightId)
}

/** Night export as CSV: share sheet, or save to a file the user picks (SAF). */
object NightExport {
    suspend fun write(context: Context, night: Night, out: Appendable) {
        val dao = context.app.database.nightDao()
        NightCsv.write(
            out,
            night,
            epochs = dao.epochs(night.id),
            events = dao.events(night.id),
            raw = dao.rawSamples(night.id),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    /** Writes the CSV to the cache and returns a share-sheet intent for it. */
    suspend fun shareIntent(context: Context, night: Night): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
        val file = File(dir, NightCsv.fileName(night))
        file.bufferedWriter().use { write(context, night, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Export night")
    }

    /** Writes the CSV to a document the user created with the system file picker. */
    suspend fun saveTo(context: Context, night: Night, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { write(context, night, it) }
    }
}
