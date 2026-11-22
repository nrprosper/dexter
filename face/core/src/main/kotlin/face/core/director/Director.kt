package face.core.director

import face.api.DirectorSettings
import face.api.Emotion
import face.api.TransitionBeat
import face.core.behavior.FaceEvent
import face.core.behavior.LifeContext
import face.core.mood.EmotionState
import face.core.mood.MoodModel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

internal interface DirectorHooks {
    fun onAccepted(emotion: Emotion, strength: Float, confidence: Float, previousAmount: Float)

    fun playSurpriseBeat()
}

// The LLM chooses the feeling, the Director chooses the performance. Signals add leaky evidence per emotion;
// the face switches only when another emotion out-evidences the current one by 30% plus the hysteresis margin, after the
// minimum dwell. Then inertia, transition beats for big swings, and decay back to mood.
internal class Director(
    private val ctx: LifeContext,
    private val state: EmotionState,
    private val mood: MoodModel,
    private val hooks: DirectorHooks,
) {
    var config = DirectorSettings()
    var speechEnergy = -1f

    private val target = FloatArray(Emotion.entries.size)
    var currentEmotion = Emotion.Neutral
        private set
    var currentStrength = 0f
        private set
    private var sinceChange = Float.MAX_VALUE
    private val evidence = FloatArray(Emotion.entries.size)
    private val latestStrength = FloatArray(Emotion.entries.size)
    private val latestConfidence = FloatArray(Emotion.entries.size)
    private var beatLeft = -1f
    private var afterBeat: Emotion? = null
    private var afterBeatStrength = 0f
    private var afterBeatConfidence = 0f

    fun startAtMood() {
        mood.amounts.copyInto(target)
        mood.amounts.copyInto(state.amounts)
        state.arousal = mood.baselineArousal
    }

    fun signal(emotion: Emotion, intensity: Float, confidence: Float) {
        val strength = (intensity * (CONFIDENCE_FLOOR + (1f - CONFIDENCE_FLOOR) * confidence)).coerceIn(0f, 1f)
        val i = emotion.ordinal
        evidence[i] = (evidence[i] + strength).coerceAtMost(EVIDENCE_CAP)
        latestStrength[i] = strength
        latestConfidence[i] = confidence
        if (emotion == currentEmotion && beatLeft < 0f) {
            if (abs(strength - currentStrength) >= config.hysteresis) {
                apply(emotion, strength, confidence)
            } else {
                ctx.emit(FaceEvent.Emotion, IGNORED[i])
            }
            return
        }
        if (!evaluate()) ctx.emit(FaceEvent.Emotion, PENDING[i])
    }

    // Follow-ups from reactions are deliberate, so they skip dwell, evidence and beats.
    fun force(emotion: Emotion, intensity: Float) {
        val i = emotion.ordinal
        evidence[i] = maxOf(evidence[i], intensity)
        apply(emotion, intensity.coerceIn(0f, 1f), 1f)
    }

    fun update(dt: Float) {
        sinceChange += dt
        if (beatLeft >= 0f) {
            beatLeft -= dt
            val next = afterBeat
            if (beatLeft < 0f && next != null) {
                afterBeat = null
                apply(next, afterBeatStrength, afterBeatConfidence)
            }
        }
        val fade = exp(-dt / EVIDENCE_TAU)
        for (i in evidence.indices) evidence[i] *= fade
        if (beatLeft < 0f) evaluate()
        mood.update(dt)
        val decay = 1f - 0.5f.pow(dt / config.halfLife)
        val follow = 1f - exp(-dt / config.inertia)
        var arousal = mood.baselineArousal
        for (i in target.indices) {
            target[i] += (mood.amounts[i] - target[i]) * decay
            state.amounts[i] += (target[i] - state.amounts[i]) * follow
            if (i != Emotion.Neutral.ordinal) arousal += state.amounts[i] * (EmotionState.AROUSAL[i] - AROUSAL_PIVOT)
        }
        if (speechEnergy >= 0f) arousal += (speechEnergy - 0.5f) * SPEECH_AROUSAL
        state.arousal += (arousal.coerceIn(0f, 1f) - state.arousal) * (1f - exp(-dt / AROUSAL_TAU))
    }

    // Returns true when it switched to a new emotion.
    private fun evaluate(): Boolean {
        if (sinceChange < config.minDwell) return false
        var best = currentEmotion.ordinal
        for (i in evidence.indices) if (evidence[i] > evidence[best]) best = i
        val holding = evidence[currentEmotion.ordinal] * SWITCH_RATIO + config.hysteresis
        if (best == currentEmotion.ordinal || evidence[best] < holding) return false
        val emotion = EmotionState.EMOTIONS[best]
        val strength = latestStrength[best]
        val confidence = latestConfidence[best]
        if (config.transitionBeat != TransitionBeat.None && bigSwing(emotion, strength)) {
            if (config.transitionBeat == TransitionBeat.Neutral) {
                target.fill(0f)
                currentEmotion = Emotion.Neutral
                currentStrength = 0f
                sinceChange = 0f
                beatLeft = NEUTRAL_BEAT
                afterBeat = emotion
                afterBeatStrength = strength
                afterBeatConfidence = confidence
                ctx.emit(FaceEvent.Emotion, BEAT)
                return true
            }
            hooks.playSurpriseBeat()
        }
        apply(emotion, strength, confidence)
        return true
    }

    private fun apply(emotion: Emotion, strength: Float, confidence: Float) {
        val previous = state.amounts[emotion.ordinal]
        target.fill(0f)
        if (emotion != Emotion.Neutral) target[emotion.ordinal] = strength
        currentEmotion = emotion
        currentStrength = strength
        sinceChange = 0f
        mood.nudge(emotion, strength)
        hooks.onAccepted(emotion, strength, confidence, previous)
        ctx.emit(FaceEvent.Emotion, ACCEPTED[emotion.ordinal])
    }

    private fun bigSwing(emotion: Emotion, strength: Float): Boolean {
        val from = EmotionState.VALENCE[currentEmotion.ordinal] * currentStrength
        val to = EmotionState.VALENCE[emotion.ordinal] * strength
        return from * to < 0f && abs(to - from) > BIG_SWING
    }

    private companion object {
        const val CONFIDENCE_FLOOR = 0.3f
        const val EVIDENCE_TAU = 4f
        const val EVIDENCE_CAP = 2f
        const val SWITCH_RATIO = 1.3f
        const val AROUSAL_PIVOT = 0.45f
        const val SPEECH_AROUSAL = 0.4f
        const val AROUSAL_TAU = 1f
        const val NEUTRAL_BEAT = 0.4f
        const val BIG_SWING = 0.9f
        const val BEAT = "beat neutral"
        val ACCEPTED = Emotion.entries.map { "accepted ${it.name}" }.toTypedArray()
        val PENDING = Emotion.entries.map { "pending ${it.name}" }.toTypedArray()
        val IGNORED = Emotion.entries.map { "ignored ${it.name}" }.toTypedArray()
    }
}
