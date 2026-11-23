package assistant.core

// The result of checking an API key against its provider without spending tokens.
public enum class KeyCheck { Ok, Rejected, ModelUnavailable, Offline, Error }
