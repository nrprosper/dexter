package voice.piper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LexiconTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun loadsSingleWordEntriesOnly() {
        val file = File(tmp.root, "lex.txt").apply {
            writeText("# header\nHello || h ə l ˈoʊ\ndon't || d ˈoʊ n t\nnew york || n uː j ˈɔː k\n")
        }
        val lex = Lexicon.load(file)
        assertTrue("hello" in lex && "don't" in lex)
        assertTrue("new york" !in lex)
    }

    @Test
    fun unknownShortWordsAreSpelledOut() {
        val lex = Lexicon.of("hello", "the", "is", "don't")
        assertEquals("Hello, X K C D is the... Don't", lex.respell("Hello, XKCD is the... Don't"))
        assertEquals("supercalifragilistic", lex.respell("supercalifragilistic"))
    }
}
