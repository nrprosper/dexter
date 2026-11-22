package face.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.rig.FacePose
import face.core.time.Rng

@Composable
public fun rememberFaceEngine(): FaceEngine = remember { FaceEngine(ExpressionLibrary.loadBundled(), Rng(System.nanoTime())) }

// The engine is also the FaceController: hand the same object to the orchestrator.
@Composable
public fun FaceView(
    modifier: Modifier = Modifier,
    style: FaceStyle = FaceStyle.Default,
    engine: FaceEngine = rememberFaceEngine(),
) {
    AnimatedFace(engine, modifier, style)
}

// Reads revision only in the draw phase, so pose edits redraw without recomposing.
@Composable
public fun FaceCanvas(
    pose: FacePose,
    style: FaceStyle,
    revision: State<Int>,
    modifier: Modifier = Modifier,
    renderer: FaceRenderer = remember { FaceRenderer() },
    time: () -> Float = { 0f },
) {
    Canvas(modifier.fillMaxSize()) {
        revision.value
        renderer.draw(this, pose, style, time())
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun FaceViewPreview() {
    FaceView()
}
