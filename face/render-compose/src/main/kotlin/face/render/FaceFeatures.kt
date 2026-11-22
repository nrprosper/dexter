package face.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class FaceFeatures(private val renderer: FaceRenderer) {
    private val path = Path()
    private val innerPath = Path()
    private var faceTop = 0f
    private var faceLeft = 0f
    private var faceRight = 0f

    fun drawMouth(scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color) {
        val round = pose[Channel.MouthRoundness]
        val open = pose[Channel.MouthOpen]
        val curve = pose[Channel.MouthCurve]
        val asym = pose[Channel.MouthAsymmetry]
        val t = style.mouthThickness * u * pose[Channel.MouthThickness]
        val my = cy + style.mouthY * u - pose[Channel.MouthY] * MOUTH_Y_TRAVEL * u
        val w = style.mouthWidth * u * pose[Channel.MouthWidth] * (1f - ROUND_NARROWING * round)

        val corner = -curve * CORNER_LIFT * w
        val leftY = my + corner + asym * ASYM_LOW * w
        val rightY = my + corner - asym * ASYM_HIGH * w
        val middle = curve * SMILE_DEPTH * w
        val ctrlX = w * (CTRL_SPREAD + ROUND_SPREAD * round * open)
        val upper = my + middle * (1f - UPPER_FLATTEN * open) - open * round * OPEN_DEPTH * w * 0.5f
        val lower = my + middle + open * OPEN_DEPTH * w * (1f + LOWER_ROUND_BONUS * round)

        if (style.mouthStyle == MouthStyle.Stroke) {
            drawStrokeMouth(scope, style, cx, w, leftY, rightY, upper, lower, ctrlX, open, accent, renderer.mouthStroke.get(t))
            return
        }
        val half = t * 0.5f
        path.rewind()
        path.moveTo(cx - w, leftY - half)
        path.cubicTo(cx - ctrlX, upper - half, cx + ctrlX, upper - half, cx + w, rightY - half)
        path.quadraticTo(cx + w + t * CAP_BULGE, rightY, cx + w, rightY + half)
        path.cubicTo(cx + ctrlX, lower + half, cx - ctrlX, lower + half, cx - w, leftY + half)
        path.quadraticTo(cx - w - t * CAP_BULGE, leftY, cx - w, leftY - half)
        path.close()
        scope.drawPath(path, accent)
        scope.drawPath(path, accent, style = renderer.outlineStroke.get(t * OUTLINE_SOFTEN))

        val inner = ((open - INNER_MIN_OPEN) / INNER_FADE).coerceIn(0f, 1f)
        if (inner <= 0f) return
        val wi = w * INNER_SPAN
        val innerTop = upper + half * 0.4f
        val innerBottom = lower - half * 0.6f
        innerPath.rewind()
        innerPath.moveTo(cx - wi, leftY + half * 0.2f)
        innerPath.cubicTo(cx - ctrlX * INNER_SPAN, innerTop, cx + ctrlX * INNER_SPAN, innerTop, cx + wi, rightY + half * 0.2f)
        innerPath.cubicTo(cx + ctrlX * INNER_SPAN, innerBottom, cx - ctrlX * INNER_SPAN, innerBottom, cx - wi, leftY + half * 0.2f)
        innerPath.close()
        scope.drawPath(innerPath, style.pupil, alpha = inner)
        if (open > TONGUE_MIN_OPEN) {
            val depth = (innerBottom - innerTop) * TONGUE_DEPTH
            scope.clipPath(innerPath) {
                drawOval(TONGUE_COLOR, Offset(cx - wi * 0.55f, innerBottom - depth), Size(wi * 1.1f, depth * 2f), alpha = inner)
            }
        }
    }

    private fun drawStrokeMouth(
        scope: DrawScope, style: FaceStyle, cx: Float, w: Float, leftY: Float, rightY: Float,
        upper: Float, lower: Float, ctrlX: Float, open: Float, accent: Color, line: Stroke,
    ) {
        path.rewind()
        path.moveTo(cx - w, leftY)
        path.cubicTo(cx - ctrlX, upper, cx + ctrlX, upper, cx + w, rightY)
        if (open < MIN_OPEN) {
            scope.drawPath(path, accent, style = line)
            return
        }
        path.cubicTo(cx + ctrlX, lower, cx - ctrlX, lower, cx - w, leftY)
        path.close()
        scope.drawPath(path, style.pupil)
        scope.drawPath(path, accent, style = line)
    }

    fun drawBrow(scope: DrawScope, style: FaceStyle, u: Float, brow: FloatArray, accent: Color) {
        path.rewind()
        path.moveTo(brow[0], brow[1])
        path.quadraticTo((brow[0] + brow[3]) * 0.5f, brow[2], brow[3], brow[4])
        scope.drawPath(path, accent, style = renderer.browStroke.get(style.browThickness * u))
    }

    fun drawAccents(
        scope: DrawScope, pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, accent: Color, line: Stroke, time: Float,
    ) {
        drawBlush(scope, pose, style, u, cx, cy)
        drawSweat(scope, pose, style, u, cx, cy)
        drawSparkle(scope, pose, style, u, cx, cy)
        if (pose[Channel.Question] <= 0f && pose[Channel.SleepZ] <= 0f && pose[Channel.ThinkDots] <= 0f && pose[Channel.Dizzy] <= 0f) return
        measureFace(pose, style, u, cx, cy)
        drawQuestion(scope, pose, u, accent, line)
        drawSleepZ(scope, pose, u, accent, line)
        drawThinkDots(scope, pose, u, accent)
        drawDizzy(scope, pose, u, accent, time)
    }

    // Fills faceTop/faceLeft/faceRight with the eyes' and brows' extent so glyph accents can sit outside it.
    private fun measureFace(pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float) {
        faceTop = Float.MAX_VALUE
        faceLeft = Float.MAX_VALUE
        faceRight = -Float.MAX_VALUE
        for (side in SIDES) {
            val open = pose[if (side < 0) Channel.EyeOpenL else Channel.EyeOpenR]
            val ex = renderer.eyeCenterX(pose, style, u, cx, side)
            val a = renderer.eyeHalfWidth(pose, style, u, side)
            faceTop = min(faceTop, renderer.eyeCenterY(pose, style, u, cy, side) - renderer.eyeBaseHalfHeight(pose, style, u, side) * max(1f, open))
            faceLeft = min(faceLeft, ex - a)
            faceRight = max(faceRight, ex + a)
        }
        if (!style.showBrows) return
        val half = style.browThickness * u * 0.5f
        for (brow in SIDES) {
            val b = if (brow < 0) renderer.browLeft else renderer.browRight
            faceTop = min(faceTop, min(b[1], min(b[2], b[4])) - half)
            faceLeft = min(faceLeft, min(b[0], b[3]) - half)
            faceRight = max(faceRight, max(b[0], b[3]) + half)
        }
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

    private fun drawQuestion(scope: DrawScope, pose: FacePose, u: Float, accent: Color, line: Stroke) {
        val question = pose[Channel.Question]
        if (question <= 0f) return
        val q = QUESTION_SIZE * u
        val dot = line.width * 0.6f
        val x = faceRight - q
        val y = faceTop - ACCENT_GAP * u - q * 2.1f - dot
        path.rewind()
        path.moveTo(x - q, y - q * 0.2f)
        path.cubicTo(x - q, y - q * 1.3f, x + q, y - q * 1.3f, x + q, y - q * 0.2f)
        path.cubicTo(x + q, y + q * 0.5f, x, y + q * 0.4f, x, y + q * 1.2f)
        scope.drawPath(path, accent, alpha = question, style = line)
        scope.drawCircle(accent, dot, Offset(x, y + q * 2.1f), alpha = question)
    }

    private fun drawSleepZ(scope: DrawScope, pose: FacePose, u: Float, accent: Color, line: Stroke) {
        val sleep = pose[Channel.SleepZ]
        if (sleep <= 0f) return
        val zLine = renderer.glyphStroke.get(line.width * SLEEP_STROKE)
        val baseX = faceRight - SLEEP_INSET * u
        val baseY = faceTop - ACCENT_GAP * u - zLine.width * 0.5f
        for (i in 0 until 3) {
            val alpha = (sleep * 3f - i).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val s = SLEEP_SIZE * u * (1f + 0.35f * i)
            val x = baseX + i * SLEEP_STEP_X * u
            val y = baseY - i * SLEEP_STEP_Y * u - s
            path.rewind()
            path.moveTo(x, y)
            path.lineTo(x + s, y)
            path.lineTo(x, y + s)
            path.lineTo(x + s, y + s)
            scope.drawPath(path, accent, alpha = alpha, style = zLine)
        }
    }

    // Little swirls and stars circling above the head; the back half of the orbit is dimmer.
    private fun drawDizzy(scope: DrawScope, pose: FacePose, u: Float, accent: Color, time: Float) {
        val dizzy = pose[Channel.Dizzy]
        if (dizzy <= 0f) return
        val centerX = (faceLeft + faceRight) * 0.5f
        val centerY = faceTop - DIZZY_RISE * u
        val rx = (faceRight - faceLeft) * DIZZY_ORBIT
        val ry = rx * DIZZY_FLATTEN
        val stroke = renderer.glyphStroke.get(DIZZY_STROKE * u)
        val r = DIZZY_SIZE * u
        for (k in 0 until DIZZY_ITEMS) {
            val angle = 2f * PI.toFloat() * (time * DIZZY_SPEED + k.toFloat() / DIZZY_ITEMS)
            val x = centerX + rx * cos(angle)
            val y = centerY + ry * sin(angle)
            val alpha = dizzy * (if (sin(angle) > 0f) 1f else DIZZY_BACK_ALPHA)
            if (k % 2 == 0) {
                scope.drawArc(accent, angle * 57.3f, 280f, false, Offset(x - r, y - r), Size(2f * r, 2f * r), alpha, stroke)
            } else {
                path.rewind()
                path.moveTo(x, y - r)
                path.quadraticTo(x, y, x + r, y)
                path.quadraticTo(x, y, x, y + r)
                path.quadraticTo(x, y, x - r, y)
                path.quadraticTo(x, y, x, y - r)
                path.close()
                scope.drawPath(path, Color.White, alpha = alpha)
            }
        }
    }

    private fun drawThinkDots(scope: DrawScope, pose: FacePose, u: Float, accent: Color) {
        val think = pose[Channel.ThinkDots]
        if (think <= 0f) return
        for (i in 0 until 3) {
            val alpha = (think * 3f - i).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val r = THINK_DOT * u * (1f + 0.4f * i)
            val x = faceLeft + i * THINK_STEP_X * u
            val y = faceTop - ACCENT_GAP * u - r - i * THINK_STEP_Y * u
            scope.drawCircle(accent, r, Offset(x, y), alpha = alpha)
        }
    }

    internal companion object {
        val SIDES = intArrayOf(-1, 1)
        val BLUSH_COLOR = Color(0xFFFF7A9C)
        val SWEAT_COLOR = Color(0xFF8FD3FF)
        val TONGUE_COLOR = Color(0xFFFF6F91)
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
        const val CAP_BULGE = 0.55f
        const val OUTLINE_SOFTEN = 0.35f
        const val INNER_MIN_OPEN = 0.08f
        const val INNER_FADE = 0.2f
        const val INNER_SPAN = 0.82f
        const val TONGUE_MIN_OPEN = 0.3f
        const val TONGUE_DEPTH = 0.32f
        const val BLUSH_WIDTH = 0.1f
        const val BLUSH_HEIGHT = 0.04f
        const val DIZZY_RISE = 0.07f
        const val DIZZY_ORBIT = 0.32f
        const val DIZZY_FLATTEN = 0.28f
        const val DIZZY_STROKE = 0.012f
        const val DIZZY_SIZE = 0.022f
        const val DIZZY_ITEMS = 4
        const val DIZZY_SPEED = 0.8f
        const val DIZZY_BACK_ALPHA = 0.45f
        const val BLUSH_OUTSET = 0.55f
        const val BLUSH_DROP = 0.045f
        const val BLUSH_ALPHA = 0.6f
        const val SWEAT_OUTSET = 0.035f
        const val SWEAT_SIZE = 0.07f
        const val SPARKLE_OUTSET = 0.03f
        const val SPARKLE_SIZE = 0.05f
        const val QUESTION_SIZE = 0.045f
        const val SLEEP_SIZE = 0.042f
        const val SLEEP_STEP_X = 0.075f
        const val SLEEP_INSET = 0.26f
        const val SLEEP_STROKE = 0.55f
        const val SLEEP_STEP_Y = 0.085f
        const val THINK_STEP_X = 0.05f
        const val THINK_STEP_Y = 0.03f
        const val ACCENT_GAP = 0.035f
        const val THINK_DOT = 0.017f
    }
}
