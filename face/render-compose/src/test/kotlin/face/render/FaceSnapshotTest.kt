package face.render

import androidx.compose.runtime.mutableIntStateOf
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
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
    fun keyExpressionsOnDefaultSilhouette() {
        for (id in ExpressionId.entries) snapshot(id, FaceStyle.Default)
    }

    @Test
    fun neutralOnEverySilhouette() {
        for (style in FaceStyle.Silhouettes) snapshot(ExpressionId.Neutral, style)
    }

    @Test
    fun keyExpressionsWithBrows() {
        for (id in ExpressionId.entries) snapshot(id, FaceStyle.Default.copy(name = "OrbBrows", showBrows = true))
    }
}
