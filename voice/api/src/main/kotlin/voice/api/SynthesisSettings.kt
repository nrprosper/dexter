package voice.api

// speed 1 = natural; variation is the engine's expressiveness noise (Piper's noise scale); voiceId null = engine default.
public data class SynthesisSettings(
    val voiceId: String? = null,
    val speed: Float = 1f,
    val variation: Float = 0.667f,
    val speakerId: Int = 0,
) {
    init {
        require(speed > 0f) { "speed must be positive" }
        require(variation >= 0f) { "variation must not be negative" }
        require(speakerId >= 0) { "speakerId must not be negative" }
    }
}
