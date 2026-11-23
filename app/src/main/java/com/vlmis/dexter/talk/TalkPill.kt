package com.vlmis.dexter.talk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import assistant.core.AssistantState
import assistant.core.Orchestrator
import com.vlmis.dexter.settings.Glass
import com.vlmis.dexter.settings.GlassIcon
import com.vlmis.dexter.settings.GlassIconView
import kotlinx.coroutines.delay

// The glass pill at the bottom of the face: a hint on launch, a live mic with your words while listening, bouncing dots
// while thinking; it slides away while Dexter talks so the face has the stage. Tapping it starts or stops listening.
@Composable
internal fun TalkPill(orchestrator: Orchestrator, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val state by orchestrator.state.collectAsState()
    val heard by orchestrator.lastHeard.collectAsState()
    val level = orchestrator.voiceLevel.collectAsState()
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(HINT_MILLIS)
        hint = false
    }
    LaunchedEffect(state) { if (state != AssistantState.Idle) hint = false }
    val mode = when {
        state == AssistantState.Listening -> Mode.Listening
        state == AssistantState.Thinking -> Mode.Thinking
        hint -> Mode.Hint
        else -> null
    }
    var shown by remember { mutableStateOf(Mode.Hint) }
    if (mode != null) shown = mode
    AnimatedVisibility(
        visible = mode != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.78f))
                .border(BorderStroke(1.dp, Glass.FloatRim), RoundedCornerShape(28.dp))
                .clickable(onClick = onTap)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                .animateContentSize(spring(stiffness = 500f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Orb(shown, level)
            Spacer(Modifier.width(12.dp))
            Text(
                text = when (shown) {
                    Mode.Hint -> "Double-tap me to talk"
                    Mode.Listening -> heard.ifBlank { "Listening…" }.let(::latestWords)
                    Mode.Thinking -> heard.ifBlank { "Thinking…" }.let(::latestWords)
                },
                color = if (shown == Mode.Thinking) Glass.Secondary else Glass.Text,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.3.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class Mode { Hint, Listening, Thinking }

// 40 dp circle: a blue mic with a ring that swells with your voice, three bouncing dots, or Dexter's face glyph.
@Composable
private fun Orb(mode: Mode, level: State<Float>) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (mode == Mode.Listening) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val s = 1f + RING_GROWTH * level.value
                        scaleX = s
                        scaleY = s
                        alpha = 0.25f + 0.5f * level.value
                    }
                    .clip(CircleShape)
                    .background(Glass.Accent.copy(alpha = 0.45f)),
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (mode == Mode.Listening) Glass.Accent else Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center,
        ) {
            when (mode) {
                Mode.Listening -> GlassIconView(GlassIcon.Mic, Color.White, Modifier.size(20.dp))
                Mode.Thinking -> Dots()
                Mode.Hint -> GlassIconView(GlassIcon.Face, Glass.Text, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Dots() {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            Box(
                Modifier.size(5.dp)
                    .graphicsLayer {
                        val local = ((phase - i * 0.18f) % 1f + 1f) % 1f
                        translationY = -4.dp.toPx() * kotlin.math.sin(local * Math.PI.toFloat()).coerceAtLeast(0f)
                    }
                    .clip(CircleShape)
                    .background(Glass.Text),
            )
        }
    }
}

// Long sentences keep the newest words in view.
private fun latestWords(text: String): String =
    if (text.length <= MAX_CHARS) text else "…" + text.takeLast(MAX_CHARS).substringAfter(' ')

private const val HINT_MILLIS = 5_000L
private const val RING_GROWTH = 0.45f
private const val MAX_CHARS = 70
