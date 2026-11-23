package assistant.core

import face.api.SpeechStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProsodyTest {
    @Test
    fun excitedIsLivelierThanSerious() {
        assertTrue(Prosody.speed(SpeechStyle.Excited) > Prosody.speed(SpeechStyle.Serious))
        assertTrue(Prosody.pitch(SpeechStyle.Funny) > Prosody.pitch(SpeechStyle.Serious))
    }

    @Test
    fun neutralIsNatural() {
        assertEquals(1f, Prosody.speed(SpeechStyle()), 1e-4f)
        assertEquals(1f, Prosody.pitch(SpeechStyle()), 1e-4f)
    }

    @Test
    fun alwaysWithinAFewPercent() {
        for (e in listOf(0f, 1f)) for (s in listOf(0f, 1f)) for (p in listOf(0f, 1f)) {
            val style = SpeechStyle(e, s, p)
            assertTrue(Prosody.speed(style) in 0.9f..1.12f && Prosody.pitch(style) in 0.9f..1.12f)
        }
    }
}
