package app.nexstream.player.ui.screens.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.nexstream.player.data.local.entity.ChannelEntity
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val MenuBlue = Color(0xFF007AFF)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MultiScreenPlayerScreen(
    initialChannelUrl: String,
    initialChannelName: String,
    onBack: () -> Unit,
    viewModel: MultiScreenViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val slots by viewModel.slots.collectAsState()
    val configuringSlotIndex by viewModel.configuringSlotIndex.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val showFavourites by viewModel.showFavourites.collectAsState()
    val displayedChannels by viewModel.displayedChannels.collectAsState()
    val favouriteChannelIds by viewModel.favouriteChannelIds.collectAsState()

    var selectedSlotIndex by remember { mutableIntStateOf(0) }
    var showSlotMenu by remember { mutableStateOf(false) }

    val rootFR = remember { FocusRequester() }
    val menuFR  = remember { FocusRequester() }
    val listFR  = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (viewModel.slots.value.isEmpty()) {
            viewModel.initWithChannelUrl(initialChannelUrl, initialChannelName)
        }
        delay(150L)
        try { rootFR.requestFocus() } catch (_: Exception) {}
    }

    // Focus: menu → channel list → root (in priority order)
    LaunchedEffect(showSlotMenu) {
        delay(120L)
        try {
            if (showSlotMenu) menuFR.requestFocus()
            else if (configuringSlotIndex == null) rootFR.requestFocus()
        } catch (_: Exception) {}
    }
    LaunchedEffect(configuringSlotIndex) {
        delay(250L)
        try {
            if (configuringSlotIndex != null) listFR.requestFocus()
            else if (!showSlotMenu) rootFR.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(slots.size) {
        if (selectedSlotIndex >= slots.size) selectedSlotIndex = (slots.size - 1).coerceAtLeast(0)
    }

    val players = remember { mutableStateListOf<ExoPlayer?>() }
    DisposableEffect(Unit) {
        onDispose { players.forEach { it?.release() } }
    }

    LaunchedEffect(slots.size) {
        while (players.size < slots.size) {
            val p = ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
            players.add(p)
        }
        while (players.size > slots.size) {
            players.removeLastOrNull()?.release()
        }
    }

    LaunchedEffect(slots) {
        slots.forEachIndexed { i, slot ->
            val player = players.getOrNull(i) ?: return@forEachIndexed
            val url = slot.channel?.streamUrl ?: return@forEachIndexed
            val current = runCatching { player.currentMediaItem?.localConfiguration?.uri?.toString() }.getOrNull()
            if (current != url) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    // Audio follows focus
    LaunchedEffect(selectedSlotIndex, slots.size) {
        if (slots.isEmpty()) return@LaunchedEffect
        val audioIdx = selectedSlotIndex.coerceAtMost(slots.size - 1)
        players.forEachIndexed { i, player -> player?.volume = if (i == audioIdx) 1f else 0f }
    }

    BackHandler {
        when {
            showSlotMenu -> showSlotMenu = false
            configuringSlotIndex != null -> viewModel.closeConfigureSlot()
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFR)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (configuringSlotIndex != null || showSlotMenu) return@onKeyEvent false
                        showSlotMenu = true
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                        if (configuringSlotIndex != null || showSlotMenu) return@onKeyEvent false
                        selectedSlotIndex = navigateSlotSelection(selectedSlotIndex, e.key, slots.size)
                        true
                    }
                    else -> false
                }
            }
    ) {
        when (slots.size) {
            0    -> Unit
            1    -> SingleSlotLayout(slots, players, viewModel, selectedSlotIndex)
            2    -> TwoSlotLayout(slots, players, viewModel, selectedSlotIndex)
            3    -> ThreeSlotLayout(slots, players, viewModel, selectedSlotIndex)
            else -> FourSlotLayout(slots, players, viewModel, selectedSlotIndex)
        }

        // Back button
        Box(
            modifier = Modifier
                .padding(16.dp).size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onBack() }
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Slot action menu
        if (showSlotMenu && slots.isNotEmpty()) {
            SlotActionMenu(
                slot = slots.getOrNull(selectedSlotIndex),
                slotIndex = selectedSlotIndex,
                slotsCount = slots.size,
                firstItemFR = menuFR,
                onChangeChannel = {
                    showSlotMenu = false
                    viewModel.openConfigureSlot(selectedSlotIndex)
                },
                onAddScreen = {
                    showSlotMenu = false
                    viewModel.addSlot()
                },
                onMakeMain = {
                    showSlotMenu = false
                    viewModel.makeMainSlot(selectedSlotIndex)
                    selectedSlotIndex = 0
                },
                onRemove = {
                    showSlotMenu = false
                    viewModel.removeSlot(selectedSlotIndex)
                },
                onDismiss = { showSlotMenu = false },
            )
        }

        // Channel selector
        if (configuringSlotIndex != null) {
            ChannelSelectorOverlay(
                categories = categories,
                channels = displayedChannels,
                selectedCategory = selectedCategory,
                showFavourites = showFavourites,
                favouriteChannelIds = favouriteChannelIds,
                onSelectCategory = viewModel::selectCategory,
                onToggleFavourites = viewModel::toggleFavourites,
                onSelectChannel = { ch -> viewModel.setSlotChannel(configuringSlotIndex!!, ch) },
                onDismiss = viewModel::closeConfigureSlot,
                firstItemFR = listFR,
            )
        }
    }
}

