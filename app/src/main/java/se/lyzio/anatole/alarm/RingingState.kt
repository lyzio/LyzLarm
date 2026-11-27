package se.lyzio.anatole.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.lyzio.anatole.engine.SleepEngine

/** What is ringing right now; shared between the service and the ringing screen. */
data class Ringing(
    val alarmId: Long,
    val kind: AlarmScheduler.Kind,
    val label: String,
    val ringTimeMillis: Long,
    val snoozeMinutes: Int,
    /** 0..1 — how far the gradual volume has risen. */
    val volume: Float,
    val vibratingOnly: Boolean,
    /** Why it rang; null for the test alarm. */
    val reason: SleepEngine.WakeReason? = null,
    /** Snooze lock on: snooze and "I'm up" need a solved challenge. */
    val snoozeLock: Boolean = false,
    /** Allowed challenge types (Challenges bits) and math difficulty, for the lock. */
    val challengeTypes: Int = 0,
    val mathDifficulty: Int = 1,
)

object RingingState {
    private val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current

    internal fun set(value: Ringing?) {
        _current.value = value
    }

    internal fun update(transform: (Ringing) -> Ringing) {
        _current.value?.let { _current.value = transform(it) }
    }
}
