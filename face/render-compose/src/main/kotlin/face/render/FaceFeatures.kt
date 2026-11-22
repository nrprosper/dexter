package face.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import face.core.rig.Channel
import face.core.rig.FacePose

internal class FaceFeatures(private val renderer: FaceRenderer) {
    private val path = Path()

    fun drawMouth(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color, line: Stroke) {
        val round = pose[Channel.MouthRoundness]
        val open = pose[Channel.MouthOpen]
        val curve = pose[Channel.MouthCurve]
        val asym = pose[Channel.MouthAsymmetry]
        val my = cy + style.mouthY * u - pose[Channel.MouthY] * MOUTH_Y_TRAVEL * u
        val w = style.mouthWidth * u * pose[Channel.MouthWidth] * (1f - ROUND_NARROWING * round)

        val corner = -curve * CORNER_LIFT * w
        val leftY = my + corner + asym * ASYM_LOW * w
        val rightY = my + corner - asym * ASYM_HIGH * w
        val middle = curve * SMILE_DEPTH * w
        val ctrlX = w * (CTRL_SPREAD + ROUND_SPREAD * round * open)

        path.rewind()
        path.moveTo(cx - w, leftY)
        val upper = my + middle * (1f - UPPER_FLATTEN * open) - open * round * OPEN_DEPTH * w * 0.5f
        path.cubicTo(cx - ctrlX, upper, cx + ctrlX, upper, cx + w, rightY)
        if (open < MIN_OPEN) {
            scope.drawPath(path, accent, style = line)
            return
        }
        val lower = my + middle + open * OPEN_DEPTH * w * (1f + LOWER_ROUND_BONUS * round)
        path.cubicTo(cx + ctrlX, lower, cx - ctrlX, lower, cx - w, leftY)
        path.close()
        scope.drawPath(path, style.pupil)
        scope.drawPath(path, accent, style = line)
    }

    fun drawBrow(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, accent: Color, line: Stroke) {
        val left = side < 0
        val bx = renderer.eyeCenterX(pose, style, u, cx, side)
        val halfWidth = renderer.eyeHalfWidth(pose, style, u, side) * BROW_SPAN
        val by = renderer.eyeCenterY(pose, style, u, cy, side) - renderer.eyeBaseHalfHeight(pose, style, u, side) -
            BROW_GAP * u - pose[if (left) Channel.BrowHeightL else Channel.BrowHeightR] * BROW_TRAVEL * u
        val tilt = pose[if (left) Channel.BrowTiltL else Channel.BrowTiltR] * BROW_TILT * halfWidth
        val curve = pose[if (left) Channel.BrowCurveL else Channel.BrowCurveR]
        val innerX = bx - side * halfWidth
        val outerX = bx + side * halfWidth

        path.rewind()
        path.moveTo(innerX, by + tilt)
        path.quadraticTo(bx, by - (BROW_ARCH + curve * BROW_CURVE) * halfWidth, outerX, by - tilt)
        scope.drawPath(path, accent, style = line)
    }

    fun drawAccents(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color, line: Stroke) {
        drawBlush(scope, pose, style, u, cx, cy)
        drawSweat(scope, pose, style, u, cx, cy)
        drawSparkle(scope, pose, style, u, cx, cy)
        drawQuestion(scope, pose, style, u, cx, cy, accent, line)
        drawSleepZ(scope, pose, style, u, cx, cy, accent, line)
        drawThinkDots(scope, pose, style, u, cx, cy, accent)
    }

    private fun drawBlush(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float) {
        val blush = pose[Channel.Blush]
        if (blush <= 0f) return
        val w = BLUSH_WIDTH * u
        val h = BLUSH_HEIGHT * u
        for (side in SIDES) {
            val x = renderer.eyeCenterX(pose, style, u, cx, side) + side * renderer.eyeHalfWidth(pose, style, u, side) * BLUSH_OUTSET
            val y = renderer.eyeCenterY(pose, style, u, cy, side) + renderer.eyeBaseHalfHeight(pose, style, u, side) + BLUSH_DROP * u
            scope.drawOval(BLUSH_COLOR, Offset(x - w / 2f, y - h / 2f), Size(w, h), alpha = blush * BLUSH_ALPHA)
        }
    }

    private fun drawSweat(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float) {
        val sweat = pose[Channel.Sweat]
        if (sweat <= 0f) return
        val x = renderer.eyeCenterX(pose, style, u, cx, 1) + renderer.eyeHalfWidth(pose, style, u, 1) + SWEAT_OUTSET * u
        val y = renderer.eyeCenterY(pose, style, u, cy, 1) - renderer.eyeBaseHalfHeight(pose, style, u, 1) * 0.6f
        val h = SWEAT_SIZE * u * (0.7f + 0.3f * sweat)
        path.rewind()
        path.moveTo(x, y - h * 0.6f)
        path.cubicTo(x + h * 0.45f, y, x + h * 0.4f, y + h * 0.4f, x, y + h * 0.4f)
        path.cubicTo(x - h * 0.4f, y + h * 0.4f, x - h * 0.45f, y, x, y - h * 0.6f)
        path.close()
        scope.drawPath(path, SWEAT_COLOR, alpha = sweat)
    }

