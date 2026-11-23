package face.core.speech

import face.api.Emotion
import face.api.MouthFrame
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.anim.Layer
import face.core.behavior.LifeContext
import face.core.mood.EmotionState
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.exp
import kotlin.math.min

// Live speech values for debug tools; written on the frame thread once per step.
public class SpeechProbe {
    public var level: Float = 0f
        internal set
    public var envelope: Float = 0f
        internal set
    public var open: Float = 0f
        internal set
    public var brightness: Float = 0.5f
        internal set
    public var emphasis: Float = 0f
        internal set
    public var beat: Float = 0f
        internal set
    public var attached: Boolean = false
        internal set
}

// Samples the attached SpeechTrack at the frame time, shapes it with MouthMapper and adds mouth, head and beat deltas.
// After detach the mapper keeps running on silence, so the mouth winds down through the same follower and springs.
internal class SpeechTrackLayer(
    rng: Rng,
    ctx: LifeContext,
    private val emotion: EmotionState,
    config: MouthConfig,
    private val onLaughCue: () -> Unit,
) {
    private val frame = MouthFrame()
    private val scale = StyleScale()
    private val mapper = MouthMapper(config)
    private val beats = BeatGestures(rng, ctx)
    private var track: SpeechTrack? = null
    private var heardSound = false
    private var engagement = 0f
    private var emphasis = 0f
    val probe = SpeechProbe()
    var nowNanos = 0L

    var config: MouthConfig = config
        set(value) {
            field = value
            mapper.config = value
            beats.config = value.beat
        }

    init {
        beats.config = config.beat
        mapper.reset()
    }

    val speaking: Boolean get() = track != null
    val energy: Float get() = scale.energy
    val engaged: Boolean get() = track != null || mapper.envelope > ENGAGED_LEVEL || beats.value != 0f
    val springBoost: Float get() = if (engaged) scale.springBoost else 1f
    val quirksAllowed: Boolean get() = track == null || scale.quirksAllowed

    fun attach(track: SpeechTrack, style: SpeechStyle) {
        if (this.track != null) finish()
        this.track = track
        scale.set(style, config)
        heardSound = false
    }

    fun detach() {
        if (track == null) return
        finish()
        track = null
    }

    fun update(dt: Float) {
        val t = track
        var level = 0f
        var bright = HALF
        emphasis = 0f
        if (t != null && t.isActive) {
            frame.clear()
            t.sample(nowNanos, frame)
            level = frame.openness
            bright = frame.width
            emphasis = frame.emphasis
        }
        val amounts = emotion.amounts
        val worry = min(1f, amounts[Emotion.Sad.ordinal] + amounts[Emotion.Concerned.ordinal])
        val restraint = 1f - config.emotionRestraint * worry
        mapper.step(dt, level, bright, scale.gain * restraint)
        if (mapper.isOpen) heardSound = true
        beats.update(dt, emphasis, mapper.isOpen, if (t != null) scale.beatRate * restraint else 0f)
        val want = if (t != null) 1f else 0f
        engagement += (want - engagement) * (1f - exp(-dt / ENGAGE_TAU))
        probe.level = level
        probe.envelope = mapper.envelope
        probe.open = mapper.open
        probe.brightness = mapper.brightness
        probe.emphasis = emphasis
        probe.beat = beats.value
        probe.attached = t != null
    }

    fun apply(pose: FacePose) {
        val c = config
        val open = mapper.open
        val shape = min(1f, open * SHAPE_GAIN)
        pose[Channel.MouthOpen] = pose[Channel.MouthOpen] + open * c.maxOpen
        pose[Channel.MouthWidth] = pose[Channel.MouthWidth] + (mapper.brightness - HALF) * 2f * c.widthGain * shape
        pose[Channel.MouthRoundness] = pose[Channel.MouthRoundness] + (1f - mapper.brightness) * c.roundGain * shape
        pose[Channel.HeadY] = pose[Channel.HeadY] + open * c.headBob * scale.headBounce
        pose[Channel.MouthAsymmetry] = pose[Channel.MouthAsymmetry] + scale.smirk * engagement
        beats.apply(pose, scale.headBounce)
    }

    private fun finish() {
        if (heardSound && scale.laughAtEnd) onLaughCue()
    }

    private companion object {
        const val HALF = 0.5f
        const val SHAPE_GAIN = 2f
        const val ENGAGED_LEVEL = 0.01f
        const val ENGAGE_TAU = 0.25f
    }
}

internal class SpeechLayer(private val speech: SpeechTrackLayer) : Layer() {
    override fun apply(target: FacePose) {
        speech.apply(target)
    }
}
