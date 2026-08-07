package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.profile.PROFILE_EMOJIS
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    val profileManager: ProfileManager,
    private val syncManager: ProfileSyncManager,
    private val repository: PlaylistRepository
) : ViewModel() {

    val tvCategories: StateFlow<List<String>>     = repository.getChannelCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val movieCategories: StateFlow<List<String>>  = repository.getMovieCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seriesCategories: StateFlow<List<String>> = repository.getSeriesCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tvChannelCounts: StateFlow<Map<String, Int>> = repository.getChannelCountsByCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun getBlockedCategories(profileId: String, type: String) =
        profileManager.getBlockedCategories(profileId, type)

    fun save(
        existing: ProfileEntity?,
        name: String,
        emoji: String,
        pin: String?,
        removePin: Boolean,
        isRestricted: Boolean,
        maxAgeRating: String?,
        allowNr: Boolean,
        blockedTv: Set<String>,
        blockedMovies: Set<String>,
        blockedSeries: Set<String>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val profile = if (existing == null) {
                profileManager.createProfile(
                    name         = name,
                    emoji        = emoji,
                    pin          = pin.takeIf { !it.isNullOrEmpty() },
                    isRestricted = isRestricted,
                    maxAgeRating = maxAgeRating,
                    allowNr      = allowNr
                )
            } else {
                val baseProfile = existing.copy(
                    name         = name,
                    emoji        = emoji,
                    maxAgeRating = maxAgeRating,
                    allowNr      = allowNr,
                    // When removing the PIN, clear pinHash in the base profile so updateProfile
                    // sees null and doesn't fall back to the existing hash.
                    pinHash      = if (removePin) null else existing.pinHash
                )
                val pinToSet = when {
                    removePin            -> null
                    !pin.isNullOrEmpty() -> pin
                    else                 -> null
                }
                profileManager.updateProfile(
                    profile      = baseProfile,
                    pin          = pinToSet,
                    isRestricted = isRestricted
                )
            }
            profileManager.setBlockedCategories(profile.id, "TV",     blockedTv)
            profileManager.setBlockedCategories(profile.id, "MOVIE",  blockedMovies)
            profileManager.setBlockedCategories(profile.id, "SERIES", blockedSeries)
            try {
                syncManager.pushProfiles(
                    tvCategories     = repository.getChannelCategories().first(),
                    movieCategories  = repository.getMovieCategories().first(),
                    seriesCategories = repository.getSeriesCategories().first()
                )
            } catch (e: Exception) {
                android.util.Log.e("ProfileEdit", "Push failed: ${e.message}")
            }
            onDone()
        }
    }
}

