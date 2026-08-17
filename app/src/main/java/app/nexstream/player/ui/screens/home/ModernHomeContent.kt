package app.nexstream.player.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.ui.screens.picks.PickGroup
import app.nexstream.player.ui.screens.picks.PickItem
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary
import androidx.compose.material3.CircularProgressIndicator
import app.nexstream.player.ui.components.NexStreamBanner

@Composable
fun ModernHomeContent(
    firstItemFocusRequester: FocusRequester? = null,
    homeRestoreTick: Int = 0,
    continueWatching: List<RecentlyWatchedEntity>,
    picksGroups: List<PickGroup>,
    sportsEvents: List<MatchedSportEvent> = emptyList(),
    selectedSportCategory: String? = null,
    isLoadingSports: Boolean = false,
    watchProgressFractions: Map<String, Float> = emptyMap(),
    currentUkMinutes: Int = 0,
    allSportsCategories: List<String> = emptyList(),
    sportsHiddenCategories: Set<String> = emptySet(),
    sportsCategoryOrder: List<String> = emptyList(),
    onContinueWatchingClick: (RecentlyWatchedEntity) -> Unit = {},
    onPickClick: (PickItem) -> Unit = {},
    onSportChannelClick: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onGoToEpg: (channelName: String) -> Unit = { _ -> },
    onSaveSportsPreferences: (hidden: Set<String>, order: List<String>) -> Unit = { _, _ -> },
    reminderIds: Set<String> = emptySet(),
    onSetSportReminder: (channelId: String, channelName: String, streamUrl: String, title: String, startMs: Long) -> Unit = { _, _, _, _, _ -> },
    onCancelSportReminder: (channelId: String, startMs: Long) -> Unit = { _, _ -> },
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current
    val surface       = LocalNsSurface.current
    val nsTheme       = LocalNexStreamTheme.current
    val sTheme        = nsTheme.sidebar
    val headerHeight  = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var lastClickedCardFR by remember { mutableStateOf<FocusRequester?>(null) }
    var showSportsEdit by remember { mutableStateOf(false) }
    LaunchedEffect(homeRestoreTick) {
        if (homeRestoreTick > 0) {
            kotlinx.coroutines.delay(100)
            try { lastClickedCardFR?.requestFocus() } catch (_: Exception) {}
        }
    }

    val continueItems = remember(continueWatching) {
        continueWatching.filter {
            it.type == RecentlyWatchedType.MOVIE || it.type == RecentlyWatchedType.EPISODE
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(background)) {
        // Fixed header — exactly like SeriesScreen/MoviesScreen
        Row(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = if (selectedSportCategory == null) "Today's Live Sport" else "Today's $selectedSportCategory",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
            if (allSportsCategories.isNotEmpty()) {
                var editBtnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (editBtnFocused) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            width = if (editBtnFocused) 1.5.dp else 0.dp,
                            color = if (editBtnFocused) accent else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .onFocusChanged { editBtnFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (e.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { showSportsEdit = true; true }
                                else -> false
                            }
                        }
                        .clickable { showSportsEdit = true }
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
                        Text("Edit", fontSize = 12.sp, color = accent)
                    }
                }
            }
        }
        HorizontalDivider(color = sTheme.divider)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        // Invisible focus anchor — only needed when the sports section is absent
        // (when sports exist, firstItemFocusRequester goes directly on the first card)
        if (firstItemFocusRequester != null && sportsEvents.isEmpty()) {
            Box(modifier = Modifier.size(1.dp).focusRequester(firstItemFocusRequester).focusable())
        }

        Spacer(Modifier.height(16.dp))

        // Loading spinner
        if (isLoadingSports && sportsEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    Text("Fetching Today's Live Sport…", style = MaterialTheme.typography.bodySmall, color = textSecondary)
                }
            }
        }

        // Today's Live Sport — grouped by sport category
        if (sportsEvents.isNotEmpty()) {
            val sportGroups = remember(sportsEvents, sportsCategoryOrder) {
                val grouped = sportsEvents.groupBy { it.sportCategory }
                val result = linkedMapOf<String, List<MatchedSportEvent>>()
                for (cat in sportsCategoryOrder) grouped[cat]?.let { result[cat] = it }
                grouped.keys.filter { it !in result }.sorted().forEach { result[it] = grouped[it]!! }
                result
            }
            sportGroups.entries.forEachIndexed { groupIndex, (sport, events) ->
                val logoUrl = events.first().sportLogoUrl
                Row(
                    modifier              = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (logoUrl.isNotEmpty()) {
                        AsyncImage(
                            model              = logoUrl,
                            contentDescription = null,
                            modifier           = Modifier.size(16.dp),
                            contentScale       = ContentScale.Fit,
                        )
                    }
                    Text(sport, fontSize = 12.sp, color = textSecondary, fontWeight = FontWeight.SemiBold)
                }
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(events, key = { _, e -> e.id }) { itemIndex, event ->
                        val cardFR = remember { FocusRequester() }
                        val eventChannelId = "sport_${event.id}"
                        val eventStartMs = computeEventStartMs(event.timeUk)
                        val remId = "${eventChannelId}_${eventStartMs}"
                        val hasReminder = reminderIds.contains(remId)
                        SportEventCard(
                            event            = event,
                            accent           = accent,
                            surface          = surface,
                            textPrimary      = textPrimary,
                            textSecondary    = textSecondary,
                            currentUkMinutes = currentUkMinutes,
                            hasReminder      = hasReminder,
                            onChannelClick   = { url, name ->
                                lastClickedCardFR = cardFR
                                onSportChannelClick(url, name)
                            },
                            onGoToEpg        = onGoToEpg,
                            onRemind         = {
                                val ch = event.matchedChannels.firstOrNull()
                                if (hasReminder) {
                                    onCancelSportReminder(eventChannelId, eventStartMs)
                                    bannerMessage = "Reminder cancelled for ${event.eventName}"
                                } else {
                                    onSetSportReminder(
                                        eventChannelId,
                                        ch?.channelName ?: event.eventName,
                                        ch?.streamUrl ?: "",
                                        event.eventName,
                                        eventStartMs
                                    )
                                    bannerMessage = "Reminder set for ${event.eventName}"
                                }
                            },
                            focusRequester   = if (groupIndex == 0 && itemIndex == 0) firstItemFocusRequester ?: cardFR else cardFR,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(12.dp))
        }


        Spacer(Modifier.height(32.dp))
        } // inner scrollable Column
    } // outer Column
    NexStreamBanner(message = bannerMessage, onDismiss = { bannerMessage = null })

    if (showSportsEdit) {
        SportsEditDialog(
            allCategories    = allSportsCategories,
            hiddenCategories = sportsHiddenCategories,
            categoryOrder    = sportsCategoryOrder,
            accent           = accent,
            onDismiss        = { showSportsEdit = false },
            onSave           = { hidden, order ->
                onSaveSportsPreferences(hidden, order)
                showSportsEdit = false
            },
        )
    }
    } // Box
}

