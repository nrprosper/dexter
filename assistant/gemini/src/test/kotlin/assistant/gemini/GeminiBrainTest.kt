package assistant.gemini

import assistant.core.BrainEvent
import assistant.core.BrainException
import assistant.core.BrainFailure
import assistant.core.KeyCheck
import assistant.core.Turn
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import face.api.Emotion
import face.api.StageMove
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class GeminiBrainTest {
    private var status = 200
    private var reply = ""
    private var lastPath = ""
    private var lastKey: String? = null
    private var lastBody = ""
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            lastPath = ex.requestURI.toString()
            lastKey = ex.requestHeaders.getFirst("x-goog-api-key")
            lastBody = ex.requestBody.readBytes().decodeToString()
            val bytes = reply.toByteArray()
            ex.responseHeaders.add("Content-Type", if (status == 200) "text/event-stream" else "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"

    @AfterEach
    fun stop() = server.stop(0)

    private fun brain(key: String? = "test-key") = GeminiBrain({ key }, { GeminiSettings() }, base)

    // Splits the reply JSON across several SSE events, the way a stream delivers it.
    private fun sse(json: String, finish: String = "STOP"): String {
        val mapper = ObjectMapper()
        val pieces = json.chunked(17)
        return pieces.mapIndexed { i, p ->
            val chunk = mapOf(
                "candidates" to listOf(
                    mapOf("content" to mapOf("role" to "model", "parts" to listOf(mapOf("text" to p))))
                        .let { if (i == pieces.lastIndex) it + ("finishReason" to finish) else it },
                ),
            )
            "data: " + mapper.writeValueAsString(chunk) + "\r\n\r\n"
        }.joinToString("")
    }

    private val replyJson = """{"emotion":"playful","intensity":0.8,"confidence":0.9,"tone":"funny",""" +
        """"sentences":[{"text":"Octopuses have three hearts!"},{"text":"Wild, right?","move":"hop"}]}"""

    @Test
    fun streamsTheFeelingThenTheSentences() = runBlocking {
        reply = sse(replyJson)
        val events = brain().reply(listOf(Turn("hi", "Hello!")), "tell me a fact").toList()
        val header = events.first() as BrainEvent.Header
        assertEquals(Emotion.Playful, header.feeling.emotion)
        val sentences = events.filterIsInstance<BrainEvent.Sentence>().map { it.sentence }
        assertEquals(listOf("Octopuses have three hearts!", "Wild, right?"), sentences.map { it.text })
        assertEquals(StageMove.Hop, sentences[1].move)
        assertEquals(BrainEvent.Done, events.last())
        assertTrue(lastPath.startsWith("/v1beta/models/gemini-4-hydrogen:streamGenerateContent?alt=sse"))
        assertFalse(lastPath.contains("key"), "the key must not be in the URL")
        assertEquals("test-key", lastKey)
    }

    @Test
    fun requestCarriesThePromptSchemaHistoryAndThinkingLevel() = runBlocking {
        reply = sse(replyJson)
        brain().reply(listOf(Turn("hi", "Hello!")), "and now?").toList()
        val body = ObjectMapper().readTree(lastBody)
        assertTrue(body.path("systemInstruction").path("parts").path(0).path("text").asText().contains("Dexter"))
        assertEquals(listOf("user", "model", "user"), body.path("contents").map { it.path("role").asText() })
        val config = body.path("generationConfig")
        assertEquals("application/json", config.path("responseMimeType").asText())
        assertEquals("object", config.path("responseJsonSchema").path("type").asText())
        assertEquals("LOW", config.path("thinkingConfig").path("thinkingLevel").asText())
    }

    @Test
    fun safetyStopsBecomeDeclined() = runBlocking {
        reply = sse("""{"emotion":"neutral","intensity":0.3,"confidence":1,"tone":"calm","sentences":[]}""", finish = "SAFETY")
        assertTrue(brain().reply(emptyList(), "x").toList().any { it is BrainEvent.Declined })
    }

    @Test
    fun httpErrorsMapToFailures() {
        fun failureFor(code: Int, body: String): BrainFailure? {
            status = code
            reply = body
            return runCatching { runBlocking { brain().reply(emptyList(), "x").toList() } }
                .exceptionOrNull()?.let { (it as BrainException).failure }
        }
        assertEquals(BrainFailure.RateLimit, failureFor(429, """{"error":{"code":429,"message":"quota"}}"""))
        assertEquals(
            BrainFailure.Auth,
            failureFor(400, """{"error":{"code":400,"message":"API key not valid","details":[{"reason":"API_KEY_INVALID"}]}}"""),
        )
        assertEquals(BrainFailure.Server, failureFor(503, """{"error":{"code":503}}"""))
        assertEquals(BrainFailure.BadReply, failureFor(400, """{"error":{"code":400,"message":"bad schema"}}"""))
    }

    @Test
    fun noKeyFailsWithoutCallingTheApi() {
        val failure = runCatching { runBlocking { brain(key = null).reply(emptyList(), "x").toList() } }.exceptionOrNull()
        assertEquals(BrainFailure.NoKey, (failure as BrainException).failure)
        assertEquals("", lastPath)
    }

    @Test
    fun keyCheckMapsStatuses() = runBlocking {
        reply = "{}"
        status = 200
        assertEquals(KeyCheck.Ok, brain().check("k"))
        status = 400
        assertEquals(KeyCheck.Rejected, brain().check("k"))
        status = 404
        assertEquals(KeyCheck.ModelUnavailable, brain().check("k"))
    }
}
