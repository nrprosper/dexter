package face.core.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Speech mouth tuning (format 1). Levels are 0..1 track loudness, times are seconds. Bundled defaults: face/speech/mouth.json.
@Serializable
public data class MouthConfig(
    val format: Int = FORMAT,
    val gate: Float = 0.3f,
    val compression: Float = 0.6f,
    val gain: Float = 1.15f,
    val attack: Float = 0.018f,
    val release: Float = 0.075f,
    val openLevel: Float = 0.1f,
    val closeLevel: Float = 0.05f,
    val closeRatio: Float = 0.55f,
    val peakDecay: Float = 1.2f,
    val minOpen: Float = 0.05f,
    val minClosure: Float = 0.06f,
    val maxOpen: Float = 0.8f,
    val widthGain: Float = 0.35f,
    val roundGain: Float = 0.5f,
    val shapeSmoothing: Float = 0.06f,
    val headBob: Float = 0.04f,
    val emotionRestraint: Float = 0.4f,
    val springBoost: SpringBoost = SpringBoost(),
    val beat: BeatConfig = BeatConfig(),
    val style: StyleConfig = StyleConfig(),
) {
    init {
        require(format == FORMAT) { "unsupported mouth format $format" }
        require(gate in 0f..0.95f) { "gate must be in 0..0.95" }
        require(compression > 0f && compression <= 1f) { "compression must be in (0, 1]" }
        require(attack > 0f && release > 0f && shapeSmoothing > 0f) { "time constants must be positive" }
        require(minOpen >= 0f && minClosure >= 0f) { "hold times must not be negative" }
        require(closeLevel < openLevel) { "closeLevel must be below openLevel" }
        require(closeRatio in 0f..1f) { "closeRatio must be in 0..1" }
    }

    public companion object {
        public const val FORMAT: Int = 1
        private const val RESOURCE = "/face/speech/mouth.json"
        private val json = Json { prettyPrint = true; prettyPrintIndent = "  "; encodeDefaults = true }

        public fun decode(text: String): MouthConfig = json.decodeFromString(serializer(), text)

        public fun encode(config: MouthConfig): String = json.encodeToString(serializer(), config)

        public fun loadBundled(): MouthConfig {
            val text = MouthConfig::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("missing bundled $RESOURCE")
            return decode(text)
        }
    }
}

// Mouth springs run faster while talking: frequency × (base + energy × style energy).
@Serializable
public data class SpringBoost(val base: Float = 1.6f, val energy: Float = 1.4f)

@Serializable
public data class BeatConfig(
    val threshold: Float = 0.5f,
    val cooldown: Float = 0.5f,
    val duration: Float = 0.32f,
    val brow: Float = 0.3f,
    val lid: Float = 0.1f,
    val nod: Float = 0.06f,
)

// How SpeechStyle (energy, seriousness, playfulness) scales the mouth and gestures.
@Serializable
public data class StyleConfig(
    val gainBase: Float = 0.7f,
    val gainEnergy: Float = 0.6f,
    val seriousRestraint: Float = 0.35f,
    val seriousBeats: Float = 0.6f,
    val smirk: Float = 0.25f,
    val smirkPlayfulness: Float = 0.5f,
    val laughPlayfulness: Float = 0.7f,
    val quirkSeriousness: Float = 0.6f,
)
