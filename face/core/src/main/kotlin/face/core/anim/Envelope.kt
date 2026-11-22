package face.core.anim

// Authored attack/hold/release envelope in 0..1 with smoothstep edges.
internal object Envelope {
    fun value(t: Float, attack: Float, hold: Float, release: Float): Float = when {
        t <= 0f -> 0f
        t < attack -> smooth(t / attack)
        t < attack + hold -> 1f
        t < attack + hold + release -> 1f - smooth((t - attack - hold) / release)
        else -> 0f
    }

    fun duration(attack: Float, hold: Float, release: Float): Float = attack + hold + release

    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)
}
