package face.core.rig

import kotlin.math.sqrt

public object Constraints {
    public fun apply(pose: FacePose) {
        val v = pose.values
        for (channel in Channel.entries) v[channel.ordinal] = channel.clamp(v[channel.ordinal])
        limitGazeToUnitDisc(v)
        limitLowerLid(v, Channel.LowerLidRaiseL, Channel.EyeOpenL)
        limitLowerLid(v, Channel.LowerLidRaiseR, Channel.EyeOpenR)
    }

    private fun limitGazeToUnitDisc(v: FloatArray) {
        val x = v[Channel.GazeX.ordinal]
        val y = v[Channel.GazeY.ordinal]
        val length = sqrt(x * x + y * y)
        if (length > 1f) {
            v[Channel.GazeX.ordinal] = x / length
            v[Channel.GazeY.ordinal] = y / length
        }
    }

    private fun limitLowerLid(v: FloatArray, lowerLid: Channel, open: Channel) {
        val limit = v[open.ordinal]
        if (v[lowerLid.ordinal] > limit) v[lowerLid.ordinal] = limit
    }
}
