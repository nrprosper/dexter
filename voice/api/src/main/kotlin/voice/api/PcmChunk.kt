package voice.api

// 16-bit mono PCM. startSample counts from the start of the utterance; textRange points into the utterance text.
public class PcmChunk(
    public val samples: ShortArray,
    public val sampleRate: Int,
    public val startSample: Long,
    public val textRange: IntRange? = null,
    public val alignment: Alignment? = null,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    public val endSample: Long get() = startSample + samples.size
}

public enum class AlignmentUnit { Char, Word }

// Timing of text units relative to the chunk's first sample; units index into text. Empty for engines without timing.
public class Alignment(
    public val unit: AlignmentUnit,
    public val text: String,
    public val starts: IntArray,
    public val ends: IntArray,
    public val startMs: IntArray,
    public val durationMs: IntArray,
) {
    init {
        val n = starts.size
        require(ends.size == n && startMs.size == n && durationMs.size == n) { "alignment arrays differ in size" }
    }

    public val size: Int get() = starts.size
}
