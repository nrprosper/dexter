package assistant.core

import face.api.Emotion
import face.api.StageMove

// The reply every brain must produce, as JSON, fields in this order so the feeling streams in before the words:
// {"emotion", "intensity", "confidence", "tone", "sentences": [{"text", "emotion"?, "move"?}]}.
// Labels are the face's own emotion basis, tones and stage moves (snake_case); unknown emotions read as neutral,
// unknown moves are ignored. A move is a stage direction; the face engine decides the motion.
public object ReplyContract {
    public val emotions: List<String> = Emotion.entries.map { it.name.lowercase() }
    public val tones: List<String> = Tone.entries.map { it.name.lowercase() }
    public val moves: List<String> = StageMove.entries.map { snake(it.name) }

    public fun move(label: String?): StageMove? {
        val l = label?.trim()?.lowercase() ?: return null
        return StageMove.entries.firstOrNull { snake(it.name) == l }
    }

    private fun snake(name: String): String = name.replace(Regex("(?<=[a-z])([A-Z])"), "_$1").lowercase()

    public fun emotion(label: String?): Emotion =
        Emotion.entries.firstOrNull { it.name.equals(label?.trim(), ignoreCase = true) } ?: Emotion.Neutral

    public fun tone(label: String?): Tone = Tone.entries.firstOrNull { it.name.equals(label?.trim(), ignoreCase = true) } ?: Tone.Neutral

    // JSON Schema for structured output.
    public val schema: String = buildString {
        val emotionEnum = emotions.joinToString(",") { "\"$it\"" }
        val toneEnum = tones.joinToString(",") { "\"$it\"" }
        val moveEnum = moves.joinToString(",") { "\"$it\"" }
        append("""{"type":"object","additionalProperties":false,""")
        append(""""required":["emotion","intensity","confidence","tone","sentences"],"properties":{""")
        append(""""emotion":{"type":"string","enum":[$emotionEnum]},""")
        append(""""intensity":{"type":"number"},""")
        append(""""confidence":{"type":"number"},""")
        append(""""tone":{"type":"string","enum":[$toneEnum]},""")
        append(""""sentences":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["text"],""")
        append(""""properties":{"text":{"type":"string"},"emotion":{"type":"string","enum":[$emotionEnum]},""")
        append(""""move":{"type":"string","enum":[$moveEnum]}}}}}}""")
    }
}
