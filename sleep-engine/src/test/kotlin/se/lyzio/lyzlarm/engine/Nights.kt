package se.lyzio.lyzlarm.engine

/** Test helpers: synthetic nights and the owner's recorded nights (test resources). */
object Nights {
    const val E = SleepEngine.EPOCH_MS

    /** Epochs from a list of movement values, one per 30 s from [startMs]. */
    fun of(vararg movement: Float, startMs: Long = 0L, noise: Float? = null): List<Point> =
        movement.mapIndexed { i, m -> Point(startMs + i * E, m, noise) }

    fun still(n: Int, startMs: Long = 0L, noise: Float? = null): List<Point> =
        List(n) { Point(startMs + it * E, 0f, noise) }

    /** Joins runs of epochs so their times follow each other. */
    fun join(vararg parts: List<Point>): List<Point> {
        val out = ArrayList<Point>()
        for (part in parts) for (p in part) out.add(p.copy(timeMs = out.size * E))
        return out
    }

    /** Loads src/test/resources/nights/[name] (second,movement). */
    fun recorded(name: String): List<Point> {
        val text = Nights::class.java.getResource("/nights/$name")!!.readText()
        return text.lineSequence()
            .filter { it.isNotEmpty() && it[0].isDigit() }
            .map { line ->
                val (s, m) = line.split(',')
                Point(s.toLong() * 1000L, m.toFloat())
            }
            .toList()
    }

    /**
     * Replays a night through [WakeRules.decide] for a window ending at [endMs], as the
     * tracking service does: after each epoch inside the window. Returns the start of the
     * deciding epoch and the reason, or null (= rings at the window end).
     */
    fun replay(points: List<Point>, endMs: Long, windowMinutes: Int, sensitivity: Sensitivity): Pair<Long, SleepEngine.WakeReason>? {
        val start = WakeRules.windowStart(endMs, windowMinutes)
        for (i in points.indices) {
            val t = points[i].timeMs
            if (t + E < start || t >= endMs) continue
            WakeRules.decide(points.subList(0, i + 1), sensitivity)?.let { return t to it }
        }
        return null
    }
}
