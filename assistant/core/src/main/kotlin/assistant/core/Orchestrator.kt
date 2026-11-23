package assistant.core

import face.api.Activity
import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.SpeechStyle
import face.api.StageMove
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

public enum class AssistantState { Idle, Listening, Thinking, Speaking }

// One conversation turn at a time: listen → think → speak → idle. It only talks to the face through FaceController
// (intent: activity, emotion, reactions), never geometry. The brain's header reaches the face before any audio plays.
// Double-tap starts a turn, or interrupts the current one and listens again.
public class Orchestrator(
    private val face: FaceController,
    private val ears: Ears,
    private val brain: Brain,
    private val mouth: Mouth,
    private val scope: CoroutineScope,
    private val config: AssistantConfig = AssistantConfig(),
    private val clock: () -> Long,
    // A short line about the moment ("Sunday evening, 7:40 pm"), sent with each question; not part of the history.
    private val context: () -> String = { "" },
) {
    private val conversation = Conversation()
    private val mutableState = MutableStateFlow(AssistantState.Idle)
    private val mutableFeeling = MutableStateFlow<Feeling?>(null)
    private val mutableHeard = MutableStateFlow("")
    private val mutableLevel = MutableStateFlow(0f)
    private var job: Job? = null

    public val state: StateFlow<AssistantState> = mutableState
    public val lastFeeling: StateFlow<Feeling?> = mutableFeeling
    public val lastHeard: StateFlow<String> = mutableHeard

    // The user's voice level 0..1 while listening, for UI (the face gets it too).
    public val voiceLevel: StateFlow<Float> = mutableLevel

    public fun onDoubleTap() {
        start { listenThenAnswer() }
    }

    // Typed input (debug tools): skips listening.
    public fun ask(text: String) {
        start { answer(text) }
    }

    public fun stop() {
        job?.cancel()
        mouth.stop()
    }

    public fun forget() {
        conversation.clear()
    }

    private fun start(turn: suspend () -> Unit) {
        val previous = job
        val interrupting = previous?.isActive == true
        previous?.cancel()
        if (interrupting) mouth.stop()
        job = scope.launch {
            previous?.join()
            face.react(FaceReaction.Acknowledge)
            try {
                turn()
            } finally {
                face.setUserVoiceLevel(0f)
                mutableLevel.value = 0f
                if (job == coroutineContext[Job]) {
                    mutableState.value = AssistantState.Idle
                    face.setActivity(Activity.Idle)
                }
            }
        }
    }

    private suspend fun listenThenAnswer() {
        mutableHeard.value = ""
        mutableState.value = AssistantState.Listening
        face.setActivity(Activity.Listening)
        val heard = try {
            withTimeout(config.listenTimeoutMillis) { hear() }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: HearingError) {
            fail(e.failure)
            return
        }
        if (heard.isNullOrBlank()) {
            face.setEmotion(EmotionSignal(Emotion.Confused, NOTHING_HEARD_INTENSITY, NOTHING_HEARD_CONFIDENCE))
            return
        }
        answer(heard)
    }

    private suspend fun hear(): String? {
        var heard: String? = null
        ears.listen().collect { event ->
            when (event) {
                is HearingEvent.Level -> {
                    face.setUserVoiceLevel(event.level)
                    mutableLevel.value = event.level
                }
                is HearingEvent.Partial -> mutableHeard.value = event.text
                is HearingEvent.Heard -> heard = event.text
                HearingEvent.NothingHeard -> heard = null
                is HearingEvent.Failed -> throw HearingError(event.failure)
                HearingEvent.Ready -> Unit
            }
        }
        heard?.let { mutableHeard.value = it }
        return heard
    }

    private suspend fun answer(text: String) {
        mutableHeard.value = text
        mutableState.value = AssistantState.Thinking
        face.setUserVoiceLevel(0f)
        mutableLevel.value = 0f
        face.setActivity(Activity.Thinking)
        val sentences = Channel<ReplySentence>(Channel.UNLIMITED)
        val header = CompletableDeferred<Feeling>()
        val said = StringBuilder()
        val emotions = HashMap<Int, Emotion>()
        val moves = HashMap<Int, StageMove>()
        var declined = false
        try {
            coroutineScope {
                val reading = launch {
                    try {
                        val moment = context().trim()
                        val asked = if (moment.isEmpty()) text else "[$moment]\n$text"
                        brain.reply(conversation.history(clock()), asked).collect { event ->
                            when (event) {
                                is BrainEvent.Header -> {
                                    face.setEmotion(event.feeling.signal())
                                    mutableFeeling.value = event.feeling
                                    header.complete(event.feeling)
                                }
                                is BrainEvent.Sentence -> {
                                    if (!header.isCompleted) header.complete(Feeling())
                                    event.sentence.emotion?.let { emotions[event.sentence.index] = it }
                                    event.sentence.move?.let { moves[event.sentence.index] = it }
                                    if (said.isNotEmpty()) said.append(' ')
                                    said.append(event.sentence.text)
                                    sentences.send(event.sentence)
                                }
                                is BrainEvent.Declined -> declined = true
                                BrainEvent.Done -> Unit
                            }
                        }
                        sentences.close()
                    } catch (e: Throwable) {
                        sentences.close(e)
                        header.completeExceptionally(e)
                        throw e
                    }
                    if (!header.isCompleted) header.complete(Feeling())
                }
                val feeling = withTimeoutOrNull(config.headerTimeoutMillis) { header.await() }
                if (feeling == null) {
                    reading.cancel()
                    throw SlowReply()
                }
                mutableState.value = AssistantState.Speaking
                face.setActivity(Activity.Speaking)
                mouth.speak(sentences.consumeAsFlow(), feeling.style()) { index ->
                    emotions[index]?.let { face.setEmotion(EmotionSignal(it, feeling.intensity, feeling.confidence)) }
                    moves[index]?.let { face.stage(it) }
                }
            }
            if (said.isNotEmpty()) conversation.add(Turn(text, said.toString()), clock())
            if (declined) {
                face.setEmotion(EmotionSignal(Emotion.Concerned, PROBLEM_INTENSITY))
                if (said.isEmpty()) say(config.lines.declined, SpeechStyle.Calm)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlowReply) {
            problem(config.lines.slow)
        } catch (e: BrainException) {
            fail(e.failure)
        }
    }

    private suspend fun fail(failure: HearingFailure) {
        problem(if (failure == HearingFailure.NoPermission) config.lines.noMic else config.lines.broken)
    }

    private suspend fun fail(failure: BrainFailure) {
        problem(
            when (failure) {
                BrainFailure.NoKey -> config.lines.noKey
                BrainFailure.Auth -> config.lines.badKey
                BrainFailure.Network -> config.lines.offline
                BrainFailure.RateLimit -> config.lines.busy
                BrainFailure.Server, BrainFailure.BadReply -> config.lines.broken
            },
        )
    }

    private suspend fun problem(line: String) {
        face.react(FaceReaction.Oops)
        face.setEmotion(EmotionSignal(Emotion.Concerned, PROBLEM_INTENSITY))
        say(line, SpeechStyle.Calm)
    }

    private suspend fun say(line: String, style: SpeechStyle) {
        mutableState.value = AssistantState.Speaking
        face.setActivity(Activity.Speaking)
        mouth.speak(flowOf(ReplySentence(0, line)), style) {}
    }

    private class HearingError(val failure: HearingFailure) : Exception("hearing failed: $failure")

    private class SlowReply : Exception("no reply header in time")

    private companion object {
        const val NOTHING_HEARD_INTENSITY = 0.4f
        const val NOTHING_HEARD_CONFIDENCE = 0.6f
        const val PROBLEM_INTENSITY = 0.6f
    }
}
