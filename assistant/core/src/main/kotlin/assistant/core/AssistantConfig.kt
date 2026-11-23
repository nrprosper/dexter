package assistant.core

// Timeouts in milliseconds. Lines are what Dexter says when something goes wrong (spoken offline, never by the LLM).
public data class AssistantConfig(
    val headerTimeoutMillis: Long = 15_000,
    val listenTimeoutMillis: Long = 20_000,
    val lines: Lines = Lines(),
)

public data class Lines(
    val noMic: String = "I can't hear you yet. Let me use the microphone and try again.",
    val noKey: String = "I need my API key first. Long-press my icon and open Settings.",
    val badKey: String = "My API key didn't work. Check it in Settings.",
    val offline: String = "I can't reach my brain right now. Is the internet on?",
    val busy: String = "I'm a bit overloaded. Ask me again in a moment.",
    val broken: String = "Oops, my thoughts got tangled. Can you ask that again?",
    val declined: String = "Hmm, I'd rather not answer that one.",
    val slow: String = "Sorry, that took too long. Try me again?",
)