@Composable
fun ProfileEditScreen(
    existingProfile: ProfileEntity? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: ProfileEditViewModel = hiltViewModel()
) {
    var name           by remember { mutableStateOf(existingProfile?.name ?: "") }
    var emoji          by remember { mutableStateOf(existingProfile?.emoji ?: PROFILE_EMOJIS.first()) }
    var pin            by remember { mutableStateOf("") }
    var pinConfirm     by remember { mutableStateOf("") }
    var removePin      by remember { mutableStateOf(false) }
    var isRestricted   by remember { mutableStateOf(existingProfile?.isRestricted ?: false) }
    var maxAgeRating   by remember { mutableStateOf(existingProfile?.maxAgeRating) }
    var allowNr        by remember { mutableStateOf(existingProfile?.allowNr ?: true) }
    var selectedTab    by remember { mutableStateOf(0) }
    var categorySearch by remember { mutableStateOf("") }
    // "name" | "pin" | "pin_confirm" | "cat_search" | null
    var kbField        by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val isTV    = remember { context.packageManager.hasSystemFeature("android.software.leanback") }

    val isDefault = existingProfile?.isDefault == true

    val activeProfile by viewModel.profileManager.activeProfile.collectAsState()
    val canManageRestrictions = activeProfile?.isDefault == true

    val tvCategories     by viewModel.tvCategories.collectAsState()
    val movieCategories  by viewModel.movieCategories.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()
    val tvChannelCounts  by viewModel.tvChannelCounts.collectAsState()

    var blockedTv     by remember { mutableStateOf(setOf<String>()) }
    var blockedMovies by remember { mutableStateOf(setOf<String>()) }
    var blockedSeries by remember { mutableStateOf(setOf<String>()) }

    val hasPin = existingProfile?.pinHash != null

    LaunchedEffect(existingProfile?.id) {
        val id = existingProfile?.id ?: return@LaunchedEffect
        blockedTv     = viewModel.getBlockedCategories(id, "TV")
        blockedMovies = viewModel.getBlockedCategories(id, "MOVIE")
        blockedSeries = viewModel.getBlockedCategories(id, "SERIES")
    }

    val pinMismatch = pin.isNotEmpty() && pin != pinConfirm
    val canSave     = name.isNotBlank() && !pinMismatch && (pin.isEmpty() || pin.length == 4)

    // Auto-save for edit mode — debounced 700ms after last change
    LaunchedEffect(name, emoji, pin, pinConfirm, removePin, isRestricted, maxAgeRating, allowNr, blockedTv, blockedMovies, blockedSeries) {
        if (existingProfile != null && canSave) {
            kotlinx.coroutines.delay(700)
            viewModel.save(
                existing      = existingProfile,
                name          = name,
                emoji         = emoji,
                pin           = pin.ifEmpty { null },
                removePin     = removePin,
                isRestricted  = isRestricted,
                maxAgeRating  = maxAgeRating,
                allowNr       = allowNr,
                blockedTv     = blockedTv,
                blockedMovies = blockedMovies,
                blockedSeries = blockedSeries,
                onDone        = {}  // stay on screen; user navigates away with Back
            )
        }
    }

    val allCategories = when (selectedTab) { 0 -> tvCategories; 1 -> movieCategories; else -> seriesCategories }
    val blocked       = when (selectedTab) { 0 -> blockedTv;    1 -> blockedMovies;   else -> blockedSeries }
    val setBlocked: (Set<String>) -> Unit = when (selectedTab) {
        0    -> { s -> blockedTv     = s }
        1    -> { s -> blockedMovies = s }
        else -> { s -> blockedSeries = s }
    }

    val allGroups      = remember(allCategories)             { groupCategories(allCategories) }
    val filteredGroups = remember(allGroups, categorySearch) {
        if (categorySearch.isBlank()) allGroups
        else allGroups.filter { g ->
            g.displayName.contains(categorySearch, ignoreCase = true) ||
            g.categories.any { it.contains(categorySearch, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            state               = listState,
            modifier            = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding      = PaddingValues(vertical = 24.dp)
        ) {

            // ── Back button (mobile/touch only) ──────────────────────────────
            if (!isTV) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCancel() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Profiles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Emoji picker (single scrollable row) ─────────────────────────
            item {
                Text(
                    "Choose Avatar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PROFILE_EMOJIS) { e ->
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (e == emoji || isFocused) 2.dp else 0.dp,
                                    color = if (e == emoji) MaterialTheme.colorScheme.primary
                                            else if (isFocused) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) { emoji = e; true } else false
                                }
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 24.sp) }
                    }
                }
            }

            // ── Name ──────────────────────────────────────────────────────────
            item {
                var isFocused by remember { mutableStateOf(false) }
                TvDisplayField(
                    value     = name,
                    label     = "Profile Name",
                    isFocused = isFocused,
                    onFocus   = { isFocused = it },
                    onClick   = { kbField = "name" },
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            // ── Restricted account toggle (admin only; hidden for default profile) ──
            if (!isDefault && canManageRestrictions) {
                item {
                    val sTheme = LocalNexStreamTheme.current.sidebar
                    Surface(
                        shape        = RoundedCornerShape(12.dp),
                        color        = if (isRestricted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                       else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier     = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier            = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier              = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector        = if (isRestricted) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint               = if (isRestricted) MaterialTheme.colorScheme.error
                                                             else sTheme.categoryText,
                                        modifier           = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            "Restricted Profile",
                                            style      = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = sTheme.categoryTextSelected
                                        )
                                        Text(
                                            "Hides the TV channels screen entirely",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sTheme.categoryText
                                        )
                                    }
                                }
                                var switchFocused by remember { mutableStateOf(false) }
                                Switch(
                                    checked         = isRestricted,
                                    onCheckedChange = { isRestricted = it },
                                    modifier        = Modifier
                                        .onFocusChanged { switchFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (
                                                ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                            )) { isRestricted = !isRestricted; true } else false
                                        }
                                )
                            }
                            if (isRestricted) {
                                Text(
                                    "⚠ This profile will not see live TV channels. Useful for child profiles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // ── Age restriction ───────────────────────────────────────────────
            if (canManageRestrictions) {
                item {
                    val sTheme = LocalNexStreamTheme.current.sidebar
                    Surface(
                        shape        = RoundedCornerShape(12.dp),
                        color        = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier     = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp), tint = sTheme.categoryText)
                                Column {
                                    Text("Age Restriction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = sTheme.categoryTextSelected)
                                    Text("Hide Movies and Series above the selected rating", style = MaterialTheme.typography.bodySmall, color = sTheme.categoryText)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(null, "U", "PG", "12", "15", "18").forEach { rating ->
                                    val isSelected = maxAgeRating == rating
                                    val label = rating ?: "None"
                                    var chipFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else if (chipFocused) MaterialTheme.colorScheme.surfaceVariant
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                width = if (isSelected || chipFocused) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else if (chipFocused) MaterialTheme.colorScheme.outline
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .onFocusChanged { chipFocused = it.isFocused }
                                            .onKeyEvent { ev ->
                                                if (ev.type == KeyEventType.KeyDown && (
                                                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                                )) { maxAgeRating = rating; true } else false
                                            }
                                            .clickable { maxAgeRating = rating }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            // NR (Not Rated) toggle
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Allow Unrated (NR) Content",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = LocalNexStreamTheme.current.sidebar.categoryTextSelected
                                    )
                                    Text(
                                        "Shows and movies with no age certificate",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalNexStreamTheme.current.sidebar.categoryText
                                    )
                                }
                                var nrSwitchFocused by remember { mutableStateOf(false) }
                                Switch(
                                    checked         = allowNr,
                                    onCheckedChange = { allowNr = it },
                                    modifier        = Modifier
                                        .onFocusChanged { nrSwitchFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (
                                                ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                            )) { allowNr = !allowNr; true } else false
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // ── PIN — hidden for restricted profiles (they can't set their own PIN) ──
            if (!isRestricted) item {
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "PIN Protection",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (hasPin && !removePin) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("PIN is set", style = MaterialTheme.typography.bodySmall)
                                }
                                var removeFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .onFocusChanged { removeFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (
                                                ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                            )) { removePin = true; true } else false
                                        }
                                        .clickable { removePin = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        "Remove PIN",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else if (removePin) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "PIN will be removed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                var keepFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .onFocusChanged { keepFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (
                                                ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                            )) { removePin = false; true } else false
                                        }
                                        .clickable { removePin = false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("Keep PIN", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            var pinFocused by remember { mutableStateOf(false) }
                            TvDisplayField(
                                value     = if (pin.isEmpty()) "" else "•".repeat(pin.length),
                                label     = if (hasPin) "New PIN (4 digits)" else "Set PIN (optional, 4 digits)",
                                isFocused = pinFocused,
                                onFocus   = { pinFocused = it },
                                onClick   = { kbField = "pin" },
                                modifier  = Modifier.fillMaxWidth()
                            )
                            if (pin.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                var pinConfirmFocused by remember { mutableStateOf(false) }
                                TvDisplayField(
                                    value          = if (pinConfirm.isEmpty()) "" else "•".repeat(pinConfirm.length),
                                    label          = "Confirm PIN",
                                    isFocused      = pinConfirmFocused,
                                    onFocus        = { pinConfirmFocused = it },
                                    onClick        = { kbField = "pin_confirm" },
                                    isError        = pinMismatch,
                                    supportingText = if (pinMismatch) "PINs do not match" else null,
                                    modifier       = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // ── Category filters (admin can set for restricted profiles) ─────────
            if (!isRestricted || canManageRestrictions) {
                item {
                    val sTheme = LocalNexStreamTheme.current.sidebar
                    Text(
                        "Allowed Categories",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = sTheme.categoryTextSelected
                    )
                    Text(
                        "Tap a category to toggle it on or off for this profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText
                    )
                    Spacer(Modifier.height(8.dp))

                    // Tab row
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("TV", "Movies", "Series").forEachIndexed { i, tabLabel ->
                            val isSelected = selectedTab == i
                            val count = when (i) {
                                0 -> blockedTv.size; 1 -> blockedMovies.size; else -> blockedSeries.size
                            }
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        width = if (isSelected || isFocused) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else if (isFocused) MaterialTheme.colorScheme.outline
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && (
                                            ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                        )) { selectedTab = i; categorySearch = ""; true } else false
                                    }
                                    .clickable { selectedTab = i; categorySearch = "" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        tabLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (count > 0) "$count blocked" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (count > 0) MaterialTheme.colorScheme.error
                                                else Color.Transparent
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Search (TV-friendly clickable field)
                    var catSearchFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (catSearchFocused) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                if (catSearchFocused) 2.dp else 1.dp,
                                if (catSearchFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { catSearchFocused = it.isFocused }
                            .onKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && (
                                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                )) { kbField = "cat_search"; true } else false
                            }
                            .clickable { kbField = "cat_search" }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            categorySearch.ifEmpty { "Search categories…" },
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = if (categorySearch.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                       else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (categorySearch.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close, null,
                                modifier = Modifier.size(16.dp).clickable { categorySearch = "" },
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Select / Unselect all
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allAllowed  = allCategories.isNotEmpty() && allCategories.none { it in blocked }
                        val noneAllowed = allCategories.isNotEmpty() && allCategories.all  { it in blocked }
                        var aFocused by remember { mutableStateOf(false) }
                        var uFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp,
                                    if (aFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp))
                                .background(
                                    if (aFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .onFocusChanged { aFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (!allAllowed && ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) { setBlocked(blocked - allCategories.toSet()); true } else false
                                }
                                .clickable(enabled = !allAllowed) { setBlocked(blocked - allCategories.toSet()) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Select All",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (aFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp,
                                    if (uFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp))
                                .background(
                                    if (uFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .onFocusChanged { uFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (!noneAllowed && ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) { setBlocked(blocked + allCategories.toSet()); true } else false
                                }
                                .clickable(enabled = !noneAllowed) { setBlocked(blocked + allCategories.toSet()) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Unselect All",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    val allowedCount = allCategories.count { it !in blocked }
                    Text(
                        "$allowedCount of ${allCategories.size} allowed" +
                            if (categorySearch.isNotBlank()) " · ${filteredGroups.size} group(s) shown" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Category group cards — 6 per row, poster card style ──────
                items(
                    items = filteredGroups.chunked(6),
                    key   = { row -> row.first().key }
                ) { row ->
                    val primary = MaterialTheme.colorScheme.primary
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { group ->
                            val groupAllowed = group.categories.none { it in blocked }
                            val groupBlocked = group.categories.all  { it in blocked }
                            val counts = if (selectedTab == 0) tvChannelCounts else emptyMap()
                            val totalChannels = group.categories.sumOf { counts[it] ?: 0 }
                                .takeIf { it > 0 } ?: group.categories.size
                            val blockedCount = group.categories.filter { it in blocked }
                                .sumOf { counts[it] ?: 1 }
                                .takeIf { counts.isNotEmpty() } ?: group.categories.count { it in blocked }
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.7f)
                                    .border(
                                        width = if (isFocused) 3.dp else 0.dp,
                                        color = primary,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && (
                                            ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                        )) {
                                            val cats = group.categories.toSet()
                                            setBlocked(if (groupAllowed) blocked + cats else blocked - cats)
                                            true
                                        } else false
                                    }
                                    .clickable {
                                        val cats = group.categories.toSet()
                                        setBlocked(if (groupAllowed) blocked + cats else blocked - cats)
                                    }
                            ) {
                                // Centre: flag emoji or prefix label
                                if (group.isCountry) {
                                    Text(
                                        group.flag,
                                        fontSize = 22.sp,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(bottom = 18.dp)
                                    )
                                } else if (group.categories.size > 1) {
                                    Text(
                                        group.key.take(4),
                                        style      = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = Color.White.copy(alpha = 0.25f),
                                        modifier   = Modifier
                                            .align(Alignment.Center)
                                            .padding(bottom = 18.dp)
                                    )
                                }

                                // Gradient overlay covering lower half
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.6f)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0xD9000000))
                                            )
                                        )
                                )

                                // Status + title at bottom (status above name)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    if (totalChannels > 0) {
                                        Text(
                                            when {
                                                groupBlocked     -> "All blocked"
                                                blockedCount > 0 -> "$blockedCount of $totalChannels blocked"
                                                else             -> "$totalChannels channels"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (groupBlocked || blockedCount > 0)
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                            else
                                                Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        group.displayName,
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Color.White,
                                        maxLines   = 2,
                                        overflow   = TextOverflow.Ellipsis
                                    )
                                }

                                // Tick when allowed, empty circle when blocked/mixed
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (groupAllowed) primary
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (groupAllowed) primary
                                            else Color.White.copy(alpha = 0.55f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (groupAllowed) {
                                        Icon(
                                            imageVector        = Icons.Default.Check,
                                            contentDescription = null,
                                            tint               = MaterialTheme.colorScheme.onPrimary,
                                            modifier           = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // ── Create button (new profiles only — edits auto-save) ─────────────
            if (existingProfile == null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    val createFR = remember { FocusRequester() }
                    Button(
                        onClick  = {
                            if (canSave) viewModel.save(
                                existing      = null,
                                name          = name,
                                emoji         = emoji,
                                pin           = pin.ifEmpty { null },
                                removePin     = removePin,
                                isRestricted  = isRestricted,
                                maxAgeRating  = maxAgeRating,
                                allowNr       = allowNr,
                                blockedTv     = blockedTv,
                                blockedMovies = blockedMovies,
                                blockedSeries = blockedSeries,
                                onDone        = onDone
                            )
                        },
                        enabled  = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(createFR),
                        shape    = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text("Create Profile", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── TVKeyboard overlay ────────────────────────────────────────────────
        TvKeyboardSheet(
            visible = kbField != null,
            value = when (kbField) {
                "name"        -> name
                "pin"         -> pin
                "pin_confirm" -> pinConfirm
                "cat_search"  -> categorySearch
                else          -> ""
            },
            onValueChange = { v ->
                when (kbField) {
                    "name"        -> name = v
                    "pin"         -> if (v.length <= 4 && v.all { c -> c.isDigit() }) pin = v
                    "pin_confirm" -> if (v.length <= 4 && v.all { c -> c.isDigit() }) pinConfirm = v
                    "cat_search"  -> categorySearch = v
                }
            },
            onDone    = { kbField = null },
            onDismiss = { kbField = null },
            hint = when (kbField) {
                "name"        -> "Profile name"
                "pin"         -> "4-digit PIN (numbers only)"
                "pin_confirm" -> "Confirm PIN"
                "cat_search"  -> "Search categories"
                else          -> ""
            }
        )
    }
}

private data class CategoryGroup(
    val key: String,
    val displayName: String,
    val categories: List<String>,
    val isCountry: Boolean = false,
    val flag: String = ""
)

private fun countryCodeToFlag(code: String): String =
    code.uppercase().map { ch ->
        String(Character.toChars(ch.code - 'A'.code + 0x1F1E6))
    }.joinToString("")

private fun groupCategories(categories: List<String>): List<CategoryGroup> {
    val grouped = linkedMapOf<String, MutableList<String>>()
    val standalone = mutableListOf<String>()

    for (cat in categories) {
        val pipeIdx = cat.indexOf(" | ")
        if (pipeIdx > 0) {
            grouped.getOrPut(cat.substring(0, pipeIdx).trim()) { mutableListOf() }.add(cat)
        } else {
            standalone.add(cat)
        }
    }

    val result = mutableListOf<CategoryGroup>()

    for ((prefix, cats) in grouped.entries.sortedBy { it.key }) {
        val isCountry = prefix.length == 2 && prefix.all { it.isUpperCase() && it.isLetter() }
        val flag      = if (isCountry) countryCodeToFlag(prefix) else ""
        val displayName = if (isCountry) "$flag $prefix Channels" else "$prefix Channels"
        result.add(CategoryGroup(prefix, displayName, cats.sorted(), isCountry, flag))
    }

    for (cat in standalone.sorted()) {
        result.add(CategoryGroup(cat, cat, listOf(cat)))
    }

    return result
}

@Composable
private fun TvDisplayField(
    value: String,
    label: String,
    isFocused: Boolean,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    when {
                        isError   -> MaterialTheme.colorScheme.error
                        isFocused -> MaterialTheme.colorScheme.primary
                        else      -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                    RoundedCornerShape(8.dp)
                )
                .background(
                    if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                )
                .onFocusChanged { onFocus(it.isFocused) }
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && (
                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                    )) { onClick(); true } else false
                }
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isError   -> MaterialTheme.colorScheme.error
                        isFocused -> MaterialTheme.colorScheme.primary
                        else      -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    if (value.isEmpty()) "Tap to enter" else value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isEmpty())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (supportingText != null) {
            Text(
                supportingText,
                style    = MaterialTheme.typography.labelSmall,
                color    = if (isError) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
