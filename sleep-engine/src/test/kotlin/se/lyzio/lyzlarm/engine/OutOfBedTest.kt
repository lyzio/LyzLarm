package se.lyzio.lyzlarm.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.lyzio.lyzlarm.engine.Nights.E

class OutOfBedTest {
    // Empty bed: noise around 0.003. Sleeper lying still: around 0.006.
    private val emptyTest = List(20) { Point(it * E, 0f, 0.0028f + (it % 5) * 0.0001f) }
    private val ref = OutOfBed.reference(emptyTest)!!

    private fun sleep(n: Int) = List(n) { Point(0, 0f, 0.006f) }
    private fun empty(n: Int) = List(n) { Point(0, 0f, 0.003f) }
    private fun move(n: Int = 1) = List(n) { Point(0, 40f, 0.5f) }

    @Test fun referenceSkipsTheEdges() {
        assertEquals(17, ref.epochs)
        assertTrue(ref.noiseP90 in 0.0029f..0.0033f)
        assertNull(OutOfBed.reference(emptyTest.take(9)))
    }

    @Test fun detectsGettingUp() {
        val night = Nights.join(sleep(60), move(), empty(10), move(), sleep(30))
        val periods = OutOfBed.periods(night, ref)
        assertEquals(1, periods.size)
        assertEquals(61 * E, periods[0].startMs)
        assertEquals(71 * E, periods[0].endMs)
        val r = NightAnalysis.analyze(night, 0L, night.size * E, ref = ref)
        assertEquals((night.size - 10) * E, r.inBedMs)
        assertTrue(r.outOfBedReliable)
        assertTrue(r.stages.filter { it.timeMs in 61 * E until 71 * E }.all { it.stage == Stage.AWAKE })
    }

    @Test fun shortOrUnannouncedStillnessIsNotOutOfBed() {
        // Under 2 minutes.
        assertTrue(OutOfBed.periods(Nights.join(sleep(60), move(), empty(3), move(), sleep(30)), ref).isEmpty())
        // No getting-up movement before it.
        assertTrue(OutOfBed.periods(Nights.join(sleep(60), empty(10), move(), sleep(30)), ref).isEmpty())
    }

    @Test fun guardedWhenTheSleeperLooksLikeAnEmptyBed() {
        // Owner's phone: still sleep is as quiet as the empty bed. Nothing may be counted.
        val night = Nights.join(empty(60), move(), empty(10), move(), empty(30))
        assertFalse(OutOfBed.reliable(night, ref))
        assertTrue(OutOfBed.periods(night, ref).isEmpty())
        val r = NightAnalysis.analyze(night, 0L, night.size * E, ref = ref)
        assertFalse(r.outOfBedReliable)
        assertEquals(night.size * E, r.inBedMs)
    }

    @Test fun oldNightsWithoutNoiseAreNotFlagged() {
        val night = Nights.join(Nights.still(60), move(), Nights.still(30))
        val r = NightAnalysis.analyze(night, 0L, night.size * E, ref = ref)
        assertTrue(r.outOfBedReliable)
        assertTrue(r.outOfBed.isEmpty())
    }

    @Test fun noReferenceMeansNoDetection() {
        val night = Nights.join(sleep(60), move(), empty(10), move(), sleep(30))
        assertTrue(OutOfBed.periods(night, null).isEmpty())
        assertTrue(NightAnalysis.analyze(night, 0L, night.size * E).outOfBedReliable)
    }

    @Test fun liveTrackerReportsOutAndBack() {
        val tracker = OutOfBedTracker(ref)
        val night = Nights.join(sleep(60), move(), empty(10), move(), sleep(30))
        val changes = night.mapNotNull { tracker.add(it) }
        assertEquals(
            listOf(OutOfBedTracker.Change.OUT_OF_BED to 61 * E, OutOfBedTracker.Change.BACK_IN_BED to 71 * E),
            changes,
        )
        assertFalse(tracker.isOut)
        assertTrue(Nights.join(empty(60), move(), empty(10)).mapNotNull { OutOfBedTracker(ref).add(it) }.isEmpty())
        assertTrue(night.mapNotNull { OutOfBedTracker(null).add(it) }.isEmpty())
    }

    @Test fun stayAwakeCheck() {
        val nightSoFar = Nights.join(sleep(60), move())
        // Reliable reference: back in bed lying still → ring again; out of bed → fine.
        assertTrue(OutOfBed.backInBed(sleep(10), ref, nightSoFar))
        assertFalse(OutOfBed.backInBed(empty(10), ref, nightSoFar))
        assertFalse(OutOfBed.backInBed(Nights.join(sleep(3), move(7)), ref, nightSoFar))
        // Without a reference: phone still for the whole 5 minutes.
        assertTrue(OutOfBed.backInBed(empty(10), null, nightSoFar))
        assertFalse(OutOfBed.backInBed(Nights.join(empty(9), move()), null, nightSoFar))
        // Too little data: never re-rings.
        assertFalse(OutOfBed.backInBed(sleep(5), null, nightSoFar))
    }

    @Test fun noiseOfAStillPhoneIsSmall() {
        val samples = (0 until 600).map { Sample(it * 50L, 3.2f, 1.1f, 9.2f + (it % 2) * 0.01f) }
        val n = MovementScore.noise(samples)
        assertTrue("noise $n", n in 0.004f..0.006f)
        assertEquals(0f, MovementScore.noise(samples.take(1)), 0f)
        assertNotNull(EpochAccumulator().let { acc -> samples.forEach { acc.add(it) }; acc.flush() }?.noise)
    }
}
