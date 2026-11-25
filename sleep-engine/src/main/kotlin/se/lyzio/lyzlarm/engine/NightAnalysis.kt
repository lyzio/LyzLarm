package se.lyzio.lyzlarm.engine

/** Estimated sleep stage of one epoch. */
enum class Stage { AWAKE, LIGHT, DEEP }

/** One epoch's estimated stage. */
data class StagePoint(val timeMs: Long, val stage: Stage)

/** Everything the Morning screen and Past nights show, computed when shown (not stored). */
data class NightResult(
    /** When sleep started, or null if no settled stretch was found. */
    val onsetMs: Long?,
    /** Onset minus tracking start. */
    val fellAsleepMs: Long?,
    /** Light + deep epochs × 30 s. Gaps and out-of-bed time are not counted. */
    val asleepMs: Long,
    /** Start to end, minus out-of-bed time. */
    val inBedMs: Long,
    val outOfBed: List<Period>,
    /** False if an empty bed reference exists but tonight could not be told apart from it. */
    val outOfBedReliable: Boolean,
    val stages: List<StagePoint>,
)

/**
 * Time asleep, time to fall asleep and an estimated stage curve from the movement alone.
 *
 * - **Onset:** start of the first 10 minutes with at most one restless epoch and no strong one.
 * - **Awake:** before onset, out of bed, from the first ring on, and any 5 minutes with
 *   6 or more restless epochs.
 * - **Deep:** no restless epoch within 10 minutes either side. Otherwise **light**.
 *
 * The stage curve is an estimate (owner decision 25 Nov 2026: shown, labelled "Estimate").
 */
object NightAnalysis {
    const val ONSET_EPOCHS = 20
    const val ONSET_MAX_RESTLESS = 1
    const val AWAKE_HALF_BEFORE = 4
    const val AWAKE_HALF_AFTER = 5
    const val AWAKE_RESTLESS = 6
    const val DEEP_HALF = 20

    fun analyze(
        points: List<Point>,
        startMs: Long,
        endMs: Long,
        /** First ring of the night: everything from then on counts as awake. */
        awakeFromMs: Long? = null,
        ref: EmptyBedReference? = null,
    ): NightResult {
        val sorted = points.sortedBy { it.timeMs }.filter { it.timeMs < endMs }
        val outOfBed = OutOfBed.periods(sorted, ref, endMs)
        val reliable = ref == null || OutOfBed.reliable(sorted, ref)
        val outMs = outOfBed.sumOf { it.lengthMs }
        val onsetIndex = onsetIndex(sorted, outOfBed)
        val onset = onsetIndex?.let { sorted[it].timeMs }
        val stages = stages(sorted, onsetIndex, outOfBed, awakeFromMs)
        val asleep = stages.count { it.stage != Stage.AWAKE } * SleepEngine.EPOCH_MS
        return NightResult(
            onsetMs = onset,
            fellAsleepMs = onset?.let { (it - startMs).coerceAtLeast(0L) },
            asleepMs = asleep,
            inBedMs = (endMs - startMs - outMs).coerceAtLeast(0L),
            outOfBed = outOfBed,
            outOfBedReliable = reliable,
            stages = stages,
        )
    }

    /** Index of the onset epoch, or null. Used live too, for the onset estimate saved per night. */
    fun onsetIndex(points: List<Point>, outOfBed: List<Period> = emptyList()): Int? {
        for (i in 0..points.size - ONSET_EPOCHS) {
            val run = points.subList(i, i + ONSET_EPOCHS)
            if (run.last().timeMs - run.first().timeMs != (ONSET_EPOCHS - 1) * SleepEngine.EPOCH_MS) continue
            if (run.any { p -> outOfBed.any { p.timeMs >= it.startMs && p.timeMs < it.endMs } }) continue
            if (run.count { WakeRules.isRestless(it.movement) } > ONSET_MAX_RESTLESS) continue
            if (run.any { it.movement >= WakeRules.STRONG }) continue
            return i
        }
        return null
    }

    fun stages(points: List<Point>, onsetIndex: Int?, outOfBed: List<Period>, awakeFromMs: Long?): List<StagePoint> {
        val restless = BooleanArray(points.size) { WakeRules.isRestless(points[it].movement) }
        // Prefix sums for fast window counts.
        val sum = IntArray(points.size + 1)
        for (i in points.indices) sum[i + 1] = sum[i] + if (restless[i]) 1 else 0
        fun count(from: Int, to: Int): Int = sum[to.coerceIn(0, points.size)] - sum[from.coerceIn(0, points.size)]

        return points.mapIndexed { i, p ->
            val stage = when {
                onsetIndex == null || i < onsetIndex -> Stage.AWAKE
                awakeFromMs != null && p.timeMs >= awakeFromMs -> Stage.AWAKE
                outOfBed.any { p.timeMs >= it.startMs && p.timeMs < it.endMs } -> Stage.AWAKE
                count(i - AWAKE_HALF_BEFORE, i + AWAKE_HALF_AFTER + 1) >= AWAKE_RESTLESS -> Stage.AWAKE
                count(i - DEEP_HALF, i + DEEP_HALF + 1) == 0 -> Stage.DEEP
                else -> Stage.LIGHT
            }
            StagePoint(p.timeMs, stage)
        }
    }

    /** Stage curve in blocks (5 min by default): the most common stage per block, awake winning ties. */
    fun blocks(stages: List<StagePoint>, blockMs: Long = Bars.BAR_MS): List<StagePoint> =
        stages.groupBy { Math.floorDiv(it.timeMs, blockMs) * blockMs }
            .map { (start, list) ->
                val counts = Stage.entries.associateWith { s -> list.count { it.stage == s } }
                StagePoint(start, Stage.entries.maxBy { s -> counts.getValue(s) * 10 + (2 - s.ordinal) })
            }
            .sortedBy { it.timeMs }
}
