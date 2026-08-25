package app.nexstream.player.ui.screens.localfiles

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nexstream.player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.components.FolderBrowserDialog
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import java.io.File

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "ts", "m2ts", "mov", "mpg", "mpeg", "wmv", "flv", "webm", "m4v", "3gp", "divx", "ogv")

private fun File.isVideo() = extension.lowercase() in VIDEO_EXTENSIONS

private fun File.videosIn(): List<File> =
    listFiles()?.filter { it.isFile && it.isVideo() && it.canRead() }?.sortedBy { it.name.lowercase() } ?: emptyList()

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    else                    -> "${bytes / 1024} KB"
}

@Composable
fun LocalFilesScreen(
    firstItemFocusRequester: FocusRequester? = null,
    onPlayFile: (path: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val context   = LocalContext.current
    val nsTheme   = LocalNexStreamTheme.current
    val sTheme    = nsTheme.sidebar

    // Persisted folder path via SharedPreferences
    val prefs = remember { context.getSharedPreferences("local_files", android.content.Context.MODE_PRIVATE) }
    var folderPath by remember { mutableStateOf(prefs.getString("folder_path", null)) }
    var showBrowser by remember { mutableStateOf(folderPath == null) }

    val files: List<File> = remember(folderPath) {
        folderPath?.let { File(it).takeIf { f -> f.isDirectory && f.canRead() }?.videosIn() } ?: emptyList()
    }

    val gridState = rememberLazyGridState()
    val firstFR   = remember { firstItemFocusRequester ?: FocusRequester() }

    LaunchedEffect(files.size) {
        if (files.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            try { firstFR.requestFocus() } catch (_: Exception) {}
        }
    }

    if (showBrowser) {
        FolderBrowserDialog(
            onDismiss = {
                if (folderPath != null) showBrowser = false else onBack()
            },
            onFolderSelected = { path, _ ->
                prefs.edit().putString("folder_path", path).apply()
                folderPath = path
                showBrowser = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = sTheme.categoryText, modifier = Modifier.size(20.dp))
            Text(
                folderPath?.let { File(it).name.ifBlank { it } } ?: "Local Files",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sTheme.categoryText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (folderPath != null) {
                var btnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .then(if (btnFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                        .onFocusChanged { btnFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                showBrowser = true; true
                            } else false
                        }
                        .clickable { showBrowser = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.local_files_change_folder), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider(color = sTheme.divider)

        if (files.isEmpty() && folderPath != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(48.dp), tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text(stringResource(R.string.local_files_empty_title), color = sTheme.categoryText.copy(alpha = 0.5f), fontSize = 14.sp)
                    Text(stringResource(R.string.local_files_empty_formats), color = sTheme.categoryText.copy(alpha = 0.35f), fontSize = 12.sp)
                }
            }
        } else if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(48.dp), tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text(stringResource(R.string.local_files_no_folder), color = sTheme.categoryText.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns   = GridCells.Adaptive(minSize = 200.dp),
                state     = gridState,
                modifier  = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(files) { idx, file ->
                    var isFocused by remember { mutableStateOf(false) }
                    val fr = if (idx == 0) firstFR else remember { FocusRequester() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                            .focusRequester(fr)
                            .onFocusChanged { isFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                    onPlayFile("file://${file.absolutePath}", file.nameWithoutExtension); true
                                } else false
                            }
                            .clickable { onPlayFile("file://${file.absolutePath}", file.nameWithoutExtension) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayCircle, null,
                                modifier = Modifier.size(32.dp),
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                file.nameWithoutExtension,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${file.extension.uppercase()} · ${formatSize(file.length())}",
                                fontSize = 10.sp,
                                color = (if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                         else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
