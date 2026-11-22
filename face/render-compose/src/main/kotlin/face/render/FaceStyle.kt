package face.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

public enum class MouthStyle { Stroke, Filled }

public enum class Backdrop { Dark, Glow }

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
    val browThickness: Float,
    val showBrows: Boolean = true,
    val mouthStyle: MouthStyle = MouthStyle.Filled,
    val backdrop: Backdrop = Backdrop.Glow,
    val accent: Color = Accents.Mint,
    val background: Color = Color(0xFF050507),
    val pupil: Color = Color(0xFF0B0B12),
) {
    public companion object {
        public val Orb: FaceStyle = FaceStyle(
            name = "Orb",
            eyeWidth = 0.15f, eyeAspect = 1.15f, eyeSpacing = 0.24f, eyeY = -0.08f, eyeExponent = 2.2f,
            pupilRatio = 0.48f, mouthWidth = 0.12f, mouthThickness = 0.036f, mouthY = 0.25f, browThickness = 0.026f,
        )
        public val SoftBlock: FaceStyle = FaceStyle(
            name = "Soft Block",
            eyeWidth = 0.16f, eyeAspect = 1.0f, eyeSpacing = 0.25f, eyeY = -0.08f, eyeExponent = 4.5f,
            pupilRatio = 0.42f, mouthWidth = 0.14f, mouthThickness = 0.04f, mouthY = 0.26f, browThickness = 0.03f,
        )
        public val Capsule: FaceStyle = FaceStyle(
            name = "Capsule",
            eyeWidth = 0.11f, eyeAspect = 1.9f, eyeSpacing = 0.19f, eyeY = -0.1f, eyeExponent = 2.6f,
            pupilRatio = 0.58f, mouthWidth = 0.11f, mouthThickness = 0.034f, mouthY = 0.3f, browThickness = 0.024f,
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
