package face.core

import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.anim.SecondOrderDynamics
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.ChannelGroup
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.core.time.FrameTicker

// Layers decide where the face wants to be (target); springs decide how it gets there (pose).
public class FaceEngine(
    private val library: ExpressionLibrary,
    dynamics: DynamicsConfig = DynamicsConfig.Default,
) {
    private val target = FacePose()
    private val springs = SecondOrderDynamics(Channel.count)

    public val pose: FacePose = FacePose()

    public var dynamics: DynamicsConfig = dynamics
        private set

    init {
        applyDynamics()
        library.poseInto(target, ExpressionId.Neutral)
        snapToTarget()
    }

    public fun setExpression(id: ExpressionId, weight: Float = 1f) {
        library.poseInto(target, id, weight)
    }

    public fun setTarget(source: FacePose) {
        target.copyFrom(source)
        Constraints.apply(target)
    }

    public fun copyTargetInto(out: FacePose) {
        out.copyFrom(target)
    }

    public fun step(dt: Float) {
        springs.step(target.values, dt.coerceIn(0f, FrameTicker.MAX_DT))
        springs.y.copyInto(pose.values)
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

    private fun applyDynamics() {
        for (channel in Channel.entries) springs.configure(channel.ordinal, dynamics[channel.group])
    }
}
