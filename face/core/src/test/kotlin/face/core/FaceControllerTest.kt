package face.core

import face.api.Activity
import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.MouthFrame
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.behavior.BoredomConfig
import face.core.behavior.FaceEvent
import face.core.behavior.FaceEventListener
import face.core.behavior.LifeConfig
import face.core.behavior.Vigilance
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sin

class FaceControllerTest {
    private val library = ExpressionLibrary.loadBundled()
    private val dt = 1f / 60f

    private fun engine(seed: Long = 3) = FaceEngine(library, Rng(seed))

    private fun FaceEngine.run(seconds: Float, each: (FaceEngine) -> Unit = {}) {
        repeat((seconds / dt).toInt()) {
            step(dt)
            each(this)
        }
    }

    // Syllable-like mouth envelope; allocation-free.
    private class Syllables : SpeechTrack {
        override val isActive: Boolean = true

        override fun sample(nowNanos: Long, out: MouthFrame) {
            val t = nowNanos / 1e9
            out.openness = (0.5 + 0.5 * sin(t * 2 * Math.PI * 4.0)).toFloat()
            out.width = 0.5f
        }
    }

    @Test
    fun callsShowUpInTheSnapshotAfterTheNextFrame() {
        val face = engine()
        val controller: FaceController = face
        controller.setActivity(Activity.Listening)
        controller.setEmotion(EmotionSignal(Emotion.Happy, 0.8f))
        controller.react(FaceReaction.Realization)
        controller.attachSpeech(Syllables(), SpeechStyle.Calm)
        controller.setPowerMode(PowerMode.Reduced)
        assertEquals(Activity.Idle, controller.snapshot.value.activity)
        face.step(dt)
        val snap = controller.snapshot.value
        assertEquals(Activity.Listening, snap.activity)
        assertEquals(Emotion.Happy, snap.emotion)
        assertEquals(0.8f, snap.intensity)
        assertEquals(FaceReaction.Realization, snap.lastReaction)
        assertTrue(snap.speaking)
        assertEquals(PowerMode.Reduced, snap.powerMode)
        controller.detachSpeech()
        face.step(dt)
        assertTrue(!controller.snapshot.value.speaking)
    }

    @Test
    fun resendingTheSameStateChangesNothing() {
        val once = engine(8)
        val spammed = engine(8)
        val signal = EmotionSignal(Emotion.Curious, 0.6f)
        once.setActivity(Activity.Listening)
        once.setEmotion(signal)
        repeat(600) {
            spammed.setActivity(Activity.Listening)
            spammed.setEmotion(EmotionSignal(Emotion.Curious, 0.6f))
            once.step(dt)
            spammed.step(dt)
            assertArrayEquals(once.pose.values, spammed.pose.values, "frame $it")
        }
    }

    @Test
    fun callsFromManyThreadsAreSafe() {
        val face = engine()
        val callers = (0 until 6).map { i ->
            thread {
                repeat(5_000) {
                    face.setActivity(Activity.entries[(it + i) % Activity.entries.size])
                    face.setEmotion(EmotionSignal(Emotion.entries[it % Emotion.entries.size], 0.5f))
                    face.react(FaceReaction.Acknowledge)
                    face.setUserVoiceLevel((it % 7) / 7f)
                }
            }
        }
        while (callers.any { it.isAlive }) face.step(dt)
        face.setActivity(Activity.Thinking)
        face.step(dt)
        assertEquals(Activity.Thinking, face.snapshot.value.activity)
        for (c in Channel.entries) assertTrue(face.pose[c] in c.min..c.max)
    }

    @Test
    fun listeningLeansInWithBiggerPupils() {
        val idle = engine(5).apply { run(2f) }
        val listening = engine(5).apply { setActivity(Activity.Listening); run(2f) }
        assertTrue(listening.pose[Channel.PupilSize] > idle.pose[Channel.PupilSize] + 0.1f)
        assertTrue(listening.pose[Channel.HeadScale] > idle.pose[Channel.HeadScale] + 0.03f)
    }

    @Test
    fun thinkingLooksUpThenEscalates() {
        val face = engine(6)
        face.setActivity(Activity.Thinking)
        face.run(2f)
        assertTrue(face.pose[Channel.GazeY] > 0.25f, "gaze should be up, was ${face.pose[Channel.GazeY]}")
        assertTrue(face.pose[Channel.ThinkDots] > 0.2f)
        val calmEyes = face.pose[Channel.EyeOpenL]
        face.run(7f)
        assertTrue(face.pose[Channel.EyeOpenL] < calmEyes - 0.1f, "effort should narrow the eyes")
        var apologised = false
        face.run(10f) { if (abs(it.pose[Channel.GazeX]) < 0.15f && it.pose[Channel.GazeY] < 0.15f) apologised = true }
        assertTrue(apologised, "a long think should glance back at the user")
    }

    @Test
    fun speakingMovesTheMouthWithTheTrack() {
        val face = engine()
        face.setActivity(Activity.Speaking)
        face.attachSpeech(Syllables(), SpeechStyle.Excited)
        var low = Float.MAX_VALUE
        var high = 0f
        face.run(3f) {
            low = minOf(low, it.pose[Channel.MouthOpen])
            high = maxOf(high, it.pose[Channel.MouthOpen])
        }
        assertTrue(high - low > 0.15f, "mouth range ${high - low}")
    }

    @Test
    fun sleepingActivitySleepsAndAnyOtherWakes() {
        val face = engine()
        face.setActivity(Activity.Sleeping)
        face.run(5f)
        assertEquals(Vigilance.Asleep, face.vigilance)
        assertTrue(face.snapshot.value.asleep)
        face.interact()
        face.run(1f)
        assertEquals(Vigilance.Asleep, face.vigilance, "taps don't override the orchestrator's Sleeping")
        face.setActivity(Activity.Listening)
        face.run(2.5f)
        assertEquals(Vigilance.Awake, face.vigilance)
    }

    @Test
    fun userVoiceDrivesNodsOnlyWhileListening() {
        fun nods(activity: Activity, voice: (Int) -> Float): Int {
            var count = 0
            val face = FaceEngine(library, Rng(12), life = LifeConfig(boredom = BoredomConfig(1e9f, 2e9f)))
            face.listener = FaceEventListener { e, _, _ -> if (e == FaceEvent.Nod) count++ }
            face.setActivity(activity)
            repeat(600) {
                face.setUserVoiceLevel(voice(it))
                face.step(dt)
            }
            return count
        }
        val pulses: (Int) -> Float = { if ((it / 10) % 3 == 0) 0.8f else 0.05f }
        assertTrue(nods(Activity.Listening, pulses) >= 3)
        assertEquals(0, nods(Activity.Listening) { 0f })
        assertEquals(0, nods(Activity.Thinking, pulses))
    }

    @Test
    fun activitiesAndSpeechDoNotAllocatePerFrame() {
        val face = engine()
        face.setActivity(Activity.Thinking)
        face.attachSpeech(Syllables(), SpeechStyle.Funny)
        face.run(20f)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        repeat(10_000) { face.step(dt) }
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "allocated $allocated bytes over 10k frames")
    }
}
