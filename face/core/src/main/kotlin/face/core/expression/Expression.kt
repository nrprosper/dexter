package face.core.expression

import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.abs

public class Expression(
    public val id: String,
    public val channels: IntArray,
    public val deltas: FloatArray,
) {
    init {
        require(channels.size == deltas.size) { "channels and deltas differ in size for '$id'" }
    }

    public fun delta(channel: Channel): Float {
        val i = channels.indexOf(channel.ordinal)
        return if (i < 0) 0f else deltas[i]
    }

    public companion object {
        public const val EPSILON: Float = 0.005f

        public fun of(id: String, deltas: Map<Channel, Float>): Expression {
            val sorted = deltas.entries.filter { abs(it.value) > EPSILON }.sortedBy { it.key.ordinal }
            return Expression(
                id,
                IntArray(sorted.size) { sorted[it].key.ordinal },
                FloatArray(sorted.size) { sorted[it].value },
            )
        }

        public fun fromPose(id: String, pose: FacePose, base: FacePose = FacePose()): Expression =
            of(id, Channel.entries.associateWith { pose[it] - base[it] })
    }
}

public enum class ExpressionId {
    Neutral, Happy, Curious, Confused, Sad, Concerned, Playful, Surprised, Sleeping,
    Joy, Laugh, Determined, Smug, Skeptical;

    public val key: String = name.lowercase()
}
