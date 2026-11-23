package assistant.core

import face.api.Emotion
import face.api.EmotionSignal
import face.api.SpeechStyle

public enum class Tone { Neutral, Calm, Excited, Serious, Funny }

// What the brain says it feels about a reply. The face's Director decides how that looks.
public data class Feeling(
    val emotion: Emotion = Emotion.Neutral,
    val intensity: Float = DEFAULT_INTENSITY,
    val confidence: Float = 1f,
    val tone: Tone = Tone.Neutral,
) {
    public fun signal(): EmotionSignal = EmotionSignal(emotion, intensity.coerceIn(0f, 1f), confidence.coerceIn(0f, 1f))

    // Tone picks the preset; intensity nudges its energy (a mild reply is less animated than a strong one).
    public fun style(): SpeechStyle {
        val base = when (tone) {
            Tone.Neutral -> SpeechStyle()
            Tone.Calm -> SpeechStyle.Calm
            Tone.Excited -> SpeechStyle.Excited
            Tone.Serious -> SpeechStyle.Serious
            Tone.Funny -> SpeechStyle.Funny
        }
        val scale = ENERGY_BASE + ENERGY_GAIN * intensity.coerceIn(0f, 1f)
        return base.copy(energy = (base.energy * scale).coerceIn(0f, 1f))
    }

    public companion object {
        public const val DEFAULT_INTENSITY: Float = 0.5f
        private const val ENERGY_BASE = 0.7f
        private const val ENERGY_GAIN = 0.6f
    }
}
