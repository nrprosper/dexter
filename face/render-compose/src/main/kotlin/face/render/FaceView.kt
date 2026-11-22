package face.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.FacePose

@Composable
public fun FaceView(modifier: Modifier = Modifier, style: FaceStyle = FaceStyle.Default) {
    val pose = remember { FacePose().also { ExpressionLibrary.loadBundled().poseInto(it, ExpressionId.Neutral) } }
    FaceCanvas(pose, style, rememberUpdatedState(0), modifier)
}

// Reads revision only in the draw phase, so pose edits redraw without recomposing.
@Composable
public fun FaceCanvas(pose: FacePose, style: FaceStyle, revision: State<Int>, modifier: Modifier = Modifier) {
    val renderer = remember { FaceRenderer() }
    Canvas(modifier.fillMaxSize()) {
        revision.value
        renderer.draw(this, pose, style)
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun FaceViewPreview() {
    FaceView()
}
