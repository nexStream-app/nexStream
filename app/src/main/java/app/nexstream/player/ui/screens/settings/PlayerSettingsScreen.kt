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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.R
import app.nexstream.player.downloads.NexStreamDownloadManager
import app.nexstream.player.ui.components.FolderBrowserDialog
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getAutoFrameRateFlow
import app.nexstream.player.ui.theme.getSmartBufferFlow
import app.nexstream.player.ui.theme.getWhisperSubtitlesFlow
import app.nexstream.player.ui.theme.getWhisperAutostartLiveFlow
import app.nexstream.player.ui.theme.getAutoLangDetectFlow
import app.nexstream.player.ui.theme.saveAutoFrameRate
import app.nexstream.player.ui.theme.saveSmartBuffer
import app.nexstream.player.ui.theme.saveWhisperSubtitles
import app.nexstream.player.ui.theme.saveWhisperAutostartLive
import app.nexstream.player.ui.theme.saveAutoLangDetect
import app.nexstream.player.ui.screens.player.ExternalPlayerManager
import app.nexstream.player.ui.theme.getExtPlayerLiveTvFlow
import app.nexstream.player.ui.theme.getExtPlayerMoviesFlow
import app.nexstream.player.ui.theme.getExtPlayerSeriesFlow
import app.nexstream.player.ui.theme.getExtPlayerCatchupFlow
import app.nexstream.player.ui.theme.saveExtPlayerLiveTv
import app.nexstream.player.ui.theme.saveExtPlayerMovies
import app.nexstream.player.ui.theme.saveExtPlayerSeries
import app.nexstream.player.ui.theme.saveExtPlayerCatchup
import app.nexstream.player.ui.theme.getAdminNotificationsEnabledFlow
import app.nexstream.player.ui.theme.saveAdminNotificationsEnabled
import app.nexstream.player.ui.theme.getEpgTimeOffsetFlow
import app.nexstream.player.ui.theme.saveEpgTimeOffset
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    profileId: String = "default",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val scope = rememberCoroutineScope()
    val uiStyle = rememberUiStyle()

    val autoFrameRate        by context.getAutoFrameRateFlow().collectAsState(initial = true)
    val smartBuffer          by context.getSmartBufferFlow().collectAsState(initial = true)
    val whisperSubtitles     by context.getWhisperSubtitlesFlow().collectAsState(initial = false)
    val whisperAutoStartLive by context.getWhisperAutostartLiveFlow().collectAsState(initial = false)
    val autoLangDetect       by context.getAutoLangDetectFlow().collectAsState(initial = false)

    val adminNotifsEnabled by context.getAdminNotificationsEnabledFlow(profileId).collectAsState(initial = true)
    val epgTimeOffset      by context.getEpgTimeOffsetFlow().collectAsState(initial = 0)
    var showEpgOffsetDialog by remember { mutableStateOf(false) }

    val extPlayerLiveTv by context.getExtPlayerLiveTvFlow().collectAsState(initial = "nexstream")
    val extPlayerMovies  by context.getExtPlayerMoviesFlow().collectAsState(initial = "nexstream")
    val extPlayerSeries  by context.getExtPlayerSeriesFlow().collectAsState(initial = "nexstream")
    val extPlayerCatchup by context.getExtPlayerCatchupFlow().collectAsState(initial = "nexstream")

    val availablePlayers = remember { ExternalPlayerManager.getAvailablePlayers(context) }

    val frameRateFR = remember { FocusRequester() }

    var currentStorageLabel by remember { mutableStateOf(NexStreamDownloadManager.getDownloadsLabel(context)) }
    var showStoragePicker   by remember { mutableStateOf(false) }
    var showFolderBrowser   by remember { mutableStateOf(false) }

    if (showStoragePicker) {
        AlertDialog(
            onDismissRequest = { showStoragePicker = false },
            title = { Text(stringResource(R.string.player_storage_dialog_title)) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.player_storage_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable {
                                NexStreamDownloadManager.setDownloadsLocation(context, "")
                                NexStreamDownloadManager.setDownloadsLabel(context, "Internal Storage")
                                currentStorageLabel = "Internal Storage"
                                showStoragePicker = false
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentStorageLabel == "Internal Storage")
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Storage, null,
                                tint = if (currentStorageLabel == "Internal Storage")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.player_storage_internal), style = MaterialTheme.typography.bodyMedium,
                                color = if (currentStorageLabel == "Internal Storage")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                            if (currentStorageLabel == "Internal Storage") {
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showStoragePicker = false
                                showFolderBrowser = true
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.FolderOpen, null,
                                tint = MaterialTheme.colorScheme.onSurface)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.player_storage_browse), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (currentStorageLabel != "Internal Storage") {
                                    Text(stringResource(R.string.player_storage_current, currentStorageLabel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStoragePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showFolderBrowser) {
        FolderBrowserDialog(
            onDismiss = { showFolderBrowser = false },
            onFolderSelected = { path, label ->
                NexStreamDownloadManager.setDownloadsLocation(context, path)
                NexStreamDownloadManager.setDownloadsLabel(context, label)
                currentStorageLabel = label
                showFolderBrowser = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.player_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sTheme.categoryText)
            }
            HorizontalDivider(color = nsTheme.sidebar.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Frame Rate + Buffering ────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_playback),
                icon = Icons.Default.PlayCircle,
                uiStyle = uiStyle
            ) {
                SettingsToggle(
                    label = stringResource(R.string.player_auto_frame_rate_label),
                    description = stringResource(R.string.player_auto_frame_rate_desc),
                    checked = autoFrameRate,
                    uiStyle = uiStyle,
                    focusRequester = frameRateFR,
                    onToggle = { scope.launch { context.saveAutoFrameRate(!autoFrameRate) } }
                )
                SettingsToggle(
                    label = stringResource(R.string.player_smart_buffer_label),
                    description = stringResource(R.string.player_smart_buffer_desc),
                    checked = smartBuffer,
                    uiStyle = uiStyle,
                    onToggle = { scope.launch { context.saveSmartBuffer(!smartBuffer) } }
                )
            }

            // ── External Players ──────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_external_players),
                icon = Icons.Default.OpenInNew,
                uiStyle = uiStyle
            ) {
                if (availablePlayers.size <= 1) {
                    // Only NexStream available — show disabled info row
                    val sTheme = LocalNexStreamTheme.current.sidebar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = sTheme.categoryText.copy(alpha = 0.4f)
                        )
                        Text(
                            stringResource(R.string.player_no_external_players),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sTheme.categoryText.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    ExternalPlayerDropdown(
                        label = stringResource(R.string.player_ext_live_tv),
                        selectedId = extPlayerLiveTv,
                        players = availablePlayers,
                        uiStyle = uiStyle,
                        onSelect = { scope.launch { context.saveExtPlayerLiveTv(it) } }
                    )
                    ExternalPlayerDropdown(
                        label = stringResource(R.string.player_ext_movies),
                        selectedId = extPlayerMovies,
                        players = availablePlayers,
                        uiStyle = uiStyle,
                        onSelect = { scope.launch { context.saveExtPlayerMovies(it) } }
                    )
                    ExternalPlayerDropdown(
                        label = stringResource(R.string.player_ext_series),
                        selectedId = extPlayerSeries,
                        players = availablePlayers,
                        uiStyle = uiStyle,
                        onSelect = { scope.launch { context.saveExtPlayerSeries(it) } }
                    )
                    ExternalPlayerDropdown(
                        label = stringResource(R.string.player_ext_catchup),
                        selectedId = extPlayerCatchup,
                        players = availablePlayers,
                        uiStyle = uiStyle,
                        showDivider = false,
                        onSelect = { scope.launch { context.saveExtPlayerCatchup(it) } }
                    )
                }
            }

            // ── Downloads & Recordings ────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_downloads),
                icon = Icons.Default.Download,
                uiStyle = uiStyle
            ) {
                SettingsActionItem(
                    label = stringResource(R.string.player_storage_location_label),
                    description = stringResource(R.string.player_storage_location_desc),
                    value = currentStorageLabel,
                    uiStyle = uiStyle,
                    onClick = { showStoragePicker = true },
                    showDivider = false,
                )
            }

            // ── Privacy ───────────────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_privacy),
                icon = Icons.Default.Security,
                uiStyle = uiStyle
            ) {
                SettingsToggle(
                    label = stringResource(R.string.player_whisper_label),
                    description = stringResource(R.string.player_whisper_desc),
                    checked = whisperSubtitles,
                    uiStyle = uiStyle,
                    onToggle = { scope.launch { context.saveWhisperSubtitles(!whisperSubtitles) } }
                )
                if (whisperSubtitles) {
                    SettingsToggle(
                        label = stringResource(R.string.player_whisper_autostart_label),
                        description = stringResource(R.string.player_whisper_autostart_desc),
                        checked = whisperAutoStartLive,
                        uiStyle = uiStyle,
                        onToggle = { scope.launch { context.saveWhisperAutostartLive(!whisperAutoStartLive) } }
                    )
                }
                SettingsToggle(
                    label = stringResource(R.string.player_auto_lang_label),
                    description = stringResource(R.string.player_auto_lang_desc),
                    checked = autoLangDetect,
                    uiStyle = uiStyle,
                    onToggle = { scope.launch { context.saveAutoLangDetect(!autoLangDetect) } }
                )
            }


            // ── Notifications ─────────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_notifications),
                icon = Icons.Default.Notifications,
                uiStyle = uiStyle
            ) {
                SettingsToggle(
                    label = stringResource(R.string.player_admin_notifs_label),
                    description = stringResource(R.string.player_admin_notifs_desc),
                    checked = adminNotifsEnabled,
                    uiStyle = uiStyle,
                    onToggle = { scope.launch { context.saveAdminNotificationsEnabled(profileId, !adminNotifsEnabled) } }
                )
            }

            // ── EPG ───────────────────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_epg),
                icon = Icons.Default.Schedule,
                uiStyle = uiStyle
            ) {
                val epgOffsetZero = stringResource(R.string.player_epg_offset_zero)
                val offsetLabel = when {
                    epgTimeOffset > 0 -> "+${epgTimeOffset}h"
                    epgTimeOffset < 0 -> "${epgTimeOffset}h"
                    else -> epgOffsetZero
                }
                SettingsActionItem(
                    label       = stringResource(R.string.player_epg_offset_label),
                    description = stringResource(R.string.player_epg_offset_desc),
                    value       = offsetLabel,
                    uiStyle     = uiStyle,
                    onClick     = { showEpgOffsetDialog = true },
                    showDivider = false
                )
            }

            // ── Formats ───────────────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.player_section_formats),
                icon = Icons.Default.Info,
                uiStyle = uiStyle
            ) {
                SettingsInfoRow(stringResource(R.string.player_formats_supported), stringResource(R.string.player_formats_supported_value))
                SettingsInfoRow(stringResource(R.string.player_formats_audio),     stringResource(R.string.player_formats_audio_value))
                SettingsInfoRow(stringResource(R.string.player_formats_subtitles), stringResource(R.string.player_formats_subtitles_value))
            }
        }
    }

    if (showEpgOffsetDialog) {
        AlertDialog(
            onDismissRequest = { showEpgOffsetDialog = false },
            title            = { Text(stringResource(R.string.player_epg_dialog_title)) },
            text             = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.player_epg_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = { scope.launch { context.saveEpgTimeOffset(epgTimeOffset - 1) } }) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.player_epg_decrease))
                        }
                        Text(
                            text      = if (epgTimeOffset == 0) stringResource(R.string.player_epg_offset_zero) else if (epgTimeOffset > 0) "+${epgTimeOffset}h" else "${epgTimeOffset}h",
                            style     = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.width(80.dp)
                        )
                        IconButton(onClick = { scope.launch { context.saveEpgTimeOffset(epgTimeOffset + 1) } }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.player_epg_increase))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEpgOffsetDialog = false }) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { context.saveEpgTimeOffset(0) }; showEpgOffsetDialog = false }) { Text(stringResource(R.string.common_reset)) }
            }
        )
    }
}

