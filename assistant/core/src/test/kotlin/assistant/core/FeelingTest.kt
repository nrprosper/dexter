package assistant.core

import face.api.Emotion
import face.api.SpeechStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeelingTest {
    @Test
    fun toneChoosesTheSpeechStyle() {
        assertEquals(SpeechStyle.Serious.seriousness, Feeling(tone = Tone.Serious).style().seriousness)
        assertEquals(SpeechStyle.Funny.playfulness, Feeling(tone = Tone.Funny).style().playfulness)
        assertEquals(SpeechStyle().seriousness, Feeling(tone = Tone.Neutral).style().seriousness)
    }

    @Test
    fun strongerFeelingsAreMoreAnimated() {
        val mild = Feeling(Emotion.Happy, 0.1f, tone = Tone.Excited).style().energy
        val strong = Feeling(Emotion.Happy, 1f, tone = Tone.Excited).style().energy
        assertTrue(strong > mild)
        assertTrue(strong <= 1f)
    }

    @Test
    fun signalIsClamped() {
        val s = Feeling(Emotion.Sad, 2f, -1f).signal()
        assertEquals(1f, s.intensity)
        assertEquals(0f, s.confidence)
    }
}
