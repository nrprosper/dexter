package face.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmPlayerTest {
    private val rate = 16_000
    private val sink = FakeSink()
    private val player = PcmPlayer({ sink }, io = Dispatchers.Default)
    private fun block(seconds: Double) = PcmBlock(Synthetic.tone(seconds, rate) { 0.4 }, rate)

    @Test
    fun playsEverythingThenReleases() = runBlocking {
        var starts = 0
        val done = player.play(flowOf(block(0.5), block(0.25))) { starts++ }
        assertTrue(done)
        assertEquals(1, starts)
        assertTrue(sink.written >= (0.75 * rate).toLong())
        assertFalse(sink.flushed)
        assertTrue(sink.closed)
        assertFalse(player.track.isActive)
    }

    @Test
    fun stopMidSentenceFadesFlushesAndEndsTheTrack() = runBlocking {
        sink.writeDelayMillis = 5
        var upstreamDone = false
        val blocks = flow {
            emit(block(2.0))
            emit(block(2.0))
            upstreamDone = true
        }
        val job = async { player.play(blocks) }
        withTimeout(2_000) { while (!player.track.isActive) delay(5) }
        delay(30)
        player.stop()
        assertFalse(player.track.isActive)
        assertFalse(withTimeout(2_000) { job.await() })
        assertTrue(sink.flushed)
        assertTrue(sink.closed)
        assertFalse(upstreamDone)
    }

    @Test
    fun cancellingTheCallerStopsPlayback() = runBlocking {
        sink.writeDelayMillis = 5
        val job = async { player.play(flowOf(block(5.0))) }
        withTimeout(2_000) { while (!player.track.isActive) delay(5) }
        job.cancel()
        withTimeout(2_000) { while (!sink.closed) delay(5) }
        assertTrue(sink.flushed)
        assertFalse(player.track.isActive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleRateMustNotChangeMidUtterance() {
        runBlocking { player.play(flowOf(block(0.1), PcmBlock(ShortArray(100), 22_050))) }
    }
}
