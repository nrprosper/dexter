package face.core

import face.api.Activity
import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceController
import face.api.FaceReaction
import face.api.FaceSnapshot
import face.api.PowerMode
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.anim.LayerStack
import face.core.anim.SecondOrderDynamics
import face.core.behavior.ActivityController
import face.core.behavior.ActivityLayer
import face.core.behavior.BaseLayer
import face.core.behavior.BlinkScheduler
import face.core.behavior.BoredomTracker
import face.core.behavior.BreathGenerator
import face.core.behavior.FaceEvent
import face.core.behavior.FaceEventListener
import face.core.behavior.GazeController
import face.core.behavior.GazeLayer
import face.core.behavior.IdleScheduler
import face.core.behavior.LifeConfig
import face.core.behavior.LifeContext
import face.core.behavior.LifeLayer
import face.core.behavior.ReactionLayer
import face.core.behavior.SpeechLayer
import face.core.behavior.SpeechTrackLayer
import face.core.behavior.Vigilance
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.inbox.FaceInbox
import face.core.inbox.SpeechCommand
import face.core.rig.Channel
import face.core.rig.ChannelGroup
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.core.time.FrameTicker
import face.core.time.Rng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Layers decide where the face wants to be (target); springs decide how it gets there (pose); blink runs last.
// API calls land in a thread-safe inbox that step() drains first, so the simulation stays single-threaded.
public class FaceEngine(
    private val library: ExpressionLibrary,
    rng: Rng,
    dynamics: DynamicsConfig = DynamicsConfig.Default,
    life: LifeConfig = LifeConfig.Default,
) : FaceController {
    private val inbox = FaceInbox()
    private val base = FacePose()
    private val target = FacePose()
    private val sleepPose = FacePose()
    private val springs = SecondOrderDynamics(Channel.count)
    private val ctx = LifeContext()
    private val blink = BlinkScheduler(life.blink, rng, ctx)
    private val gaze = GazeController(life.gaze, rng, ctx, blink)
    private val idle = IdleScheduler(life.idle, rng, ctx, gaze)
    private val boredom = BoredomTracker(life.boredom, ctx)
    private val breath = BreathGenerator(life.breath, ctx)
    private val wakeUp: () -> Unit = { wakeIfSleepy() }
    private val activities = ActivityController(life.activity, rng, ctx, gaze, blink, boredom, wakeUp)
    private val speech = SpeechTrackLayer()
    private val gazeLayer = GazeLayer(gaze, ctx)
    private val lifeLayer = LifeLayer(idle, boredom, breath, sleepPose, ctx)
    private val activityLayer = ActivityLayer(activities)
    private val stack = LayerStack(
        arrayOf(BaseLayer(base), activityLayer, gazeLayer, lifeLayer, SpeechLayer(speech), ReactionLayer()),
    )

    private var emotion: EmotionSignal = NEUTRAL
    private var powerMode = PowerMode.Full
    private var lastReaction: FaceReaction? = null
    private val state = MutableStateFlow(FaceSnapshot())

    override val snapshot: StateFlow<FaceSnapshot> = state

    public val pose: FacePose = FacePose()

    public var dynamics: DynamicsConfig = dynamics
        private set

    public var lifeEnabled: Boolean = true
        set(value) {
            field = value
            gazeLayer.enabled = value
            lifeLayer.enabled = value
            activityLayer.enabled = value
        }

    public var listener: FaceEventListener?
        get() = ctx.listener
        set(value) {
            ctx.listener = value
        }

    public val vigilance: Vigilance get() = ctx.vigilance

    public val time: Double get() = ctx.time

    init {
        applyDynamics()
        library.poseInto(sleepPose, ExpressionId.Sleeping)
        library.poseInto(base, ExpressionId.Neutral)
        target.copyFrom(base)
        snapToTarget()
    }

    override fun setActivity(activity: Activity): Unit = inbox.setActivity(activity)

    override fun setEmotion(signal: EmotionSignal): Unit = inbox.setEmotion(signal)

    override fun react(event: FaceReaction): Unit = inbox.react(event)

    override fun attachSpeech(track: SpeechTrack, style: SpeechStyle): Unit = inbox.attachSpeech(track, style)

    override fun detachSpeech(): Unit = inbox.detachSpeech()

    override fun setUserVoiceLevel(level: Float): Unit = inbox.setVoiceLevel(level)

    override fun setPowerMode(mode: PowerMode): Unit = inbox.setPowerMode(mode)

    // Any sign of the user (a tap): resets boredom, and wakes the face if it was sleepy. Thread-safe.
    public fun interact() {
        inbox.poke()
    }

    // Debug tools only, called on the frame thread: show an expression or a raw pose directly.
    public fun setExpression(id: ExpressionId, weight: Float = 1f) {
        library.poseInto(base, id, weight)
        if (id == ExpressionId.Surprised) blink.suppress(SURPRISE_NO_BLINK) else blink.trigger()
    }

    public fun setTarget(source: FacePose) {
        base.copyFrom(source)
        Constraints.apply(base)
    }

    public fun copyTargetInto(out: FacePose) {
        out.copyFrom(base)
    }

    public fun dozeOff() {
        boredom.dozeOff()
    }

    public fun step(dt: Float) {
        val clamped = dt.coerceIn(0f, FrameTicker.MAX_DT)
        ctx.time += clamped
        speech.nowNanos = (ctx.time * NANOS_PER_SECOND).toLong()
        drainInbox()
        if (lifeEnabled) {
            activities.update(clamped, inbox.voiceLevel())
            boredom.update(clamped)
            blink.update(clamped)
            gaze.update(clamped)
            idle.update(clamped)
            breath.update(clamped)
        }
        stack.apply(target)
        Constraints.apply(target)
        springs.step(target.values, clamped)
        springs.y.copyInto(pose.values)
        if (lifeEnabled) blink.apply(pose)
        Constraints.apply(pose)
        publishIfChanged()
    }

    public fun onResume() {
        springs.stopMotion()
    }

    public fun snapToTarget() {
        springs.reset(target.values)
        pose.copyFrom(target)
    }

    public fun setProfile(group: ChannelGroup, profile: DynamicsProfile) {
        dynamics = dynamics.with(group, profile)
        applyDynamics()
    }

    internal val blinkStats: BlinkScheduler get() = blink

    private fun drainInbox() {
        inbox.takeActivity()?.let { activities.set(it) }
        inbox.takeEmotion()?.let { if (it != emotion) applyEmotion(it) }
        inbox.takeSpeech()?.let {
            when (it) {
                is SpeechCommand.Attach -> speech.attach(it.track, it.style)
                SpeechCommand.Detach -> speech.detach()
            }
        }
        inbox.takePowerMode()?.let { powerMode = it }
        if (inbox.takePoke() && activities.current != Activity.Sleeping) wakeIfSleepy()
        while (true) {
            val reaction = inbox.pollReaction() ?: break
            lastReaction = reaction
            ctx.emit(FaceEvent.Reaction, reactionName(reaction))
        }
    }

    // Phase 4 interim: an emotion shows as its expression scaled by intensity and confidence. Phase 5's Director replaces this.
    private fun applyEmotion(signal: EmotionSignal) {
        emotion = signal
        library.poseInto(base, expressionFor(signal.emotion), signal.intensity * signal.confidence)
        blink.trigger()
        ctx.emit(FaceEvent.Emotion, EMOTION_NAMES[signal.emotion.ordinal])
    }

    private fun wakeIfSleepy() {
        if (boredom.interact()) {
            idle.cancel()
            gaze.lookAtUser()
            blink.doubleBlink(WAKE)
        }
    }

    private fun publishIfChanged() {
        val current = state.value
        val asleep = ctx.vigilance == Vigilance.Asleep
        if (current.activity == activities.current && current.emotion == emotion.emotion &&
            current.intensity == emotion.intensity && current.speaking == speech.speaking &&
            current.asleep == asleep && current.powerMode == powerMode && current.lastReaction === lastReaction
        ) {
            return
        }
        state.value = FaceSnapshot(
            activity = activities.current,
            emotion = emotion.emotion,
            intensity = emotion.intensity,
            speaking = speech.speaking,
            asleep = asleep,
            powerMode = powerMode,
            lastReaction = lastReaction,
        )
    }

    private fun applyDynamics() {
        for (channel in Channel.entries) springs.configure(channel.ordinal, dynamics[channel.group])
    }

    private companion object {
        const val SURPRISE_NO_BLINK = 1.5f
        const val WAKE = "wake"
        const val NANOS_PER_SECOND = 1e9
        val NEUTRAL = EmotionSignal(Emotion.Neutral, intensity = 0f)
        val EMOTION_NAMES = Emotion.entries.map { it.name }.toTypedArray()

        fun expressionFor(emotion: Emotion): ExpressionId = when (emotion) {
            Emotion.Neutral -> ExpressionId.Neutral
            Emotion.Happy -> ExpressionId.Happy
            Emotion.Curious -> ExpressionId.Curious
            Emotion.Confused -> ExpressionId.Confused
            Emotion.Sad -> ExpressionId.Sad
            Emotion.Concerned -> ExpressionId.Concerned
            Emotion.Playful -> ExpressionId.Playful
        }

        fun reactionName(reaction: FaceReaction): String = when (reaction) {
            is FaceReaction.Surprise -> "Surprise"
            FaceReaction.Delight -> "Delight"
            FaceReaction.Startle -> "Startle"
            FaceReaction.Acknowledge -> "Acknowledge"
            FaceReaction.Oops -> "Oops"
            FaceReaction.Laugh -> "Laugh"
            FaceReaction.Realization -> "Realization"
        }
    }
}
