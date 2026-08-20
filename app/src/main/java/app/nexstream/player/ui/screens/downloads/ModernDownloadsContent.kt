package app.nexstream.player.ui.screens.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.nexstream.player.downloads.DownloadItem
import app.nexstream.player.downloads.DownloadStatus
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernDownloadsContent(
    downloads: List<DownloadItem>,
    firstItemFocusRequester: FocusRequester?,
    onCardClick: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    onRequestSidebarFocus: () -> Unit = {},
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    if (downloads.isEmpty()) {
        Box(
            modifier         = modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Download,
                    contentDescription = null,
                    tint               = textSecondary.copy(alpha = 0.35f),
                    modifier           = Modifier.size(52.dp),
                )
                Text(
                    text       = "No downloads yet",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = textPrimary.copy(alpha = 0.7f),
                )
                Text(
                    text     = "Long-press a movie to download it",
                    fontSize = 13.sp,
                    color    = textSecondary.copy(alpha = 0.55f),
                )
            }
        }
        return
    }

    val active    = remember(downloads) { downloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED } }
    val completed = remember(downloads) { downloads.filter { it.status == DownloadStatus.COMPLETED } }
    val failed    = remember(downloads) { downloads.filter { it.status == DownloadStatus.FAILED } }

    val firstItem = remember(active, completed, failed) {
        (active + completed + failed).firstOrNull()
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 130.dp),
        modifier              = modifier.fillMaxSize().background(background),
        contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (active.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DownloadSectionHeader("Downloading (${active.size})", accent, divider)
            }
            itemsIndexed(active, key = { _, it -> "a_${it.downloadId}" }) { idx, item ->
                DownloadPosterCard(
                    item            = item,
                    accent          = accent,
                    surface         = surface,
                    textPrimary     = textPrimary,
                    focusRequester  = if (idx == 0 && item == firstItem) firstItemFocusRequester else null,
                    onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                    onClick         = { onCardClick(item) },
                )
            }
        }
        if (completed.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DownloadSectionHeader("Completed (${completed.size})", accent, divider)
            }
            itemsIndexed(completed, key = { _, it -> "c_${it.downloadId}" }) { idx, item ->
                DownloadPosterCard(
                    item            = item,
                    accent          = accent,
                    surface         = surface,
                    textPrimary     = textPrimary,
                    focusRequester  = if (idx == 0 && item == firstItem) firstItemFocusRequester else null,
                    onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                    onClick         = { onCardClick(item) },
                )
            }
        }
        if (failed.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DownloadSectionHeader("Failed (${failed.size})", accent, divider)
            }
            itemsIndexed(failed, key = { _, it -> "f_${it.downloadId}" }) { idx, item ->
                DownloadPosterCard(
                    item            = item,
                    accent          = accent,
                    surface         = surface,
                    textPrimary     = textPrimary,
                    focusRequester  = if (idx == 0 && item == firstItem) firstItemFocusRequester else null,
                    onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                    onClick         = { onCardClick(item) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait card with status badge + animated progress bar overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadPosterCard(
    item: DownloadItem,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    focusRequester: FocusRequester?,
    onDirectionLeft: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context   = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }
    val scale     by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "dlScale")

    val isActive = item.status == DownloadStatus.RUNNING ||
                   item.status == DownloadStatus.PENDING ||
                   item.status == DownloadStatus.PAUSED

    val animProgress by animateFloatAsState(
        targetValue    = item.progressPercent / 100f,
        animationSpec  = tween(600),
        label          = "dlProgress",
    )

    val statusColor = when (item.status) {
        DownloadStatus.RUNNING   -> accent
        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
        DownloadStatus.FAILED    -> Color(0xFFF44336)
        DownloadStatus.PAUSED    -> Color(0xFFFFC107)
        DownloadStatus.PENDING   -> Color(0xFF9E9E9E)
    }

    val statusLabel = when (item.status) {
        DownloadStatus.RUNNING   -> "${item.progressPercent}%"
        DownloadStatus.COMPLETED -> "Done"
        DownloadStatus.FAILED    -> "Failed"
        DownloadStatus.PAUSED    -> "Paused"
        DownloadStatus.PENDING   -> "Waiting"
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
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused.value = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                        Key.DirectionLeft -> if (onDirectionLeft != null) { onDirectionLeft(); true } else false
                        else -> false
                    }
                }
                .clickable { onClick() }
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Movie,
                    contentDescription = null,
                    tint               = textPrimary.copy(alpha = 0.25f),
                    modifier           = Modifier.align(Alignment.Center).size(36.dp),
                )
            }

            // Bottom scrim
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

            // Progress bar overlay — bottom edge, accent track over dark background
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            }

            // Status badge — top-right pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.9f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = statusLabel,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
            }

            // Completed: play icon overlay when focused
            if (item.status == DownloadStatus.COMPLETED && isFocused.value) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }
        }

        Text(
            text     = item.title,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadSectionHeader(
    title: String,
    accent: Color,
    divider: Color,
) {
    Column(
        modifier            = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text       = title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = accent,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.4f)),
        )
    }
}
