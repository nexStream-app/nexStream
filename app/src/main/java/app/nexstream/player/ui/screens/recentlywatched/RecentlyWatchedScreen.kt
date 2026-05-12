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
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import coil.compose.AsyncImage

@Composable
fun RecentlyWatchedScreen(
    onChannelClick: (streamUrl: String, name: String) -> Unit,
    onMovieClick: (item: RecentlyWatchedEntity) -> Unit,
    onEpisodeClick: (item: RecentlyWatchedEntity) -> Unit,
    selectedType: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: RecentlyWatchedViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val items by viewModel.recentlyWatched.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredItems = remember(items, selectedType) {
        when (selectedType) {
            "Live TV"  -> items.filter { it.type == RecentlyWatchedType.CHANNEL }
            "Movies"   -> items.filter { it.type == RecentlyWatchedType.MOVIE }
            "Episodes" -> items.filter { it.type == RecentlyWatchedType.EPISODE }
            else       -> items
        }
    }

    val firstItemFR = firstItemFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(filteredItems.size) {
        if (filteredItems.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            try { firstItemFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedType ?: "All",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.ClearAll, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = sTheme.divider)

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text("Nothing watched yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Text("Content you watch will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f))
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No ${selectedType?.lowercase() ?: "items"} watched recently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isFirst = filteredItems.indexOf(item) == 0
                    val icon = when (item.type) {
                        RecentlyWatchedType.CHANNEL -> Icons.Default.Tv
                        RecentlyWatchedType.MOVIE   -> Icons.Default.Movie
                        RecentlyWatchedType.EPISODE -> Icons.Default.VideoLibrary
                    }
                    RecentItemRow(
                        item = item,
                        defaultIcon = icon,
                        focusRequester = if (isFirst) firstItemFR else null,
                        onClick = {
                            when (item.type) {
                                RecentlyWatchedType.CHANNEL -> onChannelClick(item.streamUrl, item.name)
                                RecentlyWatchedType.MOVIE   -> onMovieClick(item)
                                RecentlyWatchedType.EPISODE -> onEpisodeClick(item)
                            }
                        },
                        onRemove = { viewModel.delete(item.id) }
                    )
                }
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
