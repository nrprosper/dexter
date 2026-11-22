package face.core.personality

import face.api.BlinkSettings
import face.api.DirectorSettings
import face.api.Emotion
import face.api.GazeSettings
import face.api.IdleSettings
import face.api.MoodSettings
import face.api.MotionSettings
import face.api.Personality
import face.api.Quirk
import face.api.SleepSettings
import face.api.TransitionBeat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Personality JSON, format 1. Unknown keys, emotions or quirks are errors; anything left out keeps the default.
public object PersonalityCodec {
    public const val FORMAT: Int = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    public fun decode(text: String): Personality {
        val f = json.decodeFromString(File.serializer(), text)
        require(f.format == FORMAT) { "unsupported personality format ${f.format}" }
        val d = Personality()
        return Personality(
            id = f.id,
            name = f.name ?: f.id,
            mood = f.mood?.let {
                MoodSettings(
                    baseline = it.baseline?.let(::emotions) ?: d.mood.baseline,
                    arousal = it.arousal ?: d.mood.arousal,
                    driftHalfLife = it.driftHalfLife ?: d.mood.driftHalfLife,
                )
            } ?: d.mood,
            expressiveness = f.expressiveness ?: d.expressiveness,
            emotionGain = f.emotionGain?.let(::emotions) ?: d.emotionGain,
            director = f.director?.let {
                DirectorSettings(
                    halfLife = it.halfLife ?: d.director.halfLife,
                    minDwell = it.minDwell ?: d.director.minDwell,
                    hysteresis = it.hysteresis ?: d.director.hysteresis,
                    inertia = it.inertia ?: d.director.inertia,
                    transitionBeat = it.transitionBeat?.let(::beat) ?: d.director.transitionBeat,
                )
            } ?: d.director,
            motion = f.motion?.let {
                MotionSettings(it.reactivity ?: d.motion.reactivity, it.bounciness ?: d.motion.bounciness)
            } ?: d.motion,
            blink = f.blink?.let { BlinkSettings(it.meanInterval ?: d.blink.meanInterval, it.spread ?: d.blink.spread) } ?: d.blink,
            gaze = f.gaze?.let { GazeSettings(it.restlessness ?: d.gaze.restlessness) } ?: d.gaze,
            idle = f.idle?.let {
                IdleSettings(it.budgetPerMinute ?: d.idle.budgetPerMinute, it.weights ?: d.idle.weights)
            } ?: d.idle,
            curiosity = f.curiosity ?: d.curiosity,
            playfulness = f.playfulness ?: d.playfulness,
            sleepiness = f.sleepiness?.let {
                SleepSettings(it.drowsyAfter ?: d.sleepiness.drowsyAfter, it.sleepAfter ?: d.sleepiness.sleepAfter)
            } ?: d.sleepiness,
            quirks = f.quirks?.map(::quirk)?.toSet() ?: d.quirks,
        )
    }

    public fun encode(p: Personality): String = json.encodeToString(
        File.serializer(),
        File(
            format = FORMAT,
            id = p.id,
            name = p.name,
            mood = Mood(keyed(p.mood.baseline), p.mood.arousal, p.mood.driftHalfLife),
            expressiveness = p.expressiveness,
            emotionGain = keyed(p.emotionGain),
            director = Director(
                p.director.halfLife, p.director.minDwell, p.director.hysteresis, p.director.inertia,
                p.director.transitionBeat.name.lowercase(),
            ),
            motion = Motion(p.motion.reactivity, p.motion.bounciness),
            blink = Blink(p.blink.meanInterval, p.blink.spread),
            gaze = Gaze(p.gaze.restlessness),
            idle = Idle(p.idle.budgetPerMinute, p.idle.weights),
            curiosity = p.curiosity,
            playfulness = p.playfulness,
            sleepiness = Sleep(p.sleepiness.drowsyAfter, p.sleepiness.sleepAfter),
            quirks = p.quirks.map { key(it.name) },
        ),
    ) + "\n"

    private fun emotions(map: Map<String, Float>): Map<Emotion, Float> = map.mapKeys { (k, _) ->
        Emotion.entries.firstOrNull { key(it.name) == k } ?: throw IllegalArgumentException("unknown emotion '$k'")
    }

    private fun keyed(map: Map<Emotion, Float>): Map<String, Float> = map.mapKeys { key(it.key.name) }

    private fun beat(value: String): TransitionBeat =
        TransitionBeat.entries.firstOrNull { it.name.lowercase() == value }
            ?: throw IllegalArgumentException("unknown transitionBeat '$value'")

    private fun quirk(value: String): Quirk =
        Quirk.entries.firstOrNull { key(it.name) == value } ?: throw IllegalArgumentException("unknown quirk '$value'")

    private fun key(name: String): String = name.replaceFirstChar { it.lowercaseChar() }

    @Serializable
    private class File(
        val format: Int,
        val id: String,
        val name: String? = null,
        val mood: Mood? = null,
        val expressiveness: Float? = null,
        val emotionGain: Map<String, Float>? = null,
        val director: Director? = null,
        val motion: Motion? = null,
        val blink: Blink? = null,
        val gaze: Gaze? = null,
        val idle: Idle? = null,
        val curiosity: Float? = null,
        val playfulness: Float? = null,
        val sleepiness: Sleep? = null,
        val quirks: List<String>? = null,
    )

    @Serializable
    private class Mood(val baseline: Map<String, Float>? = null, val arousal: Float? = null, val driftHalfLife: Float? = null)

    @Serializable
    private class Director(
        val halfLife: Float? = null,
        val minDwell: Float? = null,
        val hysteresis: Float? = null,
        val inertia: Float? = null,
        val transitionBeat: String? = null,
    )

    @Serializable
    private class Motion(val reactivity: Float? = null, val bounciness: Float? = null)

    @Serializable
    private class Blink(val meanInterval: Float? = null, val spread: Float? = null)

    @Serializable
    private class Gaze(val restlessness: Float? = null)

    @Serializable
    private class Idle(val budgetPerMinute: Int? = null, val weights: Map<String, Int>? = null)

    @Serializable
    private class Sleep(val drowsyAfter: Float? = null, val sleepAfter: Float? = null)
}
