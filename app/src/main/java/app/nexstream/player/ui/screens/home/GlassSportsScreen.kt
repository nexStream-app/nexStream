package app.nexstream.player.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage

@Composable
fun GlassSportsScreen(
    events: List<MatchedSportEvent>,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    onChannelPlay: (streamUrl: String, channelName: String) -> Unit,
    onFocusNavBar: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
) {
    if (events.isEmpty()) return

    val heroEvent = events.first()
    val heroFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Hero card
        GlassSportsHero(
            event = heroEvent,
            focusRequester = heroFR,
            onFocusUp = onFocusNavBar,
            onPlay = {
                heroEvent.matchedChannels.firstOrNull()?.let { ch ->
                    onChannelPlay(ch.streamUrl, ch.channelName)
                }
            },
        )

        // Category pills
        if (categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                // "All" pill
                item {
                    GlassCategoryPill(
                        label = "All",
                        isSelected = selectedCategory == null,
                        onClick = { onCategorySelect(null) },
                    )
                }
                itemsIndexed(categories) { _, cat ->
                    GlassCategoryPill(
                        label = cat,
                        isSelected = cat == selectedCategory,
                        onClick = { onCategorySelect(cat) },
                    )
                }
            }
        }

        // Match cards row
        if (events.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                itemsIndexed(events.drop(1)) { _, event ->
                    GlassSportsMatchCard(
                        event = event,
                        onPlay = {
                            event.matchedChannels.firstOrNull()?.let { ch ->
                                onChannelPlay(ch.streamUrl, ch.channelName)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSportsHero(
    event: MatchedSportEvent,
    focusRequester: FocusRequester,
    onFocusUp: () -> Unit,
    onPlay: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val firstChannel = event.matchedChannels.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPlay(); true }
                    Key.DirectionUp -> { onFocusUp(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable { onPlay() }
            .clip(RoundedCornerShape(GlassTokens.RadiusCard))
            .background(GlassTokens.GlassSmallSolid)
            .then(
                if (isFocused)
                    Modifier.border(GlassTokens.FocusOutlineWidth, GlassTokens.AccentDefault, RoundedCornerShape(GlassTokens.RadiusCard))
                else
                    Modifier.border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusCard))
            ),
    ) {
        // Background image from first channel logo or sport logo
        AsyncImage(
            model = firstChannel?.logoUrl ?: event.sportLogoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(GlassTokens.GlassPanelSolid, Color.Transparent),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Live badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(GlassTokens.LiveBadge)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (event.isReplay) "REPLAY" else "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = event.eventName,
                    color = GlassTokens.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = event.sportCategory,
                    color = GlassTokens.AccentDefault,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = event.timeUk,
                    color = GlassTokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GlassCategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val active = isSelected || isFocused
    Box(
        modifier = Modifier
            .height(32.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
            .background(if (active) GlassTokens.AccentDefault else GlassTokens.GlassSmallSolid)
            .border(1.dp, if (active) GlassTokens.AccentDefault else GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusPill))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) GlassTokens.AccentOnDark else GlassTokens.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun GlassSportsMatchCard(
    event: MatchedSportEvent,
    onPlay: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) GlassTokens.FocusScaleMax else 1f,
        label = "matchCardScale",
    )
    val firstChannel = event.matchedChannels.firstOrNull()

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onPlay(); true
                } else false
            }
            .focusable()
            .clickable { onPlay() }
            .clip(RoundedCornerShape(GlassTokens.RadiusTile))
            .background(GlassTokens.GlassSmallSolid)
            .then(
                if (isFocused)
                    Modifier.border(GlassTokens.FocusOutlineWidth, GlassTokens.AccentDefault, RoundedCornerShape(GlassTokens.RadiusTile))
                else
                    Modifier.border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusTile))
            ),
    ) {
        // Channel logo
        if (firstChannel?.logoUrl != null) {
            AsyncImage(
                model = firstChannel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassTokens.GlassPanelSolid.copy(alpha = 0.7f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = event.eventName,
                color = GlassTokens.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = event.timeUk,
                    color = GlassTokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!event.isReplay) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassTokens.LiveBadge)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(text = "LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
