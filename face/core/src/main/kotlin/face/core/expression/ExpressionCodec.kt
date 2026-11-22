package face.core.expression

import face.core.rig.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

public object ExpressionCodec {
    public const val FORMAT: Int = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    @Serializable
    private class ExpressionJson(val format: Int, val id: String, val deltas: Map<String, Float>)

    public fun decode(text: String): Expression {
        val file = json.decodeFromString(ExpressionJson.serializer(), text)
        require(file.format == FORMAT) { "unsupported expression format ${file.format} in '${file.id}'" }
        val deltas = file.deltas.mapKeys { (key, _) ->
            Channel.fromKey(key) ?: throw IllegalArgumentException("unknown channel '$key' in '${file.id}'")
        }
        return Expression.of(file.id, deltas)
    }

    public fun encode(expression: Expression): String {
        val deltas = LinkedHashMap<String, Float>()
        for (i in expression.channels.indices) {
            deltas[Channel.entries[expression.channels[i]].key] = round3(expression.deltas[i])
        }
        return json.encodeToString(ExpressionJson.serializer(), ExpressionJson(FORMAT, expression.id, deltas)) + "\n"
    }

    private fun round3(value: Float): Float = (value * 1000f).roundToInt() / 1000f
}
