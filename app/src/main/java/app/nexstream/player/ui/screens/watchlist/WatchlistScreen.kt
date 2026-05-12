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
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import coil.compose.AsyncImage

@Composable
fun WatchlistScreen(
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (item: WatchlistEntity) -> Unit,
    onSeriesClick: (item: WatchlistEntity) -> Unit,
    selectedType: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val allItems by viewModel.allItems.collectAsState()

    val filteredItems = remember(allItems, selectedType) {
        when (selectedType) {
            "Live TV" -> allItems.filter { it.type == WatchlistType.CHANNEL }
            "Movies"  -> allItems.filter { it.type == WatchlistType.MOVIE }
            "Series"  -> allItems.filter { it.type == WatchlistType.SERIES }
            else      -> allItems
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = selectedType ?: "All",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        if (allItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text("Your list is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Text("Bookmark channels, movies and series to add them here",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f))
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No ${selectedType?.lowercase() ?: "items"} in your list",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                    val icon = when (item.type) {
                        WatchlistType.CHANNEL -> Icons.Default.Tv
                        WatchlistType.MOVIE   -> Icons.Default.Movie
                        WatchlistType.SERIES  -> Icons.Default.VideoLibrary
                    }
                    WatchlistRow(
                        item = item,
                        defaultIcon = icon,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = {
                            when (item.type) {
                                WatchlistType.CHANNEL -> item.streamUrl?.let { onChannelClick(it, item.name) }
                                WatchlistType.MOVIE   -> onMovieClick(item)
                                WatchlistType.SERIES  -> onSeriesClick(item)
                            }
                        },
                        onRemove = { viewModel.removeFromWatchlist(item.id, item.type) }
                    )
                }
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
