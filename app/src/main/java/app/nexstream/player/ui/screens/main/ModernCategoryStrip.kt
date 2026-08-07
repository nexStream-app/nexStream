package app.nexstream.player.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LogoMode
import coil.compose.AsyncImage

@Composable
fun ModernCategoryStrip(
    categories:      List<String>,
    selected:        String?,
    onSelect:        (String?) -> Unit,
    onFocusUp:       () -> Unit,
    onFocusDown:     () -> Unit,
    focusTick:       Int,
    modifier:        Modifier = Modifier,
    showLeadingLogo: Boolean = false,
    onBack:          (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isTV    = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val nsTheme     = LocalNexStreamTheme.current
    val fontScale   = nsTheme.typography.scale
    val fontWeight  = when (nsTheme.typography.weight) {
        "semibold" -> FontWeight.SemiBold
        "bold"     -> FontWeight.Bold
        else       -> FontWeight.Normal
    }
    val accent      = LocalNsAccent.current
    val background  = LocalNsBackground.current
    val surface     = LocalNsSurface.current
    val divider     = LocalNsDivider.current
    val textPrimary = LocalNsTextPrimary.current

    val allItems = remember(categories) { listOf(null as String?) + categories.map { it } }
    val focusRequesters = remember(allItems.size) { List(allItems.size) { FocusRequester() } }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(32)
        val idx = allItems.indexOfFirst { it == selected }.coerceAtLeast(0)
        try { focusRequesters[idx].requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = modifier.fillMaxWidth().background(background)) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (!isTV && onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showLeadingLogo) {
            if (nsTheme.identity.logoMode == LogoMode.IMAGE && nsTheme.identity.logoUrl != null) {
                AsyncImage(
                    model              = nsTheme.identity.logoUrl,
                    contentDescription = nsTheme.identity.appName,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.height(40.dp).padding(start = 20.dp, end = 8.dp),
                )
            } else {
                Text(
                    text       = nsTheme.identity.appName,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = accent,
                    modifier   = Modifier.padding(start = 20.dp, end = 8.dp),
                )
            }
        }
        LazyRow(
            modifier              = Modifier.weight(1f),
            contentPadding        = PaddingValues(horizontal = if (showLeadingLogo) 4.dp else 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            itemsIndexed(allItems) { idx, cat ->
                val label     = cat ?: "All"
                val isSelected = cat == selected
                val isFocused = remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .focusRequester(focusRequesters[idx])
                        .onFocusChanged { isFocused.value = it.isFocused }
                        .onKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (ev.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    onSelect(cat); true
                                }
                                Key.DirectionUp   -> { onFocusUp(); true }
                                Key.DirectionDown -> { onFocusDown(); true }
                                else -> false
                            }
                        }
                        .clickable(
                            indication     = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSelect(cat) }
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            when {
                                isSelected      -> accent
                                isFocused.value -> accent.copy(alpha = 0.25f)
                                else            -> Color.Transparent
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isSelected && isFocused.value -> Color.White.copy(alpha = 0.85f)
                                isSelected      -> accent
                                isFocused.value -> accent
                                else            -> divider.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(50.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = label,
                        fontSize   = (12f * fontScale).sp,
                        fontWeight = fontWeight,
                        color      = when {
                            isSelected      -> background
                            isFocused.value -> accent
                            else            -> textPrimary.copy(alpha = 0.55f)
                        },
                    )
                }
            }
        }
        } // end Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.25f)),
        )
    }
}
