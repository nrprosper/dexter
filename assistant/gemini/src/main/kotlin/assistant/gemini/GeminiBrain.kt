package assistant.gemini

import assistant.core.Brain
import assistant.core.BrainEvent
import assistant.core.BrainException
import assistant.core.BrainFailure
import assistant.core.DexterPrompt
import assistant.core.KeyCheck
import assistant.core.ReplyContract
import assistant.core.ReplyParser
import assistant.core.Turn
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

// Gemini as Dexter's brain, over the Gemini API's REST streaming endpoint (models/{model}:streamGenerateContent,
// server-sent events), constrained to the reply JSON schema and parsed as it arrives, like ClaudeBrain.
// The key travels in the x-goog-api-key header, never in the URL, and is never logged.
public class GeminiBrain(
    private val apiKey: () -> String?,
    private val settings: () -> GeminiSettings = { GeminiSettings() },
    private val baseUrl: String = BASE_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Brain {
    private val json = ObjectMapper()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().readTimeout(settings().timeoutSeconds, TimeUnit.SECONDS).build()
    }

    override fun reply(history: List<Turn>, user: String): Flow<BrainEvent> = flow {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: throw BrainException(BrainFailure.NoKey, "no Gemini API key set")
        val s = settings()
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/${s.model}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
            .post(json.writeValueAsString(body(s, history, user)).toRequestBody(JSON))
            .build()
        val call = http.newCall(request)
        val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        val pending = ArrayList<BrainEvent>()
        val parser = ReplyParser({ pending.add(BrainEvent.Header(it)) }, { pending.add(BrainEvent.Sentence(it)) })
        var declined = false
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw BrainException(BrainFailure.Network, "network: ${e.message}", e)
            }
            response.use { r ->
                if (!r.isSuccessful) throw failure(r)
                val source = r.body?.source() ?: throw BrainException(BrainFailure.BadReply, "empty response")
                while (true) {
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        throw BrainException(BrainFailure.Network, "stream broke: ${e.message}", e)
                    } ?: break
                    if (!line.startsWith(DATA)) continue
                    val chunk = json.readTree(line.removePrefix(DATA).trim())
                    if (chunk.path("promptFeedback").hasNonNull("blockReason")) declined = true
                    val candidate = chunk.path("candidates").path(0)
                    for (part in candidate.path("content").path("parts")) {
                        if (part.path("thought").asBoolean(false)) continue
                        part.get("text")?.asText()?.let { parser.feed(it) }
                    }
                    if (candidate.path("finishReason").asText("") in DECLINED) declined = true
                    for (e in pending) emit(e)
                    pending.clear()
                }
            }
        } finally {
            cancel?.dispose()
        }
        parser.finish()
        for (e in pending) emit(e)
        if (declined) emit(BrainEvent.Declined("safety"))
        emit(BrainEvent.Done)
    }.flowOn(io)

    // Looks the model up: free, and enough to tell a working key from a bad one.
    public suspend fun check(key: String): KeyCheck = withContext(io) {
        val request = Request.Builder().url("$baseUrl/v1beta/models/${settings().model}").header("x-goog-api-key", key.trim()).get().build()
        try {
            http.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> KeyCheck.Ok
                    r.code == HTTP_NOT_FOUND -> KeyCheck.ModelUnavailable
                    r.code == HTTP_BAD_REQUEST || r.code == HTTP_UNAUTHORIZED || r.code == HTTP_FORBIDDEN -> KeyCheck.Rejected
                    else -> KeyCheck.Error
                }
            }
        } catch (e: IOException) {
            KeyCheck.Offline
        }
    }

    internal fun body(s: GeminiSettings, history: List<Turn>, user: String): Map<String, Any> {
        val contents = ArrayList<Map<String, Any>>()
        for (t in history) {
            contents.add(message("user", t.user))
            contents.add(message("model", t.assistant))
        }
        contents.add(message("user", user))
        return mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to DexterPrompt.text))),
            "contents" to contents,
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "responseJsonSchema" to SCHEMA,
                "maxOutputTokens" to s.maxOutputTokens,
                "thinkingConfig" to mapOf("thinkingLevel" to s.thinkingLevel),
            ),
        )
    }

    private fun message(role: String, text: String): Map<String, Any> = mapOf("role" to role, "parts" to listOf(mapOf("text" to text)))

    private fun failure(r: Response): BrainException {
        val text = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
        val status = runCatching { json.readTree(text) }.getOrNull()?.path("error")
        val reason = status?.path("details")?.findValues("reason")?.firstOrNull()?.asText().orEmpty()
        return when {
            r.code == HTTP_TOO_MANY -> BrainException(BrainFailure.RateLimit, "Gemini quota or rate limit reached")
            r.code == HTTP_UNAUTHORIZED || r.code == HTTP_FORBIDDEN || reason == "API_KEY_INVALID" ->
                BrainException(BrainFailure.Auth, "Gemini rejected the API key")
            r.code >= HTTP_SERVER -> BrainException(BrainFailure.Server, "Gemini error ${r.code}")
            else -> BrainException(BrainFailure.BadReply, "Gemini rejected the request (${r.code}): ${status?.path("message")?.asText().orEmpty()}")
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com"
        const val DATA = "data:"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY = 429
        const val HTTP_SERVER = 500
        val JSON = "application/json; charset=utf-8".toMediaType()
        val DECLINED = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION")
        val SCHEMA: JsonNode = ObjectMapper().readTree(ReplyContract.schema)
    }
}
