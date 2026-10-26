package app.nexstream.player.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.GlassTokens
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LogoMode
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val GLASS_NAV_ITEMS = listOf(
    AppRoute.Home    to "Home",
    AppRoute.Guide   to "Guide",
    AppRoute.Sports  to "Sports",
    AppRoute.Movies  to "Movies",
    AppRoute.Series  to "Series",
    AppRoute.CatchUp to "Catch Up",
)

@Composable
fun GlassTopNav(
    currentRoute:       AppRoute,
    onNavigate:         (AppRoute) -> Unit,
    onDropToContent:    () -> Unit,
    focusTick:          Int,
    activeProfileEmoji: String,
    activeProfileName:  String,
    onProfileClick:     () -> Unit,
    onSearchClick:      () -> Unit = {},
    onSettingsClick:    () -> Unit = {},
    onStopPlayer:       () -> Unit = {},
    modifier:           Modifier = Modifier,
) {
    val nsTheme = LocalNexStreamTheme.current
    val accent  = LocalNsAccent.current
    val bgColor = GlassTokens.GlassPanelSolid

    val focusRequesters = remember { List(GLASS_NAV_ITEMS.size) { FocusRequester() } }
    val searchFR   = remember { FocusRequester() }
    val settingsFR = remember { FocusRequester() }
    val profileFR  = remember { FocusRequester() }

    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            timeStr = LocalTime.now().format(fmt)
            delay(30_000L)
        }
    }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        delay(32)
        val idx = GLASS_NAV_ITEMS.indexOfFirst { it.first == currentRoute.rootSection() }.coerceAtLeast(0)
        try { focusRequesters[idx].requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(bgColor),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(24.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            if (nsTheme.identity.logoMode == LogoMode.IMAGE && nsTheme.identity.logoUrl != null) {
                AsyncImage(
                    model              = nsTheme.identity.logoUrl,
                    contentDescription = nsTheme.identity.appName,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .height(32.dp)
                        .padding(end = 16.dp),
                )
            } else {
                Text(
                    text       = nsTheme.identity.appName,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = accent,
                    modifier   = Modifier.padding(end = 16.dp),
                )
            }

            // ── Nav pills ─────────────────────────────────────────────────────
            LazyRow(
                modifier              = Modifier.weight(1f),
                contentPadding        = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                itemsIndexed(GLASS_NAV_ITEMS) { idx, (route, label) ->
                    GlassNavPill(
                        label          = label,
                        selected       = currentRoute.rootSection() == route,
                        focusRequester = focusRequesters[idx],
                        accent         = accent,
                        onSelect       = { onStopPlayer(); onNavigate(route); onDropToContent() },
                        onDown         = { onStopPlayer(); onNavigate(route); onDropToContent() },
                    )
                }
            }

            // ── Search icon ───────────────────────────────────────────────────
            GlassNavIconButton(
                icon          = Icons.Default.Search,
                contentDesc   = "Search",
                focusRequester = searchFR,
                accent        = accent,
                onClick       = { onStopPlayer(); onSearchClick() },
                onDown        = { onDropToContent() },
            )

            // ── Settings icon ─────────────────────────────────────────────────
            GlassNavIconButton(
                icon          = Icons.Default.Settings,
                contentDesc   = "Settings",
                focusRequester = settingsFR,
                accent        = accent,
                onClick       = { onStopPlayer(); onSettingsClick() },
                onDown        = { onDropToContent() },
            )

            // ── Clock ─────────────────────────────────────────────────────────
            if (timeStr.isNotEmpty()) {
                Text(
                    text       = timeStr,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = GlassTokens.TextSecondary,
                    modifier   = Modifier.padding(horizontal = 12.dp),
                )
            }

            // ── Profile avatar ────────────────────────────────────────────────
            var profileFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(end = 24.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (profileFocused) GlassTokens.GlassSmallSolid else Color.Transparent)
                    .border(
                        width = if (profileFocused) GlassTokens.FocusOutlineWidth else 1.dp,
                        color = if (profileFocused) accent else GlassTokens.GlassBorder,
                        shape = CircleShape,
                    )
                    .focusRequester(profileFR)
                    .onFocusChanged { profileFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onProfileClick(); true }
                            Key.DirectionDown -> { onDropToContent(); true }
                            else -> false
                        }
                    }
                    .focusable()
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
                .height(1.dp)
                .background(GlassTokens.GlassBorder),
        )
    }
}

@Composable
private fun GlassNavIconButton(
    icon:           ImageVector,
    contentDesc:    String,
    focusRequester: FocusRequester,
    accent:         Color,
    onClick:        () -> Unit,
    onDown:         () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isFocused) GlassTokens.GlassSmallSolid else Color.Transparent)
            .then(
                if (isFocused) Modifier.border(GlassTokens.FocusOutlineWidth, accent, CircleShape)
                else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDesc,
            tint               = if (isFocused) accent else GlassTokens.TextSecondary,
            modifier           = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GlassNavPill(
    label:          String,
    selected:       Boolean,
    focusRequester: FocusRequester,
    accent:         Color,
    onSelect:       () -> Unit,
    onDown:         () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(GlassTokens.RadiusPill)

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
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
            .clip(pillShape)
            .background(if (selected) GlassTokens.TextPrimary else Color.Transparent)
            .then(
                if (isFocused) Modifier.border(GlassTokens.FocusOutlineWidth, accent, pillShape)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = when {
                selected  -> GlassTokens.AccentOnDark
                isFocused -> accent
                else      -> GlassTokens.TextPrimary.copy(alpha = 0.70f)
            },
        )
    }
}