// D-pad navigation between slots only (no separate addIndex — add is in the action menu)
private fun navigateSlotSelection(current: Int, key: Key, slotsCount: Int): Int {
    return when (slotsCount) {
        0, 1 -> current
        2 -> when (key) {
            Key.DirectionRight -> 1
            Key.DirectionLeft  -> 0
            else -> current
        }
        3 -> when (key) {
            // Layout: slot 0 left (big), slot 1 top-right, slot 2 bottom-right
            Key.DirectionRight -> if (current == 0) 1 else current
            Key.DirectionLeft  -> if (current != 0) 0 else current
            Key.DirectionDown  -> if (current == 1) 2 else current
            Key.DirectionUp    -> if (current == 2) 1 else current
            else -> current
        }
        else -> when (key) {
            // 2×2: [0][1] / [2][3]
            Key.DirectionRight -> when (current) { 0 -> 1; 2 -> 3; else -> current }
            Key.DirectionLeft  -> when (current) { 1 -> 0; 3 -> 2; else -> current }
            Key.DirectionDown  -> when (current) { 0 -> 2; 1 -> 3; else -> current }
            Key.DirectionUp    -> when (current) { 2 -> 0; 3 -> 1; else -> current }
            else -> current
        }
    }
}

// ── Layouts ──────────────────────────────────────────────────────────────────

@Composable
private fun SingleSlotLayout(
    slots: List<MultiScreenSlot>,
    players: List<ExoPlayer?>,
    viewModel: MultiScreenViewModel,
    selectedSlotIndex: Int,
) {
    Box(modifier = Modifier.fillMaxSize().then(if (selectedSlotIndex == 0) Modifier.border(2.dp, Color.White) else Modifier)) {
        PlayerSurface(players.getOrNull(0), Modifier.fillMaxSize())
        SlotInfoOverlay(slots[0], onConfigure = { viewModel.openConfigureSlot(0) }, showRemove = false, onRemove = {})
    }
}

@Composable
private fun TwoSlotLayout(
    slots: List<MultiScreenSlot>,
    players: List<ExoPlayer?>,
    viewModel: MultiScreenViewModel,
    selectedSlotIndex: Int,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        slots.forEachIndexed { i, slot ->
            Box(
                modifier = Modifier
                    .weight(1f).fillMaxHeight()
                    .then(if (selectedSlotIndex == i) Modifier.border(2.dp, Color.White) else Modifier)
            ) {
                PlayerSurface(players.getOrNull(i), Modifier.fillMaxSize())
                SlotInfoOverlay(slot, onConfigure = { viewModel.openConfigureSlot(i) }, showRemove = i > 0, onRemove = { viewModel.removeSlot(i) })
            }
            if (i < slots.size - 1) Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
        }
    }
}

@Composable
private fun ThreeSlotLayout(
    slots: List<MultiScreenSlot>,
    players: List<ExoPlayer?>,
    viewModel: MultiScreenViewModel,
    selectedSlotIndex: Int,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(2f).fillMaxHeight()
                .then(if (selectedSlotIndex == 0) Modifier.border(2.dp, Color.White) else Modifier)
        ) {
            PlayerSurface(players.getOrNull(0), Modifier.fillMaxSize())
            SlotInfoOverlay(slots[0], onConfigure = { viewModel.openConfigureSlot(0) }, showRemove = false, onRemove = {})
        }
        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            slots.drop(1).forEachIndexed { j, slot ->
                val i = j + 1
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxWidth()
                        .then(if (selectedSlotIndex == i) Modifier.border(2.dp, Color.White) else Modifier)
                ) {
                    PlayerSurface(players.getOrNull(i), Modifier.fillMaxSize())
                    SlotInfoOverlay(slot, onConfigure = { viewModel.openConfigureSlot(i) }, showRemove = true, onRemove = { viewModel.removeSlot(i) })
                }
                if (j == 0) Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(Color.Black))
            }
        }
    }
}

@Composable
private fun FourSlotLayout(
    slots: List<MultiScreenSlot>,
    players: List<ExoPlayer?>,
    viewModel: MultiScreenViewModel,
    selectedSlotIndex: Int,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SlotCell(0, slots, players, viewModel, Modifier.weight(1f), selectedSlotIndex == 0)
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
            SlotCell(1, slots, players, viewModel, Modifier.weight(1f), selectedSlotIndex == 1)
        }
        Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(Color.Black))
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SlotCell(2, slots, players, viewModel, Modifier.weight(1f), selectedSlotIndex == 2)
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
            SlotCell(3, slots, players, viewModel, Modifier.weight(1f), selectedSlotIndex == 3)
        }
    }
}

