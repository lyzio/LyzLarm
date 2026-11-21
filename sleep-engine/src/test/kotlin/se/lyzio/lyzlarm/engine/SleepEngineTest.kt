package se.lyzio.lyzlarm.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepEngineTest {
    @Test
    fun withoutTrackingTheAlarmIsFixed() {
        assertEquals(SleepEngine.WakeReason.FIXED, SleepEngine.decideWithoutTracking())
    }
}
