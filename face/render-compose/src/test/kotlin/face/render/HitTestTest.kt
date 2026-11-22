package face.render

import app.cash.paparazzi.Paparazzi
import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.senses.FacePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Uses the Paparazzi rule only to load layoutlib, since FaceRenderer creates android Paths.
class HitTestTest {
    @get:Rule
    val paparazzi = Paparazzi()

    private val renderer by lazy { FaceRenderer() }
    private val style = FaceStyle.Default
    private val w = 1080f
    private val h = 2340f
    private val u = w
    private val pose = FacePose().also { ExpressionLibrary.loadBundled().poseInto(it, ExpressionId.Neutral) }

    private fun at(x: Float, y: Float, p: FacePose = pose) = renderer.hitTest(x, y, p, style, w, h)

    @Test
    fun partsAreFoundWhereTheyAreDrawn() {
        val eyeY = h / 2f + style.eyeY * u
        assertEquals(FacePart.LeftEye, at(w / 2f - style.eyeSpacing * u, eyeY).part)
        assertEquals(FacePart.RightEye, at(w / 2f + style.eyeSpacing * u, eyeY).part)
        assertEquals(FacePart.Mouth, at(w / 2f, h / 2f + style.mouthY * u).part)
        assertEquals(FacePart.Forehead, at(w / 2f, eyeY - style.eyeWidth * style.eyeAspect * u - 0.05f * u).part)
        assertEquals(FacePart.Outside, at(20f, 40f).part)
    }

    @Test
    fun hitTestFollowsTheFaceWhenItMoves() {
        val moved = FacePose().also {
            it.copyFrom(pose)
            it[Channel.HeadX] = 0.4f
            it[Channel.HeadY] = 0.4f
        }
        val cx = w / 2f + 0.4f * 0.5f * w
        val cy = h / 2f - 0.4f * 0.5f * h
        assertEquals(FacePart.RightEye, at(cx + style.eyeSpacing * u, cy + style.eyeY * u, moved).part)
        assertNotEquals(FacePart.LeftEye, at(w / 2f - style.eyeSpacing * u, h / 2f + style.eyeY * u, moved).part)
    }

    @Test
    fun lookPointsTowardsTheTouch() {
        val hit = at(w - 10f, 10f)
        assertTrue(hit.lookX > 0.9f && hit.lookY > 0.9f)
    }
}
