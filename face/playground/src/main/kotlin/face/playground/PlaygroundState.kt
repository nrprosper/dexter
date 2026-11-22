package face.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import face.core.expression.Expression
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.Constraints
import face.core.rig.FacePose
import face.render.FaceStyle

internal class PlaygroundState(private val library: ExpressionLibrary, saved: List<Expression>) {
    private val edited = FacePose()
    val display = FacePose()
    val revision = mutableIntStateOf(0)
    val values = mutableStateListOf<Float>()
    val savedPoses = mutableStateListOf<Expression>().apply { addAll(saved) }

    var style by mutableStateOf(FaceStyle.Default)
    var selected by mutableStateOf<String?>(null)

    init {
        repeat(Channel.count) { values.add(0f) }
        load(ExpressionId.Neutral)
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

    fun set(channel: Channel, value: Float) {
        edited[channel] = value
        selected = null
        publish()
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
        display.copyFrom(edited)
        Constraints.apply(display)
        for (c in Channel.entries) values[c.ordinal] = edited[c]
        revision.intValue++
    }
}
