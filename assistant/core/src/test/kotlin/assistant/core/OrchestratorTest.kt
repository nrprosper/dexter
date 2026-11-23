package assistant.core

import face.api.Activity
import face.api.DeviceState
import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.FaceSnapshot
import face.api.Personality
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.api.StageMove
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrchestratorTest {
    private class Face : FaceController {
        val log = ArrayList<String>()
        val levels = ArrayList<Float>()
        override fun setActivity(activity: Activity) { log.add("activity $activity") }
        override fun setEmotion(signal: EmotionSignal) { log.add("emotion ${signal.emotion}") }
        override fun react(event: FaceReaction) { log.add("react ${event::class.simpleName}") }
        override fun attachSpeech(track: SpeechTrack, style: SpeechStyle) = Unit
        override fun detachSpeech() = Unit
        override fun setUserVoiceLevel(level: Float) { levels.add(level) }
        override fun setPowerMode(mode: PowerMode) = Unit
        override fun setPersonality(personality: Personality) = Unit
        override fun setDeviceState(state: DeviceState) = Unit
        override fun showVolume(level: Float) = Unit
        override fun setTilt(x: Float, y: Float) = Unit
        override fun stage(move: StageMove) { log.add("stage $move") }
        override val snapshot: StateFlow<FaceSnapshot> = MutableStateFlow(FaceSnapshot())
    }

    private class Ears(var events: List<HearingEvent>) : assistant.core.Ears {
        var listens = 0
        override fun listen(): Flow<HearingEvent> = flow {
            listens++
            for (e in events) {
                delay(100)
                emit(e)
            }
        }
    }

    private class Brain(private val face: Face) : assistant.core.Brain {
        var failure: BrainFailure? = null
        var headerDelay = 300L
        var events: List<BrainEvent> = listOf(
            BrainEvent.Header(Feeling(Emotion.Happy, 0.8f, 1f, Tone.Excited)),
            BrainEvent.Sentence(ReplySentence(0, "Hello!")),
            BrainEvent.Sentence(ReplySentence(1, "I'm worried.", Emotion.Concerned, StageMove.Closer)),
            BrainEvent.Done,
        )
        val asked = ArrayList<Pair<Int, String>>()
        override fun reply(history: List<Turn>, user: String): Flow<BrainEvent> = flow {
            asked.add(history.size to user)
            delay(headerDelay)
            failure?.let { throw BrainException(it, "fake") }
            for (e in events) {
                emit(e)
                delay(50)
            }
        }
    }

    private class Mouth(private val face: Face) : assistant.core.Mouth {
        val spoken = ArrayList<String>()
        var styles = ArrayList<SpeechStyle>()
        var stops = 0
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun speak(sentences: Flow<ReplySentence>, style: SpeechStyle, onSentenceHeard: (Int) -> Unit) {
            styles.add(style)
            sentences.collect {
                face.log.add("say ${it.text}")
                spoken.add(it.text)
                onSentenceHeard(it.index)
                delay(200)
                hold?.await()
            }
        }
        override fun stop() { stops++ }
    }

    private val face = Face()
    private val ears = Ears(listOf(HearingEvent.Ready, HearingEvent.Level(0.6f), HearingEvent.Partial("hel"), HearingEvent.Heard("hello dexter")))
    private val brain = Brain(face)
    private val mouth = Mouth(face)

    private fun TestScope.orchestrator() = Orchestrator(face, ears, brain, mouth, this, clock = { testScheduler.currentTime })

    @Test
    fun aTurnListensThinksThenSpeaksWithTheFaceChangingFirst() = runTest {
        val o = orchestrator()
        o.onDoubleTap()
        advanceUntilIdle()
        val log = face.log
        assertEquals("react Acknowledge", log[0])
        assertEquals("activity Listening", log[1])
        assertTrue(face.levels.contains(0.6f))
        val emotionAt = log.indexOf("emotion Happy")
        val firstWords = log.indexOf("say Hello!")
        assertTrue(emotionAt in 0 until firstWords, "emotion must be set before the voice: $log")
        assertTrue(log.indexOf("activity Thinking") < emotionAt)
        assertTrue(log.indexOf("emotion Concerned") > firstWords, "per-sentence emotion when that sentence is heard")
        assertTrue(log.indexOf("stage Closer") > log.indexOf("say I'm worried."), "a move plays as its sentence is heard")
        assertEquals("activity Idle", log.last())
        assertEquals(AssistantState.Idle, o.state.value)
        assertEquals("hello dexter", o.lastHeard.value)
        assertEquals(SpeechStyle.Excited.playfulness, mouth.styles.single().playfulness)
        o.ask("and again?")
        advanceUntilIdle()
        assertEquals(listOf(0 to "hello dexter", 1 to "and again?"), brain.asked)
    }

    @Test
    fun theMomentTravelsWithTheQuestionButNotIntoHistory() = runTest {
        val o = Orchestrator(face, ears, brain, mouth, this, clock = { testScheduler.currentTime }, context = { "Sunday evening, 7:40 pm" })
        o.ask("what should I do tonight?")
        advanceUntilIdle()
        o.ask("and tomorrow?")
        advanceUntilIdle()
        assertEquals("[Sunday evening, 7:40 pm]\nwhat should I do tonight?", brain.asked[0].second)
        assertEquals(1, brain.asked[1].first)
    }

    @Test
    fun listeningClearsTheLastTranscriptAndReportsTheVoiceLevel() = runTest {
        val o = orchestrator()
        o.ask("old question")
        advanceUntilIdle()
        val levels = ArrayList<Float>()
        val heard = ArrayList<String>()
        val watch = launch { o.voiceLevel.collect { levels.add(it) } }
        val watchHeard = launch { o.lastHeard.collect { heard.add(it) } }
        o.onDoubleTap()
        advanceUntilIdle()
        watch.cancel()
        watchHeard.cancel()
        assertTrue(0.6f in levels)
        assertEquals(0f, o.voiceLevel.value)
        assertTrue("" in heard, "the old transcript should clear when listening starts: $heard")
        assertTrue("hel" in heard)
    }

    @Test
    fun nothingHeardLooksConfusedAndDoesNotAsk() = runTest {
        ears.events = listOf(HearingEvent.Ready, HearingEvent.NothingHeard)
        orchestrator().onDoubleTap()
        advanceUntilIdle()
        assertTrue("emotion Confused" in face.log)
        assertTrue(brain.asked.isEmpty())
        assertEquals("activity Idle", face.log.last())
    }

    @Test
    fun microphoneProblemsSayOops() = runTest {
        ears.events = listOf(HearingEvent.Failed(HearingFailure.NoPermission))
        orchestrator().onDoubleTap()
        advanceUntilIdle()
        assertTrue("react Oops" in face.log)
        assertEquals(listOf(AssistantConfig().lines.noMic), mouth.spoken)
    }

    @Test
    fun brainFailuresSpeakTheMatchingLine() = runTest {
        val lines = AssistantConfig().lines
        val cases = mapOf(
            BrainFailure.NoKey to lines.noKey,
            BrainFailure.Auth to lines.badKey,
            BrainFailure.Network to lines.offline,
            BrainFailure.RateLimit to lines.busy,
            BrainFailure.Server to lines.broken,
        )
        val o = orchestrator()
        for ((failure, line) in cases) {
            brain.failure = failure
            mouth.spoken.clear()
            o.ask("hi")
            advanceUntilIdle()
            assertEquals(listOf(line), mouth.spoken, "$failure")
            assertEquals(AssistantState.Idle, o.state.value)
        }
        assertTrue(face.log.count { it == "react Oops" } == cases.size)
    }

    @Test
    fun aSlowBrainApologises() = runTest {
        brain.headerDelay = 60_000
        orchestrator().ask("hi")
        advanceUntilIdle()
        assertEquals(listOf(AssistantConfig().lines.slow), mouth.spoken)
    }

    @Test
    fun doubleTapWhileSpeakingInterruptsAndListensAgain() = runTest {
        val o = orchestrator()
        mouth.hold = CompletableDeferred()
        o.ask("tell me a long story")
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(AssistantState.Speaking, o.state.value)
        mouth.hold = null
        o.onDoubleTap()
        advanceUntilIdle()
        assertTrue(mouth.stops >= 1)
        assertEquals(1, ears.listens)
        assertEquals(listOf(0 to "tell me a long story", 0 to "hello dexter"), brain.asked, "an interrupted turn isn't remembered")
        assertEquals(AssistantState.Idle, o.state.value)
        assertEquals("activity Idle", face.log.last())
    }

    @Test
    fun declinedWithoutWordsSaysSo() = runTest {
        brain.events = listOf(BrainEvent.Header(Feeling()), BrainEvent.Declined("refusal"), BrainEvent.Done)
        orchestrator().ask("something odd")
        advanceUntilIdle()
        assertEquals(listOf(AssistantConfig().lines.declined), mouth.spoken)
        assertTrue("emotion Concerned" in face.log)
    }
}
