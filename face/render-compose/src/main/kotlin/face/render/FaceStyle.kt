package face.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Lengths are fractions of the canvas' shorter side; eye and mouth widths are half-widths.
@Immutable
public data class FaceStyle(
    val name: String,
    val eyeWidth: Float,
    val eyeAspect: Float,
    val eyeSpacing: Float,
    val eyeY: Float,
    val eyeExponent: Float,
    val pupilRatio: Float,
    val mouthWidth: Float,
    val mouthThickness: Float,
    val mouthY: Float,
    val showBrows: Boolean = false,
    val accent: Color = Accents.Amber,
    val background: Color = Color(0xFF050507),
    val pupil: Color = Color(0xFF0B0B12),
) {
    public companion object {
        public val Orb: FaceStyle = FaceStyle(
            name = "Orb",
            eyeWidth = 0.12f, eyeAspect = 1.0f, eyeSpacing = 0.2f, eyeY = -0.05f, eyeExponent = 2.2f,
            pupilRatio = 0.5f, mouthWidth = 0.065f, mouthThickness = 0.02f, mouthY = 0.17f,
        )
        public val SoftBlock: FaceStyle = FaceStyle(
            name = "Soft Block",
            eyeWidth = 0.13f, eyeAspect = 0.8f, eyeSpacing = 0.22f, eyeY = -0.05f, eyeExponent = 4.5f,
            pupilRatio = 0.42f, mouthWidth = 0.09f, mouthThickness = 0.024f, mouthY = 0.17f,
        )
        public val Capsule: FaceStyle = FaceStyle(
            name = "Capsule",
            eyeWidth = 0.075f, eyeAspect = 1.65f, eyeSpacing = 0.15f, eyeY = -0.06f, eyeExponent = 2.6f,
            pupilRatio = 0.6f, mouthWidth = 0.06f, mouthThickness = 0.02f, mouthY = 0.18f,
        )
        public val Silhouettes: List<FaceStyle> = listOf(Orb, SoftBlock, Capsule)
        public val Default: FaceStyle = Orb
    }
}

public object Accents {
    public val Amber: Color = Color(0xFFFFB547)
    public val Mint: Color = Color(0xFF5CF2C2)
    public val Cyan: Color = Color(0xFF4CC9F0)
    public val Coral: Color = Color(0xFFFF6B6B)
    public val Candidates: List<Color> = listOf(Amber, Mint, Cyan, Coral)
}
