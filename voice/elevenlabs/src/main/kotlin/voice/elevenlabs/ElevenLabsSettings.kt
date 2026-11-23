package voice.elevenlabs

// model: eleven_flash_v2_5 (fastest, ~100 ms), eleven_v4_turbo (more expressive, fast) or eleven_multilingual_v2
// (lifelike, slower). Voice settings are 0..1 and override the voice's stored ones per request.
public data class ElevenLabsSettings(
    val model: String = DEFAULT_MODEL,
    val stability: Float = 0.45f,
    val similarity: Float = 0.8f,
    val style: Float = 0.25f,
    val speakerBoost: Boolean = true,
    val timeoutSeconds: Long = 30,
) {
    public companion object {
        public const val DEFAULT_MODEL: String = "eleven_flash_v2_5"
        public val MODELS: List<String> = listOf("eleven_flash_v2_5", "eleven_v4_turbo", "eleven_multilingual_v2")
    }
}

// One voice in the account's library.
public data class ElevenLabsVoice(
    val voiceId: String,
    val name: String,
    val category: String,
    val description: String,
    val previewUrl: String?,
) {
    public val id: String get() = ElevenLabsEngine.PREFIX + voiceId
}
