package face.core.mood

import face.api.Emotion
import face.api.Personality
import face.core.anim.Layer
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.FacePose

// pose = neutral + Σ amountᵢ × gainᵢ × expressiveness × arousal boost × expressionᵢ. Manual mode shows a raw pose instead.
internal class EmotionLayer(
    library: ExpressionLibrary,
    private val neutral: FacePose,
    private val state: EmotionState,
    private val manual: FacePose,
) : Layer() {
    private val channels = Array(Emotion.entries.size) { i -> expression(library, i)?.channels ?: IntArray(0) }
    private val deltas = Array(Emotion.entries.size) { i -> expression(library, i)?.deltas ?: FloatArray(0) }
    private val gains = FloatArray(Emotion.entries.size) { 1f }
    private var expressiveness = 1f
    var manualMode = false

    fun configure(p: Personality) {
        for (e in Emotion.entries) gains[e.ordinal] = p.gain(e)
        expressiveness = p.expressiveness
    }

    override fun apply(target: FacePose) {
        if (manualMode) {
            target.copyFrom(manual)
            return
        }
        target.copyFrom(neutral)
        val boost = expressiveness * (AROUSAL_BASE + AROUSAL_GAIN * state.arousal)
        val v = target.values
        for (i in channels.indices) {
            val amount = state.amounts[i] * gains[i] * boost
            if (amount <= MIN_AMOUNT) continue
            val ch = channels[i]
            val d = deltas[i]
            for (k in ch.indices) v[ch[k]] += d[k] * amount
        }
    }

    private companion object {
        const val AROUSAL_BASE = 0.85f
        const val AROUSAL_GAIN = 0.3f
        const val MIN_AMOUNT = 0.001f

        fun expression(library: ExpressionLibrary, i: Int) = when (Emotion.entries[i]) {
            Emotion.Neutral -> null
            Emotion.Happy -> library[ExpressionId.Happy]
            Emotion.Curious -> library[ExpressionId.Curious]
            Emotion.Confused -> library[ExpressionId.Confused]
            Emotion.Sad -> library[ExpressionId.Sad]
            Emotion.Concerned -> library[ExpressionId.Concerned]
            Emotion.Playful -> library[ExpressionId.Playful]
        }
    }
}
