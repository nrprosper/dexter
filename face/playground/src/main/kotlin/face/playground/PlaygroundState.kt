package face.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import face.core.FaceEngine
import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.behavior.FaceEvent
import face.core.behavior.FaceEventListener
import face.core.behavior.Vigilance
import face.core.expression.Expression
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.ChannelGroup
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.core.time.Rng
import face.render.FaceStyle
import face.render.FrameStats

// Sliders and presets edit the engine's target; the springs animate the visible face towards it.
internal class PlaygroundState(private val library: ExpressionLibrary, saved: List<Expression>) {
    private val edited = FacePose()
    val engine = FaceEngine(library, Rng(System.nanoTime()))
    val log = mutableStateListOf<String>()
    val stats = FrameStats()
    val values = mutableStateListOf<Float>()
    val savedPoses = mutableStateListOf<Expression>().apply { addAll(saved) }

    var style by mutableStateOf(FaceStyle.Default)
    var selected by mutableStateOf<String?>(null)
    var spamming by mutableStateOf(false)
    var tunedGroup by mutableStateOf(ChannelGroup.Head)
    val dynamicsRevision = mutableIntStateOf(0)
    var vigilance by mutableStateOf(Vigilance.Awake)
    var fastTime by mutableStateOf(false)
    var lifeOn by mutableStateOf(true)
        private set

    init {
        repeat(Channel.count) { values.add(0f) }
        engine.listener = FaceEventListener { event, detail, time ->
            if (event == FaceEvent.Saccade) return@FaceEventListener
            vigilance = engine.vigilance
            log.add(0, "%7.1fs  %s %s".format(time, event.name, detail))
            if (log.size > LOG_SIZE) log.removeAt(log.lastIndex)
        }
        // Start under the Director's control; sliders and expression chips switch to a manual pose when touched.
        library.poseInto(edited, ExpressionId.Neutral)
        for (c in Channel.entries) values[c.ordinal] = edited[c]
    }

    fun toggleLife(on: Boolean) {
        lifeOn = on
        engine.lifeEnabled = on
    }

    fun dozeOff() {
        engine.dozeOff()
    }

    fun poke() {
        engine.interact()
    }

    fun load(id: ExpressionId) {
        library.poseInto(edited, id)
        selected = id.key
        publish()
    }

    fun load(saved: Expression) {
        edited.resetToRest()
        if (saved.id != ExpressionId.Neutral.key) edited.add(library.neutral)
        edited.add(saved)
        Constraints.apply(edited)
        selected = saved.id
        publish()
    }

    fun center() {
        for (c in HEAD_PLACEMENT) edited[c] = c.rest
        publish()
    }

    fun set(channel: Channel, value: Float) {
        edited[channel] = value
        selected = null
        publish()
    }

    // A random expression, often thrown somewhere else on screen at a random size and tilt.
    fun randomTarget(rng: Rng) {
        val id = ExpressionId.entries[rng.nextInt(ExpressionId.entries.size)]
        library.poseInto(edited, id)
        if (rng.nextInt(2) == 0) {
            edited[Channel.HeadX] = rng.nextFloat(-0.6f, 0.6f)
            edited[Channel.HeadY] = rng.nextFloat(-0.6f, 0.6f)
            edited[Channel.HeadScale] = rng.nextFloat(0.6f, 1.4f)
            edited[Channel.HeadTilt] = rng.nextFloat(-0.5f, 0.5f)
        }
        selected = id.key
        publish()
    }

    fun profile(group: ChannelGroup): DynamicsProfile = engine.dynamics[group]

    fun setProfile(group: ChannelGroup, profile: DynamicsProfile) {
        engine.setProfile(group, profile)
        dynamicsRevision.intValue++
    }

    fun resetDynamics() {
        for (group in ChannelGroup.entries) engine.setProfile(group, DynamicsConfig.Default[group])
        dynamicsRevision.intValue++
    }

    // Neutral is saved relative to the rig rest pose; everything else relative to neutral.
    fun export(name: String): Expression {
        val base = FacePose()
        if (name != ExpressionId.Neutral.key) base.add(library.neutral)
        return Expression.fromPose(name, edited, base)
    }

    fun remember(expression: Expression) {
        savedPoses.removeAll { it.id == expression.id }
        savedPoses.add(expression)
        selected = expression.id
    }

    private fun publish() {
        engine.interact()
        engine.setTarget(edited)
        for (c in Channel.entries) values[c.ordinal] = edited[c]
    }

    private companion object {
        val HEAD_PLACEMENT = listOf(Channel.HeadX, Channel.HeadY, Channel.HeadTilt, Channel.HeadScale)
        const val LOG_SIZE = 60
    }
}