@Composable
private fun SlotCell(
    index: Int,
    slots: List<MultiScreenSlot>,
    players: List<ExoPlayer?>,
    viewModel: MultiScreenViewModel,
    modifier: Modifier,
    isSelected: Boolean,
) {
    Box(modifier = modifier.fillMaxHeight().background(Color(0xFF111111)).then(if (isSelected) Modifier.border(2.dp, Color.White) else Modifier)) {
        slots.getOrNull(index)?.let { slot ->
            PlayerSurface(players.getOrNull(index), Modifier.fillMaxSize())
            SlotInfoOverlay(slot, onConfigure = { viewModel.openConfigureSlot(index) }, showRemove = index > 0, onRemove = { viewModel.removeSlot(index) })
        }
    }
}

// ── Shared slot UI ────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer?, modifier: Modifier) {
    if (player == null) { Box(modifier = modifier.background(Color.Black)); return }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        update = { view -> view.player = player },
        modifier = modifier,
    )
}

@Composable
private fun SlotInfoOverlay(slot: MultiScreenSlot, onConfigure: () -> Unit, showRemove: Boolean, onRemove: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (slot.channel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center).clickable { onConfigure() }.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                Text("Choose channel", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.4f).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp, end = 36.dp)) {
                Text(slot.channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                slot.currentProgramme?.let { Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Icon(
                Icons.Default.SwapHoriz, contentDescription = "Change channel", tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp).clickable { onConfigure() },
            )
            if (showRemove) {
                Icon(
                    Icons.Default.Close, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp).clickable { onRemove() },
                )
            }
        }
    }
}

// ── Slot action menu ──────────────────────────────────────────────────────────

@Composable
private fun SlotActionMenu(
    slot: MultiScreenSlot?,
    slotIndex: Int,
    slotsCount: Int,
    firstItemFR: FocusRequester,
    onChangeChannel: () -> Unit,
    onAddScreen: () -> Unit,
    onMakeMain: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Build the list of actions for this slot
    data class Action(val icon: ImageVector, val label: String, val onClick: () -> Unit)
    val actions = buildList {
        add(Action(Icons.Default.Tv, "Change Channel", onChangeChannel))
        if (slotsCount < 4) add(Action(Icons.Default.Add, "Add Screen", onAddScreen))
        if (slotIndex != 0 && slotsCount >= 2) add(Action(Icons.Default.Star, "Make Main Screen", onMakeMain))
        if (slotsCount > 1) add(Action(Icons.Default.Close, "Remove Screen", onRemove))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() })

        // Menu panel
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(240.dp)
                .background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
        ) {
            // Channel / programme header
            if (slot?.channel != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(slot.channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    slot.currentProgramme?.let {
                        Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }

            actions.forEachIndexed { idx, action ->
                MenuRow(
                    icon = action.icon,
                    label = action.label,
                    focusRequester = if (idx == 0) firstItemFR else null,
                    onClick = action.onClick,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                ) { onClick(); true } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.White else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Text(label, color = if (focused) Color.White else Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

// ── Channel selector overlay ──────────────────────────────────────────────────

@Composable
private fun ChannelSelectorOverlay(
    categories: List<String>,
    channels: List<ChannelEntity>,
    selectedCategory: String?,
    showFavourites: Boolean,
    favouriteChannelIds: Set<String>,
    onSelectCategory: (String?) -> Unit,
    onToggleFavourites: () -> Unit,
    onSelectChannel: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    firstItemFR: FocusRequester,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() })

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .background(Color(0xFF1C1C1E))
                .padding(top = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Select Channel", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).clickable { onDismiss() })
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { CategoryPill("All", selectedCategory == null && !showFavourites) { onSelectCategory(null) } }
                item { CategoryPill("Favourites", showFavourites, Icons.Default.Favorite) { onToggleFavourites() } }
                items(categories) { cat -> CategoryPill(cat, selectedCategory == cat && !showFavourites) { onSelectCategory(cat) } }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                    ChannelRow(
                        channel = channel,
                        isFav = channel.id in favouriteChannelIds,
                        onSelect = { onSelectChannel(channel) },
                        focusRequester = if (index == 0) firstItemFR else null,
                    )
                }
                if (channels.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No channels", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) MenuBlue else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ChannelRow(channel: ChannelEntity, isFav: Boolean, onSelect: () -> Unit, focusRequester: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                ) { onSelect(); true } else false
            }
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!channel.logoUrl.isNullOrEmpty()) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
        } else {
            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
        }
        Text(channel.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (isFav) Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(14.dp))
    }
}
