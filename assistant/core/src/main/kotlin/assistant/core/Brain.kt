package assistant.core

import face.api.Emotion
import face.api.StageMove
import kotlinx.coroutines.flow.Flow

// emotion and move are optional per-sentence directions, applied as that sentence starts being heard.
public class ReplySentence(
    public val index: Int,
    public val text: String,
    public val emotion: Emotion? = null,
    public val move: StageMove? = null,
)

public sealed interface BrainEvent {
    // Always first: the face changes on this, before any sentence is spoken.
    public data class Header(val feeling: Feeling) : BrainEvent

    public data class Sentence(val sentence: ReplySentence) : BrainEvent

    // The model declined to answer (a refusal); the reply so far, if any, still stands.
    public data class Declined(val reason: String) : BrainEvent

    public data object Done : BrainEvent
}

public enum class BrainFailure { NoKey, Auth, Network, RateLimit, Server, BadReply }

public class BrainException(public val failure: BrainFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

public class Turn(public val user: String, public val assistant: String)

// Cold: collecting sends the request, cancelling the collector abandons it. Failures are thrown as BrainException.
public interface Brain {
    public fun reply(history: List<Turn>, user: String): Flow<BrainEvent>
}
