package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    val profileManager: ProfileManager,
    private val syncManager: ProfileSyncManager
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileManager.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfile: StateFlow<ProfileEntity?> = profileManager.activeProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setActive(profile: ProfileEntity) = profileManager.setActiveProfile(profile)

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            syncManager.pushProfiles()
        }
    }

    fun switchProfile(profile: ProfileEntity) {
        profileManager.setActiveProfile(profile)
    }
}

@Composable
fun ProfilesSettingsScreen(
    onEditProfile: (ProfileEntity?) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val profiles      by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var profileToDelete  by remember { mutableStateOf<ProfileEntity?>(null) }
    var showSwitchPin    by remember { mutableStateOf(false) }
    var switchTarget     by remember { mutableStateOf<ProfileEntity?>(null) }
    var showEditPin      by remember { mutableStateOf(false) }
    var editTarget       by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError         by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var actionProfile    by remember { mutableStateOf<ProfileEntity?>(null) }

    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = LocalUiStyle.current

    val isRestricted    = activeProfile?.isRestricted == true
    val visibleProfiles = if (isRestricted) profiles.filter { it.id == activeProfile?.id } else profiles

    // Build FocusRequesters for each profile row + add button
    val rowFRs = remember(visibleProfiles.size) { List(visibleProfiles.size) { FocusRequester() } }
    val addFR  = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Profiles", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active profile indicator
            activeProfile?.let { active ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(active.emoji, fontSize = 28.sp)
                        Column {
                            Text(
                                "Active Profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                active.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Profile list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleProfiles.forEachIndexed { index, profile ->
                    val isActive = profile.id == activeProfile?.id
                    val thisFR = rowFRs.getOrNull(index)
                    val canAdd = !isRestricted && visibleProfiles.size < 6
                    val prevFR = if (index > 0) rowFRs[index - 1] else if (canAdd) addFR else rowFRs.last()
                    val nextFR = if (index < visibleProfiles.lastIndex) rowFRs[index + 1] else if (canAdd) addFR else rowFRs.first()
                    ProfileRow(
                        profile          = profile,
                        isActive         = isActive,
                        isRestrictedSelf = isRestricted && isActive,
                        focusRequester   = thisFR,
                        upFR             = prevFR,
                        downFR           = nextFR,
                        onClick          = {
                            val isAdmin = activeProfile?.isDefault == true
                            when {
                                // Admin tapping a non-default profile → show action menu (edit/switch/delete)
                                isAdmin && !profile.isDefault -> {
                                    actionProfile = profile; showActionDialog = true
                                }
                                // Non-admin tapping a pinned profile they don't own → PIN gate
                                !profile.pinHash.isNullOrBlank() && profile.id != activeProfile?.id -> {
                                    editTarget = profile; showEditPin = true; pinError = false
                                }
                                else -> onEditProfile(profile)
                            }
                        }
                    )
                }
            }

            // Add profile button (max 6, hidden for restricted profiles)
            if (!isRestricted && profiles.size < 6) {
                var addFocused by remember { mutableStateOf(false) }
                val lastRowFR = rowFRs.lastOrNull()
                val firstRowFR = rowFRs.firstOrNull()
                OutlinedButton(
                    onClick  = { onEditProfile(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(addFR)
                        .focusProperties {
                            if (lastRowFR != null) up = lastRowFR
                            if (firstRowFR != null) down = firstRowFR
                        }
                        .onFocusChanged { addFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                            ) { onEditProfile(null); true } else false
                        },
                    border = BorderStroke(
                        width = if (addFocused) 2.dp else 1.dp,
                        color = if (addFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (addFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (addFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Profile")
                }
            }
        }
    }

    // Action menu (Edit / Switch / Delete) — shown for non-default profiles when admin is active
    if (showActionDialog && actionProfile != null) {
        val target = actionProfile!!
        ProfileActionDialog(
            profile   = target,
            isActive  = target.id == activeProfile?.id,
            canDelete = !target.isDefault,
            onEdit    = {
                showActionDialog = false; actionProfile = null
                onEditProfile(target)
            },
            onSwitch  = {
                showActionDialog = false; actionProfile = null
                if (!target.pinHash.isNullOrBlank()) {
                    switchTarget = target; showSwitchPin = true; pinError = false
                } else {
                    viewModel.switchProfile(target)
                }
            },
            onDelete  = {
                showActionDialog = false; actionProfile = null
                profileToDelete = target; showDeleteConfirm = true
            },
            onDismiss = { showActionDialog = false; actionProfile = null }
        )
    }

    // Delete confirm
    if (showDeleteConfirm && profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete Profile") },
            text    = { Text("Delete \"${profileToDelete!!.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profileToDelete!!.id)
                    showDeleteConfirm = false; profileToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // PIN gate for editing another profile
    if (showEditPin && editTarget != null) {
        ThemedPinEntryDialog(
            profile   = editTarget!!,
            hasError  = pinError,
            onDismiss = { showEditPin = false; editTarget = null; pinError = false },
            onConfirm = { pin ->
                if (hashPin(pin) == editTarget!!.pinHash) {
                    val target = editTarget!!
                    showEditPin = false; editTarget = null; pinError = false
                    onEditProfile(target)
                } else {
                    pinError = true
                }
            }
        )
    }

    // PIN for switch
    if (showSwitchPin && switchTarget != null) {
        ThemedPinEntryDialog(
            profile   = switchTarget!!,
            hasError  = pinError,
            onDismiss = { showSwitchPin = false; switchTarget = null },
            onConfirm = { pin ->
                if (hashPin(pin) == switchTarget!!.pinHash) {
                    viewModel.switchProfile(switchTarget!!)
                    showSwitchPin = false; switchTarget = null
                } else {
                    pinError = true
                }
            }
        )
    }
}

@Composable
private fun ProfileActionDialog(
    profile: ProfileEntity,
    isActive: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val editFR   = remember { FocusRequester() }
    val switchFR = remember { FocusRequester() }
    val deleteFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { editFR.requestFocus() } catch (_: Exception) {}
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.55f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(profile.emoji, fontSize = 28.sp)
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Edit Profile",
                        icon  = Icons.Default.Edit,
                        focusRequester = editFR,
                        upFR   = if (canDelete) deleteFR else if (!isActive) switchFR else editFR,
                        downFR = if (!isActive) switchFR else if (canDelete) deleteFR else editFR,
                        onClick = onEdit
                    )
                    if (!isActive) {
                        ActionButton(
                            label = "Switch to This Profile",
                            icon  = Icons.Default.SwapHoriz,
                            focusRequester = switchFR,
                            upFR   = editFR,
                            downFR = if (canDelete) deleteFR else editFR,
                            onClick = onSwitch
                        )
                    }
                    if (canDelete) {
                        ActionButton(
                            label = "Delete Profile",
                            icon  = Icons.Default.Delete,
                            focusRequester = deleteFR,
                            upFR   = if (!isActive) switchFR else editFR,
                            downFR = editFR,
                            onClick = onDelete,
                            isDestructive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester,
    upFR: FocusRequester,
    downFR: FocusRequester,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .focusRequester(focusRequester)
            .focusProperties { up = upFR; down = downFR }
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp),
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else tint)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else tint
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    isActive: Boolean,
    isRestrictedSelf: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFR: FocusRequester? = null,
    downFR: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (upFR != null) up = upFR
                if (downFR != null) down = downFR
            }
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer
            isActive  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else      -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(profile.emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (profile.pinHash != null) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    when {
                        profile.isDefault  -> "Default profile"
                        isRestrictedSelf   -> "Tap to edit name & avatar"
                        isActive           -> "Currently active"
                        else               -> "Tap to edit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.Edit, null,
                modifier = Modifier.size(16.dp),
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
