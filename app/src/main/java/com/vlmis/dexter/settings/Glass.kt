package com.vlmis.dexter.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Measured from the phone's own Settings (440 dpi): solid dark cards on black, 61 dp icon rows, 56 dp plain rows,
// 35 dp icon tiles, 17.5 dp margins. Glass (translucency + a light rim) only on floating controls.
internal object Glass {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF1C1C1C)
    val Text = Color(0xFFF2F2F2)
    val Secondary = Color(0xFF8C8C8C)
    val Chevron = Color(0xFF6E6E6E)
    val Separator = Color(0xFF2E2E2E)
    val Accent = Color(0xFF2F7BFF)
    val Danger = Color(0xFFFF5A5A)
    val Float = Color(0xFF1E1E1E).copy(alpha = 0.88f)
    val FloatRim = Color(0xFF3A3A3A)
    val CardShape = RoundedCornerShape(22.dp)

    val Margin = 17.5.dp
    val IconRow = 61.dp
    val PlainRow = 56.dp
    val IconTile = 35.dp
    val TextStart = 70.dp
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Glass.CardShape).background(Glass.Card), content = content)
}

// The big title on the top-level page only.
@Composable
internal fun LargeTitle(text: String) {
    Text(
        text,
        color = Glass.Text,
        fontSize = 38.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 9.5.dp, top = 64.dp, bottom = 18.dp),
    )
}

// Sub-page header: an icon-only round back button, the title beside it.
@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 23.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(47.dp).clip(CircleShape).background(Glass.Float).border(BorderStroke(1.dp, Glass.FloatRim), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            GlassIconView(GlassIcon.Back, Glass.Text, Modifier.size(23.dp))
        }
        Spacer(Modifier.width(23.dp))
        Text(title, color = Glass.Text, fontSize = 22.sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = Glass.Secondary, fontSize = 15.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 18.dp, top = 26.dp, bottom = 10.dp))
}

@Composable
internal fun CardGap() {
    Spacer(Modifier.height(Glass.Margin))
}

// One list row: optional icon tile, title (+ optional second line), value, trailing control, chevron.
@Composable
internal fun GlassRow(
    title: String,
    icon: GlassIcon? = null,
    color: Color = Glass.Accent,
    detail: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (icon != null) Glass.IconRow else Glass.PlainRow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Glass.Margin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, color)
            Spacer(Modifier.width(Glass.TextStart - Glass.Margin - Glass.IconTile))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Glass.Text, fontSize = 17.sp, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, color = Glass.Secondary, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) Text(value, color = Glass.Secondary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            GlassIconView(GlassIcon.Chevron, Glass.Chevron, Modifier.size(16.dp))
        }
    }
}

// The phone's tiles: one colour, slightly lighter at the top, white glyph.
@Composable
internal fun IconTile(icon: GlassIcon, color: Color) {
    val top = Color(
        red = color.red + (1f - color.red) * 0.18f,
        green = color.green + (1f - color.green) * 0.18f,
        blue = color.blue + (1f - color.blue) * 0.18f,
    )
    Box(
        Modifier.size(Glass.IconTile).clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(top, color))),
        contentAlignment = Alignment.Center,
    ) {
        GlassIconView(icon, Color.White, Modifier.size(20.dp))
    }
}

// Inset like the phone's: from the text start (or the card padding for plain rows) to the right padding.
@Composable
internal fun RowSeparator(withIcon: Boolean = true) {
    Box(
        Modifier.padding(start = if (withIcon) Glass.TextStart else Glass.Margin, end = Glass.Margin).fillMaxWidth().height(1.dp)
            .background(Glass.Separator),
    )
}

@Composable
internal fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 44.dp, height = 24.dp).clip(CircleShape)
            .background(if (checked) Glass.Accent else Color(0xFF3A3A3A))
            .clickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
    }
}

// The phone's slider: an 11 dp rounded track and a white thumb with an accent ring.
@Composable
internal fun GlassSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest = rememberUpdatedState(onChange)
    fun valueAt(x: Float, width: Float, thumb: Float): Float {
        val t = ((x - thumb / 2f) / (width - thumb)).coerceIn(0f, 1f)
        return range.start + t * (range.endInclusive - range.start)
    }
    Canvas(
        modifier.fillMaxWidth().height(32.dp)
            .pointerInput(range) { detectTapGestures { latest.value(valueAt(it.x, size.width.toFloat(), 23.dp.toPx())) } }
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ -> latest.value(valueAt(change.position.x, size.width.toFloat(), 23.dp.toPx())) }
            },
    ) {
        val track = 11.dp.toPx()
        val thumb = 23.dp.toPx()
        val cy = size.height / 2f
        val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val x = thumb / 2f + t * (size.width - thumb)
        drawRoundRect(Color(0xFF3A3A3A), Offset(0f, cy - track / 2f), Size(size.width, track), CornerRadius(track / 2f))
        drawRoundRect(Glass.Accent, Offset(0f, cy - track / 2f), Size(x, track), CornerRadius(track / 2f))
        drawCircle(Color.White, thumb / 2f, Offset(x, cy))
        drawCircle(Glass.Accent, thumb / 2f - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
    }
}

@Composable
internal fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color(0xFF262626)
                    primary -> Glass.Accent
                    else -> Color(0xFF2C2C2C)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Glass.Secondary, fontSize = 15.sp, letterSpacing = 0.3.sp, maxLines = 1)
    }
}

// A secret field: always masked, no suggestions or autocorrect.
@Composable
internal fun GlassSecretField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF262626)).padding(horizontal = 16.dp, vertical = 14.dp),
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

// The floating glass pill at the bottom (like the phone's search bar).
@Composable
internal fun FloatingPill(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .height(47.dp)
            .clip(CircleShape)
            .background(Glass.Float)
            .border(BorderStroke(1.dp, Glass.FloatRim), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

@Composable
internal fun Note(text: String, color: Color = Glass.Secondary) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}

@Composable
internal fun ButtonRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Glass.Margin, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}
