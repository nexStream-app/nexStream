package app.nexstream.player.ui.screens.recentlywatched

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import coil.compose.AsyncImage

@Composable
fun RecentlyWatchedScreen(
    onChannelClick: (streamUrl: String, name: String) -> Unit,
    onMovieClick: (item: RecentlyWatchedEntity) -> Unit,
    onEpisodeClick: (item: RecentlyWatchedEntity) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: RecentlyWatchedViewModel = hiltViewModel()
) {
    val items by viewModel.recentlyWatched.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val channels = items.filter { it.type == RecentlyWatchedType.CHANNEL }
    val movies   = items.filter { it.type == RecentlyWatchedType.MOVIE }
    val episodes = items.filter { it.type == RecentlyWatchedType.EPISODE }

    // Auto-focus first item when content loads, not the tab row
    val firstItemFR = firstItemFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            try { firstItemFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("Nothing watched yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Content you watch will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Channels (${channels.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Movies (${movies.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Series (${episodes.size})") })
            }

            when (selectedTab) {
                0 -> RecentItemList(
                    items = channels,
                    emptyMessage = "No channels watched recently",
                    defaultIcon = Icons.Default.Tv,
                    firstItemFocusRequester = firstItemFR,
                    onClick = { onChannelClick(it.streamUrl, it.name) },
                    onRemove = { viewModel.delete(it.id) }
                )
                1 -> RecentItemList(
                    items = movies,
                    emptyMessage = "No movies watched recently",
                    defaultIcon = Icons.Default.Movie,
                    firstItemFocusRequester = firstItemFR,
                    onClick = { onMovieClick(it) },
                    onRemove = { viewModel.delete(it.id) }
                )
                2 -> RecentItemList(
                    items = episodes,
                    emptyMessage = "No series watched recently",
                    defaultIcon = Icons.Default.VideoLibrary,
                    firstItemFocusRequester = firstItemFR,
                    onClick = { onEpisodeClick(it) },
                    onRemove = { viewModel.delete(it.id) }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear History") },
            text = { Text("Remove all recently watched items?") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearConfirm = false }) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecentItemList(
    items: List<RecentlyWatchedEntity>,
    emptyMessage: String,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
    firstItemFocusRequester: FocusRequester? = null,
    onClick: (RecentlyWatchedEntity) -> Unit,
    onRemove: (RecentlyWatchedEntity) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                val isFirst = items.indexOf(item) == 0
                RecentItemRow(
                    item = item,
                    defaultIcon = defaultIcon,
                    focusRequester = if (isFirst) firstItemFocusRequester else null,
                    onClick = { onClick(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: RecentlyWatchedEntity,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logoUrl.isNullOrEmpty()) {
                    AsyncImage(model = item.logoUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(imageVector = defaultIcon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (item.subtitle != null) {
                    Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}