package face.core.senses

import face.api.DeviceState
import face.core.anim.Envelope
import face.core.behavior.BoredomTracker
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng

// Sustained phone conditions: low battery turns the face red and worried (and sleepier); face-down puts it to sleep.
internal class DeviceResponder(private val rng: Rng, private val boredom: BoredomTracker) {
    var state = DeviceState()
        private set
    private var sweatElapsed = -1f
    private var untilSweat = 0f

    private val warning: Boolean get() = state.batteryLow && !state.charging

    // Returns true when the phone was just picked up again, so the face should wake.
    fun set(next: DeviceState): Boolean {
        val pickedUp = state.faceDown && !next.faceDown
        state = next
        if (warning) untilSweat = rng.nextFloat(SWEAT_MIN_GAP, SWEAT_MAX_GAP) * 0.3f
        return pickedUp
    }

    fun update(dt: Float) {
        if (state.faceDown) boredom.dozeOff()
        boredom.tiredness = if (warning) LOW_BATTERY_TIREDNESS else 1f
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

    private companion object {
        const val LOW_BATTERY_TIREDNESS = 1.6f
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
