package app.nexstream.player.ui.components

import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * TV-native folder/file browser.
 * Folder mode (default): pass onFolderSelected, leave onFileSelected null.
 * File mode: pass onFileSelected (and optionally fileExtension e.g. ".json").
 */
@Composable
fun FolderBrowserDialog(
    onDismiss: () -> Unit,
    onFolderSelected: ((path: String, label: String) -> Unit)? = null,
    onFileSelected: ((path: String) -> Unit)? = null,
    fileExtension: String? = null
) {
    val context = LocalContext.current
    val isFileMode = onFileSelected != null

    val roots: List<Pair<File, String>> = remember(context) {
        val list = mutableListOf<Pair<File, String>>()
        val addedPaths = mutableSetOf<String>()

        fun addIfNew(file: File, label: String) {
            val canon = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
            if (file.exists() && canon !in addedPaths) {
                list.add(file to label)
                addedPaths.add(canon)
            }
        }

        val primary = Environment.getExternalStorageDirectory()
        addIfNew(primary, "Internal Storage")

        // App-scoped external dirs (covers SD cards on most devices)
        try {
            context.getExternalFilesDirs(null).forEachIndexed { idx, dir ->
                if (idx > 0 && dir != null) {
                    var root = dir
                    repeat(4) { root = root.parentFile ?: root }
                    addIfNew(root, root.name.ifBlank { "External Storage" })
                }
            }
        } catch (_: Exception) {}

        // StorageManager volumes (API 30+) — provides proper volume labels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sm = context.getSystemService(StorageManager::class.java)
                sm?.storageVolumes?.forEach { vol ->
                    if (!vol.isPrimary) {
                        val dir = vol.directory
                        if (dir != null) addIfNew(dir, vol.getDescription(context))
                    }
                }
            } catch (_: Exception) {}
        }

        // Direct /storage/ scan — catches USB drives on Allwinner boxes and Fire TV
        try {
            val skipNames = setOf("emulated", "self")
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name !in skipNames && vol.canRead()) {
                    val label = when {
                        vol.name.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}")) -> "SD Card (${vol.name})"
                        vol.name.startsWith("usb") || vol.name.startsWith("sda") -> "USB (${vol.name})"
                        else -> vol.name.replaceFirstChar { it.uppercase() }
                    }
                    addIfNew(vol, label)
                }
            }
        } catch (_: Exception) {}

        list
    }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val entries: List<Pair<File, String>> = remember(currentDir, isFileMode, fileExtension) {
        if (currentDir == null) {
            roots
        } else {
            val allFiles = currentDir!!.listFiles() ?: emptyArray()
            val dirs = allFiles
                .filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                .sortedBy { it.name.lowercase() }
                .map { it to it.name }
            val files = if (isFileMode) {
                allFiles
                    .filter { it.isFile && !it.name.startsWith(".") &&
                        (fileExtension == null || it.name.endsWith(fileExtension, ignoreCase = true)) }
                    .sortedBy { it.name.lowercase() }
                    .map { it to it.name }
            } else emptyList()
            dirs + files
        }
    }

    LaunchedEffect(currentDir) {
        selectedIndex = 0
        listState.scrollToItem(0)
    }

    LaunchedEffect(selectedIndex) {
        if (entries.isNotEmpty())
            listState.animateScrollToItem(selectedIndex.coerceIn(0, entries.lastIndex))
    }

    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { dialogFR.requestFocus() } catch (_: Exception) {}
    }

    fun navigateUp() {
        val isRoot = roots.any { it.first.absolutePath == currentDir?.absolutePath }
        currentDir = if (currentDir == null || isRoot) null else currentDir!!.parentFile
    }

    fun navigateInto(file: File) {
        if (file.isDirectory) currentDir = file
        else if (isFileMode) {
            onFileSelected?.invoke(file.absolutePath)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(Unit) {
            dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setDimAmount(0.95f)
        }
        BackHandler {
            if (currentDir != null) navigateUp()
            else onDismiss()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.80f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true
                            }
                            Key.DirectionDown -> {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(entries.lastIndex.coerceAtLeast(0)); true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                entries.getOrNull(selectedIndex)?.first?.let { navigateInto(it) }; true
                            }
                            Key.DirectionRight -> {
                                entries.getOrNull(selectedIndex)?.first?.let { navigateInto(it) }; true
                            }
                            Key.DirectionLeft -> {
                                if (currentDir != null) navigateUp() else onDismiss(); true
                            }
                            Key.Back -> {
                                if (currentDir != null) navigateUp() else onDismiss(); true
                            }
                            else -> false
                        }
                    }
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (currentDir == null) Icons.Default.Storage else Icons.Default.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when {
                            currentDir == null -> if (isFileMode) "Select File" else "Select Storage"
                            roots.any { it.first.absolutePath == currentDir!!.absolutePath } ->
                                roots.first { it.first.absolutePath == currentDir!!.absolutePath }.second
                            else -> currentDir!!.name
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentDir != null) {
                        IconButton(
                            onClick = { navigateUp() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward, "Up",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ── Breadcrumb ────────────────────────────────────────────────
                if (currentDir != null) {
                    Text(
                        text = currentDir!!.absolutePath,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    HorizontalDivider()
                }

                // ── List ──────────────────────────────────────────────────────
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen, null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                if (isFileMode) "No ${fileExtension ?: ""} files found"
                                else "No subfolders here",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(entries) { idx, (file, name) ->
                            val isSelected = idx == selectedIndex
                            val isFile = file.isFile
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedIndex = idx
                                        navigateInto(file)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    when {
                                        isFile -> Icons.Default.Description
                                        currentDir == null -> Icons.Default.Storage
                                        else -> Icons.Default.Folder
                                    },
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else if (isFile) MaterialTheme.colorScheme.tertiary
                                           else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!isFile) {
                                    Icon(
                                        Icons.Default.ChevronRight, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Actions ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!isFileMode && currentDir != null) {
                        Button(onClick = {
                            val dir = currentDir!!
                            val label = roots.firstOrNull { it.first.absolutePath == dir.absolutePath }?.second
                                ?: dir.name.ifBlank { "External Storage" }
                            onFolderSelected?.invoke(dir.absolutePath, label)
                        }) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select This Folder")
                        }
                    }
                    if (isFileMode) {
                        val selectedFile = entries.getOrNull(selectedIndex)?.first
                        if (selectedFile != null && selectedFile.isFile) {
                            Button(onClick = {
                                onFileSelected?.invoke(selectedFile.absolutePath)
                                onDismiss()
                            }) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Select This File")
                            }
                        }
                    }
                }
            }
        }
    }
}
