package face.api

// A character: how it feels at rest and how it moves. Mirrors the personality JSON (format 1); defaults are Dexter.
public data class Personality(
    val id: String = "dexter",
    val name: String = "Dexter",
    val mood: MoodSettings = MoodSettings(),
    val expressiveness: Float = 1f,
    val emotionGain: Map<Emotion, Float> = mapOf(Emotion.Happy to 1f, Emotion.Sad to 0.7f, Emotion.Playful to 1.1f),
    val director: DirectorSettings = DirectorSettings(),
    val motion: MotionSettings = MotionSettings(),
    val blink: BlinkSettings = BlinkSettings(),
    val gaze: GazeSettings = GazeSettings(),
    val idle: IdleSettings = IdleSettings(),
    val curiosity: Float = 0.6f,
    val playfulness: Float = 0.6f,
    val sleepiness: SleepSettings = SleepSettings(),
    val quirks: Set<Quirk> = setOf(Quirk.DoubleBlinkWhenUnsure, Quirk.HappyWobble, Quirk.OneEyePeek),
) {
    public fun gain(emotion: Emotion): Float = emotionGain[emotion] ?: 1f
}

public data class MoodSettings(
    val baseline: Map<Emotion, Float> = emptyMap(),
    val arousal: Float = 0.5f,
    val driftHalfLife: Float = 600f,
)

public enum class TransitionBeat { Surprise, Neutral, None }

public data class DirectorSettings(
    val halfLife: Float = 12f,
    val minDwell: Float = 1.2f,
    val hysteresis: Float = 0.15f,
    val inertia: Float = 0.35f,
    val transitionBeat: TransitionBeat = TransitionBeat.Surprise,
)

public data class MotionSettings(val reactivity: Float = 1f, val bounciness: Float = 0.6f)

public data class BlinkSettings(val meanInterval: Float = 4f, val spread: Float = 0.45f)

public data class GazeSettings(val restlessness: Float = 1f)

public data class IdleSettings(
    val budgetPerMinute: Int = 4,
    val weights: Map<String, Int> = mapOf("tilt" to 3, "hum" to 2, "bounce" to 2),
)

public data class SleepSettings(val drowsyAfter: Float = 90f, val sleepAfter: Float = 180f)

public enum class Quirk { DoubleBlinkWhenUnsure, HappyWobble, OneEyePeek }
