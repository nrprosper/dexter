package face.api

// Filled by a SpeechTrack each frame; values are 0..1 and the object is reused, never reallocated.
public class MouthFrame {
    public var openness: Float = 0f
    public var width: Float = 0f
    public var roundness: Float = 0f
    public var emphasis: Float = 0f

    public fun clear() {
        openness = 0f
        width = 0f
        roundness = 0f
        emphasis = 0f
    }
}

// Pulled at frame time, never pushed: the face samples the mouth at the frame's timestamp.
public interface SpeechTrack {
    public fun sample(nowNanos: Long, out: MouthFrame)

    public val isActive: Boolean
}
