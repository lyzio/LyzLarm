package se.lyzio.anatole.engine

/**
 * Pure-Kotlin sleep engine (no Android imports, so it can be reused for iOS later):
 * movement scores, tracking gaps, bedtime learning, the smart-wake decision ([WakeRules]),
 * out-of-bed detection ([OutOfBed]) and the night analysis ([NightAnalysis]).
 */
object SleepEngine {
    /** Stored per night, so later versions know which engine produced the data. v3 = v0.3. */
    const val VERSION = 3

    /** Length of one epoch, the unit the movement score is computed for. */
    const val EPOCH_MS = 30_000L

    /** Why the alarm rang (or the night ended). Stored per night. */
    enum class WakeReason {
        /** Light sleep detected inside the wake window. */
        LIGHT_SLEEP,
        /** Continuous movement inside the window: already awake. */
        ALREADY_AWAKE,
        /** No light sleep found: rang at the end of the window (latest wake time). */
        WINDOW_END,
        /** Alarm without a window: rang at its set time. */
        FIXED,
        /** Tracking stopped by hand before the alarm. */
        MANUAL;

        /** Lower-case name as stored in exports and events. */
        val key: String get() = name.lowercase()

        companion object {
            fun parse(value: String?): WakeReason? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
