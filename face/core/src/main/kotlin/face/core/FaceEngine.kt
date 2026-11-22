package face.core

import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.anim.LayerStack
import face.core.anim.SecondOrderDynamics
import face.core.behavior.ActivityLayer
import face.core.behavior.BaseLayer
import face.core.behavior.BlinkScheduler
import face.core.behavior.BoredomTracker
import face.core.behavior.BreathGenerator
import face.core.behavior.FaceEventListener
import face.core.behavior.GazeController
import face.core.behavior.GazeLayer
import face.core.behavior.IdleScheduler
import face.core.behavior.LifeConfig
import face.core.behavior.LifeContext
import face.core.behavior.LifeLayer
import face.core.behavior.ReactionLayer
import face.core.behavior.SpeechLayer
import face.core.behavior.Vigilance
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.ChannelGroup
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.core.time.FrameTicker
import face.core.time.Rng

// Layers decide where the face wants to be (target); springs decide how it gets there (pose); blink runs last.
public class FaceEngine(
    private val library: ExpressionLibrary,
    rng: Rng,
    dynamics: DynamicsConfig = DynamicsConfig.Default,
    life: LifeConfig = LifeConfig.Default,
) {
    private val base = FacePose()
    private val target = FacePose()
    private val sleepPose = FacePose()
    private val springs = SecondOrderDynamics(Channel.count)
    private val ctx = LifeContext()
    private val blink = BlinkScheduler(life.blink, rng, ctx)
    private val gaze = GazeController(life.gaze, rng, ctx, blink)
    private val idle = IdleScheduler(life.idle, rng, ctx, gaze)
    private val boredom = BoredomTracker(life.boredom, ctx)
    private val breath = BreathGenerator(life.breath, ctx)
    private val gazeLayer = GazeLayer(gaze, ctx)
    private val lifeLayer = LifeLayer(idle, boredom, breath, sleepPose, ctx)
    private val stack = LayerStack(
        arrayOf(BaseLayer(base), ActivityLayer(), gazeLayer, lifeLayer, SpeechLayer(), ReactionLayer()),
    )

    public val pose: FacePose = FacePose()

    public var dynamics: DynamicsConfig = dynamics
        private set

    public var lifeEnabled: Boolean = true
        set(value) {
            field = value
            gazeLayer.enabled = value
            lifeLayer.enabled = value
        }

    public var listener: FaceEventListener?
        get() = ctx.listener
        set(value) {
            ctx.listener = value
        }

    public val vigilance: Vigilance get() = ctx.vigilance

    public val time: Double get() = ctx.time

    init {
        applyDynamics()
        library.poseInto(sleepPose, ExpressionId.Sleeping)
        library.poseInto(base, ExpressionId.Neutral)
        target.copyFrom(base)
        snapToTarget()
    }

    public fun setExpression(id: ExpressionId, weight: Float = 1f) {
        library.poseInto(base, id, weight)
        if (id == ExpressionId.Surprised) blink.suppress(SURPRISE_NO_BLINK) else blink.trigger()
    }

    public fun setTarget(source: FacePose) {
        base.copyFrom(source)
        Constraints.apply(base)
    }

    public fun copyTargetInto(out: FacePose) {
        out.copyFrom(base)
    }

    // Any sign of the user: resets boredom, and wakes the face with a double blink and a look if it was sleepy.
    public fun interact() {
        if (boredom.interact()) {
            idle.cancel()
            gaze.lookAtUser()
            blink.doubleBlink(WAKE)
        }
    }

    public fun dozeOff() {
        boredom.dozeOff()
    }

    public fun step(dt: Float) {
        val clamped = dt.coerceIn(0f, FrameTicker.MAX_DT)
        ctx.time += clamped
        if (lifeEnabled) {
            boredom.update(clamped)
            blink.update(clamped)
            gaze.update(clamped)
            idle.update(clamped)
            breath.update(clamped)
        }
        stack.apply(target)
        Constraints.apply(target)
        springs.step(target.values, clamped)
        springs.y.copyInto(pose.values)
        if (lifeEnabled) blink.apply(pose)
        Constraints.apply(pose)
    }

    public fun onResume() {
        springs.stopMotion()
    }

    public fun snapToTarget() {
        springs.reset(target.values)
        pose.copyFrom(target)
    }

    public fun setProfile(group: ChannelGroup, profile: DynamicsProfile) {
        dynamics = dynamics.with(group, profile)
        applyDynamics()
    }

    internal val blinkStats: BlinkScheduler get() = blink

    private fun applyDynamics() {
        for (channel in Channel.entries) springs.configure(channel.ordinal, dynamics[channel.group])
    }

    private companion object {
        const val SURPRISE_NO_BLINK = 1.5f
        const val WAKE = "wake"
    }
}