@Composable
private fun ExternalPlayerDropdown(
    label: String,
    selectedId: String,
    players: List<ExternalPlayerManager.PlayerOption>,
    uiStyle: UiStyle,
    showDivider: Boolean = true,
    onSelect: (String) -> Unit
) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    val selectedName = players.firstOrNull { it.id == selectedId }?.displayName
        ?: ExternalPlayerManager.NEXSTREAM.displayName

    var expanded by remember { mutableStateOf(false) }
    var triggerFocused by remember { mutableStateOf(false) }
    val triggerFR = remember { FocusRequester() }
    val optionFRs = remember(players.size) { List(players.size) { FocusRequester() } }
    var prevExpanded by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(expanded) {
        val prev = prevExpanded
        prevExpanded = expanded
        if (prev == null) return@LaunchedEffect
        kotlinx.coroutines.delay(50)
        try {
            if (expanded) {
                val idx = players.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                optionFRs.getOrElse(idx) { optionFRs.first() }.requestFocus()
            } else {
                triggerFR.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (triggerFocused || expanded)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(triggerFR)
                .onFocusChanged { triggerFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown &&
                        (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                        expanded = !expanded; true
                    } else false
                }
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = sTheme.categoryText
                )
                Text(
                    selectedName,
                    style = MaterialTheme.typography.bodySmall,
                    color = sTheme.categoryText.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = sTheme.categoryText.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            players.forEachIndexed { idx, player ->
                val isCurrent = player.id == selectedId
                var optFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(optionFRs[idx])
                        .background(
                            when {
                                isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                optFocused -> MaterialTheme.colorScheme.surfaceVariant
                                else       -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .onFocusChanged { optFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                onSelect(player.id); expanded = false; true
                            } else false
                        }
                        .clickable { onSelect(player.id); expanded = false }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        player.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    }
}

// ── Legacy private composables kept for backward compatibility ────────────────

@Composable
private fun SettingsActionRow(
    label: String,
    description: String,
    value: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = sTheme.categoryText)
            if (isFocused) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = sTheme.categoryText.copy(alpha = 0.7f))
            }
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Default.ChevronRight, null, tint = sTheme.categoryText.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onToggle(); true
                } else false
            }
            .clickable { onToggle() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = sTheme.categoryText)
            if (isFocused) {
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = sTheme.categoryText.copy(alpha = 0.7f))
            }
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = sTheme.categoryText.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = sTheme.categoryText)
    }
}
