package voice.speaker

import face.api.Activity
import face.api.FaceController
import face.api.SpeechStyle
import face.speech.PcmBlock
import face.speech.PcmPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.api.RouteChoice
import voice.api.SynthesisSettings
import voice.api.TtsEngine
import voice.api.TtsInput
import voice.api.TtsRouter

// Last utterance: worst sentence synthesis time vs its audio length (> 1 means the voice can't keep up: gaps),
// and playback underruns (cuts). -1 when unknown.
public data class SpeechStats(val sentences: Int = 0, val worstRealTime: Float = 0f, val underruns: Int = -1)

public sealed interface SpeakerState {
    public data object Idle : SpeakerState
    public data class Preparing(val engineId: String) : SpeakerState
    public data class Speaking(val engineId: String) : SpeakerState
    public data class Failed(val reason: String) : SpeakerState
}

// Text in, voice and moving mouth out. Engine-agnostic: the router picks an engine, every engine feeds the same player,
// and the face only ever sees the player's SpeechTrack. Latest say() wins; stop() interrupts at once.
// If an engine fails before any audio plays, the next engine is tried (whole-text input only; a stream can't be replayed).
public class Speaker(
    private val router: TtsRouter,
    private val player: PcmPlayer,
    private val face: FaceController,
    private val scope: CoroutineScope,
    private val manageActivity: Boolean = true,
) {
    private val mutableState = MutableStateFlow<SpeakerState>(SpeakerState.Idle)
    public val state: StateFlow<SpeakerState> = mutableState

    private var job: Job? = null
    private val mutableStats = MutableStateFlow(SpeechStats())
    public val stats: StateFlow<SpeechStats> = mutableStats

    // Which input part (TtsInput.Sentences index) is being heard now; -1 when nothing is playing.
    public val heardPart: StateFlow<Int> get() = player.heardTag

    @Volatile
    private var engine: TtsEngine? = null

    public fun say(text: String, settings: SynthesisSettings = SynthesisSettings(), style: SpeechStyle = SpeechStyle()): Job =
        say(TtsInput.Text(text), settings, style)

    public fun say(input: TtsInput, settings: SynthesisSettings = SynthesisSettings(), style: SpeechStyle = SpeechStyle()): Job {
        val previous = job
        stop()
        val next = scope.launch {
            previous?.join()
            speak(input, settings, style)
        }
        job = next
        return next
    }

    // Raw PCM (test signals, earcons) through the same player and face path as speech.
    public fun play(pcm: Flow<PcmBlock>, style: SpeechStyle = SpeechStyle()): Job {
        val previous = job
        stop()
        val next = scope.launch {
            previous?.join()
            mutableState.value = SpeakerState.Preparing(PCM)
            try {
                playBlocks(PCM, pcm, style)
                mutableState.value = SpeakerState.Idle
            } catch (e: CancellationException) {
                mutableState.value = SpeakerState.Idle
                throw e
            }
        }
        job = next
        return next
    }

    public fun stop() {
        engine?.interrupt()
        player.stop()
        job?.cancel()
    }

    private suspend fun speak(input: TtsInput, settings: SynthesisSettings, style: SpeechStyle) {
        val tried = HashSet<String>()
        while (true) {
            val choice = router.choose(settings, exclude = tried)
            if (choice is RouteChoice.NoEngine) {
                mutableState.value = SpeakerState.Failed(choice.reasons.entries.joinToString { "${it.key}: ${it.value}" })
                return
            }
            val use = choice as RouteChoice.Use
            val chosen = use.engine
            engine = chosen
            mutableState.value = SpeakerState.Preparing(chosen.id)
            var last = System.nanoTime()
            var count = 0
            var worst = 0f
            val blocks = chosen.synthesize(input, settings.copy(voiceId = use.voiceId ?: settings.voiceId))
                .map {
                    val now = System.nanoTime()
                    val audioNanos = it.samples.size * NANOS_PER_SECOND / it.sampleRate
                    if (audioNanos > 0) worst = maxOf(worst, (now - last).toFloat() / audioNanos)
                    last = now
                    count++
                    PcmBlock(it.samples, it.sampleRate, it.part)
                }
                .buffer(LOOKAHEAD_SENTENCES)
            try {
                playBlocks(chosen.id, blocks, style)
                mutableStats.value = SpeechStats(count, worst, player.lastUnderruns)
                mutableState.value = SpeakerState.Idle
                return
            } catch (e: CancellationException) {
                mutableState.value = SpeakerState.Idle
                throw e
            } catch (e: StartedFailure) {
                mutableState.value = SpeakerState.Failed("${chosen.id}: ${e.cause?.message}")
                return
            } catch (e: Exception) {
                if (input !is TtsInput.Text) {
                    mutableState.value = SpeakerState.Failed("${chosen.id}: ${e.message}")
                    return
                }
                tried += chosen.id
            } finally {
                engine = null
            }
        }
    }

    // Returns whether any audio started. A failure after audio started is wrapped, so it isn't retried elsewhere.
    private suspend fun playBlocks(source: String, blocks: Flow<PcmBlock>, style: SpeechStyle): Boolean {
        var started = false
        try {
            player.play(blocks) {
                started = true
                face.attachSpeech(player.track, style)
                if (manageActivity) face.setActivity(Activity.Speaking)
                mutableState.value = SpeakerState.Speaking(source)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started) throw StartedFailure(e)
            throw e
        } finally {
            if (started) {
                face.detachSpeech()
                if (manageActivity) face.setActivity(Activity.Idle)
            }
        }
        return started
    }

    private class StartedFailure(cause: Exception) : Exception(cause)

    private companion object {
        const val PCM = "pcm"
        const val LOOKAHEAD_SENTENCES = 2
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
