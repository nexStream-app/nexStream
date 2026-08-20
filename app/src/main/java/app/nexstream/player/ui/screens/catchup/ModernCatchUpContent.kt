package app.nexstream.player.ui.screens.catchup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary
import java.text.SimpleDateFormat

@Composable
fun ModernCatchUpContent(
    programmes: List<ProgramEntity>,
    thumbnails: Map<String, String>,
    catchUpChannels: List<ChannelEntity>,
    isLoading: Boolean,
    today: Long,
    timeFormat: SimpleDateFormat,
    onProgrammeClick: (title: String) -> Unit,
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current

    val postered = remember(programmes, thumbnails) {
        programmes.filter { thumbnails[it.title] != null }
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 130.dp),
        modifier              = Modifier.fillMaxSize().background(background),
        contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = accent) }
                }
            }
            postered.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text     = "No programmes available",
                            color    = textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            else -> {
                items(
                    count = postered.size,
                    key   = { postered[it].title },
                ) { index ->
                    val prog    = postered[index]
                    val poster  = thumbnails[prog.title]
                    val channel = remember(prog.channelId, catchUpChannels) {
                        catchUpChannels.firstOrNull { it.epgChannelId == prog.channelId }
                            ?: catchUpChannels.firstOrNull { it.id == prog.channelId }
                    }
                    CatchUpPosterCard(
                        posterUrl   = poster,
                        title       = prog.title,
                        channelName = channel?.name?.take(14),
                        startTime   = prog.startTime,
                        endTime     = prog.endTime,
                        today       = today,
                        timeFormat  = timeFormat,
                        accent      = accent,
                        surface     = surface,
                        textPrimary = textPrimary,
                        onClick     = { onProgrammeClick(prog.title) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatchUpDateChip(
    label: String,
    selected: Boolean,
    accent: Color,
    textPrimary: Color,
    divider: Color,
    onClick: () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "chipScale")

    val borderColor = if (selected || isFocused.value) accent else divider
    val bgColor     = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor   = if (selected || isFocused.value) accent else textPrimary

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50.dp))
            .onFocusChanged { isFocused.value = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, color = textColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Poster card — portrait with channel badge + time overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatchUpPosterCard(
    posterUrl:   String?,
    title:       String,
    channelName: String?,
    startTime:   Long,
    endTime:     Long,
    today:       Long,
    timeFormat:  SimpleDateFormat,
    accent:      Color,
    surface:     Color,
    textPrimary: Color,
    onClick:     () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "catchUpScale")

    val timeLabel = remember(startTime) { timeFormat.format(java.util.Date(startTime)) }
    val durMins   = remember(startTime, endTime) { ((endTime - startTime) / 60_000L).toInt() }
    val durLabel  = remember(durMins) {
        when {
            durMins / 60 > 0 && durMins % 60 > 0 -> "${durMins / 60}h ${durMins % 60}m"
            durMins / 60 > 0                      -> "${durMins / 60}h"
            else                                  -> "${durMins}m"
        }
    }

    Column(
        modifier            = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .onFocusChanged { isFocused.value = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                        else -> false
                    }
                }
                .clickable { onClick() }
        ) {
            AsyncImage(
                model              = posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            // Bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            // Time + duration
            Text(
                text     = "$timeLabel · $durLabel",
                fontSize = 10.sp,
                color    = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
            // Channel name badge — top-right dark pill
            if (channelName != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text     = channelName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color    = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text     = title,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
