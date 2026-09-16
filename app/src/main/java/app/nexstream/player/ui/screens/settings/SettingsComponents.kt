package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getUiStyleFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

// ── Style helper ──────────────────────────────────────────────────────────────

/** Reads UiStyle from DataStore and provides it as a remembered state. */
@Composable
fun rememberUiStyle(): UiStyle {
    val context = LocalContext.current
    val uiStyle by context.getUiStyleFlow().collectAsState(initial = UiStyle.CLASSIC)
    return uiStyle
}

// ── Section container ─────────────────────────────────────────────────────────

/**
 * MODERN: wraps content in a tonal Surface card with an icon-badge header.
 * CLASSIC: renders a flat section label + divider above the content, no card.
 */
@Composable
fun SettingsSectionContainer(
    title: String,
    icon: ImageVector,
    uiStyle: UiStyle,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    when (uiStyle) {
        UiStyle.MODERN -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    // Modern card header: icon badge + title
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    content()
                }
            }
        }
        UiStyle.CLASSIC -> {
            Column(modifier = modifier.fillMaxWidth()) {
                // Classic: small label above items
                Row(
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content()
            }
        }
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

/**
 * MODERN: description always visible; focus = primary bg fill.
 * CLASSIC: description only on focus; focus = 2dp primary border, transparent bg.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    uiStyle: UiStyle,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bvr        = remember { BringIntoViewRequester() }
    val scope      = rememberCoroutineScope()
    var nodeHeight by remember { mutableStateOf(0) }

    val rowModifier = when (uiStyle) {
        UiStyle.MODERN -> Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bvr)
            .onSizeChanged { nodeHeight = it.height }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) scope.launch {
                    bvr.bringIntoView(Rect(0f, 0f, 10000f, nodeHeight + 80f))
                }
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onToggle(); true
                } else false
            }
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp)

        UiStyle.CLASSIC -> Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bvr)
            .onSizeChanged { nodeHeight = it.height }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) scope.launch {
                    bvr.bringIntoView(Rect(0f, 0f, 10000f, nodeHeight + 80f))
                }
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onToggle(); true
                } else false
            }
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused && uiStyle == UiStyle.MODERN)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused && uiStyle == UiStyle.MODERN)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }

}

// ── Action item ───────────────────────────────────────────────────────────────

/**
 * MODERN: description always visible; focus = primary bg fill.
 * CLASSIC: description only on focus; focus = 2dp primary border.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsActionItem(
    label: String,
    description: String,
    value: String = "",
    uiStyle: UiStyle,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bvr        = remember { BringIntoViewRequester() }
    val scope      = rememberCoroutineScope()
    var nodeHeight by remember { mutableStateOf(0) }

    val rowModifier = when (uiStyle) {
        UiStyle.MODERN -> Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bvr)
            .onSizeChanged { nodeHeight = it.height }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) scope.launch {
                    bvr.bringIntoView(Rect(0f, 0f, 10000f, nodeHeight + 80f))
                }
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)

        UiStyle.CLASSIC -> Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bvr)
            .onSizeChanged { nodeHeight = it.height }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) scope.launch {
                    bvr.bringIntoView(Rect(0f, 0f, 10000f, nodeHeight + 80f))
                }
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused && uiStyle == UiStyle.MODERN)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused && uiStyle == UiStyle.MODERN)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused && uiStyle == UiStyle.MODERN)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.primary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isFocused && uiStyle == UiStyle.MODERN)
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }

    if (uiStyle == UiStyle.CLASSIC && showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

// ── Info row (read-only) ──────────────────────────────────────────────────────

@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
fun SettingsSectionLabel(text: String, uiStyle: UiStyle) {
    when (uiStyle) {
        UiStyle.CLASSIC -> Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp)
        )
        UiStyle.MODERN -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}
