package face.core.director

import face.api.Emotion
import face.api.FaceReaction
import face.core.anim.Envelope
import face.core.behavior.BlinkScheduler
import face.core.behavior.FaceEvent
import face.core.behavior.LifeContext
import face.core.rig.FacePose
import kotlin.math.PI
import kotlin.math.sin

// Resolves queued reactions by priority and cooldown and plays them as envelopes. A replaced reaction fades out quickly.
internal class ReactionRouter(
    private val ctx: LifeContext,
    private val blink: BlinkScheduler,
    private val director: Director,
    private val defs: Array<ReactionDef> = Reactions.Defaults,
) {
    private val cooldownLeft = FloatArray(defs.size)
    private var active = -1
    private var elapsed = 0f
    private var followUp: Emotion? = null
    private var followUpSent = false
    private var previous = -1
    private var previousElapsed = 0f
    private var previousFrom = 0f
    private var previousFade = 0f

    val playing: Int get() = active

    fun react(reaction: FaceReaction) {
        val i = Reactions.indexOf(reaction)
        val def = defs[i]
        if (cooldownLeft[i] > 0f) {
            ctx.emit(FaceEvent.Reaction, DROPPED_COOLDOWN[i])
            return
        }
        val current = active
        if (current >= 0 && def.priority < defs[current].priority) {
            ctx.emit(FaceEvent.Reaction, DROPPED_PRIORITY[i])
            return
        }
        if (current >= 0) {
            previous = current
            previousElapsed = elapsed
            previousFrom = envelope(current, elapsed)
            previousFade = 0f
        }
        active = i
        elapsed = 0f
        cooldownLeft[i] = def.cooldown
        followUp = if (reaction is FaceReaction.Surprise) reaction.followUp else def.followUp
        followUpSent = false
        if (def.noBlink) blink.suppress(def.duration)
        if (def.blinkOnStart) blink.trigger()
        ctx.emit(FaceEvent.Reaction, PLAY[i])
    }

    fun update(dt: Float) {
        for (i in cooldownLeft.indices) if (cooldownLeft[i] > 0f) cooldownLeft[i] -= dt
        if (previous >= 0) {
            previousFade += dt
            previousElapsed += dt
            if (previousFade >= HANDOVER) previous = -1
        }
        val i = active
        if (i < 0) return
        elapsed += dt
        val def = defs[i]
        val next = followUp
        if (!followUpSent && next != null && elapsed >= def.attack + def.hold) {
            followUpSent = true
            director.force(next, def.followUpIntensity)
        }
        if (elapsed >= def.duration) active = -1
    }

    fun apply(pose: FacePose, neutral: FacePose) {
        if (previous >= 0) play(pose, neutral, previous, previousElapsed, previousFrom * (1f - previousFade / HANDOVER))
        if (active >= 0) play(pose, neutral, active, elapsed, envelope(active, elapsed))
    }

    private fun envelope(i: Int, t: Float): Float {
        val d = defs[i]
        return Envelope.value(t, d.attack, d.hold, d.release)
    }

    private fun play(pose: FacePose, neutral: FacePose, i: Int, t: Float, e: Float) {
        if (e <= 0f) return
        val d = defs[i]
        val v = pose.values
        val n = neutral.values
        for (k in d.setChannels.indices) {
            val ch = d.setChannels[k]
            v[ch] += (n[ch] + d.setValues[k] - v[ch]) * e
        }
        for (k in d.addChannels.indices) v[d.addChannels[k]] += d.addValues[k] * e
        val shake = d.shake
        if (shake != null) v[shake.ordinal] += d.shakeAmplitude * e * sin(2f * PI.toFloat() * d.shakeFrequency * t)
    }

    private companion object {
        const val HANDOVER = 0.15f
        val PLAY = Reactions.Defaults.map { "play ${it.name}" }.toTypedArray()
        val DROPPED_COOLDOWN = Reactions.Defaults.map { "dropped ${it.name} (cooldown)" }.toTypedArray()
        val DROPPED_PRIORITY = Reactions.Defaults.map { "dropped ${it.name} (priority)" }.toTypedArray()
    }
}
