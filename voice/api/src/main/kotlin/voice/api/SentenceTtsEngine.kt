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

    // When > 0, the first sentence of an utterance is split at a clause mark if longer than this, so a slow engine
    // starts speaking sooner while the rest is made during playback.
    protected open val quickStartChars: Int = 0

    override fun synthesize(input: TtsInput, settings: SynthesisSettings): Flow<PcmChunk> = flow {
        var position = 0L
        var first = true
        sentences(input).collect { whole ->
            val pieces = if (first) splitter.quickStart(whole, quickStartChars) else listOf(whole)
            first = false
            for (sentence in pieces) speak(sentence, settings, position)?.let {
                emit(it)
                position += it.samples.size
            }
        }
    }

    private suspend fun speak(sentence: Sentence, settings: SynthesisSettings, position: Long): PcmChunk? {
        run {
            val text = prepare(sentence.text)
            if (text.isBlank()) return null
            currentCoroutineContext().ensureActive()
            val audio = synthesizeSentence(text, settings)
            currentCoroutineContext().ensureActive()
            if (audio.samples.isEmpty()) return null
            return PcmChunk(audio.samples, audio.sampleRate, position, sentence.range, audio.alignment, sentence.part)
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
