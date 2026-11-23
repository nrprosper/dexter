package assistant.claude

// effort: low | medium | high (low suits short spoken chat and keeps the first words quick).
public data class ClaudeSettings(
    val model: String = DEFAULT_MODEL,
    val effort: String = "low",
    val maxTokens: Long = 4_096,
    val timeoutSeconds: Long = 30,
) {
    public companion object {
        public const val DEFAULT_MODEL: String = "claude-opus-5-5"
        public val MODELS: List<String> = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
    }
}
