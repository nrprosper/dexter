package voice.elevenlabs

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import voice.api.Availability
import voice.api.SynthesisSettings
import voice.api.TtsException
import voice.api.TtsInput
import java.net.InetSocketAddress

class ElevenLabsEngineTest {
    private val responses = HashMap<String, Pair<Int, ByteArray>>()
    private val requests = ArrayList<Triple<String, String?, String>>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val path = ex.requestURI.toString()
            requests.add(Triple(path, ex.requestHeaders.getFirst("xi-api-key"), ex.requestBody.readBytes().decodeToString()))
            val (code, body) = responses.entries.firstOrNull { path.startsWith(it.key) }?.value ?: (404 to ByteArray(0))
            ex.sendResponseHeaders(code, body.size.toLong().coerceAtLeast(-1L).let { if (body.isEmpty()) -1 else it })
            ex.responseBody.use { it.write(body) }
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"

    @AfterEach
    fun stop() = server.stop(0)

    private fun engine(key: String? = "el-key") = ElevenLabsEngine({ key }, { ElevenLabsSettings() }, base)

    private val voicesJson = """{"voices":[{"voice_id":"abc","name":"Rachel","category":"premade",
        "labels":{"gender":"female","accent":"american"},"preview_url":"https://example.com/r.mp3"},
        {"voice_id":"def","name":"Adam","category":"premade","labels":{}}],"has_more":false}""".toByteArray()

    @Test
    fun synthesizesPcmWithTheChosenVoiceAndHeaderKey() = runBlocking {
        val pcm = byteArrayOf(1, 0, -1, -1, 0, 64)
        responses["/v1/text-to-speech/abc/stream"] = 200 to pcm
        val chunks = engine().synthesize(TtsInput.Text("Hello there."), SynthesisSettings(voiceId = "elevenlabs:abc")).toList()
        assertArrayEquals(shortArrayOf(1, -1, 16384), chunks.single().samples)
        assertEquals(24_000, chunks.single().sampleRate)
        val (path, key, body) = requests.single()
        assertTrue(path.startsWith("/v1/text-to-speech/abc/stream?output_format=pcm_24000"))
        assertFalse(path.contains("el-key"), "the key must not be in the URL")
        assertEquals("el-key", key)
        val json = ObjectMapper().readTree(body)
        assertEquals("Hello there.", json.path("text").asText())
        assertEquals("eleven_flash_v2_5", json.path("model_id").asText())
        assertTrue(json.path("voice_settings").has("stability"))
    }

    @Test
    fun withoutAChosenVoiceItUsesTheFirstFromTheLibrary() = runBlocking {
        responses["/v2/voices"] = 200 to voicesJson
        responses["/v1/text-to-speech/abc/stream"] = 200 to byteArrayOf(0, 0)
        engine().synthesize(TtsInput.Text("Hi."), SynthesisSettings()).toList()
        assertTrue(requests.last().first.startsWith("/v1/text-to-speech/abc/"))
    }

    @Test
    fun libraryIsParsedAndCached() = runBlocking {
        responses["/v2/voices"] = 200 to voicesJson
        val e = engine()
        val voices = e.library()
        e.library()
        assertEquals(listOf("Rachel", "Adam"), voices.map { it.name })
        assertEquals("female · american", voices[0].description)
        assertEquals("elevenlabs:abc", voices[0].id)
        assertEquals(1, requests.count { it.first.startsWith("/v2/voices") })
    }

    @Test
    fun errorsBecomeTtsExceptionsSoTheSpeakerCanFallBack() {
        fun error(code: Int, body: String): String? {
            responses["/v1/text-to-speech/abc/stream"] = code to body.toByteArray()
            return runCatching { runBlocking { engine().synthesize(TtsInput.Text("Hi."), SynthesisSettings(voiceId = "elevenlabs:abc")).toList() } }
                .exceptionOrNull()?.let { (it as TtsException).message }
        }
        assertEquals("ElevenLabs credits used up", error(401, """{"detail":{"status":"quota_exceeded","message":"out"}}"""))
        assertEquals("ElevenLabs rejected the key", error(401, """{"detail":{"status":"invalid_api_key"}}"""))
        assertTrue(error(429, "{}")!!.contains("busy"))
    }

    @Test
    fun availabilityAndKeyCheck() = runBlocking {
        assertTrue(engine(key = null).availability() is Availability.Unavailable)
        assertEquals(Availability.Ready, engine().availability())
        responses["/v1/user"] = 200 to "{}".toByteArray()
        assertEquals(ElevenLabsKeyCheck.Ok, engine().check("k"))
        responses["/v1/user"] = 401 to "{}".toByteArray()
        assertEquals(ElevenLabsKeyCheck.Rejected, engine().check("k"))
    }
}
