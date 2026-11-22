package face.core.time

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// SplitMix64: tiny, fast and fully reproducible for a given seed.
public class Rng(seed: Long) {
    private var state: Long = seed

    public fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    public fun nextFloat(): Float = (nextLong() ushr 40).toFloat() * FLOAT_UNIT

    public fun nextFloat(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    // Box-Muller; uses two uniforms per call so the sequence stays easy to reason about.
    public fun nextGaussian(): Float {
        val u1 = nextFloat().coerceAtLeast(MIN_UNIFORM)
        val u2 = nextFloat()
        return sqrt(-2f * ln(u1)) * cos(2f * PI.toFloat() * u2)
    }

    // Log-normal with the given arithmetic mean: a typical value with a long tail, like real timing.
    public fun nextLogNormal(mean: Float, sigma: Float): Float {
        val mu = ln(mean) - sigma * sigma / 2f
        return exp(mu + sigma * nextGaussian())
    }

    public fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 33) % bound).toInt()
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val FLOAT_UNIT = 1f / (1 shl 24)
        const val MIN_UNIFORM = 1e-7f
    }
}
