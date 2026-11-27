package se.lyzio.anatole.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.lyzio.anatole.engine.SleepEngine.WakeReason

/**
 * The owner's first three tracked nights (Nov 2026; movement only, no dates). Each ended
 * with the owner awake: A at the 07:00 alarm after waking around 06:43, B and C stopped by hand.
 * A 30-minute window ending when the night ended must find the waking, not ring at the end.
 */
class RecordedNightsTest {
    private val a = Nights.recorded("night-a.csv")
    private val b = Nights.recorded("night-b.csv")
    private val c = Nights.recorded("night-c.csv")

    private fun end(points: List<Point>) = points.last().timeMs + SleepEngine.EPOCH_MS

    @Test fun nightsLoad() {
        assertEquals(1234, a.size)
        assertEquals(1062, b.size)
        assertEquals(1048, c.size)
    }

    @Test fun smartWakeOnNightA() {
        // Window 06:30–07:00 (night starts 20:43:30, so 07:00 is 37 020 s in).
        assertEquals(36_150_000L to WakeReason.LIGHT_SLEEP, Nights.replay(a, end(a), 30, Sensitivity.BALANCED))
        assertEquals(36_210_000L to WakeReason.LIGHT_SLEEP, Nights.replay(a, end(a), 30, Sensitivity.CAUTIOUS))
    }

    @Test fun smartWakeOnNightsBAndC() {
        assertEquals(31_230_000L, Nights.replay(b, end(b), 30, Sensitivity.BALANCED)?.first)
        assertEquals(31_410_000L, Nights.replay(c, end(c), 30, Sensitivity.BALANCED)?.first)
        // Cautious finds nothing on night C before the end: rings at the window end.
        assertNull(Nights.replay(c, end(c), 30, Sensitivity.CAUTIOUS))
    }

    @Test fun deepSleepHoursDoNotTrigger() {
        // Night B 01:10–01:40 local is 19 260–21 060 s in: no movement, so the window would end.
        val windowEnd = 21_060_000L
        for (s in Sensitivity.entries) assertNull(Nights.replay(b, windowEnd, 30, s))
    }

    @Test fun onsetAndTimeAsleep() {
        val ra = NightAnalysis.analyze(a, 0L, end(a))
        assertEquals(780_000L, ra.onsetMs)
        assertEquals(780_000L, ra.fellAsleepMs)
        assertEquals(588.5, ra.asleepMs / 60_000.0, 0.01)
        assertEquals(end(a), ra.inBedMs)
        assertEquals(1_110_000L, NightAnalysis.analyze(b, 0L, end(b)).onsetMs)
        assertEquals(1_470_000L, NightAnalysis.analyze(c, 0L, end(c)).onsetMs)
    }

    @Test fun stagesLookPlausible() {
        for (night in listOf(a, b, c)) {
            val r = NightAnalysis.analyze(night, 0L, end(night))
            val total = r.stages.size.toDouble()
            val deep = r.stages.count { it.stage == Stage.DEEP } / total
            val light = r.stages.count { it.stage == Stage.LIGHT } / total
            assertTrue("deep share $deep", deep in 0.15..0.6)
            assertTrue("light share $light", light in 0.3..0.85)
            assertEquals(Stage.AWAKE, r.stages.first().stage)
        }
    }
}
