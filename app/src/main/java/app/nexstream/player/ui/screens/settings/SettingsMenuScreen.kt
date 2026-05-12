package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.screens.main.AppRoute

@Composable
fun SettingsMenuScreen(
    onNavigate: (AppRoute) -> Unit,
    playlistsFocusRequester: FocusRequester? = null,
    appearanceFocusRequester: FocusRequester? = null,
    licenceFocusRequester: FocusRequester? = null,
    playerFocusRequester: FocusRequester? = null,
    accountFocusRequester: FocusRequester? = null,
    viewModel: LicenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SettingsMenuCard(
            title = "Playlists",
            subtitle = "Add, remove or refresh your IPTV playlists",
            icon = Icons.Default.List,
            focusRequester = playlistsFocusRequester,
            onClick = { onNavigate(AppRoute.SettingsPlaylists) }
        )

        SettingsMenuCard(
            title = "Appearance",
            subtitle = "Change theme, colours and display options",
            icon = Icons.Default.Palette,
            focusRequester = appearanceFocusRequester,
            onClick = { onNavigate(AppRoute.SettingsAppearance) }
        )

        SettingsMenuCard(
            title = "Player",
            subtitle = "Frame rate, buffering and playback options",
            icon = Icons.Default.PlayCircle,
            focusRequester = playerFocusRequester,
            onClick = { onNavigate(AppRoute.SettingsPlayer) }
        )

        SettingsMenuCard(
            title = "Licence",
            subtitle = if (uiState.isActivated)
                "Active · ${uiState.email ?: ""}"
            else
                "Not activated — tap to enter your licence key",
            icon = if (uiState.isActivated) Icons.Default.VerifiedUser else Icons.Default.Lock,
            focusRequester = licenceFocusRequester,
            onClick = { onNavigate(AppRoute.SettingsLicence) }
        )

        SettingsMenuCard(
            title = "Account",
            subtitle = "Xtream account status, connections and server info",
            icon = Icons.Default.ManageAccounts,
            focusRequester = accountFocusRequester,
            onClick = { onNavigate(AppRoute.SettingsAccount) }
        )

        SettingsMenuCard(
            title = "Profiles",
            subtitle = "Manage viewer profiles and content restrictions",
            icon = Icons.Default.People,
            onClick = { onNavigate(AppRoute.SettingsProfiles) }
        )

        SettingsMenuCard(
            title = "About",
            subtitle = "Device info, IDs and diagnostics",
            icon = Icons.Default.Info,
            onClick = { onNavigate(AppRoute.SettingsAbout) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isFocused) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}