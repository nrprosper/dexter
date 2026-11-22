package face.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Draws a FacePose with no per-frame allocation: paths are rewound, brushes and strokes cached by key.
public class FaceRenderer {
    private val eyePath = Path()
    private val aperturePath = Path()
    private val lidLinePath = Path()
    private val upperLid = FloatArray(LID_SAMPLES)
    private val lowerLid = FloatArray(LID_SAMPLES)

    private var glowBrush: Brush? = null
    private var glowColor = Color.Unspecified
    private var stroke: Stroke? = null
    private var strokeWidth = -1f

    internal val features = FaceFeatures(this)

    public fun draw(scope: DrawScope, pose: FacePose, style: FaceStyle): Unit = with(scope) {
        drawRect(style.background)
        val u = min(size.width, size.height)
        val cx = size.width / 2f + pose[Channel.HeadX] * HEAD_TRAVEL * u
        val cy = size.height / 2f - pose[Channel.HeadY] * HEAD_TRAVEL * u
        val accent = tinted(style.accent, pose[Channel.TintShift])
        val squash = pose[Channel.HeadSquash] * SQUASH_AMOUNT
        val scale = pose[Channel.HeadScale]
        val line = strokeFor(style.mouthThickness * u)

        withTransform({
            rotate(pose[Channel.HeadTilt] * MAX_TILT_DEGREES, Offset(cx, cy))
            scale(scale * (1f - squash), scale * (1f + squash), Offset(cx, cy))
        }) {
            for (side in SIDES) drawEyeGlow(pose, style, u, cx, cy, side, accent)
            for (side in SIDES) drawEye(pose, style, u, cx, cy, side, accent, line)
            features.drawMouth(this, pose, style, u, cx, cy, accent, line)
            if (style.showBrows) for (side in SIDES) features.drawBrow(this, pose, style, u, cx, cy, side, accent, line)
            features.drawAccents(this, pose, style, u, cx, cy, accent, line)
        }
    }

    internal fun eyeCenterX(pose: FacePose, style: FaceStyle, u: Float, cx: Float, side: Int): Float =
        cx + side * style.eyeSpacing * u * (1f + SPACING_RANGE * pose[Channel.EyeSpacing])

    internal fun eyeCenterY(pose: FacePose, style: FaceStyle, u: Float, cy: Float, side: Int): Float =
        cy + style.eyeY * u - pose[Channel.EyeY] * EYE_Y_TRAVEL * u - side * pose[Channel.EyeAsymmetry] * ASYMMETRY_TRAVEL * u

    internal fun eyeHalfWidth(pose: FacePose, style: FaceStyle, u: Float, side: Int): Float =
        style.eyeWidth * u * pose[if (side < 0) Channel.EyeWidthL else Channel.EyeWidthR]

    internal fun eyeBaseHalfHeight(pose: FacePose, style: FaceStyle, u: Float, side: Int): Float =
        style.eyeWidth * u * style.eyeAspect * pose[if (side < 0) Channel.EyeHeightL else Channel.EyeHeightR]

    private fun DrawScope.drawEye(
        pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, accent: Color, line: Stroke,
    ) {
        val left = side < 0
        val open = pose[if (left) Channel.EyeOpenL else Channel.EyeOpenR]
        val ex = eyeCenterX(pose, style, u, cx, side)
        val ey = eyeCenterY(pose, style, u, cy, side)
        val a = eyeHalfWidth(pose, style, u, side)
        val b = eyeBaseHalfHeight(pose, style, u, side) * max(1f, open)
        val n = (style.eyeExponent * 2f.pow(-pose[Channel.EyeRoundness])).coerceIn(MIN_EXPONENT, MAX_EXPONENT)

        buildSuperellipse(eyePath, ex, ey, a, b, n)
        val opening = buildLids(
            pose, ex, ey, a, b, side, min(open, 1f),
            pose[if (left) Channel.UpperLidTiltL else Channel.UpperLidTiltR],
            pose[if (left) Channel.UpperLidCurveL else Channel.UpperLidCurveR],
            pose[if (left) Channel.LowerLidRaiseL else Channel.LowerLidRaiseR],
        )

        clipPath(eyePath) {
            drawPath(aperturePath, accent)
            clipPath(aperturePath) {
                val r = EyeLayout.pupilRadius(a, b, style.pupilRatio, pose[Channel.PupilSize])
                val px = ex + EyeLayout.pupilOffsetX(a, b, r, pose[Channel.GazeX])
                val py = ey + EyeLayout.pupilOffsetY(a, b, r, pose[Channel.GazeY])
                drawCircle(style.pupil, r, Offset(px, py))
                val highlight = pose[Channel.HighlightIntensity]
                if (highlight > 0f) {
                    drawCircle(Color.White, r * HIGHLIGHT_SIZE, Offset(px - r * HIGHLIGHT_OFFSET, py - r * HIGHLIGHT_OFFSET), highlight)
                }
            }
        }

        val closedness = 1f - (opening / (b * CLOSED_THRESHOLD)).coerceIn(0f, 1f)
        if (closedness > 0f) drawPath(lidLinePath, accent, alpha = closedness, style = line)
    }

