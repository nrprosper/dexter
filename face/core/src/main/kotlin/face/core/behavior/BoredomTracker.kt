package face.core.behavior

import face.core.anim.Envelope
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.max
import kotlin.math.min

// The longer nobody interacts, the lower the energy: awake, then drowsy (heavy lids), then asleep. A tap wakes it.
internal class BoredomTracker(var config: BoredomConfig, private val ctx: LifeContext) {
    private var idleTime = 0f
    private var wakeElapsed = 0f

    fun update(dt: Float) {
        idleTime += dt
        if (ctx.vigilance == Vigilance.Waking) {
            wakeElapsed += dt
            ctx.sleepWeight = max(0f, ctx.sleepWeight - dt / WAKE_FADE)
            if (wakeElapsed >= config.wakeDuration) enter(Vigilance.Awake)
            return
        }
        ctx.drowsiness = ((idleTime - config.drowsyAfter) / (config.sleepAfter - config.drowsyAfter)).coerceIn(0f, 1f)
        if (idleTime >= config.sleepAfter) {
            if (ctx.vigilance != Vigilance.Asleep) enter(Vigilance.Asleep)
            ctx.sleepWeight = min(1f, ctx.sleepWeight + dt / config.sleepFade)
        } else if (idleTime >= config.drowsyAfter && ctx.vigilance == Vigilance.Awake) {
            enter(Vigilance.Drowsy)
        }
    }

    // Returns true when the interaction woke the face up (it was asleep or quite drowsy).
    fun interact(): Boolean {
        val sleepy = ctx.vigilance == Vigilance.Asleep ||
            (ctx.vigilance == Vigilance.Drowsy && ctx.drowsiness > SLEEPY_DROWSINESS)
        idleTime = 0f
        ctx.drowsiness = 0f
        if (sleepy) {
            wakeElapsed = 0f
            enter(Vigilance.Waking)
            return true
        }
        if (ctx.vigilance == Vigilance.Drowsy) enter(Vigilance.Awake)
        return false
    }

    fun keepAwake() {
        idleTime = 0f
        ctx.drowsiness = 0f
        if (ctx.vigilance == Vigilance.Drowsy) enter(Vigilance.Awake)
    }

    fun dozeOff() {
        idleTime = max(idleTime, config.sleepAfter)
    }

    fun apply(pose: FacePose) {
        val d = ctx.drowsiness
        if (d > 0f) {
            add(pose, Channel.EyeOpenL, -DROWSY_LIDS * d)
            add(pose, Channel.EyeOpenR, -DROWSY_LIDS * d)
            add(pose, Channel.UpperLidCurveL, -DROWSY_CURVE * d)
            add(pose, Channel.UpperLidCurveR, -DROWSY_CURVE * d)
            add(pose, Channel.BrowHeightL, -DROWSY_BROWS * d)
            add(pose, Channel.BrowHeightR, -DROWSY_BROWS * d)
            add(pose, Channel.HeadY, -DROWSY_SLUMP * d)
        }
        if (ctx.vigilance == Vigilance.Waking) {
            val e = Envelope.value(wakeElapsed, WAKE_ATTACK, WAKE_HOLD, WAKE_RELEASE)
            add(pose, Channel.HeadSquash, WAKE_STRETCH * e)
            add(pose, Channel.HeadScale, WAKE_GROW * e)
            add(pose, Channel.BrowHeightL, WAKE_BROWS * e)
            add(pose, Channel.BrowHeightR, WAKE_BROWS * e)
            add(pose, Channel.EyeOpenL, WAKE_EYES * e)
            add(pose, Channel.EyeOpenR, WAKE_EYES * e)
        }
    }

    private fun enter(state: Vigilance) {
        ctx.vigilance = state
        ctx.emit(EVENTS[state.ordinal])
    }

    private fun add(pose: FacePose, channel: Channel, delta: Float) {
        pose[channel] = pose[channel] + delta
    }

    private companion object {
        val EVENTS = arrayOf(FaceEvent.Awake, FaceEvent.Drowsy, FaceEvent.Asleep, FaceEvent.Waking)
        const val SLEEPY_DROWSINESS = 0.3f
        const val WAKE_FADE = 0.5f
        const val DROWSY_LIDS = 0.4f
        const val DROWSY_CURVE = 0.2f
        const val DROWSY_BROWS = 0.2f
        const val DROWSY_SLUMP = 0.04f
        const val WAKE_ATTACK = 0.25f
        const val WAKE_HOLD = 0.5f
        const val WAKE_RELEASE = 0.85f
        const val WAKE_STRETCH = 0.35f
        const val WAKE_GROW = 0.06f
        const val WAKE_BROWS = 0.45f
        const val WAKE_EYES = 0.2f
    }
}
