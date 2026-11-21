package se.lyzio.lyzlarm.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.ui.theme.Lz

@Composable
fun LzIcon(@DrawableRes id: Int, contentDescription: String?, tint: Color = Lz.Text, size: Dp = 22.dp) {
    Icon(painterResource(id), contentDescription, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 60.dp, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(height / 2),
        colors = ButtonDefaults.buttonColors(containerColor = Lz.Accent, contentColor = Lz.OnAccent),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, border: Color = Lz.Outline, textColor: Color = Lz.Text, height: Dp = 56.dp) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(height / 2),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, color = Lz.TextSoft, fontSize = 15.sp)
    }
}

/** Rounded surface card. */
@Composable
fun LzCard(
    modifier: Modifier = Modifier,
    color: Color = Lz.Surface,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(padding),
        content = content,
    )
}

/** Pill toggle from the mockup: accent when on. */
@Composable
fun LzSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String) {
    Box(
        Modifier
            .width(60.dp)
            .height(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(if (checked) Lz.Accent else Lz.Outline)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (checked) Color.White else Lz.TextDim))
        }
    }
}

/** Small pink badge, e.g. "Arrives in v0.3". */
@Composable
fun SoonBadge(text: String) {
    Text(
        text,
        color = Lz.OnAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Lz.AccentSoft)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionLabel(text: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = Lz.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (badge != null) SoonBadge(badge)
    }
}

/** Segmented control. When [enabled] is false it is shown at 40% opacity (feature not yet available). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedColor: Color = Lz.Highlight,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(Lz.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) selectedColor else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else Lz.TextSoft,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Row of separate chips (wake window lengths). */
@Composable
fun ChipRow(options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Lz.Accent else Lz.Surface)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, Lz.Outline, RoundedCornerShape(12.dp)))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) Lz.OnAccent else Lz.TextSoft,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** A row inside a grouped card: label on the left, value on the right. */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)?, showDivider: Boolean, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Lz.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) Text(value, color = Lz.TextMuted, fontSize = 15.sp)
            trailing?.invoke(this)
        }
        if (showDivider) HorizontalDivider(color = Lz.Divider, thickness = 1.dp)
    }
}

/** Header for setup steps: "Step 2 of 5". */
@Composable
fun StepHeader(step: Int, total: Int) {
    Text("Step $step of $total", color = Lz.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

/** Round icon badge used at the top of setup screens. */
@Composable
fun IconBadge(@DrawableRes id: Int, background: Color = Lz.Highlight, tint: Color = Color.White) {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) { LzIcon(id, null, tint = tint, size = 34.dp) }
}
