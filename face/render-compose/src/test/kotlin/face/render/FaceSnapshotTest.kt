package face.render

import androidx.compose.runtime.mutableIntStateOf
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.FacePose
import org.junit.Rule
import org.junit.Test

class FaceSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val library = ExpressionLibrary.loadBundled()

    private fun snapshot(id: ExpressionId, style: FaceStyle) {
        val pose = FacePose().also { library.poseInto(it, id) }
        val revision = mutableIntStateOf(0)
        paparazzi.snapshot("${style.name.replace(" ", "")}_${id.key}") {
            FaceCanvas(pose, style, revision)
        }
    }

    @Test
    fun everyExpressionOnDefaultStyle() {
        for (id in ExpressionId.entries) snapshot(id, FaceStyle.Default)
    }

    @Test
    fun neutralAndLaughOnEverySilhouette() {
        for (style in FaceStyle.Silhouettes) {
            snapshot(ExpressionId.Neutral, style)
            snapshot(ExpressionId.Laugh, style)
        }
    }

    @Test
    fun strokeMouthAndDarkBackdrop() {
        val style = FaceStyle.Default.copy(name = "OrbStrokeDark", mouthStyle = MouthStyle.Stroke, backdrop = Backdrop.Dark)
        for (id in listOf(ExpressionId.Happy, ExpressionId.Surprised, ExpressionId.Sad)) snapshot(id, style)
    }

    @Test
    fun roamingPoses() {
        for ((name, x, y, scale, tilt) in ROAMS) {
            val pose = FacePose().also { library.poseInto(it, ExpressionId.Happy) }
            pose[Channel.HeadX] = x
            pose[Channel.HeadY] = y
            pose[Channel.HeadScale] = scale
            pose[Channel.HeadTilt] = tilt
            val revision = mutableIntStateOf(0)
            paparazzi.snapshot("roam_$name") { FaceCanvas(pose, FaceStyle.Default, revision) }
        }
    }

    private data class Roam(val name: String, val x: Float, val y: Float, val scale: Float, val tilt: Float)

    private companion object {
        val ROAMS = listOf(
            Roam("cornerSmall", -0.55f, -0.6f, 0.55f, 0f),
            Roam("topRightBigTilted", 0.35f, 0.45f, 1.5f, 0.6f),
            Roam("halfOffRight", 1f, 0f, 1f, -0.3f),
        )
    }
}
