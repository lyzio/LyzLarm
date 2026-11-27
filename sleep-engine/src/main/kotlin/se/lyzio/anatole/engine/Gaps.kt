package se.lyzio.anatole.engine

/** A stretch of the night without movement data. */
data class Gap(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * Finds tracking gaps: time between the night's start and end not covered by any epoch.
 * Short holes (under [MIN_GAP_MS]) are normal jitter and not counted.
 */
object Gaps {
    const val MIN_GAP_MS = 2 * 60_000L

    /** Gap between the end of covered time and the next covered moment, or null if too short. */
    fun between(coveredUntilMs: Long, nextStartMs: Long, minGapMs: Long = MIN_GAP_MS): Gap? =
        if (nextStartMs - coveredUntilMs >= minGapMs) Gap(coveredUntilMs, nextStartMs) else null

    /** [epochStarts] in any order; each epoch covers [start, start + epochMs). */
    fun find(
        epochStarts: List<Long>,
        nightStartMs: Long,
        nightEndMs: Long,
        epochMs: Long = SleepEngine.EPOCH_MS,
        minGapMs: Long = MIN_GAP_MS,
    ): List<Gap> {
        val gaps = ArrayList<Gap>()
        var covered = nightStartMs
        for (t in epochStarts.sorted()) {
            if (t >= nightEndMs) break
            between(covered, t, minGapMs)?.let(gaps::add)
            covered = maxOf(covered, t + epochMs)
        }
        between(covered, nightEndMs, minGapMs)?.let(gaps::add)
        return gaps
    }

    /** Night length minus gaps. */
    fun trackedMs(nightStartMs: Long, nightEndMs: Long, gaps: List<Gap>): Long =
        (nightEndMs - nightStartMs - gaps.sumOf { it.lengthMs }).coerceAtLeast(0L)
}

/** Groups epochs into graph bars (5 min by default); each bar shows the peak movement in it. */
object Bars {
    const val BAR_MS = 5 * 60_000L

    data class Bar(val startMs: Long, val movement: Float)

    fun of(epochs: List<Pair<Long, Float>>, barMs: Long = BAR_MS): List<Bar> =
        epochs
            .groupBy { Math.floorDiv(it.first, barMs) * barMs }
            .map { (start, list) -> Bar(start, list.maxOf { it.second }) }
            .sortedBy { it.startMs }
}
