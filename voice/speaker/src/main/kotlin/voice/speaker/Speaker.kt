package voice.speaker

import face.api.Activity
import face.api.FaceController
import face.api.SpeechStyle
import face.speech.PcmBlock
import face.speech.PcmPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
            var started = false
            try {
                val blocks = chosen.synthesize(input, settings.copy(voiceId = use.voiceId))
                    .map { PcmBlock(it.samples, it.sampleRate) }
                    .buffer(LOOKAHEAD_SENTENCES)
                player.play(blocks) {
                    started = true
                    face.attachSpeech(player.track, style)
                    if (manageActivity) face.setActivity(Activity.Speaking)
                    mutableState.value = SpeakerState.Speaking(chosen.id)
                }
                mutableState.value = SpeakerState.Idle
                return
            } catch (e: CancellationException) {
                mutableState.value = SpeakerState.Idle
                throw e
            } catch (e: Exception) {
                if (started || input !is TtsInput.Text) {
                    mutableState.value = SpeakerState.Failed("${chosen.id}: ${e.message}")
                    return
                }
                tried += chosen.id
            } finally {
                engine = null
                if (started) {
                    face.detachSpeech()
                    if (manageActivity) face.setActivity(Activity.Idle)
                }
            }
        }
    }

    private companion object {
        const val LOOKAHEAD_SENTENCES = 2
    }
}
