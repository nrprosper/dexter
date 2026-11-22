package face.speech

import face.api.MouthFrame
import face.api.SpeechStyle
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.personality.PersonalityPresets
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

// PCM → envelope → AmplitudeSpeechTrack → FaceEngine, on a simulated clock.
class SpeechChainTest {
    private val rate = 22_050
    private val dt = 1f / 60f
    private val frameNanos = 16_666_667L
    private var now = 1_000_000_000L
    private val fake = FakeSink { now }

    private fun engine() = FaceEngine(ExpressionLibrary.loadBundled(), Rng(1), personality = PersonalityPresets.load("dexter"))

    private fun speak(pcm: ShortArray): AmplitudeSpeechTrack {
        val envelope = EnvelopeBuilder().apply { start(rate); analyze(pcm) }
        fake.open(rate)
        return AmplitudeSpeechTrack(envelope).apply { begin(fake) }
    }

    private fun FaceEngine.frames(seconds: Float, each: (FaceEngine) -> Unit = {}) {
        repeat(Math.round(seconds / dt)) {
            now += frameNanos
            step(dt, now)
            each(this)
        }
    }

    // Syllables at about 6 Hz: speech-like bursts with a consonant gap between them.
    private val fastSpeech = Synthetic.tone(4.0, rate) { t -> 0.35 * max(0.0, sin(2 * PI * 6 * t)) }

    @Test
    fun fastSpeechMovesTheMouthWithoutFlapping() {
        val engine = engine()
        val track = speak(fastSpeech)
        engine.attachSpeech(track, SpeechStyle())
        var openings = 0
        var wasOpen = false
        var closedFor = 0f
        var shortestGap = Float.MAX_VALUE
        var lo = 1f
        var hi = 0f
        engine.frames(3.5f) {
            val open = it.speechProbe.open > 0f
            if (open && !wasOpen) {
                openings++
                if (openings > 1) shortestGap = minOf(shortestGap, closedFor)
            }
            closedFor = if (open) 0f else closedFor + dt
            wasOpen = open
            lo = minOf(lo, it.pose[Channel.MouthOpen])
            hi = maxOf(hi, it.pose[Channel.MouthOpen])
        }
        assertTrue("mouth range $lo..$hi", hi - lo > 0.25f)
        assertTrue("$openings openings for ~21 syllables", openings in 15..23)
        assertTrue("gap $shortestGap", shortestGap >= 0.06f - dt)
    }

    @Test
    fun stopMidSentenceClosesTheMouthWithoutPops() {
        val engine = engine()
        val rest = engine.pose[Channel.MouthOpen]
        val track = speak(Synthetic.tone(4.0, rate) { t -> 0.4 * (0.6 + 0.4 * sin(2 * PI * 3 * t)) })
        engine.attachSpeech(track, SpeechStyle.Excited)
        engine.frames(1f)
        assertTrue("mouth should be open mid-sentence", engine.speechProbe.open > 0.3f)
        track.end()
        engine.detachSpeech()
        val previous = FacePose().also { it.copyFrom(engine.pose) }
        var closedAt = -1f
        var time = 0f
        engine.frames(1f) {
            time += dt
            for (c in Channel.entries) {
                val delta = abs(it.pose[c] - previous[c])
                assertTrue("$c jumped $delta", delta <= 0.35f * (c.max - c.min))
            }
            previous.copyFrom(it.pose)
            if (closedAt < 0f && abs(it.pose[Channel.MouthOpen] - rest) < 0.02f) closedAt = time
        }
        assertTrue("closed after $closedAt s", closedAt in 0f..0.5f)
        assertEquals(0f, MouthFrame().also { track.sample(now, it) }.openness, 0f)
    }
}
