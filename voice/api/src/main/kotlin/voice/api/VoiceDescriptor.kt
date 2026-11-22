package voice.api

public enum class VoiceQuality { Low, Medium, High }

public data class VoiceDescriptor(
    val id: String,
    val engineId: String,
    val name: String,
    val language: String,
    val sampleRate: Int,
    val speakers: Int = 1,
    val quality: VoiceQuality = VoiceQuality.Medium,
    val license: String = "",
    val installed: Boolean = true,
    val downloadBytes: Long = 0,
)
