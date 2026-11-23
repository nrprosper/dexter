package com.vlmis.dexter.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Liquid glass" building blocks: translucent cards with a light-catching rim, floating over a slowly drifting glow.
internal object Glass {
    val Background = Color(0xFF000000)
    val Card = Color.White.copy(alpha = 0.075f)
    val CardPressed = Color.White.copy(alpha = 0.13f)
    val Text = Color(0xFFF4F4F7)
    val Secondary = Color(0xFF9A9AA2)
    val Separator = Color.White.copy(alpha = 0.08f)
    val Accent = Color(0xFF4CC9F0)
    val AccentDeep = Color(0xFF3A86FF)
    val Danger = Color(0xFFFF6B6B)
    val Rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f)))
    val Sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent))
    val CardShape = RoundedCornerShape(30.dp)
    val PillShape = RoundedCornerShape(50)
    const val ICON_CORNER = 11
}

// Soft colour blobs, heavily blurred, drifting slowly behind everything: the "liquid" under the glass.
@Composable
internal fun GlassBackdrop(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Box(modifier.fillMaxSize().background(Glass.Background)) {
        Canvas(Modifier.fillMaxSize().blur(90.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(Glass.AccentDeep.copy(alpha = 0.38f), radius = w * 0.55f, center = Offset(w * (0.1f + 0.25f * drift), h * 0.12f))
            drawCircle(Glass.Accent.copy(alpha = 0.26f), radius = w * 0.45f, center = Offset(w * (0.95f - 0.2f * drift), h * (0.42f + 0.06f * drift)))
            drawCircle(Color(0xFF7B5CFF).copy(alpha = 0.24f), radius = w * 0.5f, center = Offset(w * (0.2f + 0.15f * drift), h * 0.92f))
        }
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Glass.CardShape)
            .background(Glass.Card)
            .background(Glass.Sheen)
            .border(BorderStroke(1.dp, Glass.Rim), Glass.CardShape),
        content = content,
    )
}

@Composable
internal fun LargeTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 8.dp, top = 28.dp, bottom = 18.dp)) {
        Text(text, color = Glass.Text, fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = 1.2.sp)
        if (subtitle != null) Text(subtitle, color = Glass.Secondary, fontSize = 15.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Glass.Secondary,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

// One row of a card: a gradient icon tile, a title (and an optional detail line), a value and/or trailing control.
@Composable
internal fun GlassRow(
    title: String,
    icon: GlassIcon? = null,
    tint: List<Color> = listOf(Glass.AccentDeep, Glass.Accent),
    detail: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(interaction, ripple(color = Color.White), onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, tint)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Glass.Text, fontSize = 17.sp, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, color = Glass.Secondary, fontSize = 13.sp, letterSpacing = 0.2.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, color = Glass.Secondary, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        trailing?.invoke()
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            GlassIconView(GlassIcon.Chevron, Glass.Secondary, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun IconTile(icon: GlassIcon, tint: List<Color>, size: Dp = 38.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(Glass.ICON_CORNER.dp)).background(Brush.linearGradient(tint)),
        contentAlignment = Alignment.Center,
    ) {
        GlassIconView(icon, Color.White, Modifier.size(size * 0.58f))
    }
}

@Composable
internal fun RowSeparator(inset: Dp = 72.dp) {
    Box(Modifier.padding(start = inset, end = 18.dp).fillMaxWidth().height(1.dp).background(Glass.Separator))
}

@Composable
internal fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked,
        onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Glass.Accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val shape = Glass.PillShape
    val fill = when {
        !enabled -> Modifier.background(Color.White.copy(alpha = 0.05f))
        primary -> Modifier.background(Brush.horizontalGradient(listOf(Glass.Accent, Glass.AccentDeep)))
        else -> Modifier.background(Glass.Card).border(BorderStroke(1.dp, Glass.Rim), shape)
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Glass.Secondary
                primary -> Color(0xFF02131A)
                else -> Glass.Text
            },
            fontSize = 16.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.4.sp,
        )
    }
}

// A secret field: always masked, no suggestions or autocorrect.
@Composable
internal fun GlassSecretField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, Glass.Rim), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Glass.Secondary, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Glass.Text, fontSize = 16.sp, letterSpacing = 1.sp),
            cursorBrush = SolidColor(Glass.Accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// A floating glass pill, e.g. the back button or the bottom action.
@Composable
internal fun FloatingPill(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(Glass.PillShape)
            .background(Color(0xFF16181C).copy(alpha = 0.82f))
            .background(Glass.Sheen)
            .border(BorderStroke(1.dp, Glass.Rim), Glass.PillShape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun Note(text: String, color: Color = Glass.Secondary) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
internal fun ButtonRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}
