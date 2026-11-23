package com.vlmis.dexter.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// Small line icons drawn on a 24-unit grid, so the settings screen needs no icon library.
internal enum class GlassIcon { Key, Spark, Speaker, Wave, Shield, Download, Chevron, Back, Check, Play, Brain, Face, Mic }

@Composable
internal fun GlassIconView(icon: GlassIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(vararg pts: Pair<Float, Float>) {
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
            drawPath(path, color, style = stroke)
        }
        when (icon) {
            GlassIcon.Key -> {
                drawCircle(color, 4.5f * u, p(8f, 12f), style = stroke)
                line(12.5f to 12f, 21f to 12f)
                line(18f to 12f, 18f to 15.5f)
                line(15f to 12f, 15f to 14.5f)
            }
            GlassIcon.Spark -> {
                line(12f to 3f, 13.8f to 10.2f, 21f to 12f, 13.8f to 13.8f, 12f to 21f, 10.2f to 13.8f, 3f to 12f, 10.2f to 10.2f, 12f to 3f)
            }
            GlassIcon.Brain -> {
                drawCircle(color, 3.2f * u, p(8.5f, 9f), style = stroke)
                drawCircle(color, 3.2f * u, p(15.5f, 9f), style = stroke)
                drawCircle(color, 3.2f * u, p(8.5f, 15f), style = stroke)
                drawCircle(color, 3.2f * u, p(15.5f, 15f), style = stroke)
            }
            GlassIcon.Speaker -> {
                line(4f to 9.5f, 8f to 9.5f, 13f to 5f, 13f to 19f, 8f to 14.5f, 4f to 14.5f, 4f to 9.5f)
                drawArc(color, -45f, 90f, false, p(12f, 8.5f), Size(7f * u, 7f * u), style = stroke)
            }
            GlassIcon.Wave -> {
                for ((x, h) in listOf(5f to 4f, 9f to 10f, 13f to 14f, 17f to 8f, 21f to 4f)) line(x - 1f to 12f - h / 2f, x - 1f to 12f + h / 2f)
            }
            GlassIcon.Shield -> {
                line(12f to 3f, 20f to 6f, 20f to 12f, 12f to 21f, 4f to 12f, 4f to 6f, 12f to 3f)
                line(8.5f to 12f, 11f to 14.5f, 15.5f to 9.5f)
            }
            GlassIcon.Download -> {
                line(12f to 4f, 12f to 15f)
                line(7.5f to 10.5f, 12f to 15f, 16.5f to 10.5f)
                line(5f to 19.5f, 19f to 19.5f)
            }
            GlassIcon.Chevron -> line(9f to 5f, 16f to 12f, 9f to 19f)
            GlassIcon.Back -> line(15f to 5f, 8f to 12f, 15f to 19f)
            GlassIcon.Check -> line(5f to 12.5f, 10f to 17.5f, 19f to 7f)
            GlassIcon.Play -> filledTriangle(color, u)
            GlassIcon.Mic -> {
                drawRoundRect(color, p(9f, 3f), Size(6f * u, 11f * u), androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke)
                drawArc(color, 0f, 180f, false, p(5.5f, 7.5f), Size(13f * u, 10f * u), style = stroke)
                line(12f to 17.5f, 12f to 21f)
            }
            GlassIcon.Face -> {
                drawCircle(color, 9f * u, p(12f, 12f), style = stroke)
                drawCircle(color, 1.5f * u, p(9f, 10f))
                drawCircle(color, 1.5f * u, p(15f, 10f))
                drawArc(color, 20f, 140f, false, p(8f, 10f), Size(8f * u, 6f * u), style = stroke)
            }
        }
    }
}

private fun DrawScope.filledTriangle(color: Color, u: Float) {
    val path = Path().apply {
        moveTo(8f * u, 5f * u)
        lineTo(19f * u, 12f * u)
        lineTo(8f * u, 19f * u)
        close()
    }
    drawPath(path, color)
}
