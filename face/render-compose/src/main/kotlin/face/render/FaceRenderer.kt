package face.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.senses.FacePart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Draws a FacePose with no per-frame allocation: paths are rewound, brushes and strokes cached by key.
public class FaceRenderer {
    private val eyePath = Path()
    private val aperturePath = Path()
    private val lidLinePath = Path()
    private val chevronPath = Path()
    private val upperLid = FloatArray(LID_SAMPLES)
    private val lowerLid = FloatArray(LID_SAMPLES)
    internal val browLeft = FloatArray(BROW_PARAMS)
    internal val browRight = FloatArray(BROW_PARAMS)

    private var glowBrush: Brush? = null
    private var glowColor = Color.Unspecified
    internal val mouthStroke = StrokeCache()
    internal val outlineStroke = StrokeCache()
    private val eyeLineStroke = StrokeCache()
    internal val browStroke = StrokeCache()
    internal val glyphStroke = StrokeCache()

    internal val features = FaceFeatures(this)

    private var lastWidth = 0f
    private var lastHeight = 0f

    // time (seconds) only drives looping decorations such as the dizzy lines.
    public fun draw(scope: DrawScope, pose: FacePose, style: FaceStyle, time: Float = 0f): Unit = with(scope) {
        lastWidth = size.width
        lastHeight = size.height
        drawRect(style.background)
        val u = min(size.width, size.height)
        val cx = size.width / 2f + pose[Channel.HeadX] * HEAD_TRAVEL * size.width
        val cy = size.height / 2f - pose[Channel.HeadY] * HEAD_TRAVEL * size.height
        val accent = warned(tinted(style.accent, pose[Channel.TintShift]), pose[Channel.WarnTint])
        val squash = pose[Channel.HeadSquash] * SQUASH_AMOUNT
        val scale = pose[Channel.HeadScale]

        if (style.backdrop == Backdrop.Glow) {
            drawSoftGlow(cx, cy, BACKDROP_RADIUS * u * scale, accent, BACKDROP_ALPHA * (0.5f + pose[Channel.Glow]))
        }
        withTransform({
            rotate(pose[Channel.HeadTilt] * MAX_TILT_DEGREES, Offset(cx, cy))
            scale(scale * (1f - squash), scale * (1f + squash), Offset(cx, cy))
        }) {
            computeBrow(pose, style, u, cx, cy, -1, browLeft)
            computeBrow(pose, style, u, cx, cy, 1, browRight)
            for (side in SIDES) drawEyeGlow(pose, style, u, cx, cy, side, accent)
            for (side in SIDES) drawEye(pose, style, u, cx, cy, side, accent)
            features.drawMouth(this, pose, style, u, cx, cy, accent)
            if (style.showBrows) for (side in SIDES) features.drawBrow(this, style, u, if (side < 0) browLeft else browRight, accent)
            features.drawAccents(this, pose, style, u, cx, cy, accent, mouthStroke.get(style.mouthThickness * u), time)
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

    // Brow is a quadratic from inner to outer end with its control point at mid-x: [innerX, innerY, ctrlY, outerX, outerY].
    private fun computeBrow(pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, out: FloatArray) {
        val left = side < 0
        val bx = eyeCenterX(pose, style, u, cx, side)
        val a = eyeHalfWidth(pose, style, u, side)
        val top = eyeCenterY(pose, style, u, cy, side) - eyeBaseHalfHeight(pose, style, u, side)
        val by = top - a * (BROW_REST + BROW_TRAVEL * pose[if (left) Channel.BrowHeightL else Channel.BrowHeightR])
        val half = a * BROW_SPAN
        val tilt = pose[if (left) Channel.BrowTiltL else Channel.BrowTiltR] * BROW_TILT * half
        val curve = pose[if (left) Channel.BrowCurveL else Channel.BrowCurveR]
        out[0] = bx - side * half
        out[1] = by + tilt
        out[2] = by - (BROW_ARCH + curve * BROW_CURVE) * half
        out[3] = bx + side * half
        out[4] = by - tilt
    }

    private fun browYAt(brow: FloatArray, x: Float): Float {
        val t = ((x - brow[0]) / (brow[3] - brow[0])).coerceIn(0f, 1f)
        val s = 1f - t
        return s * s * brow[1] + 2f * t * s * brow[2] + t * t * brow[4]
    }

    private fun DrawScope.drawEye(pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, accent: Color) {
        val left = side < 0
        val open = pose[if (left) Channel.EyeOpenL else Channel.EyeOpenR]
        val ex = eyeCenterX(pose, style, u, cx, side)
        val ey = eyeCenterY(pose, style, u, cy, side)
        val a = eyeHalfWidth(pose, style, u, side)
        val b = eyeBaseHalfHeight(pose, style, u, side) * max(1f, open)
        val n = (style.eyeExponent * 2f.pow(-pose[Channel.EyeRoundness])).coerceIn(MIN_EXPONENT, MAX_EXPONENT)
        val smile = pose[if (left) Channel.EyeSmileL else Channel.EyeSmileR]
        val squint = pose[Channel.EyeSquint]

        buildSuperellipse(eyePath, ex, ey, a, b, n)
        val opening = buildLids(
            ex, ey, a, b, side, min(open, 1f),
            pose[if (left) Channel.UpperLidTiltL else Channel.UpperLidTiltR],
            pose[if (left) Channel.UpperLidCurveL else Channel.UpperLidCurveR],
            pose[if (left) Channel.LowerLidRaiseL else Channel.LowerLidRaiseR],
            smile, squint,
            if (style.showBrows) (if (left) browLeft else browRight) else null,
            style.browThickness * u * 0.5f,
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
                    drawCircle(Color.White, r * SPARK_SIZE, Offset(px + r * SPARK_OFFSET, py + r * SPARK_OFFSET), highlight * SPARK_ALPHA)
                }
            }
        }

        val closedness = 1f - (opening / (b * CLOSED_THRESHOLD)).coerceIn(0f, 1f)
        if (closedness <= 0f) return
        val morph = smile + squint
        val squintShare = if (morph > 0f) squint / morph else 0f
        val line = eyeLineStroke.get(style.mouthThickness * u * EYE_LINE_SCALE)
        if (squintShare < 1f) drawPath(lidLinePath, accent, alpha = closedness * (1f - squintShare), style = line)
        if (squintShare > 0f) {
            val h = min(a, b) * CHEVRON_HEIGHT
            chevronPath.rewind()
            chevronPath.moveTo(ex + side * a * CHEVRON_BACK, ey - h)
            chevronPath.lineTo(ex - side * a * CHEVRON_TIP, ey)
            chevronPath.lineTo(ex + side * a * CHEVRON_BACK, ey + h)
            drawPath(chevronPath, accent, alpha = closedness * squintShare, style = line)
        }
    }

