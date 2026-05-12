package app.nexstream.player.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import coil.compose.AsyncImage

@Composable
fun WatchlistScreen(
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (item: WatchlistEntity) -> Unit,
    onSeriesClick: (item: WatchlistEntity) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val allItems by viewModel.allItems.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val channels = allItems.filter { it.type == WatchlistType.CHANNEL }
    val movies = allItems.filter { it.type == WatchlistType.MOVIE }
    val series = allItems.filter { it.type == WatchlistType.SERIES }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("Your list is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Bookmark channels, movies and series to add them here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Channels (${channels.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Movies (${movies.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Series (${series.size})") })
            }
            when (selectedTab) {
                0 -> WatchlistItemList(items = channels, emptyMessage = "No channels saved", icon = Icons.Default.Tv, firstItemFocusRequester = firstItemFocusRequester, onClick = { item -> item.streamUrl?.let { onChannelClick(it, item.name) } }, onRemove = { viewModel.removeFromWatchlist(it.id, it.type) })
                1 -> WatchlistItemList(items = movies, emptyMessage = "No movies saved", icon = Icons.Default.Movie, firstItemFocusRequester = firstItemFocusRequester, onClick = { onMovieClick(it) }, onRemove = { viewModel.removeFromWatchlist(it.id, it.type) })
                2 -> WatchlistItemList(items = series, emptyMessage = "No series saved", icon = Icons.Default.VideoLibrary, firstItemFocusRequester = firstItemFocusRequester, onClick = { onSeriesClick(it) }, onRemove = { viewModel.removeFromWatchlist(it.id, it.type) })
            }
        }
    }
}

@Composable
private fun WatchlistItemList(
    items: List<WatchlistEntity>,
    emptyMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    firstItemFocusRequester: FocusRequester? = null,
    onClick: (WatchlistEntity) -> Unit,
    onRemove: (WatchlistEntity) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                WatchlistRow(
                    item = item,
                    defaultIcon = icon,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onClick = { onClick(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
    }
}

@Composable
private fun WatchlistRow(
    item: WatchlistEntity,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrEmpty()) {
                    AsyncImage(model = item.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(imageVector = defaultIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            Text(text = item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Default.BookmarkRemove, contentDescription = "Remove from list", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}