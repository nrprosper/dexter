package face.core.senses

import face.api.DayPart
import face.api.DeviceState
import face.api.Heat
import face.core.anim.Envelope
import face.core.behavior.BoredomTracker
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.PI
import kotlin.math.sin

// Sustained phone conditions (battery, heat, hand, pocket, dark, saver, night, face-down); several make it sleepier.
internal class DeviceResponder(private val rng: Rng, private val boredom: BoredomTracker) {
    var state = DeviceState()
        private set
    private var sweatElapsed = -1f
    private var untilSweat = 0f
    private var clock = 0f
    private var handElapsed = 0f

    private val warning: Boolean get() = state.batteryLow && !state.charging

    // Returns true when the phone was just picked up or pulled out of a pocket, so the face should wake.
    fun set(next: DeviceState): Boolean {
        val pickedUp = (state.faceDown && !next.faceDown) || (state.inPocket && !next.inPocket)
        if (next.handOver && !state.handOver) handElapsed = 0f
        state = next
        if (warning) untilSweat = rng.nextFloat(SWEAT_MIN_GAP, SWEAT_MAX_GAP) * 0.3f
        return pickedUp
    }

    fun update(dt: Float) {
        clock += dt
        if (state.handOver) handElapsed += dt
        if (state.faceDown || state.inPocket) boredom.dozeOff()
        var tired = if (warning) LOW_BATTERY_TIREDNESS else 1f
        if (state.dark) tired *= DARK_TIREDNESS
        if (state.dayPart == DayPart.Night) tired *= NIGHT_TIREDNESS
        if (state.batterySaver) tired *= SAVER_TIREDNESS
        boredom.tiredness = tired
        if (sweatElapsed >= 0f) {
            sweatElapsed += dt
            if (sweatElapsed >= SWEAT_ATTACK + SWEAT_HOLD + SWEAT_RELEASE) sweatElapsed = -1f
        }
        if (!warning) return
        untilSweat -= dt
        if (untilSweat <= 0f) {
            untilSweat = rng.nextFloat(SWEAT_MIN_GAP, SWEAT_MAX_GAP)
            sweatElapsed = 0f
        }
    }

    fun apply(pose: FacePose) {
        applyHeat(pose)
        applyHand(pose)
        if (state.inPocket) add(pose, Channel.HeadScale, POCKET_CURL)
        if (state.dark) add(pose, Channel.Glow, DARK_GLOW)
        if (state.batterySaver) {
            add(pose, Channel.EyeOpenL, SAVER_LIDS)
            add(pose, Channel.EyeOpenR, SAVER_LIDS)
            add(pose, Channel.UpperLidCurveL, SAVER_CURVE)
            add(pose, Channel.UpperLidCurveR, SAVER_CURVE)
            add(pose, Channel.Glow, SAVER_GLOW)
        }
        if (sweatElapsed >= 0f) {
            pose[Channel.Sweat] = pose[Channel.Sweat] + Envelope.value(sweatElapsed, SWEAT_ATTACK, SWEAT_HOLD, SWEAT_RELEASE)
        }
        if (!warning) return
        pose[Channel.WarnTint] = pose[Channel.WarnTint] + 1f
        pose[Channel.BrowTiltL] = pose[Channel.BrowTiltL] + WORRY_BROW_TILT
        pose[Channel.BrowTiltR] = pose[Channel.BrowTiltR] + WORRY_BROW_TILT
        pose[Channel.BrowHeightL] = pose[Channel.BrowHeightL] + WORRY_BROW_LIFT
        pose[Channel.BrowHeightR] = pose[Channel.BrowHeightR] + WORRY_BROW_LIFT
        pose[Channel.MouthCurve] = pose[Channel.MouthCurve] + WORRY_MOUTH
        pose[Channel.MouthWidth] = pose[Channel.MouthWidth] + WORRY_MOUTH_WIDTH
    }

    // Warm: a flush. Hot: sweating, panting, squinting and swaying like fanning itself. Critical: a melting droop.
    private fun applyHeat(pose: FacePose) {
        when (state.heat) {
            Heat.Normal -> Unit
            Heat.Warm -> {
                add(pose, Channel.Blush, 0.3f)
                add(pose, Channel.Sweat, 0.3f)
            }
            Heat.Hot -> {
                add(pose, Channel.Blush, 0.6f)
                add(pose, Channel.Sweat, 0.7f)
                add(pose, Channel.EyeSquint, 0.35f)
                add(pose, Channel.MouthOpen, PANT_OPEN + PANT_DEPTH * sin(2f * PI.toFloat() * PANT_RATE * clock))
                add(pose, Channel.MouthRoundness, 0.3f)
                add(pose, Channel.HeadTilt, FAN_SWAY * sin(2f * PI.toFloat() * FAN_RATE * clock))
            }
            Heat.Critical -> {
                add(pose, Channel.Blush, 0.8f)
                add(pose, Channel.Sweat, 1f)
                add(pose, Channel.HeadSquash, -0.35f)
                add(pose, Channel.HeadY, -0.08f)
                add(pose, Channel.EyeOpenL, -0.4f)
                add(pose, Channel.EyeOpenR, -0.4f)
                add(pose, Channel.MouthCurve, -0.4f)
                add(pose, Channel.MouthOpen, 0.25f)
                add(pose, Channel.BrowTiltL, -0.6f)
                add(pose, Channel.BrowTiltR, -0.6f)
                add(pose, Channel.Glow, -0.2f)
            }
        }
    }

    // A hand over the screen: eyes squeezed shut, then one eye peeks out.
    private fun applyHand(pose: FacePose) {
        if (!state.handOver || state.inPocket) return
        add(pose, Channel.EyeOpenL, HAND_SHUT)
        if (handElapsed < PEEK_AFTER) {
            add(pose, Channel.EyeOpenR, HAND_SHUT)
            add(pose, Channel.HeadSquash, -0.15f)
        } else {
            add(pose, Channel.EyeOpenR, PEEK_OPEN)
            add(pose, Channel.BrowHeightR, 0.4f)
            add(pose, Channel.MouthRoundness, 0.4f)
        }
    }

    private fun add(pose: FacePose, channel: Channel, delta: Float) {
        pose[channel] = pose[channel] + delta
    }

    private companion object {
        const val LOW_BATTERY_TIREDNESS = 1.6f
        const val DARK_TIREDNESS = 1.4f
        const val NIGHT_TIREDNESS = 1.3f
        const val SAVER_TIREDNESS = 1.2f
        const val POCKET_CURL = -0.15f
        const val DARK_GLOW = -0.3f
        const val SAVER_LIDS = -0.15f
        const val SAVER_CURVE = -0.1f
        const val SAVER_GLOW = -0.2f
        const val PANT_OPEN = 0.2f
        const val PANT_DEPTH = 0.12f
        const val PANT_RATE = 2.5f
        const val FAN_SWAY = 0.05f
        const val FAN_RATE = 1.2f
        const val HAND_SHUT = -1.2f
        const val PEEK_AFTER = 0.8f
        const val PEEK_OPEN = -0.5f
        const val SWEAT_MIN_GAP = 8f
        const val SWEAT_MAX_GAP = 14f
        const val SWEAT_ATTACK = 0.3f
        const val SWEAT_HOLD = 1.2f
        const val SWEAT_RELEASE = 0.6f
        const val WORRY_BROW_TILT = -0.45f
        const val WORRY_BROW_LIFT = 0.2f
        const val WORRY_MOUTH = -0.25f
        const val WORRY_MOUTH_WIDTH = -0.1f
    }
}
