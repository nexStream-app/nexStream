package app.nexstream.player.ui.screens.device

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.DeviceFolderDao
import app.nexstream.player.data.local.entity.DeviceFolderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "ts", "m4v", "wmv", "webm",
    "flv", "3gp", "mpg", "mpeg", "m2ts", "mts", "vob", "divx"
)

data class DeviceVideoItem(
    val name: String,
    val path: String,
    val dateKey: String,
    val sizeBytes: Long,
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceFolderDao: DeviceFolderDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _videoFiles = MutableStateFlow<List<DeviceVideoItem>>(emptyList())
    val videoFiles: StateFlow<List<DeviceVideoItem>> = _videoFiles

    val folders: StateFlow<List<DeviceFolderEntity>> = deviceFolderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasDeviceFolders: StateFlow<Boolean> = deviceFolderDao.getFolderCount()
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val deviceDates: StateFlow<List<String>> = _videoFiles
        .map { files ->
            files.map { it.dateKey }.distinct().sorted().reversed()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            folders.collect { folders -> scanFolders(folders) }
        }
    }

    private suspend fun scanFolders(folders: List<DeviceFolderEntity>) {
        withContext(Dispatchers.IO) {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val results = mutableListOf<DeviceVideoItem>()
            for (folder in folders) {
                val dir = File(folder.path)
                if (!dir.exists() || !dir.isDirectory) continue
                collectVideoFiles(dir, folder.includeSubfolders, fmt, results)
            }
            results.sortWith(compareByDescending<DeviceVideoItem> { it.dateKey }.thenBy { it.name })
            _videoFiles.value = results
        }
    }

    private fun collectVideoFiles(
        dir: File,
        recursive: Boolean,
        fmt: SimpleDateFormat,
        results: MutableList<DeviceVideoItem>,
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && recursive) {
                collectVideoFiles(file, true, fmt, results)
            } else if (file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS) {
                val dateKey = fmt.format(Date(file.lastModified()))
                results.add(DeviceVideoItem(
                    name      = file.nameWithoutExtension,
                    path      = file.absolutePath,
                    dateKey   = dateKey,
                    sizeBytes = file.length(),
                ))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { scanFolders(folders.value) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            deviceFolderDao.delete(id)
        }
    }
}

@Composable
fun DeviceScreen(
    selectedDateKey: String?,
    firstItemFocusRequester: FocusRequester? = null,
    isContentFocused: Boolean = false,
    onPlayerLaunch: (url: String, movieId: String?, episodeId: String?, seriesId: String?, startPos: Long, title: String?, subtitle: String?, description: String?) -> Unit = { _, _, _, _, _, _, _, _ -> },
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val videoFiles by viewModel.videoFiles.collectAsState()
    val folders    by viewModel.folders.collectAsState()

    val filtered = remember(videoFiles, selectedDateKey) {
        if (selectedDateKey == null) videoFiles
        else videoFiles.filter { it.dateKey == selectedDateKey }
    }

    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }

    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    "No device folders added",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Go to Settings → Playlists → Device tab to add a folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No video files found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(filtered, key = { _, item -> item.path }) { index, item ->
            DeviceVideoRow(
                item           = item,
                focusRequester = if (index == 0) firstFR else null,
                onPlay         = {
                    onPlayerLaunch(
                        "file://${item.path}", null, null, null, 0L,
                        item.name, null, null
                    )
                }
            )
        }
    }
}

@Composable
private fun DeviceVideoRow(
    item: DeviceVideoItem,
    focusRequester: FocusRequester?,
    onPlay: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                ) { onPlay(); true } else false
            }
            .clickable { onPlay() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.VideoFile,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface
            )
            Text(
                item.dateKey + "  •  " + formatSize(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = (if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                         else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(20.dp),
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.0f MB".format(bytes / 1_048_576.0)
    else                    -> "%.0f KB".format(bytes / 1_024.0)
}
