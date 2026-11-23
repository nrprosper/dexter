package voice.kokoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroTest {
    @Test
    fun manifestPointsAtTheOfficialArchiveAndSkipsESpeak() {
        val m = KokoroEngine.manifest()
        val v = m.defaultVoice!!
        assertEquals(KokoroEngine.MODEL_ID, v.id)
        assertTrue(v.archive.download.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"))
        assertTrue(v.archive.files.values.none { "espeak" in it || "dict/" in it })
        for (f in listOf("model.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt")) assertTrue(f, f in v.archive.files)
    }

    @Test
    fun speakerIdsFollowTheVoicesBinOrder() {
        assertEquals(28, KokoroVoices.english.size)
        assertEquals(3, KokoroVoices.byId("kokoro:af_heart")!!.sid)
        assertEquals(20, KokoroVoices.byId("kokoro:bf_alice")!!.sid)
        assertEquals(27, KokoroVoices.english.last().sid)
    }

    @Test
    fun britishVoicesAreMarkedAndLabelled() {
        val alice = KokoroVoices.byId("kokoro:bf_alice")!!
        assertTrue(alice.british)
        assertEquals("Alice · British female", alice.label)
        assertFalse(KokoroVoices.default.british)
        assertEquals("Heart · American female", KokoroVoices.default.label)
    }
}
