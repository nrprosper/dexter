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

// Square, character-filling images for the README gallery; they double as visual regression tests.
class ReadmeGalleryTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 900, screenHeight = 900))

    private val library = ExpressionLibrary.loadBundled()

    private fun shot(name: String, id: ExpressionId, time: Float = 0f, tweak: FacePose.() -> Unit = {}) {
        val pose = FacePose().also { library.poseInto(it, id) }
        pose.tweak()
        pose[Channel.HeadScale] = pose[Channel.HeadScale] * FRAME_SCALE
        pose[Channel.HeadY] = pose[Channel.HeadY] + FRAME_DROP
        val revision = mutableIntStateOf(0)
        paparazzi.snapshot(name) { FaceCanvas(pose, FaceStyle.Default, revision, time = { time }) }
    }

    @Test
    fun expressions() {
        shot("neutral", ExpressionId.Neutral)
        shot("joy", ExpressionId.Joy)
        shot("laugh", ExpressionId.Laugh)
        shot("surprised", ExpressionId.Surprised)
        shot("determined", ExpressionId.Determined)
        shot("sleeping", ExpressionId.Sleeping)
        shot("lowBattery", ExpressionId.Concerned) { this[Channel.WarnTint] = 1f }
        shot("dizzy", ExpressionId.Confused, time = 0.3f) {
            this[Channel.Dizzy] = 1f
            this[Channel.Question] = 0f
        }
    }

    @Test
    fun watching() {
        shot("lookLeft", ExpressionId.Neutral) {
            this[Channel.GazeX] = -0.85f
            this[Channel.HeadTilt] = -0.12f
        }
        shot("lookUpRight", ExpressionId.Curious) {
            this[Channel.GazeX] = 0.7f
            this[Channel.GazeY] = 0.6f
            this[Channel.HeadTilt] = 0.15f
        }
        shot("watchDownRight", ExpressionId.Neutral) {
            this[Channel.GazeX] = 0.75f
            this[Channel.GazeY] = -0.6f
            this[Channel.BrowHeightL] = 0.3f
            this[Channel.BrowHeightR] = 0.3f
            this[Channel.HeadTilt] = 0.12f
            this[Channel.MouthRoundness] = 0.3f
        }
        shot("thinking", ExpressionId.Neutral) {
            this[Channel.GazeX] = -0.5f
            this[Channel.GazeY] = 0.55f
            this[Channel.ThinkDots] = 0.7f
            this[Channel.BrowHeightL] = 0.5f
            this[Channel.MouthAsymmetry] = 0.3f
            this[Channel.HeadTilt] = -0.1f
        }
        shot("petting", ExpressionId.Neutral) {
            this[Channel.EyeSmileL] = 0.9f
            this[Channel.EyeSmileR] = 0.9f
            this[Channel.Blush] = 0.6f
            this[Channel.MouthCurve] = 0.6f
            this[Channel.HeadTilt] = 0.15f
        }
        shot("eyePoke", ExpressionId.Neutral) {
            this[Channel.EyeOpenL] = 0f
            this[Channel.BrowTiltL] = 0.6f
            this[Channel.BrowHeightL] = -0.3f
            this[Channel.MouthCurve] = -0.3f
            this[Channel.MouthAsymmetry] = -0.4f
        }
    }

    // Leaves room around the face, more above it where accents like Zzz and dizzy swirls appear.
    private companion object {
        const val FRAME_SCALE = 0.72f
        const val FRAME_DROP = -0.15f
    }
}
