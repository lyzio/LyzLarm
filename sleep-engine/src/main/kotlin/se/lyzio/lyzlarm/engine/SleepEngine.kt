package se.lyzio.lyzlarm.engine

/**
 * Pure-Kotlin sleep engine: activity scores, sleep curve, wake decision and learning.
 *
 * v0.1 has no tracking, so the only decision is the fallback: ring at the latest wake time.
 * Tracking arrives in v0.2 and the smart wake decision in v0.3.
 */
object SleepEngine {
    const val VERSION = 1

    /** Why an alarm fired. Stored per night from v0.2 on. */
    enum class WakeReason { LIGHT_SLEEP, WINDOW_END, FIXED, MANUAL }

    /** In v0.1 every alarm is a fixed-time alarm. */
    fun decideWithoutTracking(): WakeReason = WakeReason.FIXED
}
