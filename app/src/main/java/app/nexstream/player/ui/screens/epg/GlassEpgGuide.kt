package app.nexstream.player.ui.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.ui.components.GlasCategoryPills
import app.nexstream.player.ui.screens.main.MainScreenViewModel
import app.nexstream.player.ui.theme.GlassTokens
import app.nexstream.player.ui.theme.LocalNsAccent
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val SLOT_MINS  = 30
private val SLOT_W     = 220.dp
private val CHAN_COL_W = 130.dp
private val ROW_H      = 52.dp
private val HDR_H      = 28.dp
private const val SLOT_COUNT = 10   // 5 hours visible

private fun epgStartMs(): Long {
    val now = System.currentTimeMillis()
    // Round down to 30-min boundary then back one slot so "now" is visible
    return now - (now % (SLOT_MINS * 60_000L)) - SLOT_MINS * 60_000L
}

private fun formatHm(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun GlassEpgGuide(
    selectedCategory: String?,
    guideCategories: List<String>,
    isContentFocused: Boolean,
    watchlistIds: Set<String> = emptySet(),
    firstItemFocusRequester: FocusRequester? = null,
    onFocusUp: () -> Unit = {},
    onChannelPlay: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onCategorySelect: (String?) -> Unit = {},
    mainViewModel: MainScreenViewModel = hiltViewModel(),
    viewModel: EPGViewModel = hiltViewModel(),
) {
    val allChannels      by mainViewModel.allChannels.collectAsState()
    val programsMap      by viewModel.programsMap.collectAsState()
    val blockedCats      by viewModel.profileManager.blockedTvCategories.collectAsState()
    val scope            = rememberCoroutineScope()
    val accent           = LocalNsAccent.current
    val density          = LocalDensity.current

    val channels = remember(allChannels, selectedCategory, blockedCats, watchlistIds) {
        val blocked = blockedCats.map { it.trim() }.toSet()
        val base = if (blocked.isEmpty()) allChannels
                   else allChannels.filter { it.groupTitle == null || it.groupTitle.trim() !in blocked }
        when {
            selectedCategory == null             -> base
            selectedCategory == "__favourites__" -> base.filter { it.id in watchlistIds }
            else -> base.filter { it.groupTitle?.trim() == selectedCategory.trim() }
        }
    }

    val startMs = remember { epgStartMs() }
    val endMs   = startMs + SLOT_COUNT * SLOT_MINS * 60_000L

    LaunchedEffect(channels) {
        val ids = channels.take(80).map { it.epgChannelId?.takeIf { id -> id.isNotEmpty() } ?: it.id }
        viewModel.loadProgramsForVisibleChannels(ids)
    }

    // Pre-compute per-channel programme lists so the LazyColumn items stay lightweight
    val channelProgs = remember(channels, programsMap, startMs) {
        channels.associateWith { ch ->
            val epgId = ch.epgChannelId?.takeIf { it.isNotEmpty() } ?: ch.id
            (programsMap[epgId] ?: emptyList())
                .filter { it.endTime > startMs && it.startTime < endMs }
                .sortedBy { it.startTime }
        }
    }

    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedProgram by remember { mutableStateOf<ProgramEntity?>(null) }

    LaunchedEffect(channels) {
        if (focusedChannel == null && channels.isNotEmpty()) focusedChannel = channels.first()
    }
    LaunchedEffect(focusedChannel, programsMap) {
        val ch = focusedChannel ?: return@LaunchedEffect
        val epgId = ch.epgChannelId?.takeIf { it.isNotEmpty() } ?: ch.id
        val now = System.currentTimeMillis()
        focusedProgram = programsMap[epgId]?.firstOrNull { it.startTime <= now && it.endTime > now }
    }

    val hScroll   = rememberScrollState()
    val listState = rememberLazyListState()
    val slotWidthPx = with(density) { SLOT_W.toPx() }

    // Scroll so "now" is near-visible on first render
    LaunchedEffect(Unit) {
        val nowOff = ((System.currentTimeMillis() - startMs).toFloat() / (SLOT_MINS * 60_000f) * slotWidthPx - 80f)
            .toInt().coerceAtLeast(0)
        hScroll.scrollTo(nowOff)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.Background),
    ) {

        // ── Info bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GlassTokens.ScreenGutter, end = GlassTokens.ScreenGutter,
                         top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val prog = focusedProgram
                val ch   = focusedChannel
                Text(
                    text = prog?.title ?: ch?.name ?: "Guide",
                    color = GlassTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (prog != null) {
                    val desc = buildString {
                        ch?.name?.let { append(it); append("  ·  ") }
                        append(formatHm(prog.startTime)); append(" – "); append(formatHm(prog.endTime))
                        prog.description?.takeIf { it.isNotEmpty() }?.let { append("  ·  "); append(it.take(100)) }
                    }
                    Text(
                        text = desc,
                        color = GlassTokens.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            GlasCategoryPills(
                categories = guideCategories,
                selectedCategory = selectedCategory,
                onSelect = onCategorySelect,
            )
        }

        // ── Time header ───────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().height(HDR_H)) {
            // Corner spacer aligned with channel column
            Box(Modifier.width(CHAN_COL_W).fillMaxHeight())
            // Time slot labels (share hScroll with programme rows)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScroll),
            ) {
                repeat(SLOT_COUNT) { i ->
                    Box(
                        modifier = Modifier.width(SLOT_W).fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = formatHm(startMs + i * SLOT_MINS * 60_000L),
                            color = GlassTokens.TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // Header separator
        Box(Modifier.fillMaxWidth().height(1.dp).background(GlassTokens.GlassBorder))

        // ── Channel rows ──────────────────────────────────────────────────────
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { rowIdx, channel ->
                val programmes = channelProgs[channel] ?: emptyList()
                val isFirst    = rowIdx == 0

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(ROW_H),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Channel info cell
                        Row(
                            modifier = Modifier
                                .width(CHAN_COL_W)
                                .fillMaxHeight()
                                .background(GlassTokens.GlassPanelSolid)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!channel.logoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model              = channel.logoUrl,
                                    contentDescription = null,
                                    contentScale       = ContentScale.Fit,
                                    modifier           = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text     = channel.name,
                                color    = GlassTokens.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Programme cells (horizontally scrollable, shared hScroll)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(hScroll),
                        ) {
                            EpgRowContent(
                                channel         = channel,
                                programmes      = programmes,
                                startMs         = startMs,
                                endMs           = endMs,
                                slotWidthPx     = slotWidthPx,
                                isFirstRow      = isFirst,
                                firstItemFR     = if (isFirst) firstItemFocusRequester else null,
                                accent          = accent,
                                onFocused       = { ch, prog ->
                                    focusedChannel = ch
                                    focusedProgram = prog
                                },
                                onScrollTo      = { px -> scope.launch { hScroll.animateScrollTo(px) } },
                                onFocusUp       = if (isFirst) onFocusUp else null,
                                onPlay          = { onChannelPlay(channel.streamUrl, channel.name) },
                            )
                        }
                    }
                    // Row separator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(GlassTokens.GlassBorder.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgRowContent(
    channel:     ChannelEntity,
    programmes:  List<ProgramEntity>,
    startMs:     Long,
    endMs:       Long,
    slotWidthPx: Float,
    isFirstRow:  Boolean,
    firstItemFR: FocusRequester?,
    accent:      Color,
    onFocused:   (ChannelEntity, ProgramEntity?) -> Unit,
    onScrollTo:  (Int) -> Unit,
    onFocusUp:   (() -> Unit)?,
    onPlay:      () -> Unit,
) {
    val now         = System.currentTimeMillis()
    val totalWidth  = SLOT_W * SLOT_COUNT
    val slotMsFloat = (SLOT_MINS * 60_000).toFloat()

    fun msToX(ms: Long) = ((ms - startMs).toFloat() / slotMsFloat * SLOT_W.value).dp

    if (programmes.isEmpty()) {
        // No EPG — one tappable block for the whole range
        EpgCell(
            title       = "",
            isNow       = true,
            width       = totalWidth,
            accent      = accent,
            focusReq    = firstItemFR,
            onFocused   = { onFocused(channel, null) },
            onScrollTo  = { onScrollTo(0) },
            onFocusUp   = onFocusUp,
            onPlay      = onPlay,
        )
        return
    }

    // Fill gap before first programme
    val firstVisible = programmes.first()
    val gridLeft     = maxOf(firstVisible.startTime, startMs)
    if (gridLeft > startMs) {
        Spacer(Modifier.width(msToX(gridLeft)).fillMaxHeight())
    }

    programmes.forEachIndexed { idx, prog ->
        val dispStart  = maxOf(prog.startTime, startMs)
        val dispEnd    = minOf(prog.endTime, endMs)
        if (dispEnd <= dispStart) return@forEachIndexed

        // Gap between programmes
        if (idx > 0) {
            val prev    = programmes[idx - 1]
            val gapFrom = minOf(maxOf(prev.endTime, startMs), endMs)
            val gapTo   = dispStart
            if (gapTo > gapFrom) {
                Spacer(Modifier.width(msToX(gapTo) - msToX(gapFrom)).fillMaxHeight())
            }
        }

        val cellW     = msToX(dispEnd) - msToX(dispStart)
        val scrollPx  = ((dispStart - startMs).toFloat() / slotMsFloat * slotWidthPx - 40f)
            .toInt().coerceAtLeast(0)

        EpgCell(
            title      = prog.title,
            isNow      = prog.startTime <= now && prog.endTime > now,
            width      = cellW,
            accent     = accent,
            focusReq   = if (isFirstRow && idx == 0) firstItemFR else null,
            onFocused  = { onFocused(channel, prog) },
            onScrollTo = { onScrollTo(scrollPx) },
            onFocusUp  = if (isFirstRow) onFocusUp else null,
            onPlay     = onPlay,
        )
    }

    // Fill tail gap after last programme
    val lastEnd = minOf(programmes.last().endTime, endMs)
    if (lastEnd < endMs) {
        Spacer(Modifier.width(msToX(endMs) - msToX(lastEnd)).fillMaxHeight())
    }
}

@Composable
private fun EpgCell(
    title:     String,
    isNow:     Boolean,
    width:     Dp,
    accent:    Color,
    focusReq:  FocusRequester?,
    onFocused: () -> Unit,
    onScrollTo: () -> Unit,
    onFocusUp: (() -> Unit)?,
    onPlay:    () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> accent
                    isNow     -> GlassTokens.GlassPanelSolid
                    else      -> GlassTokens.GlassPanelSolid.copy(alpha = 0.45f)
                }
            )
            .then(
                if (isNow && !isFocused)
                    Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .focusRequester(focusReq ?: fr)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) { onFocused(); onScrollTo() }
            }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { onFocusUp?.invoke(); onFocusUp != null }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPlay(); true }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (title.isNotEmpty()) {
            Text(
                text       = title,
                color      = if (isFocused) GlassTokens.AccentOnDark
                             else if (isNow) GlassTokens.TextPrimary
                             else GlassTokens.TextSecondary,
                fontSize   = 11.sp,
                fontWeight = if (isNow) FontWeight.Medium else FontWeight.Normal,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}
