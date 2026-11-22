package face.core.senses

import face.core.anim.Envelope
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.min

// While the system volume panel is up, the face scoots up and to the right, shrinks a little and watches the level.
internal class VolumeWatch {
    private var level = 0f
    private var since = Float.MAX_VALUE
    private var weight = 0f
    private var bounce = -1f

    fun show(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        since = 0f
    }

    fun update(dt: Float) {
        since += dt
        val showing = since < HOLD
        val goal = if (showing) 1f else 0f
        if (!showing && weight > 0.5f && bounce < 0f) bounce = 0f
        weight += (goal - weight) * min(1f, dt * RATE)
        if (bounce >= 0f) {
            bounce += dt
            if (bounce >= BOUNCE_ATTACK + BOUNCE_RELEASE) bounce = -1f
        }
    }

    fun apply(pose: FacePose) {
        if (bounce >= 0f) add(pose, Channel.HeadSquash, BOUNCE * Envelope.value(bounce, BOUNCE_ATTACK, 0f, BOUNCE_RELEASE))
        val w = weight
        if (w <= MIN_WEIGHT) return
        add(pose, Channel.HeadX, MOVE_X * w)
        add(pose, Channel.HeadY, MOVE_Y * w)
        add(pose, Channel.HeadScale, SHRINK * w)
        add(pose, Channel.HeadTilt, TILT * w)
        pull(pose, Channel.GazeX, LOOK_X, w)
        pull(pose, Channel.GazeY, LOOK_LOW + (LOOK_HIGH - LOOK_LOW) * level, w)
        add(pose, Channel.BrowHeightL, BROWS_PER_LEVEL * level * w)
        add(pose, Channel.BrowHeightR, BROWS_PER_LEVEL * level * w)
        add(pose, Channel.EyeOpenL, EYES_PER_LEVEL * level * w)
        add(pose, Channel.EyeOpenR, EYES_PER_LEVEL * level * w)
        when {
            level >= LOUD -> {
                add(pose, Channel.EyeSquint, WINCE * w)
                add(pose, Channel.MouthCurve, -0.2f * w)
                add(pose, Channel.MouthOpen, 0.25f * w)
            }
            level <= MUTED -> {
                add(pose, Channel.EyeOpenL, -0.4f * w)
                add(pose, Channel.EyeOpenR, -0.4f * w)
                add(pose, Channel.MouthRoundness, 0.8f * w)
                add(pose, Channel.MouthOpen, 0.08f * w)
                add(pose, Channel.MouthWidth, -0.4f * w)
            }
            else -> add(pose, Channel.MouthRoundness, 0.3f * w)
        }
    }

    private fun add(pose: FacePose, channel: Channel, delta: Float) {
        pose[channel] = pose[channel] + delta
    }

    private fun pull(pose: FacePose, channel: Channel, value: Float, weight: Float) {
        pose[channel] = pose[channel] + (value - pose[channel]) * weight
    }

    private companion object {
        const val HOLD = 2.5f
        const val RATE = 8f
        const val MIN_WEIGHT = 0.001f
        const val MOVE_X = 0.2f
        const val MOVE_Y = 0.45f
        const val SHRINK = -0.3f
        const val TILT = 0.12f
        const val LOOK_X = 0.55f
        const val LOOK_LOW = -0.85f
        const val LOOK_HIGH = 0.15f
        const val BROWS_PER_LEVEL = 0.45f
        const val EYES_PER_LEVEL = 0.15f
        const val LOUD = 0.98f
        const val MUTED = 0.02f
        const val WINCE = 0.6f
        const val BOUNCE = 0.25f
        const val BOUNCE_ATTACK = 0.1f
        const val BOUNCE_RELEASE = 0.35f
    }
}
