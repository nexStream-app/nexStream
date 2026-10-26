package app.nexstream.player.ui.screens.epg

import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.ui.components.GlasCategoryPills
import app.nexstream.player.ui.screens.main.MainScreenViewModel
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private val QUALITY_RE = Regex(
    """[\s._\-(\[](4K|UHD|2160[Pp]?|FHD|1080[Pp]?|HD|720[Pp]?|576[Pp]?|480[Pp]?|SD)[\s.)_\]-]*$""",
    setOf(RegexOption.IGNORE_CASE)
)
private val QUALITY_RANK = mapOf(
    "4K" to 6, "UHD" to 6, "2160P" to 6,
    "FHD" to 5, "1080P" to 5,
    "HD" to 4, "720P" to 4,
    "576P" to 3, "480P" to 2, "SD" to 1
)

private fun channelBaseName(name: String): String =
    QUALITY_RE.replace(name, "").trim()

private fun channelQualityTag(name: String): String? =
    QUALITY_RE.find(name)?.groupValues?.getOrNull(1)?.uppercase()

private fun qualityRank(name: String): Int =
    QUALITY_RANK[channelQualityTag(name)] ?: 0

internal data class GlassChannelRow(
    val baseName: String,
    val best: ChannelEntity,
    val qualityTag: String?,
    val alternates: List<ChannelEntity>,
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun GlassGuide(
    selectedCategory: String?,
    isContentFocused: Boolean,
    watchlistIds: Set<String> = emptySet(),
    onToggleChannelWatchlist: (channelId: String, channelName: String, logoUrl: String?, streamUrl: String?) -> Unit = { _, _, _, _ -> },
    onFocusUp: () -> Unit = {},
    onChannelPlay: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onCategorySelect: (String?) -> Unit = {},
    mainViewModel: MainScreenViewModel = hiltViewModel(),
    viewModel: EPGViewModel = hiltViewModel(),
) {
    val allChannels by mainViewModel.allChannels.collectAsState()
    val programsMap by viewModel.programsMap.collectAsState()
    val blockedTvCategories by viewModel.profileManager.blockedTvCategories.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Filter channels by category
    val channels = remember(allChannels, selectedCategory, blockedTvCategories, watchlistIds) {
        val trimmedBlocked = blockedTvCategories.map { it.trim() }.toSet()
        val filtered = if (trimmedBlocked.isEmpty()) allChannels
        else allChannels.filter { it.groupTitle == null || it.groupTitle.trim() !in trimmedBlocked }
        when {
            selectedCategory == null             -> filtered
            selectedCategory == "__favourites__" -> filtered.filter { it.id in watchlistIds }
            else -> filtered.filter { it.groupTitle?.trim() == selectedCategory.trim() }
        }
    }

    // Merge FHD/HD/SD variants of the same channel
    val channelRows = remember(channels) {
        channels
            .groupBy { channelBaseName(it.name) }
            .map { (base, group) ->
                val sorted = group.sortedByDescending { qualityRank(it.name) }
                val best = sorted.first()
                GlassChannelRow(
                    baseName = base.ifBlank { best.name },
                    best = best,
                    qualityTag = channelQualityTag(best.name),
                    alternates = sorted.drop(1),
                )
            }
    }

    // Category groups for chips
    val categoryGroups = remember(allChannels, blockedTvCategories) {
        val trimmedBlocked = blockedTvCategories.map { it.trim() }.toSet()
        allChannels
            .filter { it.groupTitle == null || it.groupTitle.trim() !in trimmedBlocked }
            .mapNotNull { it.groupTitle?.trim() }
            .distinct()
            .sorted()
    }

    // Background preview player
    val previewPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { previewPlayer.release() } }

    var previewChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var previewReady by remember { mutableStateOf(false) }

    LaunchedEffect(previewChannel) {
        previewReady = false
        val ch = previewChannel ?: return@LaunchedEffect
        previewPlayer.stop()
        previewPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
        previewPlayer.prepare()
        previewPlayer.play()
        previewReady = true
    }
    LaunchedEffect(isContentFocused) {
        if (!isContentFocused) { previewPlayer.stop(); previewReady = false }
    }

    // Load programmes for visible channels
    LaunchedEffect(channelRows) {
        val epgIds = channelRows.take(60).map { row ->
            row.best.epgChannelId?.takeIf { it.isNotEmpty() } ?: row.best.id
        }
        viewModel.loadProgramsForVisibleChannels(epgIds)
    }

    val listState = rememberLazyListState()
    val firstRowFR = remember { FocusRequester() }

    LaunchedEffect(channelRows) {
        if (channelRows.isNotEmpty() && isContentFocused) {
            kotlinx.coroutines.delay(100)
            try { firstRowFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Full-screen preview ───────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = previewPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = previewPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Gradient scrim over video so panel reads clearly
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.40f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.92f),
                )
            )
        )

        // ── Glass panel (right 55%) ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.56f)
                .background(
                    GlassTokens.GlassPanelSolid,
                    RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel)
                )
                .border(
                    width = 1.dp,
                    color = GlassTokens.GlassBorder,
                    shape = RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel)
                )
                .clip(RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Text(
                    text = when (selectedCategory) {
                        "__favourites__" -> "Favourites"
                        null -> "All Channels"
                        else -> selectedCategory
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = GlassTokens.PanelPadding,
                        end = GlassTokens.PanelPadding,
                        top = 20.dp,
                        bottom = 8.dp,
                    )
                )

                // Category chips
                if (categoryGroups.isNotEmpty()) {
                    GlasCategoryPills(
                        categories = categoryGroups,
                        selectedCategory = selectedCategory.takeIf { it != "__favourites__" },
                        modifier = Modifier.padding(horizontal = GlassTokens.PanelPadding, vertical = 4.dp),
                        onSelect = onCategorySelect,
                        onFocusDown = { try { firstRowFR.requestFocus() } catch (_: Exception) {} },
                    )
                }

                HorizontalDivider(
                    color = GlassTokens.GlassBorder,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Channel list
                if (channelRows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedCategory == "__favourites__") "No favourites yet" else "No channels",
                            color = GlassTokens.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(channelRows) { index, row ->
                            GlassChannelRowItem(
                                row = row,
                                programsMap = programsMap,
                                modifier = if (index == 0) Modifier.focusRequester(firstRowFR) else Modifier,
                                onFocused = { ch -> previewChannel = ch },
                                onKeyEvent = { ev ->
                                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp && index == 0) {
                                        onFocusUp(); true
                                    } else false
                                },
                                onPlay = { url, name ->
                                    onChannelPlay(url, name)
                                },
                                onLoadMorePrograms = { epgId ->
                                    scope.launch { viewModel.loadProgramsForVisibleChannels(listOf(epgId)) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Preview channel name overlay (top-left of video area)
        previewChannel?.let { ch ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 40.dp, top = 40.dp)
            ) {
                AsyncImage(
                    model = ch.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = channelBaseName(ch.name).ifBlank { ch.name },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun GlassChannelRowItem(
    row: GlassChannelRow,
    programsMap: Map<String, List<app.nexstream.player.data.local.entity.ProgramEntity>>,
    modifier: Modifier = Modifier,
    onFocused: (ChannelEntity) -> Unit = {},
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    onPlay: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onLoadMorePrograms: (epgId: String) -> Unit = {},
) {
    val now = remember { System.currentTimeMillis() }
    val epgKey = row.best.epgChannelId?.takeIf { it.isNotEmpty() } ?: row.best.id
    val programmes = programsMap[epgKey] ?: emptyList()
    val currentProg = programmes.firstOrNull { now in it.startTime..it.endTime }
    val nextProg = currentProg?.let { cp -> programmes.firstOrNull { it.startTime >= cp.endTime } }
    val progress = currentProg?.let { cp ->
        ((now - cp.startTime).toFloat() / (cp.endTime - cp.startTime).toFloat()).coerceIn(0f, 1f)
    } ?: 0f

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val endTimeStr = currentProg?.let { timeFmt.format(Date(it.endTime)) }

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isFocused) GlassTokens.FocusScaleMin else 1f,
        animationSpec = tween(GlassTokens.FocusScaleDurationMs)
    )

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused(row.best)
            onLoadMorePrograms(epgKey)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) GlassTokens.AccentDefault.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) GlassTokens.FocusOutlineWidth else 0.dp,
                color = if (isFocused) GlassTokens.AccentDefault else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPlay(row.best.streamUrl, row.baseName) }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        onPlay(row.best.streamUrl, row.baseName); true
                    }
                    else -> onKeyEvent(ev)
                }
            }
            .padding(horizontal = GlassTokens.PanelPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Channel logo
        AsyncImage(
            model = row.best.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Fit,
        )

        // Channel name + programme info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.baseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.qualityTag != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = GlassTokens.AccentBlue.copy(alpha = 0.25f),
                    ) {
                        Text(
                            text = row.qualityTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTokens.AccentBlue,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (currentProg != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProg.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress > 0f) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = if (isFocused) GlassTokens.AccentDefault else GlassTokens.AccentBlue,
                            trackColor = GlassTokens.GlassBorder,
                        )
                        if (endTimeStr != null) {
                            Text(
                                text = endTimeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.TextTertiary,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                if (nextProg != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Next: ${nextProg.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Play icon when focused
        if (isFocused) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = GlassTokens.AccentDefault,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
