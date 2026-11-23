package assistant.core

import face.api.SpeechStyle
import kotlinx.coroutines.flow.Flow

public interface Mouth {
    // Speaks sentences as they arrive; onSentenceHeard(index) fires as each one starts being heard.
    // Returns when everything has been said, or throws CancellationException when cancelled.
    public suspend fun speak(sentences: Flow<ReplySentence>, style: SpeechStyle, onSentenceHeard: (Int) -> Unit)

    public fun stop()
}
