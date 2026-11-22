package face.core.behavior

import face.core.anim.Layer
import face.core.rig.FacePose

// Order from architecture §6: neutral+emotion, activity, gaze, procedural life, speech, reactions. Blink runs after the springs.
internal class BaseLayer(private val base: FacePose) : Layer() {
    override fun apply(target: FacePose) {
        target.copyFrom(base)
    }
}

internal class ActivityLayer : Layer() {
    override fun apply(target: FacePose) = Unit
}

internal class GazeLayer(private val gaze: GazeController, private val ctx: LifeContext) : Layer() {
    override fun apply(target: FacePose) {
        gaze.apply(target, 1f - ctx.sleepWeight)
    }
}

internal class LifeLayer(
    private val idle: IdleScheduler,
    private val boredom: BoredomTracker,
    private val breath: BreathGenerator,
    private val sleepPose: FacePose,
    private val ctx: LifeContext,
) : Layer() {
    override fun apply(target: FacePose) {
        idle.apply(target)
        boredom.apply(target)
        val w = ctx.sleepWeight
        if (w > 0f) {
            val t = target.values
            val s = sleepPose.values
            for (i in t.indices) t[i] += (s[i] - t[i]) * w
        }
        breath.apply(target)
    }
}

internal class SpeechLayer : Layer() {
    override fun apply(target: FacePose) = Unit
}

internal class ReactionLayer : Layer() {
    override fun apply(target: FacePose) = Unit
}
