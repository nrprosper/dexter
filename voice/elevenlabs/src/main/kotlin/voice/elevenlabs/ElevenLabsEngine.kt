package voice.elevenlabs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.api.Availability
import voice.api.EngineCapabilities
import voice.api.EngineLocation
import voice.api.SentenceAudio
import voice.api.SentenceTtsEngine
import voice.api.SynthesisSettings
import voice.api.TtsException
import voice.api.VoiceDescriptor
import voice.api.VoiceQuality
import java.io.IOException
import java.util.concurrent.TimeUnit

public enum class ElevenLabsKeyCheck { Ok, Rejected, Offline, Error }

// ElevenLabs as a cloud voice: one streaming request per sentence (Flash v2.5 by default, ~100 ms), raw 24 kHz PCM, so it
// plays through our player and the mouth follows it like any other voice. The key goes in the xi-api-key header and is
// never logged. Failures throw TtsException, and the Speaker falls back to the phone's voice before any audio plays.
public class ElevenLabsEngine(
    private val apiKey: () -> String?,
    private val settings: () -> ElevenLabsSettings = { ElevenLabsSettings() },
    private val baseUrl: String = BASE_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SentenceTtsEngine() {
    private val json = ObjectMapper()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS).readTimeout(settings().timeoutSeconds, TimeUnit.SECONDS).build()
    }
    private val voicesLock = Mutex()
    private var cached: List<ElevenLabsVoice>? = null
    private var cachedFor: String? = null
    private var cachedAt = 0L

    override val id: String = ID
    override val capabilities: EngineCapabilities = EngineCapabilities(EngineLocation.Online, streamingOutput = true)

    override suspend fun availability(): Availability =
        if (apiKey().isNullOrBlank()) Availability.Unavailable("no ElevenLabs key") else Availability.Ready

    override suspend fun voices(): List<VoiceDescriptor> = runCatching { library() }.getOrDefault(emptyList()).map {
        VoiceDescriptor(it.id, ID, it.name, "en", SAMPLE_RATE, quality = VoiceQuality.High, license = "ElevenLabs")
    }

    // The account's voices (premade, cloned, library), cached for a few minutes; newest settings never need a refetch.
    public suspend fun library(refresh: Boolean = false): List<ElevenLabsVoice> = voicesLock.withLock {
        val key = requireKey()
        val now = System.currentTimeMillis()
        cached?.let { if (!refresh && cachedFor == key && now - cachedAt < CACHE_MILLIS) return@withLock it }
        val all = ArrayList<ElevenLabsVoice>()
        var token: String? = null
        for (page in 0 until MAX_PAGES) {
            val url = "$baseUrl/v2/voices".toHttpUrl().newBuilder().addQueryParameter("page_size", "100")
                .apply { token?.let { addQueryParameter("next_page_token", it) } }.build()
            val body = get(url.toString(), key)
            for (v in body.path("voices")) {
                val labels = v.path("labels")
                val description = listOf("gender", "age", "accent", "use_case")
                    .mapNotNull { labels.path(it).asText("").takeIf { s -> s.isNotBlank() } }.joinToString(" · ")
                all.add(
                    ElevenLabsVoice(
                        voiceId = v.path("voice_id").asText(),
                        name = v.path("name").asText(),
                        category = v.path("category").asText(""),
                        description = description,
                        previewUrl = v.path("preview_url").asText("").takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (!body.path("has_more").asBoolean(false)) break
            token = body.path("next_page_token").asText("").takeIf { it.isNotBlank() } ?: break
        }
        cached = all
        cachedFor = key
        cachedAt = now
        all
    }

    override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio {
        val key = requireKey()
        val wanted = settings.voiceId
        val voice = wanted?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
            ?: library().firstOrNull()?.voiceId
            ?: throw TtsException("no ElevenLabs voice available")
        val s = settings()
        val body = mapOf(
            "text" to text,
            "model_id" to s.model,
            "voice_settings" to mapOf(
                "stability" to s.stability,
                "similarity_boost" to s.similarity,
                "style" to s.style,
                "use_speaker_boost" to s.speakerBoost,
                "speed" to settings.speed.coerceIn(MIN_SPEED, MAX_SPEED),
            ),
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/text-to-speech/$voice/stream?output_format=pcm_$SAMPLE_RATE")
            .header(KEY_HEADER, key)
            .post(json.writeValueAsString(body).toRequestBody(JSON))
            .build()
        return withContext(io) {
            val call = http.newCall(request)
            val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r ->
                    if (!r.isSuccessful) throw failure(r.code, runCatching { r.body?.string() }.getOrNull())
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    SentenceAudio(pcm16(bytes), SAMPLE_RATE)
                }
            } catch (e: IOException) {
                throw TtsException("ElevenLabs unreachable: ${e.message}", e)
            } finally {
                cancel?.dispose()
            }
        }
    }

    // GET /v1/user: free, and enough to tell a working key from a bad one.
    public suspend fun check(key: String): ElevenLabsKeyCheck = withContext(io) {
        val request = Request.Builder().url("$baseUrl/v1/user").header(KEY_HEADER, key.trim()).get().build()
        try {
            http.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> ElevenLabsKeyCheck.Ok
                    r.code == HTTP_UNAUTHORIZED || r.code == HTTP_FORBIDDEN -> ElevenLabsKeyCheck.Rejected
                    else -> ElevenLabsKeyCheck.Error
                }
            }
        } catch (e: IOException) {
            ElevenLabsKeyCheck.Offline
        }
    }

    private suspend fun get(url: String, key: String): JsonNode = withContext(io) {
        val request = Request.Builder().url(url).header(KEY_HEADER, key).get().build()
        try {
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw failure(r.code, text)
                json.readTree(text)
            }
        } catch (e: IOException) {
            throw TtsException("ElevenLabs unreachable: ${e.message}", e)
        }
    }

    private fun requireKey(): String = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: throw TtsException("no ElevenLabs key")

    private fun failure(code: Int, body: String?): TtsException {
        val detail = runCatching { json.readTree(body.orEmpty()).path("detail") }.getOrNull()
        val status = detail?.path("status")?.asText("").orEmpty()
        val message = detail?.path("message")?.asText("").orEmpty()
        val reason = when {
            code == HTTP_UNAUTHORIZED && status == "quota_exceeded" -> "ElevenLabs credits used up"
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> "ElevenLabs rejected the key"
            code == HTTP_TOO_MANY -> "ElevenLabs is busy (rate or concurrency limit)"
            code >= HTTP_SERVER -> "ElevenLabs error $code"
            else -> "ElevenLabs rejected the request ($code) $message"
        }
        return TtsException(reason.trim())
    }

    private fun pcm16(bytes: ByteArray): ShortArray =
        ShortArray(bytes.size / 2) { ((bytes[2 * it].toInt() and 0xFF) or (bytes[2 * it + 1].toInt() shl 8)).toShort() }

    public companion object {
        public const val ID: String = "elevenlabs"
        public const val PREFIX: String = "elevenlabs:"
        public const val SAMPLE_RATE: Int = 24_000
        private const val BASE_URL = "https://api.elevenlabs.io"
        private const val KEY_HEADER = "xi-api-key"
        private const val CONNECT_SECONDS = 10L
        private const val CACHE_MILLIS = 10 * 60_000L
        private const val MAX_PAGES = 5
        private const val MIN_SPEED = 0.7f
        private const val MAX_SPEED = 1.2f
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER = 500
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
