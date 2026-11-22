package voice.api

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceSplitterTest {
    private val splitter = SentenceSplitter()

    private fun texts(s: String) = splitter.split(s).map { it.text }

    @Test
    fun splitsOnTerminators() {
        assertEquals(listOf("Hi there!", "How are you?", "Good."), texts("Hi there! How are you? Good."))
    }

    @Test
    fun keepsAbbreviationsDecimalsAndInitialsTogether() {
        assertEquals(listOf("Dr. Smith paid 3.50 dollars to J. R. Jones.", "Then he left."), texts("Dr. Smith paid 3.50 dollars to J. R. Jones. Then he left."))
        assertEquals(listOf("Visit example.com today."), texts("Visit example.com today."))
        assertEquals(listOf("It is in the U.S. now."), texts("It is in the U.S. now."))
    }

    @Test
    fun ellipsisBeforeLowercaseContinues() {
        assertEquals(listOf("Well... maybe.", "Or not."), texts("Well... maybe. Or not."))
    }

    @Test
    fun closingQuotesStayWithTheirSentence() {
        assertEquals(listOf("He said \"hello.\"", "She waved."), texts("He said \"hello.\" She waved."))
    }

    @Test
    fun rangesPointIntoTheOriginalText() {
        val text = "  One.  Two!  "
        for (s in splitter.split(text)) assertEquals(s.text, text.substring(s.range.first, s.range.last + 1))
    }

    @Test
    fun longSentencesBreakAtClauseMarks() {
        val text = (1..40).joinToString(", ") { "word$it" } + "."
        val parts = SentenceSplitter(maxChars = 60).split(text)
        assertTrue(parts.size > 1)
        for (p in parts) assertTrue(p.text.length <= 60, p.text)
        assertTrue(parts.dropLast(1).all { it.text.endsWith(",") })
    }

    @Test
    fun dropsEmptyAndPunctuationOnlyPieces() {
        assertEquals(listOf("Yes."), texts("   ... Yes.  !! "))
        assertTrue(splitter.split("").isEmpty())
    }

    @Test
    fun streamMatchesWholeTextSplit() = runBlocking {
        val text = "Hello there. I am Dexter! Dr. Who? It costs 3.50, really... wow. Bye"
        val pieces = text.chunked(3)
        val streamed = splitter.stream(flowOf(*pieces.toTypedArray())).toList()
        val whole = splitter.split(text)
        assertEquals(whole.map { it.text }, streamed.map { it.text })
        assertEquals(whole.map { it.range }, streamed.map { it.range })
    }

    @Test
    fun streamEmitsFirstSentenceBeforeTheRestArrives() = runBlocking {
        val seen = ArrayList<String>()
        val parts = kotlinx.coroutines.flow.flow {
            emit("First one. Sec")
            assertEquals(listOf("First one."), seen)
            emit("ond one.")
        }
        splitter.stream(parts).collect { seen.add(it.text) }
        assertEquals(listOf("First one.", "Second one."), seen)
    }
}
