package face.core

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
import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.anim.LayerStack
import face.core.anim.SecondOrderDynamics
import face.core.behavior.ActivityController
import face.core.behavior.ActivityLayer
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
import face.core.behavior.Quirks
import face.core.behavior.ReactionLayer
import face.core.behavior.Vigilance
import face.core.director.Director
import face.core.director.DirectorHooks
import face.core.director.ReactionRouter
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.inbox.FaceInbox
import face.core.inbox.SpeechCommand
import face.core.mood.EmotionLayer
import face.core.mood.EmotionState
import face.core.mood.MoodModel
import face.core.personality.PersonalityMapper
import face.core.rig.Channel
import face.core.senses.DeviceResponder
import face.core.senses.FaceTouch
import face.core.senses.SensesLayer
import face.core.senses.TiltParallax
import face.core.senses.TouchResponder
import face.core.senses.VolumeWatch
import face.core.speech.MouthConfig
import face.core.speech.SpeechLayer
import face.core.speech.SpeechProbe
import face.core.speech.SpeechTrackLayer
import face.core.stage.Stage
import face.core.stage.StageConfig
import face.core.stage.StageLayer
import face.core.rig.ChannelGroup
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.core.time.FrameTicker
import face.core.time.Rng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

// Layers decide where the face wants to be (target); springs decide how it gets there (pose); blink runs last.
// API calls land in a thread-safe inbox that step() drains first, so the simulation stays single-threaded.
// Explicit dynamics/life configs (tests, tools) win over the ones derived from the personality at construction.
public class FaceEngine(
    private val library: ExpressionLibrary,
    rng: Rng,
    dynamics: DynamicsConfig? = null,
    life: LifeConfig? = null,
    personality: Personality = Personality(),
    mouth: MouthConfig = MouthConfig.loadBundled(),
) : FaceController {
    private val inbox = FaceInbox()
    private val neutral = FacePose()
    private val manual = FacePose()
    private val target = FacePose()
    private val sleepPose = FacePose()
    private val springs = SecondOrderDynamics(Channel.count)
    private val ctx = LifeContext()
    private val startLife = life ?: PersonalityMapper.life(personality)
    private val blink = BlinkScheduler(startLife.blink, rng, ctx)
    private val gaze = GazeController(startLife.gaze, rng, ctx, blink)
    private val idle = IdleScheduler(startLife.idle, rng, ctx, gaze)
    private val boredom = BoredomTracker(startLife.boredom, ctx)
    private val breath = BreathGenerator(startLife.breath, ctx)
    private val wakeUp: () -> Unit = { wakeIfSleepy() }
    private val activities = ActivityController(startLife.activity, rng, ctx, gaze, blink, boredom, wakeUp)
    private val emotionState = EmotionState()
    private val laughCue: () -> Unit = { reactions.react(FaceReaction.Laugh) }
    private val speech = SpeechTrackLayer(rng, ctx, emotionState, mouth, laughCue)
    private val mood = MoodModel()
    private val quirks = Quirks(ctx, blink)
    private val hooks = object : DirectorHooks {
        override fun onAccepted(emotion: Emotion, strength: Float, confidence: Float, previousAmount: Float) {
            quirks.onEmotion(emotion, strength, confidence, previousAmount)
        }

        override fun playSurpriseBeat() {
            reactions.react(SURPRISE_BEAT)
        }
    }
    private val director: Director = Director(ctx, emotionState, mood, hooks)
    private val reactions: ReactionRouter = ReactionRouter(ctx, blink, director)
    private val device = DeviceResponder(rng, boredom)
    private val volume = VolumeWatch()
    private val touch = TouchResponder(ctx, blink, reactions, director)
    private val tilt = TiltParallax()
    private val stage = Stage(rng, ctx).apply { config = StageConfig.loadBundled() }
    private val emotionLayer = EmotionLayer(library, neutral, emotionState, manual)
    private val gazeLayer = GazeLayer(gaze, ctx)
    private val lifeLayer = LifeLayer(idle, boredom, breath, sleepPose, ctx)
    private val activityLayer = ActivityLayer(activities)
    private val stack = LayerStack(
        arrayOf(
            emotionLayer, activityLayer, gazeLayer, lifeLayer,
            StageLayer(stage), SpeechLayer(speech), SensesLayer(device, volume, touch, tilt), ReactionLayer(reactions, quirks, neutral),
        ),
    )

    private var lastSignal: EmotionSignal? = null
    private var powerMode = PowerMode.Full
    private var lastReaction: FaceReaction? = null
    private var mixMode = false
    private val mix = FloatArray(Emotion.entries.size)
    private var mixArousal = 0.5f
    private var appliedSpeed = -1f
    private var appliedBoost = 1f
    private val state = MutableStateFlow(FaceSnapshot())

    override val snapshot: StateFlow<FaceSnapshot> = state

    public val pose: FacePose = FacePose()

    public var personality: Personality = personality
        private set

    public var dynamics: DynamicsConfig = dynamics ?: PersonalityMapper.dynamics(personality)
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

    public val speechProbe: SpeechProbe get() = speech.probe

    public var mouthConfig: MouthConfig
        get() = speech.config
        set(value) {
            speech.config = value
        }

    init {
        library.poseInto(neutral, ExpressionId.Neutral)
        library.poseInto(sleepPose, ExpressionId.Sleeping)
        manual.copyFrom(neutral)
        applyPersonality(personality, initial = true)
        director.startAtMood()
        applyDynamics(speedFor(emotionState.arousal))
        stack.apply(target)
        Constraints.apply(target)
        snapToTarget()
    }

    override fun setActivity(activity: Activity): Unit = inbox.setActivity(activity)

    override fun setEmotion(signal: EmotionSignal): Unit = inbox.setEmotion(signal)

    override fun react(event: FaceReaction): Unit = inbox.react(event)

    override fun stage(move: StageMove): Unit = inbox.stage(move)

    override fun attachSpeech(track: SpeechTrack, style: SpeechStyle): Unit = inbox.attachSpeech(track, style)

    override fun detachSpeech(): Unit = inbox.detachSpeech()

    override fun setUserVoiceLevel(level: Float): Unit = inbox.setVoiceLevel(level)

    override fun setPowerMode(mode: PowerMode): Unit = inbox.setPowerMode(mode)

    override fun setPersonality(personality: Personality): Unit = inbox.setPersonality(personality)

    override fun setDeviceState(state: DeviceState): Unit = inbox.setDeviceState(state)

    override fun showVolume(level: Float): Unit = inbox.showVolume(level)

    override fun setTilt(x: Float, y: Float): Unit = inbox.setTilt(x, y)

    // The UI reports the canvas size so screen-aware behaviours (the volume visit) pick the right side.
    public fun setViewport(width: Float, height: Float) {
        volume.portrait = height > width
    }

    // A touch hit-tested by the renderer; any touch also counts as interaction. Thread-safe.
    public fun touch(event: FaceTouch) {
        inbox.touch(event)
    }

    // Any sign of the user (a tap): resets boredom, and wakes the face if it was sleepy. Thread-safe.
    public fun interact() {
        inbox.poke()
    }

    // Debug tools only, called on the frame thread: show an expression or a raw pose directly (bypasses the Director).
    public fun setExpression(id: ExpressionId, weight: Float = 1f) {
        library.poseInto(manual, id, weight)
        emotionLayer.manualMode = true
        if (id == ExpressionId.Surprised) blink.suppress(SURPRISE_NO_BLINK) else blink.trigger()
    }

    public fun setTarget(source: FacePose) {
        manual.copyFrom(source)
        Constraints.apply(manual)
        emotionLayer.manualMode = true
    }

    public fun copyTargetInto(out: FacePose) {
        out.copyFrom(manual)
    }

    // Debug emotion mixer: shows these amounts directly instead of the Director's.
    public fun setEmotionMix(amounts: FloatArray, arousal: Float) {
        amounts.copyInto(mix)
        mixArousal = arousal
        mixMode = true
        emotionLayer.manualMode = false
    }

    public fun useDirector() {
        mixMode = false
        emotionLayer.manualMode = false
    }

    public fun dozeOff() {
        boredom.dozeOff()
    }

    // frameNanos is the frame's System.nanoTime-based timestamp, which audio playback clocks share;
    // without one (tests, tools) speech is sampled at simulated time.
    public fun step(dt: Float, frameNanos: Long = NO_FRAME_TIME) {
        val clamped = dt.coerceIn(0f, FrameTicker.MAX_DT)
        ctx.time += clamped
        speech.nowNanos = if (frameNanos != NO_FRAME_TIME) frameNanos else (ctx.time * NANOS_PER_SECOND).toLong()
        drainInbox()
        director.speechEnergy = if (speech.speaking) speech.energy else -1f
        if (mixMode) {
            mix.copyInto(emotionState.amounts)
            emotionState.arousal = mixArousal
        } else {
            director.update(clamped)
        }
        val saver = device.state.batterySaver
        val speed = speedFor(emotionState.arousal) * (if (saver) SAVER_SPEED else 1f)
        if (abs(speed - appliedSpeed) > SPEED_EPSILON || abs(speech.springBoost - appliedBoost) > SPEED_EPSILON) applyDynamics(speed)
        idle.rateScale = (IDLE_RATE_BASE + IDLE_RATE_GAIN * emotionState.arousal) * (if (saver) SAVER_IDLE else 1f)
        tilt.x = inbox.tiltX()
        tilt.y = inbox.tiltY()
        reactions.update(clamped)
        stage.update(clamped, activities.current, volume.weight)
        quirks.playfulAllowed = speech.quirksAllowed
        quirks.update(clamped)
        speech.update(clamped)
        device.update(clamped)
        volume.update(clamped)
        touch.update(clamped)
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
        Constraints.apply(pose)
        // After the lid constraint, so a blink never drags the lower lid along at blink speed.
        if (lifeEnabled) blink.apply(pose)
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
        applyDynamics(speedFor(emotionState.arousal))
    }

    internal val blinkStats: BlinkScheduler get() = blink

    internal val emotionAmounts: FloatArray get() = emotionState.amounts

    internal val reactionPlaying: Int get() = reactions.playing

    private fun drainInbox() {
        inbox.takePersonality()?.let { applyPersonality(it, initial = false) }
        inbox.takeActivity()?.let { activities.set(it) }
        inbox.takeEmotion()?.let { applyEmotion(it) }
        inbox.takeSpeech()?.let {
            when (it) {
                is SpeechCommand.Attach -> speech.attach(it.track, it.style)
                SpeechCommand.Detach -> speech.detach()
            }
        }
        inbox.takePowerMode()?.let { powerMode = it }
        inbox.takeDeviceState()?.let { if (device.set(it)) wakeIfSleepy() }
        val level = inbox.takeVolume()
        if (level >= 0f) {
            volume.show(level)
            if (activities.current != Activity.Sleeping) wakeIfSleepy()
        }
        while (true) {
            val event = inbox.pollTouch() ?: break
            if (activities.current == Activity.Sleeping || device.state.faceDown) continue
            val sleepy = ctx.vigilance == Vigilance.Asleep || ctx.vigilance == Vigilance.Drowsy
            wakeIfSleepy()
            boredom.keepAwake()
            if (!sleepy) touch.handle(event)
        }
        if (inbox.takePoke() && activities.current != Activity.Sleeping) wakeIfSleepy()
        while (true) {
            val move = inbox.pollMove() ?: break
            if (activities.current != Activity.Sleeping) stage.move(move)
        }
        while (true) {
            val reaction = inbox.pollReaction() ?: break
            lastReaction = reaction
            reactions.react(reaction)
        }
    }

    private fun applyEmotion(signal: EmotionSignal) {
        mixMode = false
        emotionLayer.manualMode = false
        if (signal == lastSignal) return
        lastSignal = signal
        director.signal(signal.emotion, signal.intensity, signal.confidence)
    }

    private fun applyPersonality(p: Personality, initial: Boolean) {
        personality = p
        mood.configure(p.mood.baseline, p.mood.arousal, p.mood.driftHalfLife, reset = initial)
        director.config = p.director
        emotionLayer.configure(p)
        quirks.configure(p.quirks)
        reactions.playfulGain = p.expressiveness * (PLAYFUL_BASE + PLAYFUL_GAIN * p.playfulness)
        stage.amplitude = p.expressiveness.coerceIn(0f, STAGE_AMPLITUDE_MAX)
        if (!initial) {
            val life = PersonalityMapper.life(p)
            blink.config = life.blink
            gaze.config = life.gaze
            idle.configure(life.idle)
            boredom.config = life.boredom
            breath.config = life.breath
            dynamics = PersonalityMapper.dynamics(p)
            applyDynamics(speedFor(emotionState.arousal))
            ctx.emit(FaceEvent.Personality, p.id)
        }
    }

    private fun wakeIfSleepy() {
        if (boredom.interact()) {
            idle.cancel()
            gaze.lookAtUser()
            blink.doubleBlink(WAKE)
            quirks.onWake()
        }
    }

    private fun speedFor(arousal: Float): Float = SPEED_BASE + SPEED_GAIN * arousal

    private fun applyDynamics(speed: Float) {
        appliedSpeed = speed
        appliedBoost = speech.springBoost
        val all = Channel.all
        for (i in all.indices) {
            val p = dynamics[all[i].group]
            val boost = if (SPEECH_CHANNELS[i]) appliedBoost else 1f
            springs.configure(i, p.frequency * speed * boost, p.damping, p.response)
        }
    }

    private fun publishIfChanged() {
        val current = state.value
        val asleep = ctx.vigilance == Vigilance.Asleep
        val emotion = director.currentEmotion
        val intensity = director.currentStrength
        val moodNow = mood.dominant()
        val arousal = (emotionState.arousal * AROUSAL_STEPS).roundToInt() / AROUSAL_STEPS
        if (current.activity == activities.current && current.emotion == emotion && current.intensity == intensity &&
            current.speaking == speech.speaking && current.asleep == asleep && current.powerMode == powerMode &&
            current.lastReaction === lastReaction && current.mood == moodNow && current.arousal == arousal &&
            current.personality == personality.id
        ) {
            return
        }
        state.value = FaceSnapshot(
            activity = activities.current,
            emotion = emotion,
            intensity = intensity,
            speaking = speech.speaking,
            asleep = asleep,
            powerMode = powerMode,
            lastReaction = lastReaction,
            mood = moodNow,
            arousal = arousal,
            personality = personality.id,
        )
    }

    private companion object {
        const val SURPRISE_NO_BLINK = 1.5f
        const val WAKE = "wake"
        const val NANOS_PER_SECOND = 1e9
        const val NO_FRAME_TIME = Long.MIN_VALUE
        const val STAGE_AMPLITUDE_MAX = 1.3f
        val SPEECH_CHANNELS: BooleanArray = BooleanArray(Channel.count) {
            Channel.all[it] == Channel.MouthOpen || Channel.all[it] == Channel.MouthWidth || Channel.all[it] == Channel.MouthRoundness
        }
        const val SPEED_BASE = 0.85f
        const val SPEED_GAIN = 0.3f
        const val SPEED_EPSILON = 0.01f
        const val IDLE_RATE_BASE = 0.7f
        const val IDLE_RATE_GAIN = 0.6f
        const val AROUSAL_STEPS = 10f
        const val SAVER_SPEED = 0.85f
        const val SAVER_IDLE = 0.5f
        const val PLAYFUL_BASE = 0.7f
        const val PLAYFUL_GAIN = 0.6f
        val SURPRISE_BEAT: FaceReaction = FaceReaction.Surprise()
    }
}
