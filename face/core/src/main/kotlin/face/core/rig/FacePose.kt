package face.core.rig

import face.core.expression.Expression

public class FacePose {
    public val values: FloatArray = FloatArray(Channel.count)

    init {
        resetToRest()
    }

    public operator fun get(channel: Channel): Float = values[channel.ordinal]

    public operator fun set(channel: Channel, value: Float) {
        values[channel.ordinal] = value
    }

    public fun resetToRest() {
        for (channel in Channel.entries) values[channel.ordinal] = channel.rest
    }

    public fun copyFrom(other: FacePose) {
        other.values.copyInto(values)
    }

    public fun add(expression: Expression, weight: Float = 1f) {
        val channels = expression.channels
        val deltas = expression.deltas
        for (i in channels.indices) values[channels[i]] += deltas[i] * weight
    }
}
