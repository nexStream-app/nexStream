package app.nexstream.player.ui.screens.picks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernPicksContent(
    visibleGroups:           List<PickGroup>,
    loading:                 Boolean,
    recentIsEmpty:           Boolean,
    firstItemFocusRequester: FocusRequester?,
    onContentFocused:        () -> Unit,
    onPickSelected:          (PickItem) -> Unit,
    modifier:                Modifier = Modifier,
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    Box(modifier = modifier.fillMaxSize().background(background)) {
        when {
            recentIsEmpty && !loading -> {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.Stars,
                        contentDescription = null,
                        tint               = textSecondary.copy(alpha = 0.35f),
                        modifier           = Modifier.size(52.dp),
                    )
                    Text(
                        text       = "Watch something first",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = textPrimary.copy(alpha = 0.7f),
                    )
                    Text(
                        text     = "Picks shows recommendations based on what you've watched.",
                        fontSize = 13.sp,
                        color    = textSecondary.copy(alpha = 0.55f),
                    )
                }
            }

            loading && visibleGroups.isEmpty() -> {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = accent)
                    Text(
                        text     = "Finding picks for you…",
                        fontSize = 14.sp,
                        color    = textSecondary,
                    )
                }
            }

            visibleGroups.isEmpty() && !loading -> {
                Text(
                    text     = "No recommendations found",
                    fontSize = 14.sp,
                    color    = textSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(visibleGroups, key = { idx, g -> "${idx}_${g.seedTitle}" }) { idx, group ->
                        ModernPickGroupRow(
                            group                   = group,
                            accent                  = accent,
                            divider                 = divider,
                            surface                 = surface,
                            firstItemFocusRequester = if (idx == 0) firstItemFocusRequester else null,
                            onContentFocused        = onContentFocused,
                            onPickSelected          = onPickSelected,
                        )
                    }
                    if (loading) {
                        item {
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier   = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color      = accent,
                                )
                                Text(
                                    text     = "Loading more picks…",
                                    fontSize = 12.sp,
                                    color    = textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Group row: "Because you watched X" header + horizontal card strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModernPickGroupRow(
    group:                   PickGroup,
    accent:                  Color,
    divider:                 Color,
    surface:                 Color,
    firstItemFocusRequester: FocusRequester?,
    onContentFocused:        () -> Unit,
    onPickSelected:          (PickItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text       = "Because you watched ${group.seedTitle}",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = accent,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(divider.copy(alpha = 0.4f)),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(group.items, key = { _, pick -> "${group.seedTitle}-${pick.tmdbId}" }) { idx, pick ->
                ModernPickCard(
                    pick           = pick,
                    accent         = accent,
                    surface        = surface,
                    focusRequester = if (idx == 0) firstItemFocusRequester else null,
                    onFocused      = { onContentFocused() },
                    onSelected     = { onPickSelected(pick) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait pick card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModernPickCard(
    pick:           PickItem,
    accent:         Color,
    surface:        Color,
    focusRequester: FocusRequester?,
    onFocused:      () -> Unit,
    onSelected:     () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "pickScale")

    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(surface)
            .border(
                width = if (isFocused.value) 2.dp else 0.dp,
                color = if (isFocused.value) accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused.value = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onSelected(); true }
                    else -> false
                }
            }
            .clickable { onSelected() }
    ) {
        AsyncImage(
            model              = pick.posterUrl,
            contentDescription = pick.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        // Bottom scrim + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Text(
            text     = pick.title,
            fontSize = 10.sp,
            color    = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        )
        // Type badge — top-left pill
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.copy(alpha = 0.9f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text       = if (pick.mediaType == "movie") "MOVIE" else "TV",
                fontSize   = 8.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
            )
        }
    }
}
