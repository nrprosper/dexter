package face.speech

import face.api.MouthFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AmplitudeSpeechTrackTest {
    private val rate = 16_000
    private var now = 0L
    private val fake = FakeSink { now }

    // Silent for 0.5 s, then loud.
    private fun track(): AmplitudeSpeechTrack {
        val envelope = EnvelopeBuilder().apply {
            start(rate)
            analyze(Synthetic.tone(1.0, rate) { t -> if (t < 0.5) 0.0 else 0.5 })
        }
        fake.open(rate)
        return AmplitudeSpeechTrack(envelope).apply { begin(fake) }
    }

    private fun AmplitudeSpeechTrack.at(millis: Long): Float {
        val out = MouthFrame()
        sample(millis * 1_000_000L, out)
        return out.openness
    }

    @Test
    fun leadShowsTheSyllableBeforeItIsHeard() {
        val t = track().apply { leadMillis = 50 }
        assertEquals(0f, t.at(400), 0f)
        assertEquals(0.85f, t.at(470), 0.03f)
        t.leadMillis = 0
        assertEquals(0f, t.at(470), 0f)
    }

    @Test
    fun syncOffsetShiftsTheMouth() {
        val t = track().apply { leadMillis = 0; syncOffsetMillis = -100 }
        assertEquals(0f, t.at(560), 0f)
        assertEquals(0.85f, t.at(620), 0.03f)
    }

    @Test
    fun beforePlaybackAfterTheEndAndWhenInactiveTheMouthIsShut() {
        val t = track()
        now = 0
        assertEquals(0f, MouthFrame().also { t.sample(-5_000_000_000L, it) }.openness, 0f)
        assertEquals(0f, t.at(5_000), 0f)
        t.end()
        assertFalse(t.isActive)
        assertEquals(0f, t.at(700), 0f)
    }
}
