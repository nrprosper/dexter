package face.core.speech

import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class MouthMapperTest {
    private val config = MouthConfig.loadBundled()
    private val dt = 1f / 60f

    // The same loudness mapping as the amplitude track: RMS over a 10 ms window, -60..0 dBFS → 0..1.
    private fun loudness(pcm: FloatArray, at: Int, window: Int): Float {
        var sum = 0.0
        var n = 0
        for (i in max(0, at - window) until at.coerceAtMost(pcm.size)) {
            sum += pcm[i] * pcm[i]
            n++
        }
        if (n == 0) return 0f
        val db = 20 * log10(sqrt(sum / n).coerceAtLeast(1e-6))
        return ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
    }

    private fun pcm(seconds: Float, rate: Int = 16_000, amplitude: (Double) -> Double): FloatArray =
        FloatArray((seconds * rate).toInt()) { i ->
            val t = i.toDouble() / rate
            (amplitude(t) * sin(2 * PI * 220 * t)).toFloat()
        }

    private class Trace(val open: FloatArray, val isOpen: BooleanArray)

    private fun run(pcm: FloatArray, rate: Int = 16_000, step: Float = dt, gain: Float = 1f): Trace {
        val mapper = MouthMapper(config).apply { reset() }
        val frames = (pcm.size / (rate * step)).toInt()
        val open = FloatArray(frames)
        val isOpen = BooleanArray(frames)
        for (f in 0 until frames) {
            val at = ((f + 1) * step * rate).toInt()
            mapper.step(step, loudness(pcm, at, rate / 100), 0.5f, gain)
            open[f] = mapper.open
            isOpen[f] = mapper.isOpen
        }
        return Trace(open, isOpen)
    }

    // Lengths, in seconds, of each run of open (or closed) frames between transitions; the leading closed run is skipped.
    private fun runs(t: Trace, wantOpen: Boolean, step: Float = dt): List<Float> {
        val out = ArrayList<Float>()
        var length = 0
        var seenOpen = false
        for (o in t.isOpen) {
            if (o) seenOpen = true
            if (o == wantOpen) {
                length++
            } else if (length > 0) {
                if (seenOpen) out.add(length * step)
                length = 0
            }
        }
        return out
    }

    @Test
    fun silenceKeepsTheMouthShut() {
        val t = run(FloatArray(16_000 * 2))
        assertTrue(t.open.all { it == 0f })
    }

    @Test
    fun noiseBelowTheGateKeepsTheMouthShut() {
        val rng = Rng(3)
        val noise = FloatArray(16_000 * 2) { rng.nextFloat(-0.002f, 0.002f) }
        assertTrue(run(noise).isOpen.none { it })
    }

    @Test
    fun sineBurstsOpenOncePerBurst() {
        val bursts = pcm(3f) { t -> if (t % 0.5 < 0.25) 0.5 else 0.0 }
        val t = run(bursts)
        assertEquals(6, runs(t, wantOpen = true).size + if (t.isOpen.last()) 1 else 0)
        assertTrue(t.open.max() > 0.5f)
    }

    @Test
    fun fastSyllablesDoNotFlapAndRespectMinimumClosure() {
        val syllables = pcm(3f) { t -> 0.4 * max(0.0, sin(2 * PI * 7 * t)) }
        for (step in floatArrayOf(1f / 30f, 1f / 60f, 1f / 120f)) {
            val t = run(syllables, step = step)
            val closed = runs(t, wantOpen = false, step = step)
            val opened = runs(t, wantOpen = true, step = step)
            assertTrue(closed.isNotEmpty(), "syllables should close the mouth between them at $step")
            for (c in closed) assertTrue(c >= config.minClosure - step * 0.5f, "closed only $c s at $step")
            for (o in opened) assertTrue(o >= config.minOpen - step * 0.5f, "open only $o s at $step")
            assertTrue(opened.size <= 21 + 1, "${opened.size} openings for 21 syllables at $step")
        }
    }

    @Test
    fun jitteryVowelStaysOpen() {
        val rng = Rng(9)
        val jitter = FloatArray(200) { rng.nextFloat(0.75f, 1f) }
        val vowel = pcm(2f) { t -> 0.5 * jitter[(t * 100).toInt().coerceAtMost(199)] }
        val opened = runs(run(vowel), wantOpen = true)
        assertTrue(opened.size <= 1, "a held vowel flapped ${opened.size} times")
    }

    @Test
    fun compressionLiftsQuietSyllablesAndCapsLoudOnes() {
        val quiet = run(pcm(1f) { 0.02 }).open.max()
        val loud = run(pcm(1f) { 0.9 }).open.max()
        assertTrue(quiet > 0.15f, "quiet syllable only opened $quiet")
        assertTrue(loud <= 1f)
        assertTrue(loud / quiet < 0.9 / 0.02 / 10, "compression too weak: $loud vs $quiet")
    }

    @Test
    fun outputsStayInRange() {
        val mapper = MouthMapper(config).apply { reset() }
        val rng = Rng(21)
        repeat(20_000) {
            mapper.step(rng.nextFloat(0f, 0.05f), rng.nextFloat(-0.5f, 1.5f), rng.nextFloat(-1f, 2f), rng.nextFloat(0f, 3f))
            assertTrue(mapper.open in 0f..1f && mapper.envelope in 0f..1f && mapper.brightness in 0f..1f)
        }
    }

    @Test
    fun envelopeMatchesAt30_60_120Hz() {
        fun at(step: Float): FloatArray {
            val mapper = MouthMapper(config).apply { reset() }
            val out = FloatArray(90)
            var time = 0f
            var sample = 0
            val perSample = Math.round((1f / 30f) / step)
            var frame = 0
            while (sample < out.size) {
                time += step
                val level = 0.45f + 0.4f * sin(2.0 * PI * 1.5 * time).toFloat()
                mapper.step(step, level, 0.5f, 1f)
                frame++
                if (frame % perSample == 0) out[sample++] = mapper.envelope
            }
            return out
        }
        // The track is sampled once per frame, so a 30 Hz frame sees the loudness up to 33 ms late; that lag bounds the match.
        val ref = at(1f / 120f)
        for ((step, tolerance) in listOf(1f / 30f to 0.1f, 1f / 60f to 0.05f)) {
            val other = at(step)
            var worst = 0f
            for (i in ref.indices) worst = max(worst, abs(ref[i] - other[i]))
            assertTrue(worst < tolerance, "envelope differs by $worst at $step")
        }
    }
}
