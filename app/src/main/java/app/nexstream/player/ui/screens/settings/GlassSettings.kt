package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.nexstream.player.ui.screens.appearance.AppearanceScreen
import app.nexstream.player.ui.screens.appearance.AppearanceViewModel
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.theme.GlassTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class GlassSettingsSection(
    val route: AppRoute,
    val icon: ImageVector,
    val label: String,
)

private val settingsSections = listOf(
    GlassSettingsSection(AppRoute.SettingsAppearance,    Icons.Default.Palette,       "Appearance"),
    GlassSettingsSection(AppRoute.SettingsPlaylists,     Icons.Default.PlaylistPlay,   "Playlists"),
    GlassSettingsSection(AppRoute.SettingsProfiles,      Icons.Default.People,         "Profiles"),
    GlassSettingsSection(AppRoute.SettingsPlayer,        Icons.Default.PlayCircle,     "Player"),
    GlassSettingsSection(AppRoute.SettingsSports,        Icons.Default.SportsSoccer,   "Sports"),
    GlassSettingsSection(AppRoute.SettingsNavigation,    Icons.Default.Navigation,     "Navigation"),
    GlassSettingsSection(AppRoute.SettingsLanguage,      Icons.Default.Language,       "Language"),
    GlassSettingsSection(AppRoute.SettingsChannelGroups, Icons.Default.Category,       "Channel Groups"),
    GlassSettingsSection(AppRoute.SettingsSyncSettings,  Icons.Default.Sync,           "Sync"),
    GlassSettingsSection(AppRoute.SettingsAccount,       Icons.Default.AccountCircle,  "Account"),
    GlassSettingsSection(AppRoute.SettingsLicence,       Icons.Default.VpnKey,         "Licence"),
    GlassSettingsSection(AppRoute.SettingsAbout,         Icons.Default.Info,           "About"),
)

@Composable
fun GlassSettings(
    currentRoute: AppRoute,
    firstItemFocusRequester: FocusRequester? = null,
    onSectionSelect: (AppRoute) -> Unit = {},
    onNavigateToAddPlaylist: () -> Unit = {},
    hasJellyfinPlaylist: Boolean = false,
    activeProfileId: String? = null,
    onRefreshSports: () -> Unit = {},
    isRefreshingSports: Boolean = false,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val sectionFRs = remember { settingsSections.map { FocusRequester() } }
    val initialIndex = settingsSections.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val contentFirstFR = remember { FocusRequester() }

    // Focus section list when entering settings at top level
    val panelFR = firstItemFocusRequester
    LaunchedEffect(panelFR) {
        if (panelFR != null) {
            delay(160)
            try { panelFR.requestFocus() } catch (_: Exception) {}
        }
    }

    // Auto-focus content area when navigating into a sub-section
    LaunchedEffect(currentRoute) {
        if (currentRoute != AppRoute.Settings) {
            delay(80)
            try { contentFirstFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Left section panel ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = GlassTokens.RadiusCard, bottomEnd = GlassTokens.RadiusCard))
                .background(GlassTokens.GlassPanelSolid)
                .border(
                    1.dp,
                    GlassTokens.GlassBorder,
                    RoundedCornerShape(topEnd = GlassTokens.RadiusCard, bottomEnd = GlassTokens.RadiusCard),
                )
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            settingsSections.forEachIndexed { index, section ->
                val isSelected = section.route == currentRoute
                GlassSettingsSectionItem(
                    icon = section.icon,
                    label = section.label,
                    isSelected = isSelected,
                    focusRequester = if (index == initialIndex) (firstItemFocusRequester ?: sectionFRs[index]) else sectionFRs[index],
                    onKeyEvent = { e ->
                        if (e.type != KeyEventType.KeyDown) return@GlassSettingsSectionItem false
                        when (e.key) {
                            Key.DirectionUp -> {
                                if (index > 0) try { sectionFRs[index - 1].requestFocus() } catch (_: Exception) {}
                                true
                            }
                            Key.DirectionDown -> {
                                if (index < sectionFRs.lastIndex) try { sectionFRs[index + 1].requestFocus() } catch (_: Exception) {}
                                true
                            }
                            Key.DirectionRight -> {
                                scope.launch { try { contentFirstFR.requestFocus() } catch (_: Exception) {} }
                                true
                            }
                            else -> false
                        }
                    },
                    onClick = { onSectionSelect(section.route) },
                )
            }
        }

        // ── Right content panel ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (currentRoute) {
                AppRoute.SettingsAppearance -> AppearanceScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsPlaylists  -> SettingsScreen(
                    onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsProfiles -> {
                    var editingProfile by remember { mutableStateOf<app.nexstream.player.data.local.entity.ProfileEntity?>(null) }
                    var showEdit by remember { mutableStateOf(false) }
                    if (showEdit) {
                        app.nexstream.player.ui.screens.profile.ProfileEditScreen(
                            existingProfile = editingProfile,
                            onDone = { showEdit = false; editingProfile = null },
                            onCancel = { showEdit = false; editingProfile = null },
                        )
                    } else {
                        app.nexstream.player.ui.screens.profile.ProfilesSettingsScreen(
                            onEditProfile = { p: app.nexstream.player.data.local.entity.ProfileEntity? -> editingProfile = p; showEdit = true },
                            firstItemFocusRequester = contentFirstFR,
                        )
                    }
                }
                AppRoute.SettingsPlayer -> PlayerSettingsScreen(
                    profileId = activeProfileId ?: "default",
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsSports -> SportsSettingsScreen(
                    onRefresh = onRefreshSports,
                    isRefreshing = isRefreshingSports,
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsNavigation -> NavigationSettingsScreen(
                    hasJellyfinPlaylist = hasJellyfinPlaylist,
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsLanguage -> LanguageSettingsScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsChannelGroups -> ChannelGroupsScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsSyncSettings -> SyncSettingsScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsAccount -> AccountScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsLicence -> LicenceScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                AppRoute.SettingsAbout -> AboutScreen(
                    firstItemFocusRequester = contentFirstFR,
                )
                else -> SettingsMenuScreen(
                    onNavigate = onSectionSelect,
                )
            }
        }
    }
}

@Composable
private fun GlassSettingsSectionItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onKeyEvent: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val active = isSelected || isFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else onKeyEvent(e)
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) GlassTokens.AccentDefault.copy(alpha = 0.15f)
                else if (isFocused) GlassTokens.GlassSmallSolid
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .then(
                if (isSelected)
                    Modifier.border(1.dp, GlassTokens.AccentDefault.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                else if (isFocused)
                    Modifier.border(1.dp, GlassTokens.AccentDefault.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) GlassTokens.AccentDefault else GlassTokens.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = if (isSelected) GlassTokens.AccentDefault
                    else if (isFocused) GlassTokens.TextPrimary
                    else GlassTokens.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
