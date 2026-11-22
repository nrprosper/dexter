package face.core.time

// Turns frame timestamps into a clamped dt; the first frame after start or reset yields 0 so resumes never jump.
public class FrameTicker(private val maxDt: Float = MAX_DT) {
    private var lastNanos = NONE

    public fun tick(frameNanos: Long): Float {
        val last = lastNanos
        lastNanos = frameNanos
        if (last == NONE || frameNanos <= last) return 0f
        return ((frameNanos - last) / NANOS_PER_SECOND).coerceAtMost(maxDt)
    }

    public fun reset() {
        lastNanos = NONE
    }

    public companion object {
        public const val MAX_DT: Float = 0.05f
        private const val NONE = Long.MIN_VALUE
        private const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
