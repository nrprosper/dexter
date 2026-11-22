package voice.api

import kotlinx.coroutines.flow.Flow

// Turns text into audio. synthesize() is cold: collecting starts synthesis, cancelling the collector stops it.
public interface TtsEngine {
    public val id: String
    public val capabilities: EngineCapabilities

    public suspend fun availability(): Availability

    public suspend fun voices(): List<VoiceDescriptor>

    public fun synthesize(input: TtsInput, settings: SynthesisSettings = SynthesisSettings()): Flow<PcmChunk>
}

public sealed interface TtsInput {
    public class Text(public val text: String) : TtsInput

    // Text that arrives in pieces (an LLM stream); engines without streaming input group it into sentences.
    public class Stream(public val parts: Flow<String>) : TtsInput
}

public enum class EngineLocation { Offline, Online }

public data class EngineCapabilities(
    val location: EngineLocation,
    val streamingInput: Boolean = false,
    val streamingOutput: Boolean = false,
    val timestamps: AlignmentUnit? = null,
)

public sealed interface Availability {
    public data object Ready : Availability
    public data object NeedsDownload : Availability
    public data class Unavailable(val reason: String) : Availability
}

public class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)
