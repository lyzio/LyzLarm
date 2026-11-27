package se.lyzio.anatole.engine

/** How readily smart wake treats movement as light sleep. Setting in Alarm defaults. */
enum class Sensitivity {
    /** 3 of the last 4 epochs restless. */
    CAUTIOUS,
    /** 2 of the last 4 epochs restless (default). */
    BALANCED,
    /** Like Balanced, or one strong reading. */
    EAGER;

    companion object {
        fun parse(value: String?): Sensitivity = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
    }
}

/** One epoch as the engine sees it: aligned start time and movement score. */
data class Point(val timeMs: Long, val movement: Float, val noise: Float? = null)

/**
 * The smart-wake decision. Inside the wake window the tracking service calls [decide]
 * after every epoch; on a non-null answer the alarm rings at once. The alarm at the end
 * of the window is scheduled separately and is the fallback: nothing here can delay it.
 *
 * Thresholds were set from the owner's first nights (Nov 2026): a still phone scores 0,
 * about 90% of epochs are below 1, turning over scores 20–150.
 */
object WakeRules {
    /** Movement above this is "restless" (a twitch or more). */
    const val RESTLESS = 2f

    /** Movement at or above this is a strong reading (turning over). */
    const val STRONG = 20f

    /** Epochs looked at for light sleep (2 min). */
    const val LIGHT_EPOCHS = 4

    /** Epochs looked at for "already awake" (5 min) and how many must be restless. */
    const val AWAKE_EPOCHS = 10
    const val AWAKE_RESTLESS = 7

    /** Nights tracked before the chosen sensitivity is used; until then Cautious. */
    const val LEARNING_NIGHTS = 7

    fun isRestless(movement: Float) = movement > RESTLESS

    /** Sensitivity actually used: Cautious while learning (first [LEARNING_NIGHTS] tracked nights). */
    fun effective(chosen: Sensitivity, trackedNights: Int): Sensitivity =
        if (trackedNights < LEARNING_NIGHTS) Sensitivity.CAUTIOUS else chosen

    /** Night number for "Learning your sleep · night 3 of 7", or null when learning is done. */
    fun learningNight(trackedNights: Int): Int? =
        if (trackedNights < LEARNING_NIGHTS) trackedNights + 1 else null

    /**
     * Decides from the latest epochs (oldest first) whether to ring now.
     * Only an unbroken run of epochs counts: after a tracking gap there is nothing to decide on.
     * Returns [SleepEngine.WakeReason.ALREADY_AWAKE], [SleepEngine.WakeReason.LIGHT_SLEEP] or null.
     */
    fun decide(recent: List<Point>, sensitivity: Sensitivity): SleepEngine.WakeReason? {
        val awake = lastRun(recent, AWAKE_EPOCHS)
        if (awake != null && awake.count { isRestless(it.movement) } >= AWAKE_RESTLESS) {
            return SleepEngine.WakeReason.ALREADY_AWAKE
        }
        val light = lastRun(recent, LIGHT_EPOCHS) ?: return null
        val restless = light.count { isRestless(it.movement) }
        val hit = when (sensitivity) {
            Sensitivity.CAUTIOUS -> restless >= 3
            Sensitivity.BALANCED -> restless >= 2
            Sensitivity.EAGER -> restless >= 2 || light.last().movement >= STRONG
        }
        return if (hit) SleepEngine.WakeReason.LIGHT_SLEEP else null
    }

    /** The last [n] epochs if they follow each other without a gap, else null. */
    fun lastRun(points: List<Point>, n: Int, epochMs: Long = SleepEngine.EPOCH_MS): List<Point>? {
        if (points.size < n) return null
        val run = points.subList(points.size - n, points.size)
        for (i in 1 until run.size) if (run[i].timeMs - run[i - 1].timeMs != epochMs) return null
        return run
    }

    /** Start of the wake window for an alarm whose latest wake time is [endMs]. */
    fun windowStart(endMs: Long, windowMinutes: Int): Long = endMs - windowMinutes * 60_000L
}
