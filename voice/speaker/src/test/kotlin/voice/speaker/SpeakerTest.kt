package voice.speaker

import face.api.Activity
import face.api.DeviceState
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.FaceSnapshot
import face.api.Personality
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.speech.AudioSink
import face.speech.PcmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import voice.api.Availability
import voice.api.Connectivity
import voice.api.EngineCapabilities
import voice.api.EngineLocation
import voice.api.PcmChunk
import voice.api.RoutingPolicy
import voice.api.SynthesisSettings
import voice.api.TtsEngine
import voice.api.TtsInput
import voice.api.TtsRouter
import voice.api.VoiceDescriptor
import java.util.Collections

class SpeakerTest {
    private class Face : FaceController {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        override fun setActivity(activity: Activity) { calls.add("activity $activity") }
        override fun setEmotion(signal: EmotionSignal) = Unit
        override fun react(event: FaceReaction) = Unit
        override fun attachSpeech(track: SpeechTrack, style: SpeechStyle) { calls.add("attach") }
        override fun detachSpeech() { calls.add("detach") }
        override fun setUserVoiceLevel(level: Float) = Unit
        override fun setPowerMode(mode: PowerMode) = Unit
        override fun setPersonality(personality: Personality) = Unit
        override fun setDeviceState(state: DeviceState) = Unit
        override fun showVolume(level: Float) = Unit
        override fun setTilt(x: Float, y: Float) = Unit
        override fun stage(move: face.api.StageMove) = Unit
        override val snapshot: StateFlow<FaceSnapshot> = MutableStateFlow(FaceSnapshot())
    }

    private class Sink : AudioSink {
        @Volatile var flushed = false
        override fun open(sampleRate: Int) = Unit
        override fun write(samples: ShortArray, offset: Int, length: Int): Int {
            Thread.sleep(2)
            return length
        }
        override fun playedFrames(nowNanos: Long): Long = 0
        override fun headFrames(): Long = 0
        override suspend fun drain(writtenFrames: Long, timeoutMillis: Long, stopped: () -> Boolean) = !stopped()
        override fun fadeOutAndFlush() { flushed = true }
        override fun close() = Unit
    }

    private class Engine(
        override val id: String,
        private val failFirst: Boolean = false,
        private val sentences: Int = 2,
        private val samples: Int = 1_600,
    ) : TtsEngine {
        @Volatile var interrupted = false
        @Volatile var finished = false
        override val capabilities = EngineCapabilities(EngineLocation.Offline)
        override suspend fun availability() = Availability.Ready
        override suspend fun voices() = emptyList<VoiceDescriptor>()
        override fun interrupt() { interrupted = true }
        override fun synthesize(input: TtsInput, settings: SynthesisSettings): Flow<PcmChunk> = flow {
            if (failFirst) throw IllegalStateException("model broken")
            repeat(sentences) { emit(PcmChunk(ShortArray(samples), 16_000, it.toLong() * samples)) }
            finished = true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val face = Face()
    private val sink = Sink()
    private val player = PcmPlayer({ sink }, io = Dispatchers.Default)

    private fun speaker(vararg engines: TtsEngine) =
        Speaker(TtsRouter(engines.toList(), RoutingPolicy(order = engines.map { it.id })) { Connectivity.Unmetered }, player, face, scope)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun speaksThenWindsDown() = runBlocking {
        val s = speaker(Engine("piper"))
        withTimeout(5_000) { s.say("Hello there. How are you?").join() }
        assertEquals(listOf("attach", "activity Speaking", "detach", "activity Idle"), face.calls)
        assertEquals(SpeakerState.Idle, s.state.value)
    }

    @Test
    fun failsOverWhenAnEngineBreaksBeforeAudio() = runBlocking {
        val system = Engine("system")
        val s = speaker(Engine("piper", failFirst = true), system)
        withTimeout(5_000) { s.say("Hi.").join() }
        assertTrue(system.finished)
        assertEquals("attach", face.calls.first())
    }

    @Test
    fun stopInterruptsAndDetaches() = runBlocking {
        val long = Engine("piper", sentences = 50, samples = 16_000)
        val s = speaker(long)
        val job = s.say("A very long story.")
        withTimeout(5_000) { while ("attach" !in face.calls) delay(5) }
        s.stop()
        withTimeout(5_000) { job.join() }
        assertTrue(long.interrupted)
        assertFalse(long.finished)
        assertTrue(sink.flushed)
        assertEquals("detach", face.calls[face.calls.size - 2])
        assertFalse(player.track.isActive)
    }

    @Test
    fun aNewUtteranceReplacesTheCurrentOne() = runBlocking {
        val s = speaker(Engine("piper", sentences = 50, samples = 16_000))
        s.say("First, a long one.")
        withTimeout(5_000) { while ("attach" !in face.calls) delay(5) }
        val second = s.say("Second.")
        withTimeout(10_000) { second.join() }
        assertEquals(2, face.calls.count { it == "attach" })
        assertEquals(2, face.calls.count { it == "detach" })
        assertEquals("activity Idle", face.calls.last())
    }

    @Test
    fun noEngineReportsWhy() = runBlocking {
        val s = speaker()
        withTimeout(5_000) { s.say("Anyone?").join() }
        assertTrue(s.state.value is SpeakerState.Failed)
        assertTrue(face.calls.isEmpty())
    }
}
