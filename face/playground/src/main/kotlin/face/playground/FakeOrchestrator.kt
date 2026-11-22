package face.playground

import face.api.Activity
import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.MouthFrame
import face.api.SpeechStyle
import face.api.SpeechTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Stands in for the real orchestrator: it only knows FaceController, and calls it from a background dispatcher.
internal class FakeOrchestrator(private val face: FaceController, private val scope: CoroutineScope) {
    private var voiceJob: Job? = null
    private var scenarioJob: Job? = null
    private var noisyJob: Job? = null

    fun activity(activity: Activity) {
        if (activity != Activity.Speaking) face.detachSpeech()
        if (activity == Activity.Speaking) face.attachSpeech(SyntheticSpeech(), SpeechStyle.Excited)
        face.setActivity(activity)
    }

    fun emotion(emotion: Emotion, intensity: Float) = face.setEmotion(EmotionSignal(emotion, intensity))

    fun react(reaction: FaceReaction) = face.react(reaction)

    fun userTalking(on: Boolean) {
        voiceJob?.cancel()
        voiceJob = if (on) scope.launch(Dispatchers.Default) { talk(Long.MAX_VALUE) } else null
        if (!on) face.setUserVoiceLevel(0f)
    }

    // An LLM that labels too often and too randomly, mostly "happy": the Director should keep the face calm.
    fun noisyLlm(on: Boolean) {
        noisyJob?.cancel()
        noisyJob = if (!on) null else scope.launch(Dispatchers.Default) {
            val rng = Random(System.nanoTime())
            while (isActive) {
                delay(rng.nextLong(NOISY_MIN_MILLIS, NOISY_MAX_MILLIS))
                val burst = if (rng.nextFloat() < BURST_CHANCE) 3 else 1
                repeat(burst) {
                    val emotion = if (rng.nextFloat() < HAPPY_BIAS) Emotion.Happy else Emotion.entries.random(rng)
                    face.setEmotion(EmotionSignal(emotion, rng.range(0.3f, 1f), rng.range(0.2f, 1f)))
                    if (burst > 1) delay(BURST_GAP_MILLIS)
                }
            }
        }
    }

    fun runScenario() {
        scenarioJob?.cancel()
        scenarioJob = scope.launch(Dispatchers.Default) {
            face.setEmotion(EmotionSignal(Emotion.Curious, 0.5f))
            face.setActivity(Activity.Listening)
            talk(LISTEN_MILLIS)
            face.setActivity(Activity.Thinking)
            delay(THINK_MILLIS)
            face.react(FaceReaction.Realization)
            face.setEmotion(EmotionSignal(Emotion.Happy, 0.7f))
            face.attachSpeech(SyntheticSpeech(), SpeechStyle.Excited)
            face.setActivity(Activity.Speaking)
            delay(SPEAK_MILLIS)
            face.detachSpeech()
            face.setActivity(Activity.Idle)
            face.setEmotion(EmotionSignal(Emotion.Neutral, 0f))
        }
    }

    // Fake user speech: bursts of syllable-like level peaks, sent at about 30 Hz.
    private suspend fun CoroutineScope.talk(millis: Long) {
        val end = System.currentTimeMillis() + millis
        var t = 0.0
        while (isActive && System.currentTimeMillis() < end) {
            val syllable = max(0.0, sin(t * 2 * PI * 3.2))
            val phrase = if ((t % 2.4) < 1.9) 1.0 else 0.1
            face.setUserVoiceLevel((syllable * phrase * 0.9).toFloat())
            delay(VOICE_STEP_MILLIS)
            t += VOICE_STEP_MILLIS / 1000.0
        }
        face.setUserVoiceLevel(0f)
    }

    private companion object {
        const val LISTEN_MILLIS = 3_000L
        const val THINK_MILLIS = 4_000L
        const val SPEAK_MILLIS = 5_000L
        const val VOICE_STEP_MILLIS = 33L
        const val NOISY_MIN_MILLIS = 800L
        const val NOISY_MAX_MILLIS = 3_000L
        const val BURST_CHANCE = 0.15f
        const val BURST_GAP_MILLIS = 250L
        const val HAPPY_BIAS = 0.45f
    }
}

private fun Random.range(from: Float, until: Float): Float = from + (until - from) * nextFloat()

// Syllables at about 4.5 Hz with short pauses between phrases; computed from the timestamp, so it never allocates.
internal class SyntheticSpeech : SpeechTrack {
    override val isActive: Boolean = true

    override fun sample(nowNanos: Long, out: MouthFrame) {
        val t = nowNanos / 1e9
        val syllable = max(0.0, sin(t * 2 * PI * 4.5))
        val phrase = if ((t % 2.2) < 1.8) 1.0 else 0.0
        out.openness = (syllable * phrase).toFloat()
        out.roundness = (0.5 + 0.5 * sin(t * 2 * PI * 1.3)).toFloat() * 0.6f
        out.width = (0.5 + 0.3 * sin(t * 2 * PI * 0.9)).toFloat()
    }
}
