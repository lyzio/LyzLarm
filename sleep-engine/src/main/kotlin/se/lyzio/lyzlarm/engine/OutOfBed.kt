package se.lyzio.lyzlarm.engine

/**
 * The empty bed test's result: how quiet the phone is where it lies at night with nobody
 * in the bed. [noiseP50] and [noiseP90] are percentiles of the per-epoch noise (m/s²).
 */
data class EmptyBedReference(val noiseP50: Float, val noiseP90: Float, val movementMax: Float, val epochs: Int)

/** A stretch of the night spent out of bed. */
data class Period(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * Out-of-bed detection, compared against the empty bed test.
 *
 * Owner's nights (Nov 2026) showed that a sleeper lying still can look like plain sensor
 * noise, and that still stretches in sleep last 30–70 min. So detection is **guarded**
 * (decision 25 Nov 2026): it is only used when tonight's still periods are clearly noisier
 * than the empty bed ([reliable]). Otherwise nothing is counted as out of bed, and the
 * screens say detection was not reliable tonight.
 *
 * When reliable, out of bed = a restless movement (getting up), then at least
 * [MIN_EPOCHS] epochs that look like the empty bed, until the next movement (getting back in).
 */
object OutOfBed {
    /** Movement below this counts as "dead still". */
    const val STILL = 0.05f

    /** Tonight's still noise must be this many times the empty bed's 90th percentile. */
    const val SEPARATION = 1.3f

    /** An epoch "looks empty" up to this many times the empty bed's 90th percentile. */
    const val EMPTY_MARGIN = 1.1f

    /** Shortest out-of-bed stretch: 2 minutes. */
    const val MIN_EPOCHS = 4

    /** The getting-up movement must come at most this many epochs before the stillness. */
    const val LEAD_EPOCHS = 2

    /** Still epochs needed before tonight's noise level is known. */
    const val MIN_STILL_FOR_LEVEL = 20

    /** Empty bed test: epochs skipped at the start (putting the phone down, leaving) and end. */
    const val SKIP_START = 2
    const val SKIP_END = 1

    /** Builds the reference from the empty bed test's epochs, or null if too few. */
    fun reference(test: List<Point>): EmptyBedReference? {
        val used = test.drop(SKIP_START).dropLast(SKIP_END).filter { it.noise != null }
        if (used.size < 8) return null
        val noise = used.map { it.noise!! }.sorted()
        return EmptyBedReference(
            noiseP50 = percentile(noise, 0.5),
            noiseP90 = percentile(noise, 0.9),
            movementMax = used.maxOf { it.movement },
            epochs = used.size,
        )
    }

    /** Median noise of tonight's dead-still epochs, or null until there are enough of them. */
    fun stillNoiseLevel(points: List<Point>): Float? {
        val still = points.filter { it.movement < STILL && it.noise != null }.map { it.noise!! }
        if (still.size < MIN_STILL_FOR_LEVEL) return null
        return percentile(still.sorted(), 0.5)
    }

    /** True if tonight's data can tell the sleeper from the empty bed. */
    fun reliable(points: List<Point>, ref: EmptyBedReference?): Boolean {
        if (ref == null) return false
        val level = stillNoiseLevel(points) ?: return false
        return level > ref.noiseP90 * SEPARATION
    }

    fun looksEmpty(p: Point, ref: EmptyBedReference): Boolean =
        p.movement < STILL && p.noise != null && p.noise <= ref.noiseP90 * EMPTY_MARGIN

    /** Out-of-bed periods in a night (epochs oldest first). Empty unless [reliable]. */
    fun periods(points: List<Point>, ref: EmptyBedReference?, nightEndMs: Long? = null): List<Period> {
        if (ref == null || !reliable(points, ref)) return emptyList()
        val out = ArrayList<Period>()
        var i = 0
        while (i < points.size) {
            if (!looksEmpty(points[i], ref)) { i++; continue }
            var j = i
            while (j < points.size && looksEmpty(points[j], ref) &&
                (j == i || points[j].timeMs - points[j - 1].timeMs == SleepEngine.EPOCH_MS)
            ) j++
            val run = j - i
            val gotUp = (1..LEAD_EPOCHS).any { k -> i - k >= 0 && WakeRules.isRestless(points[i - k].movement) }
            if (run >= MIN_EPOCHS && gotUp) {
                val end = if (j < points.size) points[j].timeMs else (nightEndMs ?: (points[j - 1].timeMs + SleepEngine.EPOCH_MS))
                out.add(Period(points[i].timeMs, end))
            }
            i = j
        }
        return out
    }

    /**
     * Stay-awake check, 5 minutes after the challenge was solved: is the user back in bed
     * and lying still for most of that time? [points] are the epochs since the challenge.
     * Without a usable reference: the phone was still the whole time (it was left in bed,
     * or the user lies still holding it), decision 25 Nov 2026.
     */
    fun backInBed(points: List<Point>, ref: EmptyBedReference?, nightPoints: List<Point>): Boolean {
        if (points.size < STAY_AWAKE_MIN_EPOCHS) return false
        return if (ref != null && reliable(nightPoints, ref)) {
            val inBedStill = points.count { !looksEmpty(it, ref) && !WakeRules.isRestless(it.movement) }
            inBedStill >= points.size * 0.6
        } else {
            points.all { !WakeRules.isRestless(it.movement) }
        }
    }

    /** Stay-awake check length and epochs needed to decide (5 min, at least 4 of them recorded). */
    const val STAY_AWAKE_MS = 5 * 60_000L
    const val STAY_AWAKE_MIN_EPOCHS = 8

    private fun percentile(sorted: List<Float>, q: Double): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((sorted.size - 1) * q)
        val lo = idx.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val f = (idx - lo).toFloat()
        return sorted[lo] * (1 - f) + sorted[hi] * f
    }
}

