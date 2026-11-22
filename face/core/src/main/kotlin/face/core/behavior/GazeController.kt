package face.core.behavior

import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import face.core.time.SmoothNoise
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Gaze is a target point: mostly on the user, with occasional glances away; pupils jump there, the head follows slowly.
internal class GazeController(
    var config: GazeConfig,
    private val rng: Rng,
    private val ctx: LifeContext,
    private val blink: BlinkScheduler,
) {
    private var targetX = 0f
    private var targetY = 0f
    private var away = false
    private var fixationLeft = sampleFixation()
    private val noiseX = SmoothNoise(rng, config.driftFrequency)
    private val noiseY = SmoothNoise(rng, config.driftFrequency * NOISE_Y_RATIO)
    private var driftX = 0f
    private var driftY = 0f
    private var sweepStep = -1
    private var sweepLeft = 0f
    private var sweepSign = 1f
    private var holding = false
    private var holdX = 0f
    private var holdY = 0f

    var policy: GazePolicy = GazePolicy.Default

    fun update(dt: Float) {
        driftX = noiseX.next(dt)
        driftY = noiseY.next(dt)
        if (ctx.vigilance == Vigilance.Asleep) {
            targetX = 0f
            targetY = 0f
            away = false
            sweepStep = -1
            return
        }
        if (holding) return
        if (sweepStep >= 0) {
            sweepLeft -= dt
            if (sweepLeft <= 0f) advanceSweep()
            return
        }
        fixationLeft -= dt * (1f - DROWSY_SLOWDOWN * ctx.drowsiness)
        if (fixationLeft <= 0f) {
            nextFixation()
            fixationLeft = sampleFixation() * (if (away) policy.awayHold else policy.fixationScale)
        }
    }

    fun lookAround() {
        sweepSign = if (rng.nextFloat() < 0.5f) -1f else 1f
        sweepStep = 0
        advanceSweep()
    }

    // An activity pins the gaze somewhere (thinking looks up and aside); re-holding the same point is free.
    fun holdAt(x: Float, y: Float) {
        if (holding && x == holdX && y == holdY) return
        holding = true
        holdX = x
        holdY = y
        sweepStep = -1
        moveTo(x, y)
    }

    fun release() {
        if (!holding) return
        holding = false
        lookAtUser()
    }

    fun lookAtUser() {
        sweepStep = -1
        away = false
        moveTo(0f, 0f)
        fixationLeft = sampleFixation()
    }

    fun apply(pose: FacePose, weight: Float) {
        if (weight <= 0f) return
        val amp = config.driftAmplitude
        pose[Channel.GazeX] = pose[Channel.GazeX] + (targetX + driftX * amp) * weight
        pose[Channel.GazeY] = pose[Channel.GazeY] + (targetY + driftY * amp) * weight
        pose[Channel.HeadX] = pose[Channel.HeadX] + targetX * config.headFollowX * weight
        pose[Channel.HeadY] = pose[Channel.HeadY] + targetY * config.headFollowY * weight
        pose[Channel.HeadTilt] = pose[Channel.HeadTilt] + targetX * config.headFollowTilt * weight
    }

    private fun nextFixation() {
        val drowsy = ctx.drowsiness
        val p = policy
        if (away || rng.nextFloat() >= config.awayChance * p.awayScale * (1f - DROWSY_SLOWDOWN * drowsy)) {
            away = false
            val r = config.refixRadius * p.refixScale
            moveTo(rng.nextFloat(-r, r), rng.nextFloat(-r, r))
        } else {
            away = true
            val angle = rng.nextFloat(0f, 2f * PI.toFloat())
            val radius = rng.nextFloat(config.awayMin, config.awayMax) * p.awayReach * (1f - DROWSY_SLOWDOWN * drowsy)
            moveTo(cos(angle) * radius, sin(angle) * radius * VERTICAL_SQUEEZE)
        }
    }

    private fun advanceSweep() {
        val reach = config.sweepReach
        when (sweepStep) {
            0 -> moveTo(sweepSign * reach, SWEEP_LIFT)
            1 -> moveTo(-sweepSign * reach, 0f)
            2 -> moveTo(0f, 0f)
            else -> {
                sweepStep = -1
                fixationLeft = sampleFixation()
                return
            }
        }
        sweepStep++
        sweepLeft = config.sweepStep
    }

    private fun moveTo(x: Float, y: Float) {
        val dx = x - targetX
        val dy = y - targetY
        targetX = x
        targetY = y
        ctx.emit(FaceEvent.Saccade)
        if (sqrt(dx * dx + dy * dy) > config.bigSaccade) blink.trigger()
    }

    private fun sampleFixation(): Float = rng.nextLogNormal(config.fixationMean, config.fixationSpread)

    private companion object {
        const val NOISE_Y_RATIO = 1.3f
        const val DROWSY_SLOWDOWN = 0.5f
        const val VERTICAL_SQUEEZE = 0.7f
        const val SWEEP_LIFT = 0.05f
    }
}