    private fun DrawScope.drawEyeGlow(pose: FacePose, style: FaceStyle, u: Float, cx: Float, cy: Float, side: Int, accent: Color) {
        val glow = pose[Channel.Glow]
        if (glow <= 0f) return
        val left = side < 0
        val morph = max(pose[if (left) Channel.EyeSmileL else Channel.EyeSmileR], pose[Channel.EyeSquint])
        val visible = ((min(pose[if (left) Channel.EyeOpenL else Channel.EyeOpenR], 1f) -
            pose[if (left) Channel.LowerLidRaiseL else Channel.LowerLidRaiseR]) * (1f - morph)).coerceIn(0f, 1f)
        val radius = max(eyeHalfWidth(pose, style, u, side), eyeBaseHalfHeight(pose, style, u, side)) * GLOW_RADIUS
        drawSoftGlow(
            eyeCenterX(pose, style, u, cx, side), eyeCenterY(pose, style, u, cy, side), radius, accent,
            glow * GLOW_ALPHA * (GLOW_FLOOR + (1f - GLOW_FLOOR) * visible),
        )
    }

    private fun DrawScope.drawSoftGlow(x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        if (alpha <= 0f) return
        val brush = glowBrushFor(color)
        withTransform({
            translate(x, y)
            scale(radius, radius, Offset.Zero)
        }) {
            drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
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
        ex: Float, ey: Float, a: Float, b: Float, side: Int,
        open: Float, tilt: Float, curve: Float, raise: Float, smile: Float, squint: Float,
        brow: FloatArray?, browHalfThickness: Float,
    ): Float {
        val upperBase = -b + (1f - open) * 2f * b
        val morph = max(smile, squint)
        val total = smile + squint
        for (i in 0 until LID_SAMPLES) {
            val nx = -1f + 2f * i / (LID_SAMPLES - 1)
            val inner = nx * -side
            var upper = upperBase + tilt * TILT_DEPTH * b * inner + curve * CURVE_DEPTH * b * (nx * nx - CURVE_PIVOT)
            var lower = b - raise * RAISE_DEPTH * b * (1f - RAISE_CURVE * nx * nx)
            if (brow != null) upper = max(upper, browYAt(brow, ex + a * nx) - ey + browHalfThickness)
            if (morph > 0f) {
                val meet = smile / total * b * (SMILE_END - SMILE_ARCH * (1f - nx * nx))
                upper += (meet - upper) * morph
                lower += (meet - lower) * morph
            }
            upperLid[i] = min(upper, lower)
            lowerLid[i] = max(upper, lower)
        }
        aperturePath.rewind()
        for (i in 0 until LID_SAMPLES) {
            val x = ex + a * (-1f + 2f * i / (LID_SAMPLES - 1))
            if (i == 0) aperturePath.moveTo(x, ey + upperLid[i]) else aperturePath.lineTo(x, ey + upperLid[i])
        }
        for (i in LID_SAMPLES - 1 downTo 0) aperturePath.lineTo(ex + a * (-1f + 2f * i / (LID_SAMPLES - 1)), ey + lowerLid[i])
        aperturePath.close()

        lidLinePath.rewind()
        val sag = LID_LINE_SAG * b * (1f - morph)
        for (i in 0 until LID_SAMPLES) {
            val nx = -1f + 2f * i / (LID_SAMPLES - 1)
            val x = ex + a * LID_LINE_SPAN * nx
            val y = ey + (upperLid[i] + lowerLid[i]) * 0.5f + sag * (1f - nx * nx)
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

    // Low battery: the whole face fades towards the red accent.
    private fun warned(color: Color, warn: Float): Color {
        if (warn <= 0f) return color
        val w = warn.coerceAtMost(1f)
        return Color(
            color.red + (WARN_RED.red - color.red) * w,
            color.green + (WARN_RED.green - color.green) * w,
            color.blue + (WARN_RED.blue - color.blue) * w,
            color.alpha,
        )
    }

    // Maps a touch on the canvas to the face part under it, undoing the head transform.
    public fun hitTest(x: Float, y: Float, pose: FacePose, style: FaceStyle): FaceHit =
        hitTest(x, y, pose, style, lastWidth, lastHeight)

    internal fun hitTest(x: Float, y: Float, pose: FacePose, style: FaceStyle, w: Float, h: Float): FaceHit {
        if (w <= 0f || h <= 0f) return FaceHit(FacePart.Outside, 0f, 0f)
        val u = min(w, h)
        val cx = w / 2f + pose[Channel.HeadX] * HEAD_TRAVEL * w
        val cy = h / 2f - pose[Channel.HeadY] * HEAD_TRAVEL * h
        val lookX = ((x - cx) / (LOOK_SPAN * u)).coerceIn(-1f, 1f)
        val lookY = (-(y - cy) / (LOOK_SPAN * u)).coerceIn(-1f, 1f)
        val angle = -pose[Channel.HeadTilt] * MAX_TILT_DEGREES * (PI / 180.0).toFloat()
        val squash = pose[Channel.HeadSquash] * SQUASH_AMOUNT
        val scale = pose[Channel.HeadScale]
        val dx = x - cx
        val dy = y - cy
        val px = cx + (dx * cos(angle) - dy * sin(angle)) / (scale * (1f - squash))
        val py = cy + (dx * sin(angle) + dy * cos(angle)) / (scale * (1f + squash))
        var eyeTop = Float.MAX_VALUE
        for (side in SIDES) {
            val ex = eyeCenterX(pose, style, u, cx, side)
            val ey = eyeCenterY(pose, style, u, cy, side)
            val a = eyeHalfWidth(pose, style, u, side)
            val b = eyeBaseHalfHeight(pose, style, u, side) * max(1f, pose[if (side < 0) Channel.EyeOpenL else Channel.EyeOpenR])
            eyeTop = min(eyeTop, ey - b)
            val nx = (px - ex) / a
            val ny = (py - ey) / b
            if (nx * nx + ny * ny <= EYE_HIT) return FaceHit(if (side < 0) FacePart.LeftEye else FacePart.RightEye, lookX, lookY)
            val bx = ex + side * a * FaceFeatures.BLUSH_OUTSET
            val by = ey + eyeBaseHalfHeight(pose, style, u, side) + FaceFeatures.BLUSH_DROP * u
            val r = CHEEK_HIT * u
            if ((px - bx) * (px - bx) + (py - by) * (py - by) <= r * r) return FaceHit(FacePart.Cheek, lookX, lookY)
        }
        val my = cy + style.mouthY * u - pose[Channel.MouthY] * FaceFeatures.MOUTH_Y_TRAVEL * u
        val mw = style.mouthWidth * u * pose[Channel.MouthWidth] * MOUTH_HIT_WIDTH
        if (abs(px - cx) <= mw && abs(py - my) <= MOUTH_HIT_HEIGHT * u) return FaceHit(FacePart.Mouth, lookX, lookY)
        val halfFace = (style.eyeSpacing + style.eyeWidth * FACE_HIT_WIDTH) * u
        if (abs(px - cx) <= halfFace && py < eyeTop && py > eyeTop - FOREHEAD_HIT * u) return FaceHit(FacePart.Forehead, lookX, lookY)
        if (abs(px - cx) <= halfFace && py >= eyeTop - FOREHEAD_HIT * u && py <= my + CHIN_HIT * u) {
            return FaceHit(FacePart.Face, lookX, lookY)
        }
        return FaceHit(FacePart.Outside, lookX, lookY)
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
        val WARN_RED = Accents.Coral
        const val LOOK_SPAN = 0.45f
        const val EYE_HIT = 1.3f
        const val CHEEK_HIT = 0.07f
        const val MOUTH_HIT_WIDTH = 1.3f
        const val MOUTH_HIT_HEIGHT = 0.07f
        const val FACE_HIT_WIDTH = 1.4f
        const val FOREHEAD_HIT = 0.18f
        const val CHIN_HIT = 0.12f
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        const val EYE_SAMPLES = 64
        const val LID_SAMPLES = 25
        const val BROW_PARAMS = 5
        const val MIN_EXPONENT = 2f
        const val MAX_EXPONENT = 8f
        const val HEAD_TRAVEL = 0.5f
        const val MAX_TILT_DEGREES = 45f
        const val SQUASH_AMOUNT = 0.4f
        const val SPACING_RANGE = 0.3f
        const val EYE_Y_TRAVEL = 0.08f
        const val ASYMMETRY_TRAVEL = 0.04f
        const val TILT_DEPTH = 0.5f
        const val CURVE_DEPTH = 0.35f
        const val CURVE_PIVOT = 0.35f
        const val RAISE_DEPTH = 1.6f
        const val RAISE_CURVE = 0.7f
        const val SMILE_END = 0.3f
        const val SMILE_ARCH = 0.85f
        const val CHEVRON_HEIGHT = 0.6f
        const val CHEVRON_TIP = 0.6f
        const val CHEVRON_BACK = 0.7f
        const val BROW_REST = 0.55f
        const val BROW_TRAVEL = 0.7f
        const val BROW_SPAN = 1.25f
        const val BROW_TILT = 0.4f
        const val BROW_ARCH = 0.12f
        const val BROW_CURVE = 0.3f
        const val CLOSED_THRESHOLD = 0.15f
        const val EYE_LINE_SCALE = 1.1f
        const val LID_LINE_SPAN = 0.9f
        const val LID_LINE_SAG = 0.25f
        const val GLOW_RADIUS = 1.9f
        const val GLOW_ALPHA = 0.7f
        const val GLOW_FLOOR = 0.3f
        const val BACKDROP_RADIUS = 0.95f
        const val BACKDROP_ALPHA = 0.16f
        const val HIGHLIGHT_SIZE = 0.3f
        const val HIGHLIGHT_OFFSET = 0.35f
        const val SPARK_SIZE = 0.13f
        const val SPARK_OFFSET = 0.32f
        const val SPARK_ALPHA = 0.8f
        const val MAX_TINT_DEGREES = 120f
    }
}

internal class StrokeCache {
    private var stroke: Stroke? = null
    private var width = -1f

    fun get(width: Float): Stroke {
        val cached = stroke
        if (cached != null && this.width == width) return cached
        return Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round).also {
            stroke = it
            this.width = width
        }
    }
}
