package face.core.stage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Stage moves (format 1): placements are offsets on the head channels, plus where to look (towards the user);
// beats are authored attack/hold/release envelopes. Distances scale with the personality's expressiveness.
@Serializable
public data class StageConfig(
    val format: Int = FORMAT,
    val rate: Float = 6f,
    val returnAfter: Float = 2.5f,
    val placements: Map<String, Placement> = emptyMap(),
    val hop: Hop = Hop(),
    val leanIn: LeanIn = LeanIn(),
    val peek: Peek = Peek(),
) {
    init {
        require(format == FORMAT) { "unsupported stage format $format" }
        require(rate > 0f && returnAfter >= 0f) { "rate must be positive and returnAfter not negative" }
    }

    public companion object {
        public const val FORMAT: Int = 1
        private const val RESOURCE = "/face/stage/moves.json"
        private val json = Json { encodeDefaults = true }

        public fun decode(text: String): StageConfig = json.decodeFromString(serializer(), text)

        public fun loadBundled(): StageConfig {
            val text = StageConfig::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("missing bundled $RESOURCE")
            return decode(text)
        }
    }
}

@Serializable
public data class Placement(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 0f,
    val tilt: Float = 0f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
)

@Serializable
public data class Hop(val attack: Float = 0.12f, val hold: Float = 0.04f, val release: Float = 0.22f, val height: Float = 0.25f, val squash: Float = -0.25f)

@Serializable
public data class LeanIn(
    val attack: Float = 0.25f,
    val hold: Float = 0.9f,
    val release: Float = 0.4f,
    val scale: Float = 0.18f,
    val tilt: Float = 0.12f,
    val brows: Float = 0.25f,
)

@Serializable
public data class Peek(
    val attack: Float = 0.3f,
    val hold: Float = 1f,
    val release: Float = 0.45f,
    val x: Float = 0.82f,
    val tilt: Float = 0.22f,
    val lookX: Float = 0.6f,
)
