package assistant.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConversationTest {
    @Test
    fun keepsOnlyRecentTurns() {
        val c = Conversation(maxTurns = 3)
        repeat(5) { c.add(Turn("q$it", "a$it"), it.toLong()) }
        assertEquals(listOf("q2", "q3", "q4"), c.history(10).map { it.user })
    }

    @Test
    fun forgetsAfterALongSilence() {
        val c = Conversation(forgetAfterMillis = 1_000)
        c.add(Turn("q", "a"), 0)
        assertEquals(1, c.history(500).size)
        assertEquals(0, c.history(2_000).size)
    }
}
