package assistant.gemini

// thinkingLevel: MINIMAL | LOW | MEDIUM | HIGH (lower = faster first words).
public data class GeminiSettings(
    val model: String = DEFAULT_MODEL,
    val thinkingLevel: String = "LOW",
    val maxOutputTokens: Int = 2_048,
    val timeoutSeconds: Long = 30,
) {
    public companion object {
        public const val DEFAULT_MODEL: String = "gemini-4-hydrogen"

        // Models on the Gemini API free tier (checked against Google's pricing page, November 2026).
        public val MODELS: List<String> = listOf("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite")
    }
}
