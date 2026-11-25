package se.lyzio.lyzlarm.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.lyzio.lyzlarm.data.Challenges
import kotlin.random.Random

class ChallengeTest {
    @Test fun mathByDifficulty() {
        val r = Random(7)
        repeat(200) {
            val easy = MathProblem.generate(0, r)
            assertTrue(easy.text, easy.text.contains("+") && easy.answer in 24..98)
            val normal = MathProblem.generate(1, r)
            val (a, b) = normal.text.split(" × ").map { it.toInt() }
            assertTrue(a in 12..99 && b in 3..9)
            assertEquals(a * b, normal.answer)
            val hard = MathProblem.generate(2, r)
            val (c, d) = hard.text.split(" × ").map { it.toInt() }
            assertTrue(c in 13..49 && d in 12..29)
            assertEquals(c * d, hard.answer)
        }
        val p = MathProblem("37 × 4", 148)
        assertTrue(p.check(" 148 "))
        assertFalse(p.check("147"))
        assertFalse(p.check(""))
        assertFalse(p.check("abc"))
    }

    @Test fun memoryPattern() {
        val r = Random(3)
        repeat(200) {
            val m = MemoryPattern.generate(r)
            assertTrue(m.tiles.size in 5..6)
            assertTrue(m.tiles.all { it in 0 until MemoryPattern.TILES })
            for (i in 1 until m.tiles.size) assertNotEquals(m.tiles[i - 1], m.tiles[i])
        }
        val m = MemoryPattern(listOf(0, 4, 8, 2, 6))
        assertTrue(m.isOnTrack(emptyList()))
        assertTrue(m.isOnTrack(listOf(0, 4)))
        assertFalse(m.isOnTrack(listOf(0, 5)))
        assertFalse(m.isSolved(listOf(0, 4, 8, 2)))
        assertTrue(m.isSolved(listOf(0, 4, 8, 2, 6)))
        assertFalse(m.isOnTrack(listOf(0, 4, 8, 2, 6, 1)))
    }

    @Test fun typingPhrase() {
        val r = Random(5)
        repeat(100) {
            val t = TypingPhrase.generate(r)
            val words = t.text.split(" ")
            assertEquals(3, words.size)
            assertEquals(3, words.toSet().size)
            assertTrue(words.all { it in WordList.words })
        }
        val t = TypingPhrase("green river morning")
        assertTrue(t.check("Green  river morning "))
        assertFalse(t.check("green river"))
        assertTrue(WordList.words.size >= 150)
        assertTrue(WordList.words.all { it.matches(Regex("[a-z]+")) })
    }

    @Test fun pickRespectsAllowedTypesAndCamera() {
        val r = Random(1)
        val seen = (1..300).map { ChallengeType.pick(Challenges.DEFAULT, cameraAllowed = true, random = r) }.toSet()
        assertEquals(setOf(ChallengeType.MATH, ChallengeType.MEMORY, ChallengeType.TYPING), seen)
        assertEquals(ChallengeType.BARCODE, ChallengeType.pick(Challenges.BARCODE, cameraAllowed = true))
        // No camera permission: never barcode; nothing left → math.
        assertEquals(ChallengeType.MATH, ChallengeType.pick(Challenges.BARCODE, cameraAllowed = false))
        assertEquals(ChallengeType.MATH, ChallengeType.pick(0, cameraAllowed = true))
        assertEquals(ChallengeType.TYPING, ChallengeType.fallback(Challenges.BARCODE or Challenges.TYPING))
        assertEquals(ChallengeType.MATH, ChallengeType.fallback(Challenges.BARCODE))
    }

    @Test fun codeMemoryForgetsAfterSevenDays() {
        val day = 24 * 3_600_000L
        val now = 100 * day
        assertTrue(CodeMemory.usable(null, now))
        assertFalse(CodeMemory.usable(now - 6 * day, now))
        assertFalse(CodeMemory.usable(now - 7 * day + 1, now))
        assertTrue(CodeMemory.usable(now - 7 * day, now))
        assertEquals(now - 7 * day, CodeMemory.cutoff(now))
        val a = CodeMemory.fingerprint("EAN_13", "7310865004703")
        assertEquals(64, a.length)
        assertEquals(a, CodeMemory.fingerprint("EAN_13", "7310865004703"))
        assertNotEquals(a, CodeMemory.fingerprint("EAN_13", "7310865004704"))
        assertFalse(a.contains("7310865004703"))
    }
}
