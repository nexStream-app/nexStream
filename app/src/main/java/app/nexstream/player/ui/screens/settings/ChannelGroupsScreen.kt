package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.ChannelGroupMemberEntity
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import kotlinx.coroutines.launch

@Composable
fun ChannelGroupsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: ChannelGroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ChannelGroupEntity?>(null) }
    var deletingGroup by remember { mutableStateOf<ChannelGroupEntity?>(null) }
    var managingGroup by remember { mutableStateOf<ChannelGroupEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Channel Groups", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // New Group button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var addFocused by remember { mutableStateOf(false) }
                val addFR = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (firstItemFocusRequester == null) try { addFR.requestFocus() } catch (_: Exception) {}
                }
                Box(
                    modifier = Modifier
                        .then(if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier.focusRequester(addFR))
                        .onFocusChanged { addFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                showCreateDialog = true; true
                            } else false
                        }
                        .clickable { showCreateDialog = true }
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (addFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            tint = if (addFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                        Text("New Group",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (addFocused) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp))
                        Text("No groups yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text("Create groups to organise your favourite channels in the Guide",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.id }) { index, group ->
                        GroupRow(
                            group = group,
                            onManage = { managingGroup = group },
                            onRename = { editingGroup = group },
                            onDelete = { deletingGroup = group },
                            firstButtonFocusRequester = if (index == 0) firstItemFocusRequester else null
                        )
                    }
                }
            }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        GroupNameDialog(
            title = "New Group",
            initialName = "",
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Rename dialog
    editingGroup?.let { group ->
        GroupNameDialog(
            title = "Rename Group",
            initialName = group.name,
            onConfirm = { name ->
                viewModel.renameGroup(group, name)
                editingGroup = null
            },
            onDismiss = { editingGroup = null }
        )
    }

    // Delete confirm dialog
    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Delete Group") },
            text = { Text("Delete \"${group.name}\"? Channels in the group won't be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGroup(group); deletingGroup = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) { Text("Cancel") }
            }
        )
    }

    // Manage channels dialog
    managingGroup?.let { group ->
        ManageGroupChannelsDialog(
            group = group,
            viewModel = viewModel,
            onDismiss = { managingGroup = null }
        )
    }
}

@Composable
private fun GroupRow(
    group: ChannelGroupEntity,
    onManage: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    firstButtonFocusRequester: FocusRequester? = null
) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.FolderOpen, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
        )
        Text(
            group.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = sTheme.categoryText,
            maxLines = 1
        )
        GroupFocusableIconButton(
            onClick = onManage,
            icon = Icons.Default.Tv,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Manage channels",
            focusRequester = firstButtonFocusRequester
        )
        GroupFocusableIconButton(
            onClick = onRename,
            icon = Icons.Default.Edit,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = "Rename"
        )
        GroupFocusableIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            tint = MaterialTheme.colorScheme.error,
            contentDescription = "Delete"
        )
    }
}

@Composable
private fun GroupFocusableIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription,
            tint = if (isFocused) primary else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageGroupChannelsDialog(
    group: ChannelGroupEntity,
    viewModel: ChannelGroupsViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<ChannelGroupMemberEntity>>(emptyList()) }
    var allChannels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMode by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    val firstChannelFR = remember { FocusRequester() }

    LaunchedEffect(group.id) {
        members = viewModel.getMembersForGroup(group.id)
        allChannels = viewModel.getAllChannels()
    }

    val memberIds = remember(members) { members.map { it.channelId }.toSet() }
    val filteredChannels = remember(allChannels, searchQuery, memberIds) {
        val q = searchQuery.lowercase()
        allChannels.filter { ch -> ch.id !in memberIds && (q.isEmpty() || ch.name.lowercase().contains(q)) }
    }

    Dialog(
        onDismissRequest = {
            when {
                showKeyboard -> showKeyboard = false
                showAddMode  -> { showAddMode = false; searchQuery = "" }
                else         -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .then(
                        if (showKeyboard)
                            Modifier.align(Alignment.TopCenter).padding(top = 24.dp).heightIn(max = 320.dp)
                        else
                            Modifier.align(Alignment.Center).heightIn(max = 480.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showAddMode = true; showKeyboard = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add channels",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (showAddMode) {
                        if (filteredChannels.isEmpty()) {
                            Text(
                                if (searchQuery.isEmpty()) "All channels already added"
                                else "No channels match \"$searchQuery\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                itemsIndexed(filteredChannels.take(100), key = { _, ch -> ch.id }) { idx, ch ->
                                    ChannelAddRow(
                                        name = ch.name,
                                        focusRequester = if (idx == 0) firstChannelFR else null,
                                        onClick = {
                                            viewModel.addChannelToGroup(group, ch)
                                            scope.launch { members = viewModel.getMembersForGroup(group.id) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!showKeyboard) {
                                TextButton(onClick = { showKeyboard = true }) { Text("Search") }
                            }
                            TextButton(onClick = { showAddMode = false; showKeyboard = false; searchQuery = "" }) { Text("Back") }
                        }
                    } else {
                        if (members.isEmpty()) {
                            Text(
                                "No channels in this group yet. Tap + to add.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                items(members, key = { it.channelId }) { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(member.channelName, modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        IconButton(
                                            onClick = {
                                                viewModel.removeChannelFromGroup(group.id, member.channelId)
                                                scope.launch { members = viewModel.getMembersForGroup(group.id) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss) { Text("Done") }
                        }
                    }
                }
            }

            TvKeyboardSheet(
                visible = showKeyboard,
                value = searchQuery,
                onValueChange = { searchQuery = it },
                onDone = { showKeyboard = false },
                onDismiss = { showKeyboard = false },
                onNavigateUp = { try { firstChannelFR.requestFocus() } catch (_: Exception) {} },
                hint = "Search channels…",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ChannelAddRow(
    name: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(6.dp)
                ) else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Text(name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface)
    }
}
