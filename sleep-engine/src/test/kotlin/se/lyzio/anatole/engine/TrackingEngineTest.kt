package se.lyzio.anatole.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class TrackingEngineTest {

    private val rate = 50L // ms between samples (20 Hz)

    /** Phone lying still at an angle: gravity plus small sensor noise. */
    private fun still(fromMs: Long, toMs: Long, rnd: Random = Random(1)): List<Sample> =
        (fromMs until toMs step rate).map {
            Sample(it, 3.2f + noise(rnd), 1.1f + noise(rnd), 9.2f + noise(rnd))
        }

    /** Someone turning over: large slow swings of the acceleration vector. */
    private fun turning(fromMs: Long, toMs: Long): List<Sample> =
        (fromMs until toMs step rate).map {
            val p = sin((it - fromMs) / 1000.0 * 2 * PI).toFloat()
            Sample(it, 3.2f + 4f * p, 1.1f - 2f * p, 9.2f - 1.5f * p)
        }

    private fun noise(rnd: Random) = (rnd.nextFloat() - 0.5f) * 0.02f

    @Test fun stillPhoneScoresZero() {
        assertEquals(0f, MovementScore.score(still(0, 30_000)), 0.05f)
    }

    @Test fun movementScoresHigh() {
        val score = MovementScore.score(turning(0, 30_000))
        assertTrue("score was $score", score > 20f)
    }

    @Test fun moreMovementScoresHigher() {
        val small = still(0, 25_000) + turning(25_000, 26_000) + still(26_000, 30_000)
        val big = turning(0, 30_000)
        assertTrue(MovementScore.score(small) > 0f)
        assertTrue(MovementScore.score(big) > MovementScore.score(small))
    }

    @Test fun scoreIndependentOfOrientation() {
        val flat = still(0, 30_000).map { it.copy(x = 0f, y = 0f, z = 9.81f) }
        val onSide = flat.map { it.copy(x = 9.81f, z = 0f) }
        assertEquals(MovementScore.score(flat), MovementScore.score(onSide), 0.0001f)
    }

    @Test fun accumulatorEmitsAlignedEpochs() {
        val acc = EpochAccumulator()
        val base = 1_700_000_020_000L // 10 s into an epoch
        val epochs = still(base, base + 95_000).mapNotNull(acc::add) + listOfNotNull(acc.flush())
        assertEquals(4, epochs.size)
        epochs.forEach { assertEquals(0L, it.startMs % 30_000L) }
        assertEquals(acc.align(base), epochs.first().startMs)
        assertEquals(listOf(0L, 30_000L, 60_000L, 90_000L), epochs.map { it.startMs - epochs.first().startMs })
    }

    @Test fun accumulatorSkipsEmptyEpochs() {
        val acc = EpochAccumulator()
        val samples = still(0, 30_000) + still(300_000, 330_000)
        val epochs = samples.mapNotNull(acc::add) + listOfNotNull(acc.flush())
        assertEquals(listOf(0L, 300_000L), epochs.map { it.startMs })
    }

    @Test fun accumulatorDropsOutOfOrderSamples() {
        val acc = EpochAccumulator()
        acc.add(Sample(40_000, 0f, 0f, 9.8f))
        assertNull(acc.add(Sample(10_000, 0f, 0f, 9.8f)))
        assertEquals(1, acc.flush()!!.sampleCount)
    }

    @Test fun noGapsWhenFullyCovered() {
        val starts = (0L until 3_600_000L step 30_000L).toList()
        assertTrue(Gaps.find(starts, 0, 3_600_000).isEmpty())
        assertEquals(3_600_000L, Gaps.trackedMs(0, 3_600_000, emptyList()))
    }

    @Test fun findsGapInTheMiddle() {
        val starts = (0L until 600_000L step 30_000L) + (1_200_000L until 1_800_000L step 30_000L)
        val gaps = Gaps.find(starts.toList(), 0, 1_800_000)
        assertEquals(listOf(Gap(600_000, 1_200_000)), gaps)
        assertEquals(1_200_000L, Gaps.trackedMs(0, 1_800_000, gaps))
    }

    @Test fun findsGapsAtStartAndEnd() {
        val starts = (300_000L until 600_000L step 30_000L).toList()
        val gaps = Gaps.find(starts, 0, 1_000_000)
        assertEquals(listOf(Gap(0, 300_000), Gap(600_000, 1_000_000)), gaps)
    }

    @Test fun ignoresShortHoles() {
        // One missing epoch (30 s) is jitter, not a gap.
        val starts = (0L until 600_000L step 30_000L).filter { it != 300_000L }
        assertTrue(Gaps.find(starts, 0, 600_000).isEmpty())
    }

    @Test fun noDataAtAllIsOneGap() {
        assertEquals(listOf(Gap(0, 900_000)), Gaps.find(emptyList(), 0, 900_000))
    }

    @Test fun barsShowPeakPerFiveMinutes() {
        val epochs = listOf(0L to 1f, 30_000L to 5f, 299_999L to 2f, 300_000L to 0f)
        val bars = Bars.of(epochs)
        assertEquals(listOf(Bars.Bar(0, 5f), Bars.Bar(300_000, 0f)), bars)
    }

    @Test fun usualStartNeedsThreeNights() {
        assertNull(Bedtime.usualStartMinute(listOf(22 * 60, 23 * 60)))
        assertEquals(23 * 60, Bedtime.usualStartMinute(listOf(22 * 60, 23 * 60, 23 * 60 + 30)))
    }

    @Test fun usualStartWrapsAroundMidnight() {
        // 23:30, 00:10, 00:30 → median 00:10, not somewhere around noon.
        assertEquals(10, Bedtime.usualStartMinute(listOf(23 * 60 + 30, 10, 30)))
    }

    @Test fun reminderFallsBackToAlarmMinusNineHours() {
        assertEquals(22 * 60, Bedtime.reminderMinute(7 * 60, null))
        assertEquals(22 * 60 + 30, Bedtime.reminderMinute(7 * 60, 22 * 60 + 30))
    }

    @Test fun minutesBeforeAlarm() {
        assertEquals(9 * 60, Bedtime.minutesBeforeAlarm(7 * 60, 22 * 60))
        assertEquals(60, Bedtime.minutesBeforeAlarm(7 * 60, 6 * 60))
        assertEquals(24 * 60, Bedtime.minutesBeforeAlarm(7 * 60, 7 * 60))
    }
}
