package face.core.rig

public enum class ChannelGroup { EyeShape, Lids, EyePlacement, Pupils, Brows, Mouth, Head, Accents, Look }

// Vertical channels are positive-up, L/R mean screen-left/right, tilt is positive clockwise.
public enum class Channel(
    public val group: ChannelGroup,
    public val min: Float,
    public val max: Float,
    public val rest: Float,
) {
    EyeOpenL(ChannelGroup.EyeShape, 0f, 1.3f, 1f),
    EyeOpenR(ChannelGroup.EyeShape, 0f, 1.3f, 1f),
    EyeWidthL(ChannelGroup.EyeShape, 0.5f, 1.5f, 1f),
    EyeWidthR(ChannelGroup.EyeShape, 0.5f, 1.5f, 1f),
    EyeHeightL(ChannelGroup.EyeShape, 0.5f, 1.5f, 1f),
    EyeHeightR(ChannelGroup.EyeShape, 0.5f, 1.5f, 1f),
    EyeRoundness(ChannelGroup.EyeShape, -1f, 1f, 0f),

    UpperLidTiltL(ChannelGroup.Lids, -1f, 1f, 0f),
    UpperLidTiltR(ChannelGroup.Lids, -1f, 1f, 0f),
    UpperLidCurveL(ChannelGroup.Lids, -1f, 1f, 0f),
    UpperLidCurveR(ChannelGroup.Lids, -1f, 1f, 0f),
    LowerLidRaiseL(ChannelGroup.Lids, 0f, 1f, 0f),
    LowerLidRaiseR(ChannelGroup.Lids, 0f, 1f, 0f),

    EyeSpacing(ChannelGroup.EyePlacement, -1f, 1f, 0f),
    EyeY(ChannelGroup.EyePlacement, -1f, 1f, 0f),
    EyeAsymmetry(ChannelGroup.EyePlacement, -1f, 1f, 0f),

    GazeX(ChannelGroup.Pupils, -1f, 1f, 0f),
    GazeY(ChannelGroup.Pupils, -1f, 1f, 0f),
    PupilSize(ChannelGroup.Pupils, 0.5f, 1.5f, 1f),
    HighlightIntensity(ChannelGroup.Pupils, 0f, 1f, 1f),

    BrowHeightL(ChannelGroup.Brows, -1f, 1f, 0f),
    BrowHeightR(ChannelGroup.Brows, -1f, 1f, 0f),
    BrowTiltL(ChannelGroup.Brows, -1f, 1f, 0f),
    BrowTiltR(ChannelGroup.Brows, -1f, 1f, 0f),
    BrowCurveL(ChannelGroup.Brows, -1f, 1f, 0f),
    BrowCurveR(ChannelGroup.Brows, -1f, 1f, 0f),

    MouthCurve(ChannelGroup.Mouth, -1f, 1f, 0f),
    MouthOpen(ChannelGroup.Mouth, 0f, 1f, 0f),
    MouthWidth(ChannelGroup.Mouth, 0.5f, 1.5f, 1f),
    MouthRoundness(ChannelGroup.Mouth, 0f, 1f, 0f),
    MouthAsymmetry(ChannelGroup.Mouth, -1f, 1f, 0f),
    MouthY(ChannelGroup.Mouth, -1f, 1f, 0f),

    HeadX(ChannelGroup.Head, -1f, 1f, 0f),
    HeadY(ChannelGroup.Head, -1f, 1f, 0f),
    HeadTilt(ChannelGroup.Head, -1f, 1f, 0f),
    HeadScale(ChannelGroup.Head, 0.8f, 1.2f, 1f),
    HeadSquash(ChannelGroup.Head, -1f, 1f, 0f),

    Blush(ChannelGroup.Accents, 0f, 1f, 0f),
    Sweat(ChannelGroup.Accents, 0f, 1f, 0f),
    Sparkle(ChannelGroup.Accents, 0f, 1f, 0f),
    Question(ChannelGroup.Accents, 0f, 1f, 0f),
    SleepZ(ChannelGroup.Accents, 0f, 1f, 0f),
    ThinkDots(ChannelGroup.Accents, 0f, 1f, 0f),

    Glow(ChannelGroup.Look, 0f, 1f, 0.5f),
    TintShift(ChannelGroup.Look, 0f, 1f, 0f),
    ;

    public val key: String = name.replaceFirstChar { it.lowercaseChar() }

    public fun clamp(value: Float): Float = value.coerceIn(min, max)

    public companion object {
        public val count: Int = entries.size
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): Channel? = byKey[key]
    }
}
