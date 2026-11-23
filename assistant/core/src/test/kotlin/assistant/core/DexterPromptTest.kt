package assistant.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DexterPromptTest {
    private val text = DexterPrompt.text

    @Test
    fun namesItsCreator() {
        assertTrue(text.contains(DexterPrompt.CREATOR))
    }

    @Test
    fun neverNamesTheCompaniesOrModelsBehindIt() {
        for (name in listOf("Claude", "Anthropic", "Gemini", "Google", "OpenAI", "GPT")) {
            assertFalse(text.contains(name, ignoreCase = true), "prompt mentions $name")
        }
    }

    @Test
    fun describesEveryLabelTheContractAllows() {
        for (label in ReplyContract.emotions + ReplyContract.tones + ReplyContract.moves) assertTrue(text.contains(label), label)
    }
}
