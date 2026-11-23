package assistant.core

// Which service answers. Each has its own API key, encrypted separately on the phone.
public enum class BrainProvider(public val id: String, public val label: String) {
    Claude("claude", "Claude"),
    Gemini("gemini", "Gemini"),
    ;

    public companion object {
        public fun of(id: String?): BrainProvider = entries.firstOrNull { it.id == id } ?: Claude
    }
}
