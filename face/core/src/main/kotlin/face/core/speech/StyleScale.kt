package face.core.speech

import face.api.SpeechStyle

// SpeechStyle turned into multipliers, once per utterance. Serious speech has no beats or quirks; funny speech smirks and laughs at the end.
internal class StyleScale {
    var energy = 0.5f
        private set
    var gain = 1f
        private set
    var beatRate = 1f
        private set
    var headBounce = 1f
        private set
    var springBoost = 1f
        private set
    var smirk = 0f
        private set
    var quirksAllowed = true
        private set
    var laughAtEnd = false
        private set

    fun set(style: SpeechStyle, config: MouthConfig) {
        val s = config.style
        val e = style.energy.coerceIn(0f, 1f)
        val serious = style.seriousness.coerceIn(0f, 1f)
        val playful = style.playfulness.coerceIn(0f, 1f)
        energy = e
        gain = (s.gainBase + s.gainEnergy * e) * (1f - s.seriousRestraint * serious)
        beatRate = (BEAT_BASE + e) * (1f - serious / s.seriousBeats).coerceAtLeast(0f)
        headBounce = (HEAD_BASE + HEAD_ENERGY * e) * (1f - HEAD_SERIOUS * serious)
        springBoost = config.springBoost.base + config.springBoost.energy * e
        smirk = if (playful > s.smirkPlayfulness && serious < HALF) s.smirk * playful else 0f
        quirksAllowed = serious < s.quirkSeriousness
        laughAtEnd = playful >= s.laughPlayfulness && serious < HALF
    }

    private companion object {
        const val BEAT_BASE = 0.5f
        const val HEAD_BASE = 0.4f
        const val HEAD_ENERGY = 1.2f
        const val HEAD_SERIOUS = 0.7f
        const val HALF = 0.5f
    }
}
