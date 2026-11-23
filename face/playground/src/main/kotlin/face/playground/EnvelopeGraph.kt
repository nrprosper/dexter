package face.playground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import face.core.FaceEngine
import face.core.rig.Channel

// The last few seconds of speech: raw track loudness, the shaped envelope, the gated mouth opening and the pose's
// MouthOpen, with the gate as a dashed line. Bars on the right show the mouth channels and beats right now.
// History is written once per frame; only the draw phase reads it.
@Composable
internal fun EnvelopeGraph(engine: FaceEngine, modifier: Modifier = Modifier) {
    val history = remember { History(HISTORY) }
    val tick = remember { mutableIntStateOf(0) }
    val paths = remember { Array(SERIES.size) { Path() } }
    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos {
                val p = engine.speechProbe
                history.push(p.level, p.envelope, p.open, engine.pose[Channel.MouthOpen])
                tick.intValue++
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            tick.intValue
            drawRect(BACKGROUND)
            val graphWidth = size.width - BARS_WIDTH.toPx()
            val gate = engine.mouthConfig.gate
            val gateY = size.height * (1f - gate)
            drawLine(GATE, Offset(0f, gateY), Offset(graphWidth, gateY), 1.dp.toPx(), pathEffect = DASH)
            for (s in SERIES.indices) {
                val path = paths[s]
                path.reset()
                for (i in 0 until history.size) {
                    val x = graphWidth * i / (history.size - 1)
                    val y = size.height * (1f - history.at(s, i).coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, SERIES[s], style = Stroke(width = 1.5.dp.toPx()))
            }
            bars(engine, graphWidth)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, name) in LABELS.withIndex()) Legend(name, SERIES[i])
            Legend("gate", GATE)
        }
        Text("bars: open · width · round · emphasis · beat", style = MaterialTheme.typography.labelSmall)
    }
}

private fun DrawScope.bars(engine: FaceEngine, left: Float) {
    val pose = engine.pose
    val probe = engine.speechProbe
    val values = floatArrayOf(
        pose[Channel.MouthOpen],
        (pose[Channel.MouthWidth] - Channel.MouthWidth.min) / (Channel.MouthWidth.max - Channel.MouthWidth.min),
        pose[Channel.MouthRoundness],
        probe.emphasis,
        probe.beat,
    )
    val slot = (size.width - left) / values.size
    for (i in values.indices) {
        val h = size.height * values[i].coerceIn(0f, 1f)
        drawRect(BAR, Offset(left + slot * i + slot * 0.2f, size.height - h), androidx.compose.ui.geometry.Size(slot * 0.6f, h))
    }
}

@Composable
private fun Legend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

private class History(val size: Int) {
    private val data = Array(SERIES.size) { FloatArray(size) }
    private var head = 0

    fun push(vararg values: Float) {
        for (s in values.indices) data[s][head] = values[s]
        head = (head + 1) % size
    }

    fun at(series: Int, i: Int): Float = data[series][(head + i) % size]
}

private const val HISTORY = 240
private val BARS_WIDTH = 70.dp
private val BACKGROUND = Color(0xFF101014)
private val GATE = Color(0xFFFF6B6B)
private val BAR = Color(0xFF4CC9F0)
private val DASH = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
private val SERIES = arrayOf(Color(0xFF777777), Color(0xFF4CC9F0), Color(0xFF7CFFB2), Color.White)
private val LABELS = arrayOf("level", "envelope", "open", "pose")