    private fun DrawScope.drawEyeGlow(pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, accent: Color) {
        val glow = pose[Channel.Glow]
        if (glow <= 0f) return
        val x = eyeCenterX(pose, style, u, cx, side)
        val y = eyeCenterY(pose, style, u, cy, side)
        val radius = max(eyeHalfWidth(pose, style, u, side), eyeBaseHalfHeight(pose, style, u, side)) * GLOW_RADIUS
        val brush = glowBrushFor(accent)
        withTransform({
            translate(x, y)
            scale(radius, radius, Offset.Zero)
        }) {
            drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = glow * GLOW_ALPHA)
        }
    }

    private fun buildSuperellipse(path: Path, x: Float, y: Float, a: Float, b: Float, n: Float) {
        path.rewind()
        for (i in 0 until EYE_SAMPLES) {
            val t = (i.toFloat() / EYE_SAMPLES) * TWO_PI
            val px = x + EyeLayout.superellipseX(a, n, t)
            val py = y + EyeLayout.superellipseY(b, n, t)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    // Returns the vertical opening at the eye's centre; fills aperturePath and lidLinePath.
    private fun buildLids(
        pose: FacePose, ex: Float, ey: Float, a: Float, b: Float, side: Int,
        open: Float, tilt: Float, curve: Float, raise: Float,
    ): Float {
        val upperBase = -b + (1f - open) * 2f * b
        for (i in 0 until LID_SAMPLES) {
            val nx = -1f + 2f * i / (LID_SAMPLES - 1)
            val inner = nx * -side
            val upper = upperBase + tilt * TILT_DEPTH * b * inner + curve * CURVE_DEPTH * b * (nx * nx - CURVE_PIVOT)
            val lower = b - raise * RAISE_DEPTH * b * (1f - RAISE_CURVE * nx * nx)
            upperLid[i] = min(upper, lower)
            lowerLid[i] = max(upper, lower)
        }
        aperturePath.rewind()
        lidLinePath.rewind()
        for (i in 0 until LID_SAMPLES) {
            val x = ex + a * (-1f + 2f * i / (LID_SAMPLES - 1))
            if (i == 0) aperturePath.moveTo(x, ey + upperLid[i]) else aperturePath.lineTo(x, ey + upperLid[i])
        }
        for (i in LID_SAMPLES - 1 downTo 0) aperturePath.lineTo(ex + a * (-1f + 2f * i / (LID_SAMPLES - 1)), ey + lowerLid[i])
        aperturePath.close()

        for (i in 0 until LID_SAMPLES) {
            val nx = -1f + 2f * i / (LID_SAMPLES - 1)
            val x = ex + a * LID_LINE_SPAN * nx
            val y = ey + upperLid[i] + LID_LINE_SAG * b * (1f - nx * nx)
            if (i == 0) lidLinePath.moveTo(x, y) else lidLinePath.lineTo(x, y)
        }
        val mid = LID_SAMPLES / 2
        return lowerLid[mid] - upperLid[mid]
    }

    private fun glowBrushFor(color: Color): Brush {
        val cached = glowBrush
        if (cached != null && glowColor == color) return cached
        return Brush.radialGradient(listOf(color, Color.Transparent), center = Offset.Zero, radius = 1f).also {
            glowBrush = it
            glowColor = color
        }
    }

    internal fun strokeFor(width: Float): Stroke {
        val cached = stroke
        if (cached != null && strokeWidth == width) return cached
        return Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round).also {
            stroke = it
            strokeWidth = width
        }
    }

    private fun tinted(color: Color, shift: Float): Color {
        if (shift <= 0f) return color
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        if (delta == 0f) return color
        val hue = when (maxC) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        val shifted = ((hue + shift * MAX_TINT_DEGREES) % 360f + 360f) % 360f
        return Color.hsv(shifted, delta / maxC, maxC, color.alpha)
    }

    private companion object {
        val SIDES = intArrayOf(-1, 1)
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        const val EYE_SAMPLES = 64
        const val LID_SAMPLES = 17
        const val MIN_EXPONENT = 2f
        const val MAX_EXPONENT = 8f
        const val HEAD_TRAVEL = 0.08f
        const val MAX_TILT_DEGREES = 15f
        const val SQUASH_AMOUNT = 0.25f
        const val SPACING_RANGE = 0.25f
        const val EYE_Y_TRAVEL = 0.06f
        const val ASYMMETRY_TRAVEL = 0.03f
        const val TILT_DEPTH = 0.45f
        const val CURVE_DEPTH = 0.35f
        const val CURVE_PIVOT = 0.35f
        const val RAISE_DEPTH = 1.6f
        const val RAISE_CURVE = 0.7f
        const val CLOSED_THRESHOLD = 0.12f
        const val LID_LINE_SPAN = 0.85f
        const val LID_LINE_SAG = 0.25f
        const val GLOW_RADIUS = 1.9f
        const val GLOW_ALPHA = 0.7f
        const val HIGHLIGHT_SIZE = 0.3f
        const val HIGHLIGHT_OFFSET = 0.35f
        const val MAX_TINT_DEGREES = 120f
    }
}
