package face.core.mood

import face.api.Emotion
import kotlin.math.pow

// The slow temperament under the expression: starts at the personality baseline, nudged by what happens, drifting back.
internal class MoodModel {
    val amounts = FloatArray(Emotion.entries.size)
    private val baseline = FloatArray(Emotion.entries.size)
    var baselineArousal = 0.5f
        private set
    private var driftHalfLife = 600f

    fun configure(baseline: Map<Emotion, Float>, arousal: Float, driftHalfLife: Float, reset: Boolean) {
        this.baseline.fill(0f)
        for ((emotion, value) in baseline) this.baseline[emotion.ordinal] = value.coerceIn(0f, 1f)
        baselineArousal = arousal.coerceIn(0f, 1f)
        this.driftHalfLife = driftHalfLife
        if (reset) this.baseline.copyInto(amounts)
    }

    fun nudge(emotion: Emotion, strength: Float) {
        for (i in amounts.indices) {
            val goal = if (i == emotion.ordinal) strength else 0f
            amounts[i] += (goal - amounts[i]) * NUDGE
        }
    }

    fun update(dt: Float) {
        val k = 1f - 0.5f.pow(dt / driftHalfLife)
        for (i in amounts.indices) amounts[i] += (baseline[i] - amounts[i]) * k
    }

    fun dominant(): Emotion {
        var best = Emotion.Neutral.ordinal
        var bestAmount = EmotionState.DOMINANT_THRESHOLD
        for (i in amounts.indices) {
            if (i != Emotion.Neutral.ordinal && amounts[i] > bestAmount) {
                best = i
                bestAmount = amounts[i]
            }
        }
        return EmotionState.EMOTIONS[best]
    }

    private companion object {
        const val NUDGE = 0.05f
    }
}
