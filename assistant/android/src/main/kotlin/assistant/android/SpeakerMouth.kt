package assistant.android

import assistant.core.Mouth
import assistant.core.ReplySentence
import face.api.SpeechStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.api.SynthesisSettings
import voice.api.TtsInput
import voice.speaker.Speaker

// The orchestrator's mouth: sentences go to the Speaker already split, so each audio chunk knows its sentence
// and the heard-sentence callback can line up per-sentence emotion and moves with the voice.
public class SpeakerMouth(private val speaker: Speaker, private val settings: () -> SynthesisSettings) : Mouth {
    override suspend fun speak(sentences: Flow<ReplySentence>, style: SpeechStyle, onSentenceHeard: (Int) -> Unit) {
        coroutineScope {
            val watcher = launch {
                var last = -1
                speaker.heardPart.collect { part ->
                    if (part >= 0 && part != last) {
                        last = part
                        onSentenceHeard(part)
                    }
                }
            }
            val job = speaker.say(TtsInput.Sentences(sentences.map { it.text }), settings(), style)
            try {
                job.join()
            } finally {
                watcher.cancel()
                if (!job.isCompleted) speaker.stop()
            }
        }
    }

    override fun stop() {
        speaker.stop()
    }
}
