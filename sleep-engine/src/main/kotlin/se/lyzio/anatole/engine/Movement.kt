package se.lyzio.anatole.engine

import kotlin.math.max
import kotlin.math.sqrt

/** One accelerometer reading: wall-clock time in ms, acceleration in m/s². */
data class Sample(val timeMs: Long, val x: Float, val y: Float, val z: Float)

/**
 * One finished 30 s epoch: aligned start time, movement score, samples it was computed from,
 * and [noise], the spread of the acceleration length (used for out-of-bed detection).
 */
data class Epoch(val startMs: Long, val movement: Float, val sampleCount: Int, val noise: Float = 0f)

/**
 * Movement score for one epoch.
 *
 * For each pair of consecutive samples we take the length of the change in the
 * acceleration vector. Gravity is constant while the phone lies still, so it cancels
 * out whatever the phone's orientation; what remains is movement plus sensor noise.
 * Changes below [NOISE] are ignored; the rest is averaged per sample and scaled by 100.
 *
 * Result: about 0 when still, a few points for small twitches, tens to hundreds when
 * turning over. The scale is only used relatively (graph, later the sleep curve).
 */
object MovementScore {
    /** Typical phone accelerometer noise between two samples, in m/s². */
    const val NOISE = 0.03f
    const val SCALE = 100f

    /** [previous] is the last sample of the epoch before, if it directly precedes this one. */
    fun score(samples: List<Sample>, previous: Sample? = null): Float {
        if (samples.isEmpty()) return 0f
        var last = previous ?: samples.first()
        var sum = 0.0
        var pairs = 0
        val start = if (previous == null) 1 else 0
        for (i in start until samples.size) {
            val s = samples[i]
            val dx = s.x - last.x
            val dy = s.y - last.y
            val dz = s.z - last.z
            val change = sqrt(dx * dx + dy * dy + dz * dz)
            sum += max(0f, change - NOISE)
            pairs++
            last = s
        }
        if (pairs == 0) return 0f
        return (sum / pairs * SCALE).toFloat()
    }

    /**
     * Standard deviation of the acceleration vector's length over the epoch, in m/s².
     * When the phone lies still this is the sensor noise plus any tiny shaking of the
     * mattress; an empty bed should be the quietest it gets (see [OutOfBed]).
     */
    fun noise(samples: List<Sample>): Float {
        if (samples.size < 2) return 0f
        var sum = 0.0
        var sumSq = 0.0
        for (s in samples) {
            val x = s.x.toDouble()
            val y = s.y.toDouble()
            val z = s.z.toDouble()
            val m = sqrt(x * x + y * y + z * z)
            sum += m
            sumSq += m * m
        }
        val n = samples.size
        val mean = sum / n
        return sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0)).toFloat()
    }
}

/**
 * Collects a stream of samples into 30 s epochs aligned to wall-clock multiples of
 * [epochMs] (…:00 and …:30). An epoch is emitted once a sample from a later epoch
 * arrives, or on [flush]. Epochs without samples are never emitted: they show up as gaps.
 */
class EpochAccumulator(private val epochMs: Long = SleepEngine.EPOCH_MS) {
    private var windowStart = Long.MIN_VALUE
    private val current = ArrayList<Sample>(1024)
    private var previous: Sample? = null
    private var lastSampleTime = Long.MIN_VALUE

    /** Adds one sample; returns the epoch it completed, if any. Out-of-order samples are dropped. */
    fun add(sample: Sample): Epoch? {
        if (sample.timeMs < lastSampleTime) return null
        lastSampleTime = sample.timeMs
        val start = align(sample.timeMs)
        var finished: Epoch? = null
        if (start != windowStart) {
            finished = finishWindow()
            // Only carry the last sample over if the new epoch directly follows.
            if (previous != null && start != windowStart + epochMs) previous = null
            windowStart = start
        }
        current.add(sample)
        return finished
    }

    /** Emits the epoch in progress (used when tracking stops). */
    fun flush(): Epoch? {
        val e = finishWindow()
        windowStart = Long.MIN_VALUE
        previous = null
        return e
    }

    private fun finishWindow(): Epoch? {
        if (current.isEmpty()) return null
        val epoch = Epoch(windowStart, MovementScore.score(current, previous), current.size, MovementScore.noise(current))
        previous = current.last()
        current.clear()
        return epoch
    }

    fun align(timeMs: Long): Long = Math.floorDiv(timeMs, epochMs) * epochMs
}
