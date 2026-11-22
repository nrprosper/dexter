package face.core.inbox

import face.api.Activity
import face.api.EmotionSignal
import face.api.FaceReaction
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// Any thread writes, the frame thread drains at the start of each step. State is latest-wins; reactions keep their order.
internal class FaceInbox {
    private val activity = AtomicReference<Activity?>(null)
    private val emotion = AtomicReference<EmotionSignal?>(null)
    private val speech = AtomicReference<SpeechCommand?>(null)
    private val power = AtomicReference<PowerMode?>(null)
    private val voiceBits = AtomicInteger(0f.toRawBits())
    private val poked = AtomicBoolean(false)
    private val reactions = ConcurrentLinkedQueue<FaceReaction>()

    fun setActivity(value: Activity) = activity.set(value)

    fun setEmotion(value: EmotionSignal) = emotion.set(value)

    fun attachSpeech(track: SpeechTrack, style: SpeechStyle) = speech.set(SpeechCommand.Attach(track, style))

    fun detachSpeech() = speech.set(SpeechCommand.Detach)

    fun setPowerMode(value: PowerMode) = power.set(value)

    fun setVoiceLevel(level: Float) = voiceBits.set(level.coerceIn(0f, 1f).toRawBits())

    fun poke() = poked.set(true)

    fun react(event: FaceReaction) {
        reactions.add(event)
    }

    fun takeActivity(): Activity? = activity.getAndSet(null)

    fun takeEmotion(): EmotionSignal? = emotion.getAndSet(null)

    fun takeSpeech(): SpeechCommand? = speech.getAndSet(null)

    fun takePowerMode(): PowerMode? = power.getAndSet(null)

    fun takePoke(): Boolean = poked.getAndSet(false)

    fun voiceLevel(): Float = Float.fromBits(voiceBits.get())

    fun pollReaction(): FaceReaction? = reactions.poll()
}

internal sealed interface SpeechCommand {
    class Attach(val track: SpeechTrack, val style: SpeechStyle) : SpeechCommand

    data object Detach : SpeechCommand
}
