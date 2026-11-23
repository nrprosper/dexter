package face.speech

import face.api.MouthFrame
import face.api.SpeechTrack

// The SpeechTrack the face samples: envelope value at the sample being heard, plus a lead (mouths lead sound)
// and a user sync offset (positive = mouth earlier; for Bluetooth headsets the offset usually needs to grow).
public class AmplitudeSpeechTrack internal constructor(private val envelope: EnvelopeBuilder) : SpeechTrack {
    @Volatile
    internal var sink: AudioSink? = null

    @Volatile
    private var active = false

    @Volatile
    public var leadMillis: Int = DEFAULT_LEAD_MILLIS

    @Volatile
    public var syncOffsetMillis: Int = 0

    override val isActive: Boolean get() = active

    internal fun begin(sink: AudioSink) {
        this.sink = sink
        active = true
    }

    internal fun end() {
        active = false
        sink = null
    }

    override fun sample(nowNanos: Long, out: MouthFrame) {
        val s = sink
        if (!active || s == null) return
        val ahead = (leadMillis + syncOffsetMillis) * NANOS_PER_MILLI
        val frames = s.playedFrames(nowNanos + ahead)
        val hopSize = envelope.hopSize
        if (frames < 0 || hopSize <= 0) return
        val hop = frames / hopSize
        val a = envelope.loudnessAt(hop)
        if (a < 0f) return
        val b = envelope.loudnessAt(hop + 1)
        val t = (frames % hopSize).toFloat() / hopSize
        out.openness = if (b < 0f) a else a + (b - a) * t
        val bright = envelope.brightnessAt(hop)
        out.width = bright
        out.roundness = 1f - bright
        out.emphasis = envelope.emphasisAt(hop)
    }

    public companion object {
        public const val DEFAULT_LEAD_MILLIS: Int = 50
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
