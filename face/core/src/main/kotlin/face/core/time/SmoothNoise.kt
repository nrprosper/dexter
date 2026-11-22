package face.core.time

// One-dimensional value noise in -1..1: random points at a fixed rate joined by smoothstep.
internal class SmoothNoise(private val rng: Rng, private val frequency: Float) {
    private var from = rng.nextFloat(-1f, 1f)
    private var to = rng.nextFloat(-1f, 1f)
    private var t = 0f

    fun next(dt: Float): Float {
        t += dt * frequency
        while (t >= 1f) {
            t -= 1f
            from = to
            to = rng.nextFloat(-1f, 1f)
        }
        val s = t * t * (3f - 2f * t)
        return from + (to - from) * s
    }
}
