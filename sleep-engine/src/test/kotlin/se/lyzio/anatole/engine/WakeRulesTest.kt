package se.lyzio.anatole.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.lyzio.anatole.engine.Nights.E
import se.lyzio.anatole.engine.Nights.of
import se.lyzio.anatole.engine.SleepEngine.WakeReason

class WakeRulesTest {
    private val light = WakeReason.LIGHT_SLEEP

    @Test fun stillNeverTriggers() {
        val night = Nights.still(40)
        for (s in Sensitivity.entries) assertNull(WakeRules.decide(night, s))
    }

    @Test fun balancedNeedsTwoOfFour() {
        assertNull(WakeRules.decide(of(0f, 0f, 0f, 5f), Sensitivity.BALANCED))
        assertEquals(light, WakeRules.decide(of(5f, 0f, 0f, 5f), Sensitivity.BALANCED))
        // Exactly at the restless level does not count.
        assertNull(WakeRules.decide(of(2f, 2f, 2f, 2f), Sensitivity.BALANCED))
    }

    @Test fun cautiousNeedsThreeOfFour() {
        assertNull(WakeRules.decide(of(5f, 0f, 0f, 5f), Sensitivity.CAUTIOUS))
        assertEquals(light, WakeRules.decide(of(5f, 0f, 5f, 5f), Sensitivity.CAUTIOUS))
    }

    @Test fun eagerTakesOneStrongReading() {
        assertEquals(light, WakeRules.decide(of(0f, 0f, 0f, 25f), Sensitivity.EAGER))
        assertNull(WakeRules.decide(of(0f, 0f, 0f, 25f), Sensitivity.BALANCED))
        assertNull(WakeRules.decide(of(0f, 0f, 0f, 10f), Sensitivity.EAGER))
        assertEquals(light, WakeRules.decide(of(5f, 0f, 0f, 5f), Sensitivity.EAGER))
    }

    @Test fun continuousMovementIsAlreadyAwake() {
        val moving = of(5f, 6f, 9f, 4f, 0f, 7f, 8f, 5f, 0f, 3f)
        assertEquals(WakeReason.ALREADY_AWAKE, WakeRules.decide(moving, Sensitivity.CAUTIOUS))
        val notQuite = of(5f, 6f, 0f, 4f, 0f, 7f, 0f, 5f, 0f, 3f)
        assertEquals(light, WakeRules.decide(notQuite, Sensitivity.BALANCED))
    }

    @Test fun aGapMeansNoDecision() {
        val points = listOf(Point(0, 5f), Point(E, 5f), Point(5 * E, 5f), Point(6 * E, 5f))
        assertNull(WakeRules.decide(points, Sensitivity.EAGER))
        assertNull(WakeRules.decide(emptyList(), Sensitivity.EAGER))
    }

    @Test fun learningUsesCautiousForSevenNights() {
        assertEquals(Sensitivity.CAUTIOUS, WakeRules.effective(Sensitivity.EAGER, 0))
        assertEquals(Sensitivity.CAUTIOUS, WakeRules.effective(Sensitivity.BALANCED, 6))
        assertEquals(Sensitivity.BALANCED, WakeRules.effective(Sensitivity.BALANCED, 7))
        assertEquals(1, WakeRules.learningNight(0))
        assertEquals(7, WakeRules.learningNight(6))
        assertNull(WakeRules.learningNight(7))
    }

    @Test fun windowStart() {
        assertEquals(1_000_000L - 30 * 60_000L, WakeRules.windowStart(1_000_000L, 30))
        assertEquals(1_000_000L, WakeRules.windowStart(1_000_000L, 0))
    }

    @Test fun onlyTriggersInsideTheWindow() {
        // Restless at minute 10, still afterwards; window is minutes 30–60.
        val night = Nights.join(Nights.still(20), of(5f, 5f, 5f, 5f), Nights.still(100))
        assertNull(Nights.replay(night, endMs = 60 * 60_000L, windowMinutes = 30, sensitivity = Sensitivity.EAGER))
        val later = Nights.join(Nights.still(80), of(5f, 5f), Nights.still(40))
        val hit = Nights.replay(later, endMs = 60 * 60_000L, windowMinutes = 30, sensitivity = Sensitivity.BALANCED)
        assertEquals(81 * E to light, hit)
    }

    @Test fun parseNames() {
        assertEquals(WakeReason.WINDOW_END, WakeReason.parse("window_end"))
        assertEquals("already_awake", WakeReason.ALREADY_AWAKE.key)
        assertNull(WakeReason.parse(null))
        assertEquals(Sensitivity.BALANCED, Sensitivity.parse("nonsense"))
        assertEquals(Sensitivity.EAGER, Sensitivity.parse("eager"))
    }
}
