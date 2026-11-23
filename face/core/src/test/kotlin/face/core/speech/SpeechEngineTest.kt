package face.core.speech

import face.api.Emotion
import face.api.EmotionSignal
import face.api.MouthFrame
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.FaceEngine
import face.core.behavior.FaceEvent
import face.core.director.FaceFixtures
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class SpeechEngineTest {
    // Syllables at 5 Hz with an accent every second syllable; loudness is computed from the sample time.
    private class Syllables(private val loud: Float = 0.9f) : SpeechTrack {
        var lastNanos = -1L
        override var isActive: Boolean = true

        override fun sample(nowNanos: Long, out: MouthFrame) {
            lastNanos = nowNanos
            val t = nowNanos / 1e9
            val s = max(0.0, sin(2 * PI * 5 * t)).toFloat()
            out.openness = s * loud
            out.width = 0.5f + 0.3f * sin(2 * PI * 0.7 * t).toFloat()
            out.roundness = 1f - out.width
            out.emphasis = if ((t * 5).toInt() % 2 == 0) s else 0f
        }
    }

    private fun mouthOpenRange(engine: FaceEngine, seconds: Float): Pair<Float, Float> {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        engine.run(seconds) {
            lo = minOf(lo, it.pose[Channel.MouthOpen])
            hi = maxOf(hi, it.pose[Channel.MouthOpen])
        }
        return lo to hi
    }

    @Test
    fun mouthFollowsTheTrackAndSamplesAtFrameTime() {
        val engine = FaceFixtures.engine()
        val track = Syllables()
        engine.attachSpeech(track, SpeechStyle())
        engine.run(0.5f)
        val (lo, hi) = mouthOpenRange(engine, 2f)
        assertTrue(hi - lo > 0.3f, "mouth barely moved: $lo..$hi")
        engine.step(FaceFixtures.DT, frameNanos = 123_456_789L)
        assertEquals(123_456_789L, track.lastNanos)
    }

    @Test
    fun stopMidSyllableClosesWithinSpringTimeWithoutPops() {
        val engine = FaceFixtures.engine()
        val rest = engine.pose[Channel.MouthOpen]
        engine.attachSpeech(Syllables(), SpeechStyle.Excited)
        engine.run(1f)
        var guard = 0
        while (engine.speechProbe.open < 0.4f && guard++ < 120) engine.step(FaceFixtures.DT)
        assertTrue(engine.speechProbe.open >= 0.4f, "never caught the mouth open")
        engine.detachSpeech()
        val previous = FacePose().also { it.copyFrom(engine.pose) }
        var closedAt = -1f
        var time = 0f
        engine.run(1.5f) {
            time += FaceFixtures.DT
            for (c in Channel.entries) {
                val delta = abs(it.pose[c] - previous[c])
                assertTrue(delta <= POP_FRACTION * (c.max - c.min), "$c jumped $delta after stop")
            }
            previous.copyFrom(it.pose)
            if (closedAt < 0f && abs(it.pose[Channel.MouthOpen] - rest) < CLOSED) closedAt = time
        }
        assertTrue(closedAt in 0f..SPRING_TIME, "mouth closed after $closedAt s")
        assertEquals(0f, engine.speechProbe.open)
    }

    @Test
    fun randomAttachDetachSequencesNeverPopAndStayInRange() {
        val styles = listOf(SpeechStyle.Calm, SpeechStyle.Excited, SpeechStyle.Serious, SpeechStyle.Funny)
        for (seed in 1L..4L) {
            val engine = FaceFixtures.engine(seed)
            val rng = Rng(seed * 13)
            val previous = FacePose().also { it.copyFrom(engine.pose) }
            repeat(3_600) {
                when (rng.nextInt(40)) {
                    0 -> engine.attachSpeech(Syllables(rng.nextFloat(0.3f, 1f)), styles[rng.nextInt(styles.size)])
                    1 -> engine.detachSpeech()
                    2 -> engine.setEmotion(EmotionSignal(Emotion.entries[rng.nextInt(Emotion.entries.size)], rng.nextFloat()))
                }
                engine.step(FaceFixtures.DT)
                for (c in Channel.entries) {
                    val v = engine.pose[c]
                    assertTrue(v in c.min..c.max, "seed $seed $c = $v")
                    assertTrue(abs(v - previous[c]) <= POP_FRACTION * (c.max - c.min), "seed $seed $c jumped")
                }
                previous.copyFrom(engine.pose)
            }
        }
    }

    @Test
    fun happyFaceKeepsSmilingWhileTalking() {
        val engine = FaceFixtures.engine()
        engine.setEmotion(EmotionSignal(Emotion.Happy, 0.9f))
        engine.run(3f)
        val smile = engine.pose[Channel.MouthCurve]
        assertTrue(smile > 0.2f, "not smiling before speech: $smile")
        engine.attachSpeech(Syllables(), SpeechStyle())
        var minCurve = Float.MAX_VALUE
        engine.run(3f) { minCurve = minOf(minCurve, it.pose[Channel.MouthCurve]) }
        assertTrue(minCurve > smile * 0.6f, "smile dropped to $minCurve while talking")
    }

    @Test
    fun concernedFaceTalksWithRestraint() {
        fun peakOpen(emotion: Emotion): Float {
            val engine = FaceFixtures.engine()
            engine.setEmotion(EmotionSignal(emotion, 0.9f))
            engine.run(4f)
            engine.attachSpeech(Syllables(0.6f), SpeechStyle())
            engine.run(0.5f)
            var peak = 0f
            engine.run(3f) { peak = max(peak, it.speechProbe.open) }
            return peak
        }
        val happy = peakOpen(Emotion.Happy)
        val concerned = peakOpen(Emotion.Concerned)
        assertTrue(concerned < happy * 0.9f, "concerned $concerned vs happy $happy")
    }

    @Test
    fun seriousSpeechHasNoBeatsExcitedHasMany() {
        fun beats(style: SpeechStyle): Int {
            val engine = FaceFixtures.engine()
            val log = engine.record(FaceEvent.Behavior)
            engine.attachSpeech(Syllables(), style)
            engine.run(10f)
            return log.count { it == "speech beat" }
        }
        assertEquals(0, beats(SpeechStyle.Serious))
        val calm = beats(SpeechStyle.Calm)
        val excited = beats(SpeechStyle.Excited)
        assertTrue(excited > calm && excited >= 8, "excited $excited, calm $calm")
    }

    @Test
    fun funnySpeechEndsWithALaugh() {
        val engine = FaceFixtures.engine()
        val reactions = engine.record(FaceEvent.Reaction)
        engine.attachSpeech(Syllables(), SpeechStyle.Funny)
        engine.run(2f)
        engine.detachSpeech()
        engine.run(0.5f)
        assertTrue(reactions.any { it.contains("Laugh", ignoreCase = true) }, "reactions: $reactions")

        val serious = FaceFixtures.engine()
        val none = serious.record(FaceEvent.Reaction)
        serious.attachSpeech(Syllables(), SpeechStyle.Serious)
        serious.run(2f)
        serious.detachSpeech()
        serious.run(0.5f)
        assertTrue(none.none { it.contains("Laugh", ignoreCase = true) })
    }

    @Test
    fun sameSeedSameSpeechSequence() {
        fun sequence(): List<FloatArray> {
            val engine = FaceFixtures.engine(7)
            engine.attachSpeech(Syllables(), SpeechStyle.Excited)
            val out = ArrayList<FloatArray>()
            engine.run(4f) { out.add(it.pose.values.copyOf()) }
            return out
        }
        val a = sequence()
        val b = sequence()
        for (i in a.indices) assertArrayEquals(a[i], b[i])
    }

    @Test
    fun speakingStepDoesNotAllocate() {
        val engine = FaceFixtures.engine(5)
        engine.attachSpeech(Syllables(), SpeechStyle.Excited)
        repeat(20_000) { engine.step(FaceFixtures.DT, it * 16_666_667L) }
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        repeat(10_000) { engine.step(FaceFixtures.DT, (20_000 + it) * 16_666_667L) }
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "speaking step allocated $allocated bytes")
    }

    private companion object {
        const val POP_FRACTION = 0.35f
        const val CLOSED = 0.02f
        const val SPRING_TIME = 0.5f
    }
}
