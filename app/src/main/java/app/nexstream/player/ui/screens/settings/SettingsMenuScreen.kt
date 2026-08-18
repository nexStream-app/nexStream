package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.theme.UiStyle

data class MenuItemData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val focusRequester: FocusRequester?,
    val route: AppRoute
)

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
    val uiStyle = rememberUiStyle()
    val scrollState = rememberScrollState()

    val items = remember(uiState) {
        listOf(
            MenuItemData(
                title = "Playlists",
                subtitle = "Add, remove or refresh your IPTV playlists",
                icon = Icons.Default.List,
                focusRequester = playlistsFocusRequester,
                route = AppRoute.SettingsPlaylists
            ),
            MenuItemData(
                title = "Appearance",
                subtitle = "Change theme, colours and display options",
                icon = Icons.Default.Palette,
                focusRequester = appearanceFocusRequester,
                route = AppRoute.SettingsAppearance
            ),
            MenuItemData(
                title = "Player",
                subtitle = "Frame rate, buffering and playback options",
                icon = Icons.Default.PlayCircle,
                focusRequester = playerFocusRequester,
                route = AppRoute.SettingsPlayer
            ),
            MenuItemData(
                title = "Licence",
                subtitle = if (uiState.isActivated) {
                    val email = uiState.email?.takeIf { it.isNotEmpty() && !it.endsWith("@nexstream.app") }
                    if (email != null) "Active · $email" else "Active"
                } else "Not activated — tap to enter your licence key",
                icon = if (uiState.isActivated) Icons.Default.VerifiedUser else Icons.Default.Lock,
                focusRequester = licenceFocusRequester,
                route = AppRoute.SettingsLicence
            ),
            MenuItemData(
                title = "Account",
                subtitle = "Xtream account status, connections and server info",
                icon = Icons.Default.ManageAccounts,
                focusRequester = accountFocusRequester,
                route = AppRoute.SettingsAccount
            ),
            MenuItemData(
                title = "Profiles",
                subtitle = "Manage viewer profiles and content restrictions",
                icon = Icons.Default.People,
                focusRequester = null,
                route = AppRoute.SettingsProfiles
            ),
            MenuItemData(
                title = "Sync",
                subtitle = "Cloud sync and data settings per profile",
                icon = Icons.Default.Sync,
                focusRequester = null,
                route = AppRoute.SettingsSyncSettings
            ),
            MenuItemData(
                title = "Navigation",
                subtitle = "Set start screen and reorder rail items",
                icon = Icons.Default.Reorder,
                focusRequester = null,
                route = AppRoute.SettingsNavigation
            ),
            MenuItemData(
                title = "About",
                subtitle = "Device info, IDs and diagnostics",
                icon = Icons.Default.Info,
                focusRequester = null,
                route = AppRoute.SettingsAbout
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (uiStyle == UiStyle.MODERN) 12.dp else 0.dp)
        ) {
            if (uiStyle == UiStyle.CLASSIC) {
                ClassicSettingsMenuList(items = items, onNavigate = onNavigate)
            } else {
                ModernSettingsMenuCards(items = items, onNavigate = onNavigate)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ClassicSettingsMenuList(
    items: List<MenuItemData>,
    onNavigate: (AppRoute) -> Unit
) {
    items.forEach { item ->
        ClassicMenuRow(item = item, onClick = { onNavigate(item.route) })
    }
}

@Composable
private fun ClassicMenuRow(
    item: MenuItemData,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isFocused) MaterialTheme.colorScheme.primaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .then(
                    if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .then(if (item.focusRequester != null) Modifier.focusRequester(item.focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown &&
                        (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                        onClick(); true
                    } else false
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

@Composable
private fun ModernSettingsMenuCards(
    items: List<MenuItemData>,
    onNavigate: (AppRoute) -> Unit
) {
    items.forEach { item ->
        ModernMenuCard(item = item, onClick = { onNavigate(item.route) })
    }
}

@Composable
private fun ModernMenuCard(
    item: MenuItemData,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.focusRequester != null) Modifier.focusRequester(item.focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
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
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
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
