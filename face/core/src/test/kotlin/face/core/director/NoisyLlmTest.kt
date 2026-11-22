package face.core.director

import face.api.Emotion
import face.api.EmotionSignal
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

// Random labels every 0.8–3 s, biased to Happy, with random intensity and confidence: the face must stay calm.
class NoisyLlmTest {
    @Test
    fun noisyLabelsDoNotMakeTheFaceRandom() {
        val face = engine(seed = 31)
        val rng = Rng(99)
        val minutes = 30f
        var nextSignal = 0f
        var t = 0f
        var switches = 0
        var lastSwitch = -100f
        var shortest = Float.MAX_VALUE
        var shown = face.snapshot.value.emotion
        val previous = FacePose().also { it.copyFrom(face.pose) }
        face.run(minutes * 60f) {
            t += FaceFixtures.DT
            if (t >= nextSignal) {
                nextSignal = t + rng.nextFloat(0.8f, 3f)
                val emotion = if (rng.nextFloat() < 0.45f) Emotion.Happy else Emotion.entries[rng.nextInt(Emotion.entries.size)]
                it.setEmotion(EmotionSignal(emotion, rng.nextFloat(0.3f, 1f), rng.nextFloat(0.2f, 1f)))
            }
            val now = it.snapshot.value.emotion
            if (now != shown) {
                switches++
                shortest = minOf(shortest, t - lastSwitch)
                lastSwitch = t
                shown = now
            }
            for (c in Channel.entries) {
                assertTrue(abs(it.pose[c] - previous[c]) <= 0.35f * (c.max - c.min), "$c popped")
                assertTrue(it.pose[c] in c.min..c.max)
            }
            previous.copyFrom(it.pose)
        }
        val perMinute = switches / minutes
        assertTrue(perMinute <= 8f, "$perMinute emotion switches per minute")
        assertTrue(shortest >= face.personality.director.minDwell - 0.02f, "switches only $shortest s apart")
    }
}
