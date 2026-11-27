package se.lyzio.anatole.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.lyzio.anatole.tracking.AlarmFollow.Candidate

class AlarmFollowTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L
    private val max = 16 * hour

    private fun target(current: Long?, vararg c: Candidate, rang: Boolean = false, snoozed: Boolean = false) =
        AlarmFollow.target(current, rang, snoozed, c.toList(), now, max)

    @Test fun keepsTheNextAlarm() {
        assertEquals(1L, target(1, Candidate(1, now + 7 * hour), Candidate(2, now + 9 * hour)))
    }

    @Test fun currentTurnedOffMovesToTheNextEnabled() {
        // Alarm 1 is no longer a candidate (off or deleted).
        assertEquals(2L, target(1, Candidate(2, now + 9 * hour)))
    }

    @Test fun earlierAlarmAddedOrTurnedOnIsFollowed() {
        assertEquals(3L, target(1, Candidate(1, now + 7 * hour), Candidate(3, now + 6 * hour)))
    }

    @Test fun editedLaterIsDroppedForAnEarlierOne() {
        // Alarm 1 edited from 06:00 to 08:00, alarm 2 rings at 07:00.
        assertEquals(2L, target(1, Candidate(1, now + 8 * hour), Candidate(2, now + 7 * hour)))
    }

    @Test fun noAlarmsLeftMeansNone() {
        assertNull(target(1))
    }

    @Test fun alarmTooFarAheadIsIgnored() {
        assertNull(target(1, Candidate(2, now + 17 * hour)))
        assertEquals(2L, target(null, Candidate(2, now + 16 * hour)))
    }

    @Test fun alarmInThePastIsIgnored() {
        assertEquals(2L, target(1, Candidate(1, now), Candidate(2, now + hour)))
    }

    @Test fun nightWithoutAlarmPicksUpANewOne() {
        assertEquals(4L, target(null, Candidate(4, now + 5 * hour)))
    }

    @Test fun staysWithTheAlarmOnceItRang() {
        assertEquals(1L, target(1, Candidate(2, now + hour), rang = true))
        assertEquals(1L, target(1, rang = true))
    }

    @Test fun staysWithASnoozedAlarm() {
        assertEquals(1L, target(1, Candidate(1, now + 9 * 60_000L), Candidate(2, now + 60_000L), snoozed = true))
    }

    @Test fun tieKeepsCurrentThenLowestId() {
        assertEquals(5L, target(5, Candidate(2, now + hour), Candidate(5, now + hour)))
        assertEquals(2L, target(9, Candidate(5, now + hour), Candidate(2, now + hour)))
    }
}
