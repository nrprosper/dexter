package face.api

public enum class Emotion { Neutral, Happy, Curious, Confused, Sad, Concerned, Playful }

// How the assistant feels about what it's saying; intensity and confidence are 0..1.
public data class EmotionSignal(
    val emotion: Emotion,
    val intensity: Float = 0.7f,
    val confidence: Float = 1f,
) {
    init {
        require(intensity in 0f..1f) { "intensity must be in 0..1" }
        require(confidence in 0f..1f) { "confidence must be in 0..1" }
    }
}
