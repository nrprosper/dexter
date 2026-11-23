package assistant.core

import face.api.Emotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReplyParserTest {
    private val reply = """{"emotion":"happy","intensity":0.8,"confidence":0.9,"tone":"excited",""" +
        """"sentences":[{"text":"Hi there!"},{"text":"Want a \"joke\"?\nOkay é.","emotion":"playful"}]}"""

    private class Recorded {
        val events = ArrayList<String>()
        var feeling: Feeling? = null
        val sentences = ArrayList<ReplySentence>()
        val parser = ReplyParser(
            { feeling = it; events.add("header") },
            { sentences.add(it); events.add("sentence ${it.index}") },
        )
    }

    @Test
    fun wholeReply() {
        val r = Recorded()
        r.parser.feed(reply)
        r.parser.finish()
        assertEquals(Feeling(Emotion.Happy, 0.8f, 0.9f, Tone.Excited), r.feeling)
        assertEquals(listOf("Hi there!", "Want a \"joke\"?\nOkay é."), r.sentences.map { it.text })
        assertNull(r.sentences[0].emotion)
        assertEquals(Emotion.Playful, r.sentences[1].emotion)
        assertEquals(listOf("header", "sentence 0", "sentence 1"), r.events)
    }

    @Test
    fun everySplitPointGivesTheSameResult() {
        for (cut in 1 until reply.length) {
            val r = Recorded()
            r.parser.feed(reply.substring(0, cut))
            r.parser.feed(reply.substring(cut))
            r.parser.finish()
            assertEquals(Feeling(Emotion.Happy, 0.8f, 0.9f, Tone.Excited), r.feeling, "cut at $cut")
            assertEquals(2, r.sentences.size, "cut at $cut")
        }
    }

    @Test
    fun headerArrivesBeforeTheFirstSentenceIsComplete() {
        val r = Recorded()
        for (c in reply) {
            r.parser.feed(c.toString())
            if (r.sentences.isNotEmpty()) break
        }
        assertTrue(r.events.first() == "header")
        val headerAt = reply.indexOf("\"sentences\"")
        val probe = Recorded()
        probe.parser.feed(reply.substring(0, headerAt))
        assertTrue(probe.feeling != null, "header should be known before the sentences start")
    }

    @Test
    fun unknownLabelsAndMissingFieldsFallBackToNeutral() {
        val r = Recorded()
        r.parser.feed("""{"emotion":"ecstatic","tone":"sarcastic","extra":{"a":[1,2]},"sentences":[{"text":"Hmm.","emotion":"angry"}]}""")
        r.parser.finish()
        assertEquals(Feeling(Emotion.Neutral, Feeling.DEFAULT_INTENSITY, 1f, Tone.Neutral), r.feeling)
        assertEquals(Emotion.Neutral, r.sentences.single().emotion)
    }

    @Test
    fun outOfRangeNumbersAreClamped() {
        val r = Recorded()
        r.parser.feed("""{"emotion":"sad","intensity":3,"confidence":-1,"tone":"calm","sentences":[]}""")
        r.parser.finish()
        assertEquals(1f, r.feeling!!.intensity)
        assertEquals(0f, r.feeling!!.confidence)
    }

    @Test
    fun aReplyCutShortKeepsOnlyCompleteSentences() {
        val r = Recorded()
        r.parser.feed("""{"emotion":"curious","intensity":0.5,"confidence":1,"tone":"neutral","sentences":[{"text":"One."},{"text":"Tw""")
        r.parser.finish()
        assertEquals(listOf("One."), r.sentences.map { it.text })
    }

    @Test
    fun emptyTextIsSkippedAndIndexesStayDense() {
        val r = Recorded()
        r.parser.feed("""{"emotion":"happy","intensity":1,"confidence":1,"tone":"funny","sentences":[{"text":"  "},{"text":"Yes!"}]}""")
        r.parser.finish()
        assertEquals(0, r.sentences.single().index)
    }

    @Test
    fun stageMovesAreReadAndUnknownOnesIgnored() {
        val r = Recorded()
        r.parser.feed("""{"emotion":"playful","intensity":0.7,"confidence":1,"tone":"funny","sentences":[""" +
            """{"text":"Over here!","move":"left"},{"text":"Closer.","move":"lean_in"},{"text":"Wheee.","move":"cartwheel"}]}""")
        r.parser.finish()
        assertEquals(listOf(face.api.StageMove.Left, face.api.StageMove.LeanIn, null), r.sentences.map { it.move })
    }

    @Test
    fun schemaListsTheFaceEmotionsAndTones() {
        for (e in Emotion.entries) assertTrue(ReplyContract.schema.contains("\"${e.name.lowercase()}\""))
        for (t in Tone.entries) assertTrue(ReplyContract.schema.contains("\"${t.name.lowercase()}\""))
        for (m in listOf("center", "lean_in", "peek", "hop")) assertTrue(ReplyContract.schema.contains("\"$m\""), m)
    }
}
