package voice.piper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import voice.piper.model.Download
import voice.piper.model.VoiceManifest

class VoiceManifestTest {
    @Test
    fun bundledManifestIsValid() {
        val m = VoiceManifest.loadBundled()
        val voice = m.defaultVoice!!
        assertEquals("en_US-ljspeech-medium-int8", voice.id)
        assertEquals(22_050, voice.sampleRate)
        assertTrue(voice.archive.download.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/"))
        assertTrue(voice.archive.files.values.none { "espeak" in it })
        assertEquals("lexicon-en-us.txt", m.asset(voice.requires.single())!!.file)
    }

    @Test
    fun downloadsMustBeHttpsWithAFullChecksum() {
        val sha = "a".repeat(64)
        assertTrue(runCatching { Download("http://example.com/x", sha, 1) }.isFailure)
        assertTrue(runCatching { Download("https://example.com/x", "abc", 1) }.isFailure)
        assertTrue(runCatching { Download("https://example.com/x", sha, 0) }.isFailure)
        assertTrue(runCatching { Download("http://127.0.0.1:8080/x", sha, 1) }.isSuccess)
    }

    @Test
    fun unknownKeysAndUnknownAssetsAreRejected() {
        assertTrue(runCatching { VoiceManifest.decode("""{"format":1,"voices":[],"extra":1}""") }.isFailure)
        assertTrue(runCatching { VoiceManifest.decode("""{"format":2,"voices":[]}""") }.isFailure)
    }
}
