package voice.api

// speed and pitch 1 = natural; variation is the engine's expressiveness noise (Piper's noise scale);
// voiceId null = engine default. Engines that can't change pitch ignore it.
public data class SynthesisSettings(
    val voiceId: String? = null,
    val speed: Float = 1f,
    val variation: Float = 0.667f,
    val speakerId: Int = 0,
    val pitch: Float = 1f,
) {
    init {
        require(speed > 0f) { "speed must be positive" }
        require(pitch > 0f) { "pitch must be positive" }
        require(variation >= 0f) { "variation must not be negative" }
        require(speakerId >= 0) { "speakerId must not be negative" }
    }
}
