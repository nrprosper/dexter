package face.core.behavior

import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.max

// Spontaneous blinks at log-normal intervals plus triggered ones; the shape is an authored curve applied after the springs.
internal class BlinkScheduler(var config: BlinkConfig, private val rng: Rng, private val ctx: LifeContext) {
    private var untilNext = sampleInterval()
    private var t = -1f
    private var speed = 1f
    private var depth = 1f
    private var pendingDouble = -1f
    private var suppressedFor = 0f
    private var sinceLast = Float.MAX_VALUE

    var intervalScale = 1f

    var spontaneousCount = 0
        private set
    var doubleCount = 0
        private set

    fun update(dt: Float) {
        sinceLast += dt
        if (suppressedFor > 0f) suppressedFor -= dt
        if (t >= 0f) {
            t += dt
            if (t >= duration(speed)) t = -1f
        }
        if (pendingDouble >= 0f) {
            pendingDouble -= dt
            if (pendingDouble < 0f && t < 0f) begin(1f, depth)
        }
        if (ctx.vigilance == Vigilance.Asleep) return
        untilNext -= dt
        if (untilNext <= 0f) {
            untilNext = sampleInterval()
            if (t < 0f && suppressedFor <= 0f) spontaneous()
        }
    }

    fun trigger(): Boolean {
        if (t >= 0f || suppressedFor > 0f || sinceLast < config.triggerCooldown) return false
        if (ctx.vigilance == Vigilance.Asleep || rng.nextFloat() >= config.triggerChance) return false
        begin(1f, 1f)
        ctx.emit(FaceEvent.Blink, TRIGGERED)
        return true
    }

    // Mid-blink, the pair is queued after the current blink instead of restarting it (which would pop the lids).
    fun doubleBlink(reason: String) {
        if (t >= 0f) {
            pendingDouble = duration(speed) - t + config.doubleGap
            ctx.emit(FaceEvent.DoubleBlink, reason)
            return
        }
        begin(1f, 1f)
        pendingDouble = duration(1f) + config.doubleGap
        ctx.emit(FaceEvent.DoubleBlink, reason)
    }

    fun suppress(seconds: Float) {
        suppressedFor = max(suppressedFor, seconds)
    }

    fun apply(pose: FacePose) {
        val c = closure()
        if (c <= 0f) return
        pose[Channel.EyeOpenL] = pose[Channel.EyeOpenL] * (1f - c)
        pose[Channel.EyeOpenR] = pose[Channel.EyeOpenR] * (1f - c)
    }

    fun closure(): Float {
        if (t < 0f) return 0f
        val close = config.close * speed
        val hold = config.hold * speed
        val open = config.open * speed
        val shape = when {
            t < close -> (t / close).let { it * it * (3f - 2f * it) }
            t < close + hold -> 1f
            else -> (1f - ((t - close - hold) / open).coerceIn(0f, 1f)).let { it * it }
        }
        return shape * depth
    }

    private fun spontaneous() {
        spontaneousCount++
        val drowsy = ctx.drowsiness
        val slowChance = config.slowChance + (1f - config.slowChance) * drowsy * DROWSY_SLOW_SHARE
        when {
            rng.nextFloat() < slowChance -> {
                begin(config.slowFactor, 1f - (1f - config.drowsyPartialDepth) * drowsy * rng.nextFloat())
                ctx.emit(FaceEvent.SlowBlink)
            }
            rng.nextFloat() < config.doubleChance -> {
                doubleCount++
                doubleBlink(SPONTANEOUS)
            }
            else -> {
                begin(1f, 1f)
                ctx.emit(FaceEvent.Blink)
            }
        }
    }

    private fun begin(speed: Float, depth: Float) {
        t = 0f
        this.speed = speed
        this.depth = depth
        sinceLast = 0f
    }

    private fun duration(speed: Float): Float = (config.close + config.hold + config.open) * speed

    private fun sampleInterval(): Float =
        max(config.minInterval, rng.nextLogNormal(config.meanInterval, config.spread) * intervalScale)

    companion object {
        const val TRIGGERED = "triggered"
        const val SPONTANEOUS = "spontaneous"
        private const val DROWSY_SLOW_SHARE = 0.8f
    }
}
