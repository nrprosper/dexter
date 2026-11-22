package face.core.personality

import face.api.Personality
import face.core.anim.DynamicsConfig
import face.core.anim.DynamicsProfile
import face.core.behavior.IdleBehavior
import face.core.behavior.LifeConfig
import face.core.rig.ChannelGroup

// Turns personality traits into the numbers the behaviours and springs use.
internal object PersonalityMapper {
    private val CURIOUS = setOf("tilt", "lookAround", "squint")
    private val PLAYFUL = setOf("bounce", "hum")

    fun life(p: Personality, base: LifeConfig = LifeConfig.Default): LifeConfig {
        val restless = p.gaze.restlessness.coerceAtLeast(MIN_RESTLESSNESS)
        val known = base.idle.behaviors.map { it.name }.toSet()
        val unknown = p.idle.weights.keys - known
        require(unknown.isEmpty()) { "unknown idle behaviours $unknown" }
        val behaviors = base.idle.behaviors.map { b ->
            val scaled = when (b.name) {
                in CURIOUS -> b.weight * (0.5f + p.curiosity)
                in PLAYFUL -> b.weight * (0.5f + p.playfulness)
                else -> b.weight.toFloat()
            }
            b.copy(weight = p.idle.weights[b.name] ?: Math.round(scaled))
        }.filter { it.weight > 0 }
        return base.copy(
            blink = base.blink.copy(meanInterval = p.blink.meanInterval, spread = p.blink.spread),
            gaze = base.gaze.copy(
                awayChance = (base.gaze.awayChance * restless).coerceAtMost(MAX_AWAY),
                fixationMean = base.gaze.fixationMean / restless,
            ),
            idle = base.idle.copy(budgetPerMinute = p.idle.budgetPerMinute, behaviors = behaviors.ifEmpty { NONE_IDLE }),
            boredom = base.boredom.copy(drowsyAfter = p.sleepiness.drowsyAfter, sleepAfter = p.sleepiness.sleepAfter),
        )
    }

    // Bounciness lowers damping (overshoot) and adds anticipation to the head; reactivity scales every frequency.
    fun dynamics(p: Personality, base: DynamicsConfig = DynamicsConfig.Default): DynamicsConfig {
        var config = base
        val dampingScale = COMPOSED + (BOUNCY - COMPOSED) * p.motion.bounciness.coerceIn(0f, 1f)
        for (group in ChannelGroup.entries) {
            val b = base[group]
            config = config.with(
                group,
                DynamicsProfile(
                    frequency = b.frequency * p.motion.reactivity.coerceAtLeast(MIN_REACTIVITY),
                    damping = (b.damping * dampingScale).coerceIn(MIN_DAMPING, MAX_DAMPING),
                    response = if (group == ChannelGroup.Head) -HEAD_ANTICIPATION * p.motion.bounciness else b.response,
                ),
            )
        }
        return config
    }

    private val NONE_IDLE = listOf(IdleBehavior.Defaults.first { it.name == "shift" })
    private const val MIN_RESTLESSNESS = 0.2f
    private const val MAX_AWAY = 0.9f
    private const val MIN_REACTIVITY = 0.2f
    private const val COMPOSED = 1.25f
    private const val BOUNCY = 0.75f
    private const val MIN_DAMPING = 0.3f
    private const val MAX_DAMPING = 1.2f
    private const val HEAD_ANTICIPATION = 0.5f
}
