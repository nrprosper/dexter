package face.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlockShapingTest {
    private val rate = 20_000

    @Test
    fun edgesFadeInAndOut() {
        val s = ShortArray(rate) { 10_000 }
        BlockShaping.shape(s, rate)
        val fade = rate * BlockShaping.FADE_MILLIS / 1_000
        assertEquals(0, s.first().toInt())
        assertEquals(0, s.last().toInt())
        assertTrue(s[fade / 2] in 4_000..6_000)
        assertEquals(10_000, s[rate / 2].toInt())
        for (i in 1 until fade) assertTrue(s[i] >= s[i - 1])
    }

    @Test
    fun loudBlocksAreScaledNotClipped() {
        val s = ShortArray(rate) { if (it % 2 == 0) 32_767 else -32_768 }
        BlockShaping.shape(s, rate)
        val peak = s.maxOf { abs(it.toInt()) }
        assertTrue(peak <= (BlockShaping.LIMIT * 32_767).toInt() + 1)
        assertTrue(peak > 25_000)
    }

    @Test
    fun quietAudioKeepsItsLevel() {
        val s = ShortArray(rate) { if (it % 2 == 0) 3_000 else -3_000 }
        BlockShaping.shape(s, rate)
        assertEquals(3_000, s[rate / 2].toInt())
    }

    @Test
    fun tinyBlocksAreSafe() {
        val s = shortArrayOf(5, -5, 7)
        BlockShaping.shape(s, rate)
        BlockShaping.shape(ShortArray(0), rate)
    }
}
