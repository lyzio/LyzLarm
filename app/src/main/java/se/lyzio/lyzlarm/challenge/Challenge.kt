package se.lyzio.lyzlarm.challenge

import se.lyzio.lyzlarm.data.Challenges
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Snooze lock challenges (v0.3). Pure Kotlin, no Android types, so the generators are
 * unit-tested on the JVM (ChallengeTest). The screens are in ChallengeScreen.kt.
 */
enum class ChallengeType(val bit: Int, val key: String, val label: String) {
    MATH(Challenges.MATH, "math", "Math"),
    MEMORY(Challenges.MEMORY, "memory", "Memory"),
    TYPING(Challenges.TYPING, "typing", "Typing"),
    BARCODE(Challenges.BARCODE, "barcode", "Barcode scan");

    companion object {
        /** Types that never need the camera; "Can't scan?" switches to one of these. */
        val withoutCamera = listOf(MATH, MEMORY, TYPING)

        fun allowed(bits: Int): List<ChallengeType> = entries.filter { bits and it.bit != 0 }

        /**
         * A random type from [bits]. Barcode only if [cameraAllowed]; if nothing is left,
         * a math challenge (the lock must never leave the user without a way out).
         */
        fun pick(bits: Int, cameraAllowed: Boolean, random: Random = Random.Default): ChallengeType {
            val options = allowed(bits).filter { it != BARCODE || cameraAllowed }
            return if (options.isEmpty()) MATH else options.random(random)
        }

        /** Fallback from barcode ("Can't scan?"): one of the allowed non-camera types, else math. */
        fun fallback(bits: Int, random: Random = Random.Default): ChallengeType {
            val options = allowed(bits).filter { it in withoutCamera }
            return if (options.isEmpty()) MATH else options.random(random)
        }
    }
}

/** A math problem, e.g. "37 × 4". Difficulty: 0 = Easy, 1 = Normal (default), 2 = Hard. */
data class MathProblem(val text: String, val answer: Int) {
    fun check(input: String): Boolean = input.trim().toIntOrNull() == answer

    companion object {
        val DIFFICULTIES = listOf("Easy", "Normal", "Hard")

        fun generate(difficulty: Int, random: Random = Random.Default): MathProblem = when (difficulty) {
            0 -> {
                val a = random.nextInt(12, 60)
                val b = random.nextInt(12, 40)
                MathProblem("$a + $b", a + b)
            }
            2 -> {
                val a = random.nextInt(13, 50)
                val b = random.nextInt(12, 30)
                MathProblem("$a × $b", a * b)
            }
            else -> {
                val a = random.nextInt(12, 100)
                val b = random.nextInt(3, 10)
                MathProblem("$a × $b", a * b)
            }
        }
    }
}

/** Repeat a pattern of 5–6 tiles on a 3 × 3 grid, shown one tile at a time. */
data class MemoryPattern(val tiles: List<Int>) {
    /** True while [input] is a correct start of the pattern. */
    fun isOnTrack(input: List<Int>): Boolean = input.size <= tiles.size && tiles.subList(0, input.size) == input

    fun isSolved(input: List<Int>): Boolean = input == tiles

    companion object {
        const val GRID = 3
        const val TILES = GRID * GRID

        fun generate(random: Random = Random.Default): MemoryPattern {
            val length = random.nextInt(5, 7)
            val tiles = ArrayList<Int>(length)
            while (tiles.size < length) {
                val t = random.nextInt(TILES)
                // No tile twice in a row: it would look like one flash.
                if (tiles.lastOrNull() != t) tiles.add(t)
            }
            return MemoryPattern(tiles)
        }
    }
}

/** Type a short random phrase. Case and extra spaces do not matter. */
data class TypingPhrase(val text: String) {
    fun check(input: String): Boolean = normalize(input) == normalize(text)

    companion object {
        const val WORDS = 3

        fun generate(random: Random = Random.Default): TypingPhrase {
            val picked = ArrayList<String>(WORDS)
            while (picked.size < WORDS) {
                val w = WordList.words.random(random)
                if (w !in picked) picked.add(w)
            }
            return TypingPhrase(picked.joinToString(" "))
        }

        private fun normalize(s: String) = s.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
    }
}

/**
 * Barcode memory: a code scanned in the last 7 days does not count again. Only a SHA-256
 * fingerprint is stored (scanned_codes), and fingerprints older than [KEEP_MS] are deleted.
 */
object CodeMemory {
    const val KEEP_MS = 7 * 24 * 3_600_000L

    fun fingerprint(format: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$format:$text".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** True if a code last scanned at [lastScannedAt] (null = never) may be used at [now]. */
    fun usable(lastScannedAt: Long?, now: Long): Boolean = lastScannedAt == null || now - lastScannedAt >= KEEP_MS

    /** Fingerprints older than this are deleted. */
    fun cutoff(now: Long): Long = now - KEEP_MS
}
