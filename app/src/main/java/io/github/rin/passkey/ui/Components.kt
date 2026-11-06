package io.github.rin.passkey.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Horizontal space between the screen edge and the content of a row. */
internal val RowPadding = 16.dp
private val RowGap = 14.dp
private val GlyphSize = 22.dp
internal val AppIconSize = 40.dp

/** Divider insets that line up with the text of rows with a glyph or an app icon. */
internal val GlyphInset = RowPadding + GlyphSize + RowGap
internal val AppIconInset = RowPadding + AppIconSize + RowGap

/** A muted amber for advice that is not an error. */
@Composable
internal fun warningColor(): Color =
    if (MiuixTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFB454) else Color(0xFFB45F00)

@Composable
internal fun GroupCard(modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = color),
        content = content,
    )
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(start = RowPadding, end = RowPadding, top = 20.dp, bottom = 8.dp),
        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The single row layout used across the app: an optional leading glyph or image, a title with an
 * optional summary, and trailing content. The whole row is the touch target.
 */
@Composable
internal fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    summary: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = RowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        val contentAlpha = if (enabled) 1f else 0.45f
        when {
            leading != null -> Box(Modifier.alpha(contentAlpha)) { leading() }
            icon != null -> Glyph(icon, Modifier.alpha(contentAlpha))
        }
        Column(Modifier.weight(1f).alpha(contentAlpha), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (summary != null) Text(summary, fontSize = 13.sp, lineHeight = 18.sp, color = summaryColor)
        }
        trailing()
    }
}

@Composable
internal fun SwitchRow(icon: ImageVector, title: String, summary: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListRow(
        title = title, icon = icon, summary = summary, enabled = enabled,
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(checked, onCheckedChange, enabled = enabled)
    }
}

/** A row that opens a sheet or page: an optional value, then a chevron. */
@Composable
internal fun NavRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    value: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    enabled: Boolean = true,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    ListRow(title = title, icon = icon, summary = summary, summaryColor = summaryColor, enabled = enabled, onClick = onClick) {
        if (value != null) Text(value, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        TrailingGlyph(if (external) AppIcons.External else AppIcons.Chevron, Modifier.alpha(if (enabled) 1f else 0.45f))
    }
}

@Composable
internal fun Glyph(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MiuixTheme.colorScheme.onSurfaceSecondary, size: Dp = GlyphSize) {
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
internal fun TrailingGlyph(icon: ImageVector, modifier: Modifier = Modifier) {
    Glyph(icon, modifier, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f), size = 18.dp)
}

@Composable
internal fun RowDivider(start: Dp = GlyphInset) {
    Box(Modifier.padding(start = start, end = RowPadding).fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine))
}

/** An app icon, or a tinted tile with [placeholder] when there is no app. */
@Composable
internal fun AppIcon(app: ProviderApp?, placeholder: ImageVector, size: Dp = AppIconSize) {
    val bitmap = app?.icon
    if (bitmap != null) {
        Image(remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        IconTile(placeholder, size)
    }
}

@Composable
internal fun IconTile(icon: ImageVector, size: Dp = AppIconSize, color: Color = MiuixTheme.colorScheme.primary, container: Color = MiuixTheme.colorScheme.tertiaryContainer) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.55f), tint = color)
    }
}

/** An app with its package name and tags, used in the pickers and the provider list. */
@Composable
internal fun AppRow(
    app: ProviderApp,
    tags: List<String>,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = RowPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Row(Modifier.weight(1f).alpha(if (enabled) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
            AppIcon(app, AppIcons.Passkey)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        app.label, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    )
                    tags.forEach { Tag(it) }
                }
                Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        trailing()
    }
}

@Composable
internal fun SelectableAppRow(app: ProviderApp, tags: List<String>, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    AppRow(
        app, tags, selected, enabled,
        modifier = Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) { SelectionMark(selected) }
}

/** Keeps the trailing column aligned whether or not the row is selected. */
@Composable
internal fun SelectionMark(selected: Boolean) {
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (selected) Glyph(AppIcons.Check, tint = MiuixTheme.colorScheme.primary)
    }
}

@Composable
internal fun Tag(text: String, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(
        text, maxLines = 1, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(text, modifier = modifier.padding(horizontal = RowPadding, vertical = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = color)
}

/** A tinted banner with a glyph, used for connection problems and errors. */
@Composable
internal fun Banner(
    icon: ImageVector,
    text: String,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    GroupCard(modifier, color = container) {
        Row(Modifier.padding(RowPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(icon, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (title != null) Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
                Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = if (title != null) MiuixTheme.colorScheme.onSurfaceVariantSummary else tint)
            }
        }
        content()
    }
}

@Composable
internal fun ToolbarButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon, description, modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@Composable
internal fun StepRow(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = RowPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(RowGap)) {
        Box(Modifier.size(GlyphSize).clip(CircleShape).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary)
        }
        Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
internal fun HGap(width: Dp) = Spacer(Modifier.width(width))
