package assistant.core

import face.api.SpeechStyle

// A small speed/pitch shift from the speaking style, for voices that support it: livelier when excited or playful,
// steadier when serious or calm. Kept within a few percent so the voice stays itself.
public object Prosody {
    public const val SPEED_ENERGY: Float = 0.12f
    public const val SPEED_SERIOUS: Float = 0.04f
    public const val PITCH_ENERGY: Float = 0.1f
    public const val PITCH_PLAYFUL: Float = 0.05f
    public const val PITCH_SERIOUS: Float = 0.05f
    private const val MIN = 0.9f
    private const val MAX = 1.12f

    public fun speed(style: SpeechStyle): Float =
        (1f + SPEED_ENERGY * (style.energy - 0.5f) - SPEED_SERIOUS * style.seriousness).coerceIn(MIN, MAX)

    public fun pitch(style: SpeechStyle): Float =
        (1f + PITCH_ENERGY * (style.energy - 0.5f) + PITCH_PLAYFUL * style.playfulness - PITCH_SERIOUS * style.seriousness)
            .coerceIn(MIN, MAX)
}
