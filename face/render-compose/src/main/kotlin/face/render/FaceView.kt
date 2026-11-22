package face.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

private val Background = Color(0xFF050507)
private val Placeholder = Color(0x33FFFFFF)
private const val PLACEHOLDER_RADIUS_FRACTION = 0.015f

@Composable
public fun FaceView(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Background)
        drawCircle(Placeholder, radius = size.minDimension * PLACEHOLDER_RADIUS_FRACTION)
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun FaceViewPreview() {
    FaceView()
}
