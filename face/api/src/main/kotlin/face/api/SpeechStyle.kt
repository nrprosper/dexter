package face.api

// Tone of an utterance, each 0..1. It scales mouth amplitude, gestures, head motion and arousal.
public data class SpeechStyle(
    val energy: Float = 0.5f,
    val seriousness: Float = 0f,
    val playfulness: Float = 0f,
) {
    public companion object {
        public val Calm: SpeechStyle = SpeechStyle(energy = 0.25f)
        public val Excited: SpeechStyle = SpeechStyle(energy = 0.9f, playfulness = 0.3f)
        public val Serious: SpeechStyle = SpeechStyle(energy = 0.4f, seriousness = 0.9f)
        public val Funny: SpeechStyle = SpeechStyle(energy = 0.7f, playfulness = 0.9f)
    }
}
