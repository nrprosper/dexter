package face.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RngTest {
    @Test
    fun sameSeedGivesSameSequence() {
        val a = Rng(99)
        val b = Rng(99)
        repeat(1_000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun differentSeedsDiverge() {
        assertNotEquals(Rng(1).nextLong(), Rng(2).nextLong())
    }

    @Test
    fun floatsAndIntsStayInRange() {
        val rng = Rng(7)
        var sum = 0.0
        repeat(20_000) {
            val f = rng.nextFloat()
            assertTrue(f >= 0f && f < 1f)
            sum += f
            assertTrue(rng.nextInt(9) in 0 until 9)
            assertTrue(rng.nextFloat(-2f, 3f) in -2f..3f)
        }
        assertEquals(0.5, sum / 20_000, 0.01)
    }

    @Test
    fun gaussianHasUnitSpreadAndLogNormalKeepsItsMean() {
        val rng = Rng(21)
        var sum = 0.0
        var squares = 0.0
        var logSum = 0.0
        val n = 50_000
        repeat(n) {
            val g = rng.nextGaussian().toDouble()
            sum += g
            squares += g * g
            logSum += rng.nextLogNormal(4f, 0.45f)
        }
        assertEquals(0.0, sum / n, 0.02)
        assertEquals(1.0, squares / n, 0.03)
        assertEquals(4.0, logSum / n, 0.05)
    }
}
