package face.core.behavior

import face.api.Emotion
import face.api.Quirk
import face.core.anim.Envelope
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.PI
import kotlin.math.sin

// Signature behaviours that make it a character: double-blink when unsure, a happy head wobble, a one-eye peek on waking.
internal class Quirks(private val ctx: LifeContext, private val blink: BlinkScheduler) {
    private var unsure = false
    private var wobble = false
    private var peek = false
    private var unsureElapsed = -1f
    private var wobbleElapsed = -1f
    private var peekElapsed = -1f

    // Off during serious speech: the playful wobble would undercut the tone.
    var playfulAllowed = true

    fun configure(enabled: Set<Quirk>) {
        unsure = Quirk.DoubleBlinkWhenUnsure in enabled
        wobble = Quirk.HappyWobble in enabled
        peek = Quirk.OneEyePeek in enabled
    }

    fun onEmotion(emotion: Emotion, strength: Float, confidence: Float, previousAmount: Float) {
        if (unsure && (confidence < UNSURE_CONFIDENCE || emotion == Emotion.Confused)) {
            blink.doubleBlink(UNSURE)
            unsureElapsed = 0f
            ctx.emit(FaceEvent.Behavior, UNSURE)
        }
        if (wobble && playfulAllowed && emotion == Emotion.Happy && strength - previousAmount > WOBBLE_JUMP) {
            wobbleElapsed = 0f
            ctx.emit(FaceEvent.Behavior, WOBBLE)
        }
    }

    fun onWake() {
        if (!peek) return
        peekElapsed = 0f
        ctx.emit(FaceEvent.Behavior, PEEK)
    }

    fun update(dt: Float) {
        if (unsureElapsed >= 0f) unsureElapsed = advance(unsureElapsed, dt, UNSURE_ATTACK + UNSURE_HOLD + UNSURE_RELEASE)
        if (wobbleElapsed >= 0f) wobbleElapsed = advance(wobbleElapsed, dt, WOBBLE_DURATION)
        if (peekElapsed >= 0f) peekElapsed = advance(peekElapsed, dt, PEEK_HOLD + PEEK_RELEASE)
    }

    fun apply(pose: FacePose) {
        if (unsureElapsed >= 0f) {
            val e = Envelope.value(unsureElapsed, UNSURE_ATTACK, UNSURE_HOLD, UNSURE_RELEASE)
            pose[Channel.EyeOpenL] = pose[Channel.EyeOpenL] - UNSURE_NARROW * e
        }
        if (wobbleElapsed >= 0f) {
            val fade = 1f - wobbleElapsed / WOBBLE_DURATION
            pose[Channel.HeadTilt] = pose[Channel.HeadTilt] +
                WOBBLE_TILT * fade * sin(2f * PI.toFloat() * WOBBLE_FREQUENCY * wobbleElapsed)
        }
        if (peekElapsed >= 0f) {
            val e = Envelope.value(peekElapsed, 0.001f, PEEK_HOLD, PEEK_RELEASE)
            pose[Channel.EyeOpenL] = pose[Channel.EyeOpenL] - PEEK_SHUT * e
            pose[Channel.EyeOpenR] = pose[Channel.EyeOpenR] + PEEK_WIDEN * e
        }
    }

    private fun advance(t: Float, dt: Float, duration: Float): Float {
        val next = t + dt
        return if (next >= duration) -1f else next
    }

    private companion object {
        const val UNSURE = "quirk unsure"
        const val WOBBLE = "quirk wobble"
        const val PEEK = "quirk peek"
        const val UNSURE_CONFIDENCE = 0.5f
        const val UNSURE_ATTACK = 0.15f
        const val UNSURE_HOLD = 0.8f
        const val UNSURE_RELEASE = 0.4f
        const val UNSURE_NARROW = 0.25f
        const val WOBBLE_JUMP = 0.3f
        const val WOBBLE_DURATION = 0.8f
        const val WOBBLE_FREQUENCY = 3.2f
        const val WOBBLE_TILT = 0.12f
        const val PEEK_HOLD = 0.55f
        const val PEEK_RELEASE = 0.25f
        const val PEEK_SHUT = 1.2f
        const val PEEK_WIDEN = 0.1f
    }
}
