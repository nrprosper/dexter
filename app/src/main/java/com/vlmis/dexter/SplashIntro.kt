package com.vlmis.dexter

import android.app.Activity
import face.render.FaceIntro
import face.render.FaceStyle
import kotlin.math.min

// Where the splash face's eyes are, so the live face can start exactly there and grow into place.
internal object SplashIntro {
    fun forWindow(activity: Activity): FaceIntro {
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val u = min(w, h)
        val style = FaceStyle.Default
        val unit = SPLASH_ICON_DP / ICON_CANVAS * activity.resources.displayMetrics.density
        val scale = (ICON_EYE_GAP * unit) / (2f * style.eyeSpacing * u)
        val splashEyeY = (ICON_EYE_Y - ICON_CANVAS / 2f) * unit
        val faceEyeY = style.eyeY * u * scale
        return FaceIntro(startScale = scale, startOffsetY = splashEyeY - faceEyeY)
    }

    // Android draws a splash icon without a background at 288dp for the 108-unit adaptive canvas.
    private const val SPLASH_ICON_DP = 288f

    // Must match the geometry generated into drawable/ic_launcher_foreground.xml.
    private const val ICON_CANVAS = 108f
    private const val ICON_EYE_GAP = 31.68f
    private const val ICON_EYE_Y = 51.72f
}
