package face.core.behavior

import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.PI
import kotlin.math.sin

// A barely visible bob and scale that removes "frozen image" stillness; slower and deeper asleep.
internal class BreathGenerator(var config: BreathConfig, private val ctx: LifeContext) {
    private var phase = 0f

    fun update(dt: Float) {
        val w = ctx.sleepWeight
        val period = config.period + (config.sleepPeriod - config.period) * w
        phase += dt / period
        if (phase >= 1f) phase -= 1f
    }

    fun apply(pose: FacePose) {
        val depth = 1f + (config.sleepDepth - 1f) * ctx.sleepWeight
        val s = sin(2f * PI.toFloat() * phase) * depth
        pose[Channel.HeadY] = pose[Channel.HeadY] + config.headY * s
        pose[Channel.HeadScale] = pose[Channel.HeadScale] + config.scale * s
    }
}
