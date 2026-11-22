package face.core.senses

import face.core.rig.Channel
import face.core.rig.FacePose

// The face lives inside the glass: tilting the phone makes it slide and look a little "downhill".
internal class TiltParallax {
    var x = 0f
    var y = 0f

    fun apply(pose: FacePose) {
        if (x == 0f && y == 0f) return
        pose[Channel.HeadX] = pose[Channel.HeadX] + x * SLIDE_X
        pose[Channel.HeadY] = pose[Channel.HeadY] - y * SLIDE_Y
        pose[Channel.GazeX] = pose[Channel.GazeX] + x * LOOK_X
        pose[Channel.GazeY] = pose[Channel.GazeY] - y * LOOK_Y
        pose[Channel.HeadTilt] = pose[Channel.HeadTilt] - x * COUNTER_TILT
    }

    private companion object {
        const val SLIDE_X = 0.07f
        const val SLIDE_Y = 0.05f
        const val LOOK_X = 0.35f
        const val LOOK_Y = 0.3f
        const val COUNTER_TILT = 0.04f
    }
}
