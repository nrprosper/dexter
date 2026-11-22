package face.core.mood

import face.api.Emotion

// How strongly each basis emotion currently shows (0..1 each); Neutral's slot is unused, neutral is the absence of the others.
internal class EmotionState {
    val amounts = FloatArray(Emotion.entries.size)
    var arousal = 0.5f

    fun dominant(threshold: Float = DOMINANT_THRESHOLD): Emotion {
        var best = Emotion.Neutral.ordinal
        var bestAmount = threshold
        for (i in amounts.indices) {
            if (i != Emotion.Neutral.ordinal && amounts[i] > bestAmount) {
                best = i
                bestAmount = amounts[i]
            }
        }
        return EMOTIONS[best]
    }

    fun intensity(): Float {
        var max = 0f
        for (i in amounts.indices) if (i != Emotion.Neutral.ordinal && amounts[i] > max) max = amounts[i]
        return max
    }

    companion object {
        val EMOTIONS = Emotion.entries.toTypedArray()
        const val DOMINANT_THRESHOLD = 0.15f

        // Energy each emotion carries; excited is just happy with high arousal.
        val AROUSAL = floatArrayOf(0.4f, 0.65f, 0.55f, 0.45f, 0.2f, 0.45f, 0.75f)

        // Valence decides when a swing is big enough to need a transition beat.
        val VALENCE = floatArrayOf(0f, 1f, 0.3f, -0.3f, -1f, -0.6f, 0.8f)
    }
}
