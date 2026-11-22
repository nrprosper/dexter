package face.core.director

import face.api.Emotion
import face.api.EmotionSignal
import face.core.behavior.FaceEvent
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.personality.PersonalityPresets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectorTest {
    @Test
    fun aDifferentEmotionWaitsForTheMinimumDwell() {
        val face = engine()
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.5f))
        face.run(0.3f)
        face.setEmotion(EmotionSignal(Emotion.Curious, 1f))
        face.run(0.5f)
        assertEquals(Emotion.Happy, face.snapshot.value.emotion, "dwell must hold the first emotion")
        face.run(0.6f)
        assertEquals(Emotion.Curious, face.snapshot.value.emotion, "the stronger evidence wins once the dwell is over")
    }

    @Test
    fun aSingleStrayLabelDoesNotWin() {
        val face = engine()
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.8f))
        face.run(0.3f)
        face.setEmotion(EmotionSignal(Emotion.Curious, 0.8f, confidence = 0.5f))
        face.run(5f)
        assertEquals(Emotion.Happy, face.snapshot.value.emotion)
    }

    @Test
    fun smallChangesToTheSameEmotionAreIgnored() {
        val face = engine()
        val events = face.record(FaceEvent.Emotion)
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.8f))
        face.run(2f)
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.85f))
        face.run(0.1f)
        assertEquals(0.8f, face.snapshot.value.intensity)
        assertTrue("ignored Happy" in events)
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.4f))
        face.run(0.1f)
        assertEquals(0.4f, face.snapshot.value.intensity, 1e-6f)
    }

    @Test
    fun lowConfidenceScalesTheExpressionDown() {
        val sure = engine().apply { setEmotion(EmotionSignal(Emotion.Sad, 1f, confidence = 1f)); run(3f) }
        val unsure = engine().apply { setEmotion(EmotionSignal(Emotion.Sad, 1f, confidence = 0f)); run(3f) }
        assertEquals(1f, sure.snapshot.value.intensity, 1e-6f)
        assertEquals(0.3f, unsure.snapshot.value.intensity, 1e-6f)
        assertTrue(sure.emotionAmounts[Emotion.Sad.ordinal] > unsure.emotionAmounts[Emotion.Sad.ordinal] + 0.4f)
    }

    @Test
    fun expressionDecaysTowardMoodWithTheHalfLife() {
        val face = engine()
        val halfLife = face.personality.director.halfLife
        face.setEmotion(EmotionSignal(Emotion.Sad, 1f))
        face.run(halfLife)
        val sad = face.emotionAmounts[Emotion.Sad.ordinal]
        assertEquals(0.52f, sad, 0.06f)
        face.run(halfLife * 4)
        assertTrue(face.emotionAmounts[Emotion.Sad.ordinal] < 0.12f)
    }

    @Test
    fun moodIsNudgedThenDriftsBackToBaseline() {
        val face = engine()
        repeat(12) {
            face.setEmotion(EmotionSignal(Emotion.Sad, if (it % 2 == 0) 1f else 0.6f))
            face.run(2f)
        }
        assertEquals(Emotion.Sad, face.snapshot.value.mood)
        face.setEmotion(EmotionSignal(Emotion.Neutral, 0f))
        face.run(1_500f)
        assertEquals(Emotion.Neutral, face.snapshot.value.mood)
    }

    @Test
    fun bigSwingsGetATransitionBeat() {
        val dexter = engine()
        dexter.setEmotion(EmotionSignal(Emotion.Sad, 0.9f))
        dexter.run(3f)
        val dexterEvents = dexter.record(FaceEvent.Reaction)
        dexter.setEmotion(EmotionSignal(Emotion.Happy, 0.9f))
        dexter.run(0.2f)
        assertTrue("play Surprise" in dexterEvents, "Dexter bridges with a surprise beat")

        val sage = engine(personality = PersonalityPresets.load("sage"))
        sage.setEmotion(EmotionSignal(Emotion.Sad, 0.9f))
        sage.run(4f)
        val sageEvents = sage.record(FaceEvent.Emotion)
        sage.setEmotion(EmotionSignal(Emotion.Happy, 0.9f))
        sage.run(0.2f)
        assertEquals(Emotion.Neutral, sage.snapshot.value.emotion, "Sage pauses on neutral first")
        sage.run(0.5f)
        assertEquals(Emotion.Happy, sage.snapshot.value.emotion)
        assertTrue("beat neutral" in sageEvents)
    }
}
