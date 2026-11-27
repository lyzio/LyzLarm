package se.lyzio.anatole.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.lyzio.anatole.engine.Nights.E
import se.lyzio.anatole.engine.Nights.of

class StagesTest {
    @Test fun restlessStartMeansNoOnsetUntilSettled() {
        val night = Nights.join(of(30f, 30f, 5f, 5f, 5f, 0f, 5f, 0f), Nights.still(60))
        val r = NightAnalysis.analyze(night, 0L, night.size * E)
        // First 10 minutes with at most one restless epoch start at epoch 5 (the 5f at 6 is the only one).
        assertEquals(5 * E, r.onsetMs)
        assertEquals(Stage.AWAKE, r.stages[4].stage)
    }

    @Test fun neverSettledMeansNoOnset() {
        val night = List(40) { Point(it * E, if (it % 2 == 0) 5f else 0f) }
        val r = NightAnalysis.analyze(night, 0L, 40 * E)
        assertNull(r.onsetMs)
        assertEquals(0L, r.asleepMs)
    }

    @Test fun deepNeedsTenStillMinutesEitherSide() {
        val night = Nights.join(Nights.still(60), of(5f), Nights.still(60))
        val st = NightAnalysis.analyze(night, 0L, night.size * E).stages
        assertEquals(Stage.DEEP, st[30].stage)
        assertEquals(Stage.LIGHT, st[45].stage)
        assertEquals(Stage.LIGHT, st[60].stage)
        assertEquals(Stage.DEEP, st[90].stage)
    }

    @Test fun ringingOnIsAwakeAndGapsAreNotSleep() {
        val night = Nights.still(60) + Nights.still(20, startMs = 100 * E)
        val r = NightAnalysis.analyze(night, 0L, 120 * E, awakeFromMs = 110 * E)
        assertEquals(0L, r.onsetMs)
        // 60 + 10 asleep epochs; the gap (60–100) and everything after the ring are not sleep.
        assertEquals(70 * E, r.asleepMs)
        assertEquals(120 * E, r.inBedMs)
    }

    @Test fun blocksPickTheCommonStage() {
        val st = List(10) { StagePoint(it * E, if (it < 6) Stage.DEEP else Stage.LIGHT) } +
            List(10) { StagePoint((10 + it) * E, if (it < 5) Stage.AWAKE else Stage.LIGHT) }
        val b = NightAnalysis.blocks(st)
        assertEquals(listOf(Stage.DEEP, Stage.AWAKE), b.map { it.stage })
    }
}
