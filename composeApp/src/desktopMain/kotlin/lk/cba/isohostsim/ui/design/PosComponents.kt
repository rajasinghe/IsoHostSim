package lk.cba.isohostsim.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hairline colour that works on both the light and the dark surface. */
@Composable
fun posLine(): Color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)

/** Secondary text colour. */
@Composable
fun posMuted(): Color = MaterialTheme.colors.onSurface.copy(alpha = 0.66f)

/** A quiet panel fill, used for table headers and note boxes. */
@Composable
fun posSunken(): Color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f)

enum class Tone { Success, Danger, Warning, Neutral, Accent }

private data class ToneColors(val bg: Color, val fg: Color, val dot: Color)

@Composable
private fun toneColors(tone: Tone): ToneColors {
    val light = MaterialTheme.colors.isLight
    return when (tone) {
        Tone.Success -> ToneColors(
            bg = if (light) Pos.SuccessSoft else Pos.SuccessDot.copy(alpha = 0.18f),
            fg = if (light) Pos.Success else Color(0xFF6EE0B0),
            dot = Pos.SuccessDot,
        )
        Tone.Danger -> ToneColors(
            bg = if (light) Pos.DangerSoft else Color(0xFFFF6B5E).copy(alpha = 0.18f),
            fg = if (light) Pos.Danger else Color(0xFFFFA39A),
            dot = Pos.Danger,
        )
        Tone.Warning -> ToneColors(
            bg = if (light) Pos.WarnSoft else Color(0xFFF5C26B).copy(alpha = 0.16f),
            fg = if (light) Pos.Warn else Color(0xFFF5D08A),
            dot = Color(0xFFD98A00),
        )
        Tone.Accent -> ToneColors(
            bg = MaterialTheme.colors.primary.copy(alpha = if (light) 0.10f else 0.20f),
            fg = if (light) Pos.IndigoInk else MaterialTheme.colors.primary,
            dot = MaterialTheme.colors.primary,
        )
        Tone.Neutral -> ToneColors(
            bg = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
            fg = posMuted(),
            dot = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
        )
    }
}

/** A small rounded status label, optionally with a dot. */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.Neutral, showDot: Boolean = false, modifier: Modifier = Modifier) {
    val c = toneColors(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showDot) Box(Modifier.size(7.dp).clip(CircleShape).background(c.dot))
        Text(text, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A white panel with a hairline border and 12 dp corners. */
@Composable
fun PosCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, posLine()),
        elevation = 0.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A tinted box for explanations and warnings. */
@Composable
fun NoteBox(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val c = toneColors(tone)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (tone == Tone.Neutral) posSunken() else c.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (tone == Tone.Neutral) posMuted() else c.fg, fontSize = 13.sp)
    }
}

enum class PosButtonKind { Primary, Secondary, Danger }

/** A 44 dp tall button in one of three kinds. */
@Composable
fun PosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PosButtonKind = PosButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val sized = modifier.heightIn(min = 44.dp)
    val flat = ButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        disabledElevation = 0.dp,
        hoveredElevation = 0.dp,
        focusedElevation = 0.dp,
    )
    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    when (kind) {
        PosButtonKind.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            elevation = flat,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary,
            ),
            content = label,
        )
        PosButtonKind.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            elevation = flat,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.22f)),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
            ),
            content = label,
        )
        PosButtonKind.Danger -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            elevation = flat,
            border = BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.error,
            ),
            content = label,
        )
    }
}

/** One-of-many choice shown as side-by-side buttons. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colors.primary
    // Content-wide by default, with every button as wide as the widest label. A caller that wants the
    // row to fill a space passes fillMaxWidth(). Without this the weighted buttons take all the width
    // they are offered and squeeze neighbours such as a page title down to nothing.
    Row(modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (isSelected) accent.copy(alpha = 0.10f) else MaterialTheme.colors.surface)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) accent else MaterialTheme.colors.onSurface.copy(alpha = 0.22f),
                        shape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) accent else MaterialTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A field number such as F39 in a small tinted tag. */
@Composable
fun FieldTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.primary.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colors.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Page title with an optional line under it and actions on the right. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.h5)
                leading?.invoke(this)
            }
            if (subtitle != null) {
                Text(subtitle, color = posMuted(), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A dark block for JSON and hex. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Pos.Code)
            .horizontalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(text, color = Pos.CodeText, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 21.sp)
    }
}

/** A section heading inside a page. */
@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.subtitle1)
        if (subtitle != null) Text(subtitle, color = posMuted(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
