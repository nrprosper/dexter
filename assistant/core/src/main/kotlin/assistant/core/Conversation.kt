package assistant.core

// Recent turns, in memory only: never written to disk. Old turns fall off; a long silence starts a fresh conversation.
public class Conversation(private val maxTurns: Int = 12, private val forgetAfterMillis: Long = 10 * 60_000L) {
    private val turns = ArrayDeque<Turn>()
    private var lastAt = Long.MIN_VALUE

    public fun history(nowMillis: Long): List<Turn> {
        if (lastAt != Long.MIN_VALUE && nowMillis - lastAt > forgetAfterMillis) turns.clear()
        return turns.toList()
    }

    public fun add(turn: Turn, nowMillis: Long) {
        turns.addLast(turn)
        while (turns.size > maxTurns) turns.removeFirst()
        lastAt = nowMillis
    }

    public fun clear() {
        turns.clear()
    }

    public val size: Int get() = turns.size
}