    private fun drawSparkle(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float) {
        val sparkle = pose[Channel.Sparkle]
        if (sparkle <= 0f) return
        val x = renderer.eyeCenterX(pose, style, u, cx, -1) - renderer.eyeHalfWidth(pose, style, u, -1) - SPARKLE_OUTSET * u
        val y = renderer.eyeCenterY(pose, style, u, cy, -1) - renderer.eyeBaseHalfHeight(pose, style, u, -1) - SPARKLE_OUTSET * u * 0.6f
        val r = SPARKLE_SIZE * u * (0.6f + 0.4f * sparkle)
        path.rewind()
        path.moveTo(x, y - r)
        path.quadraticTo(x, y, x + r, y)
        path.quadraticTo(x, y, x, y + r)
        path.quadraticTo(x, y, x - r, y)
        path.quadraticTo(x, y, x, y - r)
        path.close()
        scope.drawPath(path, Color.White, alpha = sparkle)
    }

    private fun drawQuestion(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color, line: Stroke) {
        val question = pose[Channel.Question]
        if (question <= 0f) return
        val q = QUESTION_SIZE * u
        val x = renderer.eyeCenterX(pose, style, u, cx, 1) + renderer.eyeHalfWidth(pose, style, u, 1) * 0.4f + QUESTION_OUTSET * u
        val y = renderer.eyeCenterY(pose, style, u, cy, 1) - renderer.eyeBaseHalfHeight(pose, style, u, 1) - QUESTION_RISE * u
        path.rewind()
        path.moveTo(x - q, y - q * 0.2f)
        path.cubicTo(x - q, y - q * 1.3f, x + q, y - q * 1.3f, x + q, y - q * 0.2f)
        path.cubicTo(x + q, y + q * 0.5f, x, y + q * 0.4f, x, y + q * 1.2f)
        scope.drawPath(path, accent, alpha = question, style = line)
        scope.drawCircle(accent, line.width * 0.6f, Offset(x, y + q * 2.1f), alpha = question)
    }

    private fun drawSleepZ(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color, line: Stroke) {
        val sleep = pose[Channel.SleepZ]
        if (sleep <= 0f) return
        val baseX = cx + style.eyeSpacing * u + SLEEP_OFFSET_X * u
        val baseY = cy - SLEEP_OFFSET_Y * u
        for (i in 0 until 3) {
            val alpha = (sleep * 3f - i).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val s = SLEEP_SIZE * u * (1f + 0.35f * i)
            val x = baseX + i * 0.05f * u
            val y = baseY - i * 0.065f * u
            path.rewind()
            path.moveTo(x, y)
            path.lineTo(x + s, y)
            path.lineTo(x, y + s)
            path.lineTo(x + s, y + s)
            scope.drawPath(path, accent, alpha = alpha, style = line)
        }
    }

    private fun drawThinkDots(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color) {
        val think = pose[Channel.ThinkDots]
        if (think <= 0f) return
        for (i in 0 until 3) {
            val alpha = (think * 3f - i).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val x = cx - style.eyeSpacing * u - 0.02f * u + i * 0.045f * u
            val y = cy - THINK_RISE * u - i * 0.025f * u
            scope.drawCircle(accent, THINK_DOT * u * (1f + 0.4f * i), Offset(x, y), alpha = alpha)
        }
    }

    private companion object {
        val SIDES = intArrayOf(-1, 1)
        val BLUSH_COLOR = Color(0xFFFF7A9C)
        val SWEAT_COLOR = Color(0xFF8FD3FF)
        const val MOUTH_Y_TRAVEL = 0.05f
        const val ROUND_NARROWING = 0.25f
        const val CORNER_LIFT = 0.25f
        const val SMILE_DEPTH = 0.55f
        const val ASYM_LOW = 0.15f
        const val ASYM_HIGH = 0.35f
        const val CTRL_SPREAD = 0.4f
        const val ROUND_SPREAD = 0.6f
        const val UPPER_FLATTEN = 0.6f
        const val OPEN_DEPTH = 0.9f
        const val LOWER_ROUND_BONUS = 0.4f
        const val MIN_OPEN = 0.02f
        const val BROW_SPAN = 0.8f
        const val BROW_GAP = 0.045f
        const val BROW_TRAVEL = 0.04f
        const val BROW_TILT = 0.35f
        const val BROW_ARCH = 0.15f
        const val BROW_CURVE = 0.25f
        const val BLUSH_WIDTH = 0.07f
        const val BLUSH_HEIGHT = 0.028f
        const val BLUSH_OUTSET = 0.55f
        const val BLUSH_DROP = 0.045f
        const val BLUSH_ALPHA = 0.6f
        const val SWEAT_OUTSET = 0.035f
        const val SWEAT_SIZE = 0.05f
        const val SPARKLE_OUTSET = 0.03f
        const val SPARKLE_SIZE = 0.035f
        const val QUESTION_SIZE = 0.032f
        const val QUESTION_OUTSET = 0.05f
        const val QUESTION_RISE = 0.09f
        const val SLEEP_OFFSET_X = 0.08f
        const val SLEEP_OFFSET_Y = 0.2f
        const val SLEEP_SIZE = 0.03f
        const val THINK_RISE = 0.26f
        const val THINK_DOT = 0.012f
    }
}
