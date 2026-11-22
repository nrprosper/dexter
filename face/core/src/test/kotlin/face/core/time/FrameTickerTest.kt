package face.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrameTickerTest {
    private val frame = 16_666_667L

    @Test
    fun firstFrameAfterStartOrResetIsZero() {
        val ticker = FrameTicker()
        assertEquals(0f, ticker.tick(1_000_000_000L))
        assertEquals(1f / 60f, ticker.tick(1_000_000_000L + frame), 1e-5f)
        ticker.reset()
        assertEquals(0f, ticker.tick(9_000_000_000L))
    }

    @Test
    fun longGapsAreClamped() {
        val ticker = FrameTicker()
        ticker.tick(0L)
        assertEquals(FrameTicker.MAX_DT, ticker.tick(2_000_000_000L))
    }

    @Test
    fun timeGoingBackwardsGivesZero() {
        val ticker = FrameTicker()
        ticker.tick(5 * frame)
        assertEquals(0f, ticker.tick(4 * frame))
    }
}
