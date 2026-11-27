package se.lyzio.lyzlarm.engine

/**
 * Pure-Kotlin sleep engine: movement scores, tracking gaps, bedtime learning, and
 * (from v0.3) the sleep curve and wake decision.
 *
 * v0.2 records movement but makes no smart-wake decision yet: alarms ring at the
 * latest wake time ([WakeReason.FIXED]) or tracking is stopped by hand ([WakeReason.MANUAL]).
 */
object SleepEngine {
    /** Stored per night, so later versions know which engine produced the data. */
    const val VERSION = 2

    /** Length of one epoch, the unit the movement score is computed for. */
    const val EPOCH_MS = 30_000L

    /** Why a night ended. Stored per night. */
    enum class WakeReason { LIGHT_SLEEP, WINDOW_END, FIXED, MANUAL }

    /** In v0.2 every alarm is a fixed-time alarm. */
    fun decideWithoutTracking(): WakeReason = WakeReason.FIXED
}
