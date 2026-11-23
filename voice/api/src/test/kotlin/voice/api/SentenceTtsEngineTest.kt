package voice.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceTtsEngineTest {
    private class Counting(private val block: suspend (String) -> Unit = {}) : SentenceTtsEngine() {
        val synthesized = ArrayList<String>()
        override val id = "fake"
        override val capabilities = EngineCapabilities(EngineLocation.Offline)
        override suspend fun availability() = Availability.Ready
        override suspend fun voices() = emptyList<VoiceDescriptor>()
        override fun prepare(text: String) = TextNormalizer.normalize(text)
        override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio {
            synthesized.add(text)
            block(text)
            return SentenceAudio(ShortArray(text.length * 10), 16_000)
        }
    }

    @Test
    fun chunksAreContiguousAndCarryTextRanges() = runBlocking {
        val text = "One two. Three four five!"
        val chunks = Counting().synthesize(TtsInput.Text(text)).toList()
        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].startSample)
        assertEquals(chunks[0].endSample, chunks[1].startSample)
        assertEquals("Three four five!", text.substring(chunks[1].textRange!!.first, chunks[1].textRange!!.last + 1))
    }

    @Test
    fun cancellingAfterTheFirstChunkSkipsLaterSentences() = runBlocking {
        val engine = Counting()
        engine.synthesize(TtsInput.Text("A one. B two. C three.")).take(1).toList()
        assertEquals(listOf("A one."), engine.synthesized)
    }

    @Test
    fun cancellationInterruptsAsentenceInProgress() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val engine = Counting { text -> if (text.startsWith("Slow")) { started.complete(Unit); awaitCancellation() } }
        val job = async { engine.synthesize(TtsInput.Text("Quick one. Slow two. Never three.")).toList() }
        withTimeout(2_000) { started.await() }
        job.cancel()
        assertTrue(runCatching { job.await() }.isFailure)
        assertEquals(listOf("Quick one.", "Slow two."), engine.synthesized)
    }

    @Test
    fun streamingTextStartsBeforeTheStreamEnds() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val engine = Counting()
        val parts = flow {
            emit("Hello there. And")
            gate.await()
            emit(" more.")
        }
        val first = engine.synthesize(TtsInput.Stream(parts)).first()
        assertEquals("Hello there.", engine.synthesized.single())
        assertEquals(0L, first.startSample)
        gate.complete(Unit)
    }

    @Test
    fun preSplitSentencesKeepTheirPartIndex() = runBlocking {
        val engine = Counting()
        val chunks = engine.synthesize(TtsInput.Sentences(kotlinx.coroutines.flow.flowOf("Sure thing", "Dr. Who is here. Twice!"))).toList()
        assertEquals(listOf("Sure thing", "Doctor Who is here.", "Twice!"), engine.synthesized)
        assertEquals(listOf(0, 1, 1), chunks.map { it.part })
    }

    @Test
    fun preparedTextIsWhatGetsSynthesized() = runBlocking {
        val engine = Counting()
        engine.synthesize(TtsInput.Text("I have 2 cats.")).toList()
        assertEquals(listOf("I have two cats."), engine.synthesized)
    }
}
