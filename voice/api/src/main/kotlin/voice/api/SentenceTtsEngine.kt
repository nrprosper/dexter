package voice.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

// Audio for one sentence; alignment offsets are relative to its first sample.
public class SentenceAudio(public val samples: ShortArray, public val sampleRate: Int, public val alignment: Alignment? = null)

// Base for engines that synthesize one whole sentence at a time (Piper, the system engine).
// Cancellation is checked between sentences; synthesizeSentence should also stop early when its coroutine is cancelled.
public abstract class SentenceTtsEngine(private val splitter: SentenceSplitter = SentenceSplitter()) : TtsEngine {
    protected abstract suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio

    // Engines can rewrite a sentence before synthesis (number and symbol expansion, for example).
    protected open fun prepare(text: String): String = text

    override fun synthesize(input: TtsInput, settings: SynthesisSettings): Flow<PcmChunk> = flow {
        var position = 0L
        sentences(input).collect { sentence ->
            val text = prepare(sentence.text)
            if (text.isBlank()) return@collect
            currentCoroutineContext().ensureActive()
            val audio = synthesizeSentence(text, settings)
            currentCoroutineContext().ensureActive()
            if (audio.samples.isEmpty()) return@collect
            emit(PcmChunk(audio.samples, audio.sampleRate, position, sentence.range, audio.alignment, sentence.part))
            position += audio.samples.size
        }
    }

    private fun sentences(input: TtsInput): Flow<Sentence> = when (input) {
        is TtsInput.Text -> flowOf(*splitter.split(input.text).toTypedArray())
        is TtsInput.Stream -> splitter.stream(input.parts)
        is TtsInput.Sentences -> flow {
            var part = 0
            input.sentences.collect { s ->
                for (piece in splitter.split(s)) emit(Sentence(piece.text, piece.range, part))
                part++
            }
        }
    }
}