/**
 * Follows the night epoch by epoch and reports when the user leaves and returns to bed,
 * for the `out_of_bed` / `back_in_bed` events. Uses the same rules as [OutOfBed.periods].
 */
class OutOfBedTracker(private val ref: EmptyBedReference?) {
    enum class Change { OUT_OF_BED, BACK_IN_BED }

    private val points = ArrayList<Point>()
    private var out = false
    /** Length of the current run of empty-looking epochs that started right after getting up; -1 = no such run. */
    private var emptyRun = -1
    private var runStart = 0L
    private var lastRestlessIndex = -100

    val isOut: Boolean get() = out

    /** Adds the next epoch; returns a change and the time it happened, if any. */
    fun add(p: Point): Pair<Change, Long>? {
        val broken = points.isNotEmpty() && p.timeMs - points.last().timeMs != SleepEngine.EPOCH_MS
        if (broken) emptyRun = -1
        points.add(p)
        val index = points.lastIndex
        val r = ref ?: return null
        val empty = OutOfBed.looksEmpty(p, r)
        if (out) {
            if (empty) return null
            out = false
            emptyRun = -1
            if (WakeRules.isRestless(p.movement)) lastRestlessIndex = index
            return Change.BACK_IN_BED to p.timeMs
        }
        if (!empty) {
            emptyRun = -1
            if (WakeRules.isRestless(p.movement)) lastRestlessIndex = index
            return null
        }
        if (emptyRun < 0) {
            // A run only counts if it starts right after getting up.
            if (index - lastRestlessIndex > OutOfBed.LEAD_EPOCHS) return null
            emptyRun = 0
            runStart = p.timeMs
        }
        emptyRun++
        if (emptyRun >= OutOfBed.MIN_EPOCHS && OutOfBed.reliable(points, r)) {
            out = true
            emptyRun = -1
            return Change.OUT_OF_BED to runStart
        }
        return null
    }
}
