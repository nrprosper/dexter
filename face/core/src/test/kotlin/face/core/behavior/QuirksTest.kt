package face.core.behavior

import face.api.Emotion
import face.api.EmotionSignal
import face.api.Quirk
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.personality.PersonalityPresets
import face.core.rig.Channel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuirksTest {
    private val dexter = PersonalityPresets.load("dexter")

    @Test
    fun unsureSignalsGiveADoubleBlinkOnlyWhenEnabled() {
        val on = engine(personality = dexter)
        val onEvents = on.record(FaceEvent.Behavior, FaceEvent.DoubleBlink)
        on.setEmotion(EmotionSignal(Emotion.Confused, 0.7f, confidence = 0.3f))
        on.run(0.2f)
        assertTrue("quirk unsure" in onEvents)

        val off = engine(personality = dexter.copy(quirks = emptySet()))
        val offEvents = off.record(FaceEvent.Behavior)
        off.setEmotion(EmotionSignal(Emotion.Confused, 0.7f, confidence = 0.3f))
        off.run(0.2f)
        assertTrue("quirk unsure" !in offEvents)
    }

    @Test
    fun aHappyJumpWobblesTheHead() {
        val face = engine(personality = dexter.copy(quirks = setOf(Quirk.HappyWobble)))
        face.lifeEnabled = false
        val events = face.record(FaceEvent.Behavior)
        face.run(1f)
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.9f))
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        face.run(0.8f) {
            low = minOf(low, it.pose[Channel.HeadTilt])
            high = maxOf(high, it.pose[Channel.HeadTilt])
        }
        assertTrue("quirk wobble" in events)
        assertTrue(high - low > 0.05f, "tilt swing ${high - low}")
    }

    @Test
    fun wakingPeeksWithOneEyeFirst() {
        val face = engine(personality = dexter.copy(quirks = setOf(Quirk.OneEyePeek)))
        face.dozeOff()
        face.run(6f)
        val events = face.record(FaceEvent.Behavior)
        face.interact()
        var peeked = false
        face.run(0.7f) { if (it.pose[Channel.EyeOpenR] - it.pose[Channel.EyeOpenL] > 0.3f) peeked = true }
        assertTrue("quirk peek" in events)
        assertTrue(peeked, "the right eye should open before the left")
    }
}
