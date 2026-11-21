package se.lyzio.lyzlarm.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