// ─────────────────────────────────────────────────────────────────────────────
// Sports category edit dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SportsEditDialog(
    allCategories: List<String>,
    hiddenCategories: Set<String>,
    categoryOrder: List<String>,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (hidden: Set<String>, order: List<String>) -> Unit,
) {
    // Build the working list: ordered categories first, then any new ones alphabetically
    val initialOrder = remember(allCategories, categoryOrder) {
        val ordered = categoryOrder.filter { it in allCategories }
        val remaining = allCategories.filter { it !in ordered }.sorted()
        ordered + remaining
    }
    var workingOrder   by remember { mutableStateOf(initialOrder) }
    var workingHidden  by remember { mutableStateOf(hiddenCategories.toMutableSet() as Set<String>) }

    // Focus state: selectedRow in [0..workingOrder.lastIndex], or workingOrder.size = Save, +1 = Cancel
    val saveIdx   = workingOrder.size
    val cancelIdx = workingOrder.size + 1
    var selectedRow    by remember { mutableStateOf(0) }
    // Within a category row, column: 0=toggle, 1=move-up, 2=move-down
    var selectedCol    by remember { mutableStateOf(0) }
    val inButtonRow    = selectedRow >= saveIdx

    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { dialogFR.requestFocus() }
    }

    val dialogBackground = LocalNsBackground.current
    val surface          = LocalNsSurface.current
    val textPrimary      = LocalNsTextPrimary.current
    val textSecondary    = LocalNsTextSecondary.current

    fun toggleVisibility(cat: String) {
        workingHidden = if (cat in workingHidden) workingHidden - cat else workingHidden + cat
    }
    fun moveUp(idx: Int) {
        if (idx <= 0) return
        val list = workingOrder.toMutableList()
        val tmp = list[idx]; list[idx] = list[idx - 1]; list[idx - 1] = tmp
        workingOrder = list
        selectedRow = idx - 1
    }
    fun moveDown(idx: Int) {
        if (idx >= workingOrder.lastIndex) return
        val list = workingOrder.toMutableList()
        val tmp = list[idx]; list[idx] = list[idx + 1]; list[idx + 1] = tmp
        workingOrder = list
        selectedRow = idx + 1
    }

    fun confirm() {
        if (inButtonRow) {
            if (selectedRow == saveIdx) onSave(workingHidden, workingOrder)
            else onDismiss()
        } else {
            when (selectedCol) {
                0 -> toggleVisibility(workingOrder[selectedRow])
                1 -> moveUp(selectedRow)
                2 -> moveDown(selectedRow)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.65f).wrapContentHeight(),
            color          = dialogBackground,
            tonalElevation = 0.dp,
            shape          = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> {
                                if (inButtonRow) {
                                    selectedRow = workingOrder.lastIndex.coerceAtLeast(0)
                                } else {
                                    selectedRow = (selectedRow - 1).coerceAtLeast(0)
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                selectedRow = (selectedRow + 1).coerceAtMost(cancelIdx)
                                true
                            }
                            Key.DirectionLeft -> {
                                if (!inButtonRow) selectedCol = (selectedCol - 1).coerceAtLeast(0)
                                else selectedRow = saveIdx
                                true
                            }
                            Key.DirectionRight -> {
                                if (!inButtonRow) selectedCol = (selectedCol + 1).coerceAtMost(2)
                                else selectedRow = cancelIdx
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { confirm(); true }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text       = "Edit Sport Categories",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = textPrimary,
                    modifier   = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text     = "Toggle visibility and reorder with the arrow buttons.",
                    fontSize = 12.sp,
                    color    = textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                workingOrder.forEachIndexed { idx, cat ->
                    val isRowSelected = selectedRow == idx && !inButtonRow
                    val isHidden      = cat in workingHidden
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRowSelected) accent.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Toggle visibility column
                        val toggleSel = isRowSelected && selectedCol == 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (toggleSel) accent.copy(alpha = 0.25f) else surface)
                                .border(if (toggleSel) 1.5.dp else 0.dp, if (toggleSel) accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { selectedRow = idx; selectedCol = 0; toggleVisibility(cat) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHidden) "Hidden" else "Visible",
                                tint               = if (isHidden) textSecondary.copy(alpha = 0.5f) else accent,
                                modifier           = Modifier.size(16.dp),
                            )
                        }
                        // Category name
                        Text(
                            text      = cat,
                            fontSize  = 14.sp,
                            color     = if (isHidden) textSecondary.copy(alpha = 0.5f) else textPrimary,
                            fontWeight = if (isRowSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier  = Modifier.weight(1f),
                        )
                        // Move up
                        val upSel = isRowSelected && selectedCol == 1
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (upSel) accent.copy(alpha = 0.25f) else surface)
                                .border(if (upSel) 1.5.dp else 0.dp, if (upSel) accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable(enabled = idx > 0) { selectedRow = idx; selectedCol = 1; moveUp(idx) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(16.dp), tint = if (idx > 0) textPrimary else textSecondary.copy(alpha = 0.3f))
                        }
                        // Move down
                        val downSel = isRowSelected && selectedCol == 2
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (downSel) accent.copy(alpha = 0.25f) else surface)
                                .border(if (downSel) 1.5.dp else 0.dp, if (downSel) accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable(enabled = idx < workingOrder.lastIndex) { selectedRow = idx; selectedCol = 2; moveDown(idx) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = if (idx < workingOrder.lastIndex) textPrimary else textSecondary.copy(alpha = 0.3f))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Save / Cancel buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    val cancelSel = selectedRow == cancelIdx
                    SportDialogPill(
                        icon       = null,
                        label      = "Cancel",
                        isSelected = cancelSel,
                        isPressed  = false,
                        accent     = accent,
                        onClick    = onDismiss,
                    )
                    val saveSel = selectedRow == saveIdx
                    SportDialogPill(
                        icon       = null,
                        label      = "Save",
                        isSelected = saveSel,
                        isPressed  = false,
                        accent     = accent,
                        onClick    = { onSave(workingHidden, workingOrder) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live-now helpers (top-level so they can be used in card and dialog)
// ─────────────────────────────────────────────────────────────────────────────

private fun computeEventStartMs(timeUk: String): Long {
    val parts = timeUk.split(":")
    if (parts.size != 2) return 0L
    val h = parts[0].toIntOrNull() ?: return 0L
    val m = parts[1].toIntOrNull() ?: return 0L
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"))
    cal.set(java.util.Calendar.HOUR_OF_DAY, h)
    cal.set(java.util.Calendar.MINUTE, m)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}


private fun isLiveNow(event: MatchedSportEvent, currentUkMinutes: Int): Boolean {
    val now     = System.currentTimeMillis()
    val startMs = parseUtcIsoMs(event.startUtc) ?: return false
    val endMs   = event.endEpochMs ?: (startMs + 120 * 60_000L)
    return now in startMs..endMs
}

private fun isPastEvent(event: MatchedSportEvent, currentUkMinutes: Int): Boolean {
    val now   = System.currentTimeMillis()
    val endMs = event.endEpochMs
        ?: (parseUtcIsoMs(event.startUtc)?.let { it + 120 * 60_000L })
        ?: return false
    return now > endMs
}

private fun parseUtcIsoMs(s: String?): Long? = try {
    if (s == null) null else java.time.Instant.parse(s).toEpochMilli()
} catch (_: Exception) { null }

// ─────────────────────────────────────────────────────────────────────────────
// Sport event card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SportEventCard(
    event: MatchedSportEvent,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    currentUkMinutes: Int = 0,
    hasReminder: Boolean = false,
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onGoToEpg: (channelName: String) -> Unit = {},
    onRemind: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var isFocused  by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1f, label = "scale")
    val hasMatch   = event.matchedChannels.isNotEmpty()
    val firstMatch = event.matchedChannels.firstOrNull()
    val live       = isLiveNow(event, currentUkMinutes)
    val past       = isPastEvent(event, currentUkMinutes)

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(116.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (past) 0.5f else 1f }
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .then(if (isFocused) Modifier.border(2.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (past || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { showDialog = true; true }
                    else -> false
                }
            }
            .clickable(enabled = !past) { showDialog = true }
            .focusable()
            .padding(12.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Sport logo top-left (replaces text chip)
            if (event.sportLogoUrl.isNotEmpty()) {
                AsyncImage(
                    model              = event.sportLogoUrl,
                    contentDescription = event.sportCategory,
                    modifier           = Modifier.size(20.dp),
                    contentScale       = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(event.sportCategory, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
            // Event name
            Text(
                text       = event.eventName,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = textPrimary,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f),
            )
            // Time + watch indicator
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(event.timeUk, fontSize = 11.sp, color = textSecondary)
                if (hasMatch) Text("▶ WATCH", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Medium)
                else Text("Not in playlist", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.5f))
            }
            // Channel name
            if (firstMatch != null) {
                Text(
                    text     = firstMatch.channelName,
                    fontSize = 10.sp,
                    color    = textSecondary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Top-right badge — LIVE (live), time chip (past), bell (upcoming with reminder)
        when {
            live -> {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val transition = rememberInfiniteTransition(label = "live")
                    val dotAlpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue  = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(700, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = dotAlpha))
                    )
                    Text("LIVE", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
            past -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textSecondary.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = event.timeUk,
                        fontSize   = 9.sp,
                        color      = textSecondary.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            hasReminder -> {
                Icon(
                    imageVector        = Icons.Default.NotificationsActive,
                    contentDescription = "Reminder set",
                    tint               = accent,
                    modifier           = Modifier.align(Alignment.TopEnd).size(16.dp)
                )
            }
        }
    }

    if (showDialog) {
        SportEventDialog(
            event        = event,
            accent       = accent,
            currentUkMinutes = currentUkMinutes,
            hasReminder  = hasReminder,
            onDismiss    = { showDialog = false },
            onWatch      = { url, name -> showDialog = false; onChannelClick(url, name) },
            onGoToEpg    = { name -> showDialog = false; onGoToEpg(name) },
            onRemind     = { showDialog = false; onRemind() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sport event dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SportEventDialog(
    event: MatchedSportEvent,
    accent: Color,
    currentUkMinutes: Int = 0,
    hasReminder: Boolean = false,
    onDismiss: () -> Unit,
    onWatch: (streamUrl: String, channelName: String) -> Unit,
    onGoToEpg: (channelName: String) -> Unit,
    onRemind: () -> Unit = {},
) {
    val hasChannels    = event.matchedChannels.isNotEmpty()
    val multiChannel   = event.matchedChannels.size > 1
    val live           = isLiveNow(event, currentUkMinutes)
    val past           = isPastEvent(event, currentUkMinutes)
    val isFuture       = !live && !past
    val dialogBg       = LocalNsBackground.current
    val surface        = LocalNsSurface.current
    val textPrimary    = LocalNsTextPrimary.current
    val textSecondary  = LocalNsTextSecondary.current

    // Button indices: 0=Close, [1=ChannelPicker if multi], Watch, EPG, [Remind if future]
    val idxChannelPicker = if (multiChannel) 1 else -1
    val base             = if (multiChannel) 2 else 1
    val idxWatchNow      = if (hasChannels) base else -1
    val idxGoToEpg       = if (hasChannels) base + 1 else 1
    val idxRemindMe      = if (isFuture) idxGoToEpg + 1 else -1
    val buttonCount      = idxGoToEpg + 1 + (if (isFuture) 1 else 0)

    var selectedButton    by remember { mutableStateOf(if (hasChannels) base else 1) }
    var pressedButton     by remember { mutableStateOf(-1) }
    var selectedChannelIdx by remember { mutableStateOf(0) }

    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { dialogFR.requestFocus() } catch (_: Exception) {}
    }

    fun confirm() {
        val ch = event.matchedChannels.getOrNull(selectedChannelIdx) ?: event.matchedChannels.firstOrNull()
        when (selectedButton) {
            0           -> onDismiss()
            idxWatchNow -> ch?.let { onWatch(it.streamUrl, it.channelName) }
            idxGoToEpg  -> ch?.let { onGoToEpg(it.channelName) } ?: onDismiss()
            idxRemindMe -> onRemind()
            else        -> onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true,
        )
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f),
            color          = dialogBg,
            tonalElevation = 0.dp,
            shape          = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> {
                                if (selectedButton == idxChannelPicker && multiChannel)
                                    selectedChannelIdx = (selectedChannelIdx - 1 + event.matchedChannels.size) % event.matchedChannels.size
                                else
                                    selectedButton = (selectedButton - 1 + buttonCount) % buttonCount
                                true
                            }
                            Key.DirectionRight -> {
                                if (selectedButton == idxChannelPicker && multiChannel)
                                    selectedChannelIdx = (selectedChannelIdx + 1) % event.matchedChannels.size
                                else
                                    selectedButton = (selectedButton + 1) % buttonCount
                                true
                            }
                            Key.DirectionUp -> { selectedButton = (selectedButton - 1 + buttonCount) % buttonCount; true }
                            Key.DirectionDown -> { selectedButton = (selectedButton + 1) % buttonCount; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { pressedButton = selectedButton; confirm(); true }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
            ) {
                // ── Coloured header bar ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (event.sportLogoUrl.isNotEmpty()) {
                            AsyncImage(
                                model              = event.sportLogoUrl,
                                contentDescription = null,
                                modifier           = Modifier.size(20.dp),
                                contentScale       = ContentScale.Fit,
                            )
                        }
                        Text(
                            text       = event.sportCategory,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = accent,
                        )
                        if (event.groupName.isNotEmpty() && event.groupName != event.sportCategory) {
                            Text("·", fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f))
                            Text(
                                text     = event.groupName,
                                fontSize = 12.sp,
                                color    = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        // Status chip
                        when {
                            live -> {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val transition = rememberInfiniteTransition(label = "live")
                                    val dotAlpha by transition.animateFloat(
                                        initialValue = 1f,
                                        targetValue  = 0.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation  = tween(700, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "dot"
                                    )
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.Red.copy(alpha = dotAlpha)))
                                    Text("LIVE", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                            past -> Text("Ended", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.5f))
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(event.timeUk, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // ── Event body ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Event name
                    Text(
                        text       = event.eventName,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = textPrimary,
                        maxLines   = 3,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    // Time
                    Text(
                        text     = "Today · ${event.timeUk} UK",
                        fontSize = 12.sp,
                        color    = textSecondary,
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.12f))
                    Spacer(Modifier.height(4.dp))

                    // Channel availability
                    if (hasChannels) {
                        val visibleChannels = event.matchedChannels.take(3)
                        val extra           = event.matchedChannels.size - visibleChannels.size
                        Text(
                            text     = "Available on your playlist:",
                            fontSize = 11.sp,
                            color    = textSecondary.copy(alpha = 0.7f),
                        )
                        visibleChannels.forEach { ch ->
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.padding(vertical = 1.dp),
                            ) {
                                if (!ch.logoUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model              = ch.logoUrl,
                                        contentDescription = null,
                                        modifier           = Modifier.size(16.dp),
                                        contentScale       = ContentScale.Fit,
                                    )
                                }
                                Text(ch.channelName, fontSize = 12.sp, color = textPrimary)
                            }
                        }
                        if (extra > 0) {
                            Text(
                                text     = "+ $extra more",
                                fontSize = 11.sp,
                                color    = textSecondary.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        Text(
                            text     = "Not in your playlist",
                            fontSize = 12.sp,
                            color    = textSecondary.copy(alpha = 0.6f),
                        )
                    }

                    // Channel picker (multi-channel only)
                    if (multiChannel) {
                        Spacer(Modifier.height(4.dp))
                        val isPickerSelected = selectedButton == idxChannelPicker
                        val selectedCh       = event.matchedChannels.getOrNull(selectedChannelIdx)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isPickerSelected) accent.copy(alpha = 0.2f) else surface)
                                .border(if (isPickerSelected) 1.5.dp else 0.dp, if (isPickerSelected) accent else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickable { selectedButton = idxChannelPicker }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text("‹", fontSize = 14.sp, color = textSecondary,
                                modifier = Modifier.clickable { selectedChannelIdx = (selectedChannelIdx - 1 + event.matchedChannels.size) % event.matchedChannels.size })
                            Text(
                                text       = selectedCh?.channelName ?: "",
                                fontSize   = 12.sp,
                                color      = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.weight(1f),
                            )
                            Text("›", fontSize = 14.sp, color = textSecondary,
                                modifier = Modifier.clickable { selectedChannelIdx = (selectedChannelIdx + 1) % event.matchedChannels.size })
                        }
                    }

                }

                HorizontalDivider(color = textSecondary.copy(alpha = 0.08f))

                // Action buttons — pinned at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    val selectedCh = event.matchedChannels.getOrNull(selectedChannelIdx) ?: event.matchedChannels.firstOrNull()
                    if (hasChannels) {
                        SportDialogPill(
                            icon       = Icons.Default.PlayArrow,
                            label      = "Watch Now",
                            isSelected = selectedButton == idxWatchNow,
                            isPressed  = pressedButton == idxWatchNow,
                            accent     = accent,
                            onClick    = { selectedCh?.let { onWatch(it.streamUrl, it.channelName) } },
                        )
                    }
                    SportDialogPill(
                        icon       = Icons.Default.Tv,
                        label      = "Go To EPG",
                        isSelected = selectedButton == idxGoToEpg,
                        isPressed  = pressedButton == idxGoToEpg,
                        accent     = accent,
                        onClick    = { selectedCh?.let { onGoToEpg(it.channelName) } ?: onDismiss() },
                    )
                    if (isFuture) {
                        SportDialogPill(
                            icon       = if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            label      = if (hasReminder) "Remove Reminder" else "Remind Me",
                            isSelected = selectedButton == idxRemindMe,
                            isPressed  = pressedButton == idxRemindMe,
                            accent     = accent,
                            onClick    = onRemind,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SportDialogPill(
                        icon       = Icons.Default.Close,
                        label      = "Close",
                        isSelected = selectedButton == 0,
                        isPressed  = pressedButton == 0,
                        accent     = accent,
                        onClick    = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SportDialogPill(
    icon: ImageVector?,
    label: String,
    isSelected: Boolean,
    isPressed: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = when {
        isPressed  -> accent
        isSelected -> accent.copy(alpha = 0.85f)
        else       -> Color.White.copy(alpha = 0.15f)
    }
    val textCol = if (isSelected || isPressed) Color.Black else Color.White
    Surface(
        onClick  = onClick,
        color    = bg,
        shape    = RoundedCornerShape(20.dp),
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(16.dp), tint = textCol)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textCol)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String? = null,
    accent: Color,
    textPrimary: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text       = title,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Medium,
            color      = textPrimary
        )
        if (subtitle != null) {
            Text(
                text     = subtitle,
                fontSize = 11.sp,
                color    = accent.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Continue Watching card — 16:9 landscape (200 × 112 dp)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContinueWatchingCard(
    item:             RecentlyWatchedEntity,
    progressFraction: Float,
    accent:           Color,
    surface:          Color,
    textPrimary:      Color,
    onClick:          () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused.value) 1.05f else 1.0f,
        label       = "cwScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = 200.dp, height = 112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(surface)
            .border(
                width = if (isFocused.value) 2.dp else 0.dp,
                color = if (isFocused.value) accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
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
            model              = item.logoUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        // Gradient scrim at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )
        // Title + subtitle
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
        ) {
            Text(
                text     = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color    = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text     = item.subtitle,
                    fontSize = 10.sp,
                    color    = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Play icon when focused
        if (isFocused.value) {
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(accent.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
        // Progress bar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(accent)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait poster card — 120 × 180 dp
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PosterCard(
    imageUrl:   String?,
    title:      String,
    accent:     Color,
    surface:    Color,
    textPrimary: Color,
    onClick:    () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused.value) 1.05f else 1.0f,
        label       = "posterScale"
    )

    Column(
        modifier           = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
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
                model              = imageUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        Text(
            text     = title,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp)
        )
    }
}
