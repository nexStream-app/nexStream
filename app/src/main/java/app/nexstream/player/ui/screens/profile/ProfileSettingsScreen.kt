package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var profileToDelete  by remember { mutableStateOf<ProfileEntity?>(null) }
    var showSwitchPin    by remember { mutableStateOf(false) }
    var switchTarget     by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError         by remember { mutableStateOf(false) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null) {
            kotlinx.coroutines.delay(100)
            try { firstFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Profiles", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
        }
        HorizontalDivider(color = sTheme.divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active profile indicator
            activeProfile?.let { active ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(active.emoji, fontSize = 32.sp)
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

            // PIN tip card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Text(
                        "If child profiles have restricted categories, add a PIN to your default profile to prevent easy switching.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Profile list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(
                        profile        = profile,
                        isActive       = profile.id == activeProfile?.id,
                        focusRequester = if (profiles.indexOf(profile) == 0) firstFocus else null,
                        onEdit         = { onEditProfile(profile) },
                        onSwitch       = {
                            if (profile.pinHash != null) {
                                switchTarget = profile; showSwitchPin = true; pinError = false
                            } else {
                                viewModel.switchProfile(profile)
                            }
                        },
                        onDelete       = if (!profile.isDefault) {
                            { profileToDelete = profile; showDeleteConfirm = true }
                        } else null
                    )
                }
            }

            // Add profile button (max 3)
            if (profiles.size < 3) {
                var addFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick  = { onEditProfile(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { addFocused = it.isFocused },
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

    // PIN for switch
    if (showSwitchPin && switchTarget != null) {
        PinEntryDialog(
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
private fun ProfileRow(
    profile: ProfileEntity,
    isActive: Boolean,
    focusRequester: FocusRequester? = null,
    onEdit: () -> Unit,
    onSwitch: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.hasFocus },
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(profile.emoji, fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
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
                        Icon(
                            Icons.Default.Lock, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (profile.isDefault) {
                    Text(
                        "Default profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isActive) {
                TextButton(onClick = onSwitch) { Text("Switch") }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
