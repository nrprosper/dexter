package face.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

internal object EyeLayout {
    private const val MAX_PUPIL_FILL = 0.9f

    fun pupilRadius(halfWidth: Float, halfHeight: Float, ratio: Float, size: Float): Float {
        val limit = min(halfWidth, halfHeight)
        return min(limit * ratio * size, limit * MAX_PUPIL_FILL)
    }

    // Travel on the eye ellipse scaled by (1 - r/min(a, b)): by convexity the pupil disc then stays inside any n >= 2 eye.
    fun pupilOffsetX(halfWidth: Float, halfHeight: Float, radius: Float, gazeX: Float): Float =
        gazeX * halfWidth * travelScale(halfWidth, halfHeight, radius)

    fun pupilOffsetY(halfWidth: Float, halfHeight: Float, radius: Float, gazeY: Float): Float =
        -gazeY * halfHeight * travelScale(halfWidth, halfHeight, radius)

    private fun travelScale(halfWidth: Float, halfHeight: Float, radius: Float): Float =
        1f - radius / min(halfWidth, halfHeight)

    fun superellipseX(halfWidth: Float, exponent: Float, t: Float): Float {
        val c = cos(t)
        return halfWidth * sign(c) * abs(c).pow(2f / exponent)
    }

    fun superellipseY(halfHeight: Float, exponent: Float, t: Float): Float {
        val s = sin(t)
        return halfHeight * sign(s) * abs(s).pow(2f / exponent)
    }

    fun insideSuperellipse(x: Float, y: Float, halfWidth: Float, halfHeight: Float, exponent: Float): Boolean =
        abs(x / halfWidth).pow(exponent) + abs(y / halfHeight).pow(exponent) <= 1f + 1e-4f
}
