package se.lyzio.anatole.tracking

/**
 * Which alarm a night tracks towards (v0.4 fix, 26 Nov 2026). Pure logic, no Android types,
 * so it is unit-tested on the JVM (AlarmFollowTest).
 *
 * Until an alarm has rung tonight, the night follows the next alarm that will ring: turning
 * alarms off or on, deleting, adding or editing one applies at once to smart wake, the
 * Bedtime screen and early stop. Once one has rung (or is snoozed) the night stays with it,
 * so snooze, the stay-awake check and the Morning screen keep the alarm that woke you.
 */
object AlarmFollow {

    /** An alarm that will ring at [ringAt] (its next ring, or the end of its snooze). */
    data class Candidate(val alarmId: Long, val ringAt: Long)

    /**
     * The alarm id the night should track now, or null if no alarm rings within [maxAheadMs].
     *
     * [current]: the night's alarm today. [currentRang]: an alarm has rung tonight.
     * [currentSnoozed]: the current alarm is snoozed. [candidates]: enabled or snoozed alarms.
     */
    fun target(
        current: Long?,
        currentRang: Boolean,
        currentSnoozed: Boolean,
        candidates: List<Candidate>,
        now: Long,
        maxAheadMs: Long,
    ): Long? {
        if (current != null && (currentRang || currentSnoozed)) return current
        return candidates
            .filter { it.ringAt > now && it.ringAt - now <= maxAheadMs }
            // Same time: keep the current one, else the lower id, so the choice is stable.
            .minWithOrNull(compareBy<Candidate>({ it.ringAt }, { if (it.alarmId == current) 0 else 1 }, { it.alarmId }))
            ?.alarmId
    }
}
