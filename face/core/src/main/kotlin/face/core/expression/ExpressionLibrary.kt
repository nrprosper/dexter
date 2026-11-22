package face.core.expression

import face.core.rig.Constraints
import face.core.rig.FacePose

public class ExpressionLibrary(private val expressions: Map<ExpressionId, Expression>) {
    public operator fun get(id: ExpressionId): Expression = expressions.getValue(id)

    public val neutral: Expression get() = get(ExpressionId.Neutral)

    // Neutral is the base every other expression is layered on.
    public fun poseInto(out: FacePose, id: ExpressionId, weight: Float = 1f) {
        out.resetToRest()
        out.add(neutral)
        if (id != ExpressionId.Neutral) out.add(get(id), weight)
        Constraints.apply(out)
    }

    public companion object {
        private const val RESOURCE_DIR = "/face/expressions/"

        public fun loadBundled(): ExpressionLibrary =
            ExpressionLibrary(ExpressionId.entries.associateWith { load(it) })

        private fun load(id: ExpressionId): Expression {
            val path = "$RESOURCE_DIR${id.key}.json"
            val text = ExpressionLibrary::class.java.getResourceAsStream(path)
                ?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("missing bundled expression $path")
            return ExpressionCodec.decode(text)
        }
    }
}
