package app.nexstream.player.ui.components

import android.os.Environment
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

@Composable
fun FolderBrowserDialog(
    onDismiss: () -> Unit,
    onFolderSelected: (path: String, label: String) -> Unit
) {
    val context = LocalContext.current

    val roots: List<Pair<File, String>> = remember(context) {
        val list = mutableListOf<Pair<File, String>>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary.exists()) list.add(primary to "Internal Storage")
        try {
            context.getExternalFilesDirs(null).forEachIndexed { idx, dir ->
                if (idx > 0 && dir != null) {
                    // Walk up 4 levels to find the volume root
                    var root = dir
                    repeat(4) { root = root.parentFile ?: root }
                    if (root.exists() && root.absolutePath != primary.absolutePath) {
                        list.add(root to (root.name.ifBlank { "External Storage" }))
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val entries: List<Pair<File, String>> = remember(currentDir) {
        if (currentDir == null) {
            roots
        } else {
            currentDir!!.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it to it.name }
                ?: emptyList()
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
                            currentDir == null -> "Select Storage"
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

                // ── Directory list ────────────────────────────────────────────
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
                                "No subfolders here",
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
                                    if (currentDir == null) Icons.Default.Storage else Icons.Default.Folder,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
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

                HorizontalDivider()

                // ── Actions ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (currentDir != null) {
                        Button(onClick = {
                            val dir = currentDir!!
                            val label = roots.firstOrNull { it.first.absolutePath == dir.absolutePath }?.second
                                ?: dir.name.ifBlank { "External Storage" }
                            onFolderSelected(dir.absolutePath, label)
                        }) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select This Folder")
                        }
                    }
                }
            }
        }
    }
}
