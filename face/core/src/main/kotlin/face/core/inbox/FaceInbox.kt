package face.core.inbox

import face.api.Activity
import face.api.DeviceState
import face.api.EmotionSignal
import face.api.FaceReaction
import face.api.Personality
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.senses.FaceTouch
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
    private val personality = AtomicReference<Personality?>(null)
    private val device = AtomicReference<DeviceState?>(null)
    private val volumeBits = AtomicInteger(0)
    private val volumeShown = AtomicBoolean(false)
    private val tiltXBits = AtomicInteger(0f.toRawBits())
    private val tiltYBits = AtomicInteger(0f.toRawBits())
    private val touches = ConcurrentLinkedQueue<FaceTouch>()
    private val voiceBits = AtomicInteger(0f.toRawBits())
    private val poked = AtomicBoolean(false)
    private val reactions = ConcurrentLinkedQueue<FaceReaction>()

    fun setActivity(value: Activity) = activity.set(value)

    fun setEmotion(value: EmotionSignal) = emotion.set(value)

    fun attachSpeech(track: SpeechTrack, style: SpeechStyle) = speech.set(SpeechCommand.Attach(track, style))

    fun detachSpeech() = speech.set(SpeechCommand.Detach)

    fun setPowerMode(value: PowerMode) = power.set(value)

    fun setPersonality(value: Personality) = personality.set(value)

    fun setDeviceState(value: DeviceState) = device.set(value)

    fun showVolume(level: Float) {
        volumeBits.set(level.coerceIn(0f, 1f).toRawBits())
        volumeShown.set(true)
    }

    fun setTilt(x: Float, y: Float) {
        tiltXBits.set(x.coerceIn(-1f, 1f).toRawBits())
        tiltYBits.set(y.coerceIn(-1f, 1f).toRawBits())
    }

    fun tiltX(): Float = Float.fromBits(tiltXBits.get())

    fun tiltY(): Float = Float.fromBits(tiltYBits.get())

    fun touch(value: FaceTouch) {
        touches.add(value)
    }

    fun setVoiceLevel(level: Float) = voiceBits.set(level.coerceIn(0f, 1f).toRawBits())

    fun poke() = poked.set(true)

    fun react(event: FaceReaction) {
        reactions.add(event)
    }

    fun takeActivity(): Activity? = activity.getAndSet(null)

    fun takeEmotion(): EmotionSignal? = emotion.getAndSet(null)

    fun takeSpeech(): SpeechCommand? = speech.getAndSet(null)

    fun takePowerMode(): PowerMode? = power.getAndSet(null)

    fun takePersonality(): Personality? = personality.getAndSet(null)

    fun takeDeviceState(): DeviceState? = device.getAndSet(null)

    fun takeVolume(): Float = if (volumeShown.getAndSet(false)) Float.fromBits(volumeBits.get()) else -1f

    fun pollTouch(): FaceTouch? = touches.poll()

    fun takePoke(): Boolean = poked.getAndSet(false)

    fun voiceLevel(): Float = Float.fromBits(voiceBits.get())

    fun pollReaction(): FaceReaction? = reactions.poll()
}

internal sealed interface SpeechCommand {
    class Attach(val track: SpeechTrack, val style: SpeechStyle) : SpeechCommand

    data object Detach : SpeechCommand
}
