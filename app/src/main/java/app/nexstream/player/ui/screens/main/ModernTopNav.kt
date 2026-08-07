package app.nexstream.player.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val TOP_NAV_ITEMS = listOf(
    AppRoute.Guide     to "Guide",
    AppRoute.Movies    to "Movies",
    AppRoute.Series    to "Series",
    AppRoute.CatchUp   to "Catch Up",
    AppRoute.Picks     to "Picks",
    AppRoute.Search    to "Search",
    AppRoute.MyList    to "My List",
    AppRoute.Downloads to "Downloads",
    AppRoute.Settings  to "Settings",
)

@Composable
fun ModernTopNav(
    currentRoute:       AppRoute,
    onNavigate:         (AppRoute) -> Unit,
    onDropToContent:    () -> Unit,
    focusTick:          Int,
    activeProfileEmoji: String,
    activeProfileName:  String,
    onProfileClick:     () -> Unit,
    onStopPlayer:       () -> Unit = {},
    modifier:           Modifier = Modifier,
) {
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

    val focusRequesters = remember { List(TOP_NAV_ITEMS.size) { FocusRequester() } }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(32)
        val idx = TOP_NAV_ITEMS.indexOfFirst { it.first == currentRoute.rootSection() }.coerceAtLeast(0)
        try { focusRequesters[idx].requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Logo ──────────────────────────────────────────────────────────
            if (nsTheme.identity.logoMode == LogoMode.IMAGE && nsTheme.identity.logoUrl != null) {
                AsyncImage(
                    model              = nsTheme.identity.logoUrl,
                    contentDescription = nsTheme.identity.appName,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .height(40.dp)
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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

            // ── Nav pills ─────────────────────────────────────────────────────
            LazyRow(
                modifier              = Modifier.weight(1f),
                contentPadding        = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                itemsIndexed(TOP_NAV_ITEMS) { idx, (route, label) ->
                    TopNavPill(
                        label          = label,
                        selected       = currentRoute.rootSection() == route,
                        focusRequester = focusRequesters[idx],
                        accent         = accent,
                        background     = background,
                        textPrimary    = textPrimary,
                        divider        = divider,
                        fontScale      = fontScale,
                        fontWeight     = fontWeight,
                        onSelect       = { onStopPlayer(); onNavigate(route); onDropToContent() },
                        onDown         = { onStopPlayer(); onNavigate(route); onDropToContent() },
                    )
                }
            }

            // ── Profile avatar ────────────────────────────────────────────────
            var profileFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (profileFocused) surface else background)
                    .border(
                        width = 1.dp,
                        color = if (profileFocused) accent else divider.copy(alpha = 0.5f),
                        shape = CircleShape,
                    )
                    .onFocusChanged { profileFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onProfileClick(); true }
                            Key.DirectionDown -> { onDropToContent(); true }
                            else -> false
                        }
                    }
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onProfileClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = activeProfileEmoji, fontSize = 18.sp)
            }
        }

        // Bottom separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun TopNavPill(
    label:          String,
    selected:       Boolean,
    focusRequester: FocusRequester,
    accent:         Color,
    background:     Color,
    textPrimary:    Color,
    divider:        Color,
    fontScale:      Float,
    fontWeight:     FontWeight,
    onSelect:       () -> Unit,
    onDown:         () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused.value = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onSelect(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSelect() }
            .clip(RoundedCornerShape(50.dp))
            .background(
                when {
                    selected        -> accent
                    isFocused.value -> accent.copy(alpha = 0.25f)
                    else            -> Color.Transparent
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    selected && isFocused.value -> Color.White.copy(alpha = 0.85f)
                    selected        -> accent
                    isFocused.value -> accent
                    else            -> divider.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(50.dp),
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            fontSize   = (13f * fontScale).sp,
            fontWeight = fontWeight,
            color      = when {
                selected        -> background
                isFocused.value -> accent
                else            -> textPrimary.copy(alpha = 0.55f)
            },
        )
    }
}
