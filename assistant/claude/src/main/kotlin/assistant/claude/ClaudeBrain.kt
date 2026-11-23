package assistant.claude

import assistant.core.Brain
import assistant.core.BrainEvent
import assistant.core.BrainException
import assistant.core.BrainFailure
import assistant.core.ReplyContract
import assistant.core.ReplyParser
import assistant.core.Turn
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Duration

// Claude as Dexter's brain: one streaming Messages request per turn, constrained to the reply JSON schema, parsed
// as it arrives so the header reaches the face first. The server-side refusal fallback is on, so a declined
// request can be retried on another model by the API. The API key comes from the caller and is never logged.
public class ClaudeBrain(
    private val apiKey: () -> String?,
    private val settings: () -> ClaudeSettings = { ClaudeSettings() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Brain {
    private var client: AnthropicClient? = null
    private var clientFor: String? = null

    override fun reply(history: List<Turn>, user: String): Flow<BrainEvent> = flow {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: throw BrainException(BrainFailure.NoKey, "no API key set")
        val params = params(settings(), history, user)
        val pending = ArrayList<BrainEvent>()
        val parser = ReplyParser({ pending.add(BrainEvent.Header(it)) }, { pending.add(BrainEvent.Sentence(it)) })
        var declined = false
        val stream = guarded { client(key).beta().messages().createStreaming(params) }
        val closer = currentCoroutineContext()[Job]?.invokeOnCompletion { stream.close() }
        try {
            val events = stream.stream().iterator()
            while (guarded { events.hasNext() }) {
                val event = events.next()
                event.contentBlockDelta().ifPresent { d -> d.delta().text().ifPresent { parser.feed(it.text()) } }
                event.messageDelta().ifPresent { d ->
                    if (d.delta().stopReason().map { it == BetaStopReason.REFUSAL }.orElse(false)) declined = true
                }
                for (e in pending) emit(e)
                pending.clear()
            }
        } finally {
            closer?.dispose()
            stream.close()
        }
        parser.finish()
        for (e in pending) emit(e)
        if (declined) emit(BrainEvent.Declined("refusal"))
        emit(BrainEvent.Done)
    }.flowOn(io)

    // Asks the Models API about the configured model: free, and enough to tell a working key from a bad one.
    public suspend fun check(key: String): KeyCheck = withContext(io) {
        val probe = AnthropicOkHttpClient.builder().apiKey(key.trim()).timeout(Duration.ofSeconds(CHECK_TIMEOUT)).maxRetries(0).build()
        try {
            probe.models().retrieve(settings().model)
            KeyCheck.Ok
        } catch (e: UnauthorizedException) {
            KeyCheck.Rejected
        } catch (e: PermissionDeniedException) {
            KeyCheck.Rejected
        } catch (e: NotFoundException) {
            KeyCheck.ModelUnavailable
        } catch (e: AnthropicIoException) {
            KeyCheck.Offline
        } catch (e: AnthropicServiceException) {
            KeyCheck.Error
        } finally {
            probe.close()
        }
    }

    internal fun params(s: ClaudeSettings, history: List<Turn>, user: String): MessageCreateParams {
        val b = MessageCreateParams.builder()
            .model(s.model)
            .maxTokens(s.maxTokens)
            .systemOfBetaTextBlockParams(
                listOf(BetaTextBlockParam.builder().text(DexterPrompt.text).cacheControl(BetaCacheControlEphemeral.builder().build()).build()),
            )
        for (t in history) {
            b.addUserMessage(t.user)
            b.addAssistantMessage(t.assistant)
        }
        b.addUserMessage(user)
        b.addBeta(FALLBACK_BETA)
        b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        b.putAdditionalBodyProperty(
            "output_config",
            JsonValue.from(
                mapOf(
                    "effort" to s.effort,
                    "format" to mapOf("type" to "json_schema", "schema" to SCHEMA),
                ),
            ),
        )
        return b.build()
    }

    private fun client(key: String): AnthropicClient {
        val existing = client
        if (existing != null && clientFor == key) return existing
        existing?.close()
        val s = settings()
        val made = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(s.timeoutSeconds))
            .maxRetries(1)
            .build()
        client = made
        clientFor = key
        return made
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: UnauthorizedException) {
        throw BrainException(BrainFailure.Auth, "API key rejected", e)
    } catch (e: PermissionDeniedException) {
        throw BrainException(BrainFailure.Auth, "API key not allowed", e)
    } catch (e: RateLimitException) {
        throw BrainException(BrainFailure.RateLimit, "rate limited", e)
    } catch (e: BadRequestException) {
        throw BrainException(BrainFailure.BadReply, "request rejected: ${e.message}", e)
    } catch (e: AnthropicServiceException) {
        throw BrainException(BrainFailure.Server, "API error ${e.statusCode()}", e)
    } catch (e: AnthropicIoException) {
        throw BrainException(BrainFailure.Network, "network: ${e.message}", e)
    }

    private companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val CHECK_TIMEOUT = 15L
        val SCHEMA: Any = ObjectMapper().readValue(ReplyContract.schema, Map::class.java)
    }
}

public enum class KeyCheck { Ok, Rejected, ModelUnavailable, Offline, Error }
