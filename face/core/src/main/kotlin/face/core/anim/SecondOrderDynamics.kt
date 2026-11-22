package face.core.anim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

// Per-channel second-order systems (f, ζ, r) stored as flat arrays. Each frame is split into fixed
// sub-steps of at most MAX_SUBSTEP so motion is the same at 30, 60 and 120 Hz; poles are matched for stability.
internal class SecondOrderDynamics(private val count: Int) {
    val y = FloatArray(count)
    private val v = FloatArray(count)
    private val xPrev = FloatArray(count)
    private val k1 = FloatArray(count)
    private val k2 = FloatArray(count)
    private val k3 = FloatArray(count)
    private val omega = FloatArray(count)
    private val zeta = FloatArray(count)
    private val damped = FloatArray(count)

    fun configure(index: Int, profile: DynamicsProfile) {
        val w = 2f * PI.toFloat() * profile.frequency
        val z = profile.damping
        omega[index] = w
        zeta[index] = z
        damped[index] = w * sqrt(abs(z * z - 1f))
        k1[index] = z / (PI.toFloat() * profile.frequency)
        k2[index] = 1f / (w * w)
        k3[index] = profile.response * z / w
    }

    fun reset(values: FloatArray) {
        values.copyInto(y)
        values.copyInto(xPrev)
        v.fill(0f)
    }

    fun stopMotion() {
        v.fill(0f)
    }

    fun step(target: FloatArray, dt: Float) {
        if (dt <= 0f) return
        val substeps = max(1, ceil(dt / MAX_SUBSTEP - SUBSTEP_EPSILON).toInt())
        val h = dt / substeps
        for (i in 0 until count) {
            val w = omega[i]
            val z = zeta[i]
            val k1s: Float
            val k2s: Float
            if (w * h < z) {
                k1s = k1[i]
                k2s = max(k2[i], max(h * h / 2f + h * k1s / 2f, h * k1s))
            } else {
                val t1 = exp(-z * w * h)
                val alpha = 2f * t1 * (if (z <= 1f) cos(h * damped[i]) else cosh(h * damped[i]))
                val beta = t1 * t1
                val t2 = h / (1f + beta - alpha)
                k1s = (1f - beta) * t2
                k2s = h * t2
            }
            val x = target[i]
            val xd = (x - xPrev[i]) / h
            var pos = y[i]
            var vel = v[i]
            for (s in 0 until substeps) {
                pos += h * vel
                vel += h * (x + (if (s == 0) k3[i] * xd else 0f) - pos - k1s * vel) / k2s
            }
            y[i] = pos
            v[i] = vel
            xPrev[i] = x
        }
    }

    private companion object {
        const val MAX_SUBSTEP = 1f / 240f
        const val SUBSTEP_EPSILON = 1e-3f
    }
}
