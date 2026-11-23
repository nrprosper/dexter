package face.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeBuilderTest {
    private val rate = 22_050

    private fun built(pcm: ShortArray, chunk: Int = pcm.size) = EnvelopeBuilder().apply {
        start(rate)
        var at = 0
        while (at < pcm.size) {
            val n = minOf(chunk, pcm.size - at)
            analyze(pcm, at, n)
            at += n
        }
    }

    @Test
    fun silenceIsZero() {
        val e = built(ShortArray(rate))
        assertEquals(rate / e.hopSize.toLong(), e.written)
        for (h in 0 until e.written) assertEquals(0f, e.loudnessAt(h), 0f)
    }

    @Test
    fun sineLoudnessFollowsItsLevel() {
        val e = built(Synthetic.tone(1.0, rate) { 0.5 })
        // 0.5 peak sine = -9 dBFS RMS → (60 - 9) / 60.
        assertEquals(0.85f, e.loudnessAt(50), 0.02f)
        val quiet = built(Synthetic.tone(1.0, rate) { 0.005 })
        // 0.005 peak = -49 dBFS RMS → 11 / 60.
        assertEquals(0.18f, quiet.loudnessAt(50), 0.02f)
    }

    @Test
    fun brightnessSeparatesLowAndHighSounds() {
        val low = built(Synthetic.tone(0.5, rate, hz = 200.0) { 0.5 })
        val high = built(Synthetic.tone(0.5, rate, hz = 5_000.0) { 0.5 })
        assertTrue("low ${low.brightnessAt(20)}", low.brightnessAt(20) < 0.2f)
        assertTrue("high ${high.brightnessAt(20)}", high.brightnessAt(20) > 0.8f)
    }

    @Test
    fun emphasisMarksSuddenlyLouderSyllables() {
        val e = built(Synthetic.tone(2.0, rate) { t -> if (t < 1.2) 0.05 else 0.4 })
        val steady = (60 until 110).maxOf { e.emphasisAt(it.toLong()) }
        val accent = (118 until 130).maxOf { e.emphasisAt(it.toLong()) }
        assertTrue("steady $steady", steady < 0.05f)
        assertTrue("accent $accent", accent > 0.5f)
    }

    @Test
    fun chunkedAnalysisMatchesOneShot() {
        val pcm = Synthetic.tone(1.0, rate) { t -> 0.3 + 0.2 * kotlin.math.sin(t * 20) }
        val whole = built(pcm)
        val pieces = built(pcm, chunk = 317)
        assertEquals(whole.written, pieces.written)
        for (h in 0 until whole.written) assertEquals(whole.loudnessAt(h), pieces.loudnessAt(h), 1e-6f)
    }

    @Test
    fun unwrittenAndOverwrittenHopsAreUnreadable() {
        val e = EnvelopeBuilder(EnvelopeConfig(capacitySeconds = 2)).apply { start(rate) }
        e.analyze(Synthetic.tone(3.0, rate) { 0.5 })
        assertEquals(-1f, e.loudnessAt(e.written), 0f)
        assertEquals(-1f, e.loudnessAt(0), 0f)
        assertTrue(e.loudnessAt(e.written - 1) > 0f)
    }
}
