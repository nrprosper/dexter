package face.core.time

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

    public fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 33) % bound).toInt()
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val FLOAT_UNIT = 1f / (1 shl 24)
    }
}
