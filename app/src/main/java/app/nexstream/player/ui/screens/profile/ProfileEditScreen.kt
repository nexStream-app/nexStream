package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
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

    suspend fun getBlockedCategories(profileId: String, type: String) =
        profileManager.getBlockedCategories(profileId, type)

    fun save(
        existing: ProfileEntity?,
        name: String,
        emoji: String,
        pin: String?,
        removePin: Boolean,
        isRestricted: Boolean,
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
                    isRestricted = isRestricted
                )
            } else {
                val pinToSet = when {
                    removePin            -> ""
                    !pin.isNullOrEmpty() -> pin
                    else                 -> null
                }
                profileManager.updateProfile(
                    profile      = existing.copy(name = name, emoji = emoji),
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
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: ProfileEditViewModel = hiltViewModel()
) {
    var name         by remember { mutableStateOf(existingProfile?.name ?: "") }
    var emoji        by remember { mutableStateOf(existingProfile?.emoji ?: PROFILE_EMOJIS.first()) }
    var pin          by remember { mutableStateOf("") }
    var pinConfirm   by remember { mutableStateOf("") }
    var removePin    by remember { mutableStateOf(false) }
    var isRestricted by remember { mutableStateOf(existingProfile?.isRestricted ?: false) }
    var selectedTab  by remember { mutableStateOf(0) }
    var categorySearch by remember { mutableStateOf("") }

    val tvCategories     by viewModel.tvCategories.collectAsState()
    val movieCategories  by viewModel.movieCategories.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()

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

    val allCategories = when (selectedTab) { 0 -> tvCategories; 1 -> movieCategories; else -> seriesCategories }
    val blocked       = when (selectedTab) { 0 -> blockedTv;    1 -> blockedMovies;   else -> blockedSeries }
    val setBlocked: (Set<String>) -> Unit = when (selectedTab) {
        0    -> { s -> blockedTv     = s }
        1    -> { s -> blockedMovies = s }
        else -> { s -> blockedSeries = s }
    }

    val filteredCategories = remember(allCategories, categorySearch) {
        if (categorySearch.isBlank()) allCategories
        else allCategories.filter { it.contains(categorySearch, ignoreCase = true) }
    }

    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LazyColumn(
        state              = listState,
        modifier           = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding      = PaddingValues(vertical = 24.dp)
    ) {

        // ── Emoji picker ──────────────────────────────────────────────────────
        item {
            Text(
                "Choose Avatar",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PROFILE_EMOJIS.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { e ->
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                        else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
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
                                                    ev.key == Key.Enter ||
                                                            ev.key == Key.NumPadEnter ||
                                                            ev.key == Key.DirectionCenter
                                                    )) { emoji = e; true } else false
                                    }
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 28.sp) }
                        }
                    }
                }
            }
        }

        // ── Name ──────────────────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Profile Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        }

        // ── Restricted account toggle ─────────────────────────────────────────
        item {
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = if (isRestricted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isRestricted) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isRestricted) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Restricted Profile",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Hides the TV channels screen entirely",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                ev.key == Key.Enter ||
                                                        ev.key == Key.NumPadEnter ||
                                                        ev.key == Key.DirectionCenter
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

        // ── PIN ───────────────────────────────────────────────────────────────
        item {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("PIN Protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (hasPin && !removePin) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                    ev.key == Key.Enter ||
                                                            ev.key == Key.NumPadEnter ||
                                                            ev.key == Key.DirectionCenter
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
                            Text("PIN will be removed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            var keepFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .onFocusChanged { keepFocused = it.isFocused }
                                    .onKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && (
                                                    ev.key == Key.Enter ||
                                                            ev.key == Key.NumPadEnter ||
                                                            ev.key == Key.DirectionCenter
                                                    )) { removePin = false; true } else false
                                    }
                                    .clickable { removePin = false }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Keep PIN", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value         = pin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                            label         = { Text(if (hasPin) "New PIN (4 digits)" else "Set PIN (optional, 4 digits)") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        if (pin.isNotEmpty()) {
                            OutlinedTextField(
                                value         = pinConfirm,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinConfirm = it },
                                label         = { Text("Confirm PIN") },
                                singleLine    = true,
                                isError       = pinMismatch,
                                supportingText = if (pinMismatch) { { Text("PINs do not match") } } else null,
                                modifier      = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // ── Category filters ──────────────────────────────────────────────────
        item {
            Text("Allowed Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Uncheck categories to hide them from this profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // Tab row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("TV", "Movies", "Series").forEachIndexed { i, label ->
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
                                            ev.key == Key.Enter ||
                                                    ev.key == Key.NumPadEnter ||
                                                    ev.key == Key.DirectionCenter
                                            )) { selectedTab = i; categorySearch = ""; true } else false
                            }
                            .clickable { selectedTab = i; categorySearch = "" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (count > 0) {
                                Text(
                                    "$count blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search box
            OutlinedTextField(
                value         = categorySearch,
                onValueChange = { categorySearch = it },
                placeholder   = { Text("Search categories...") },
                leadingIcon   = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon  = if (categorySearch.isNotEmpty()) {
                    { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp).clickable { categorySearch = "" }) }
                } else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(8.dp)
            )

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
                        .background(if (aFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        .onFocusChanged { aFocused = it.isFocused }
                        .onKeyEvent { ev ->
                            if (!allAllowed && ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                        )) { setBlocked(blocked - allCategories.toSet()); true } else false
                        }
                        .clickable(enabled = !allAllowed) { setBlocked(blocked - allCategories.toSet()) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Select All", style = MaterialTheme.typography.labelMedium) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp,
                            if (uFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp))
                        .background(if (uFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        .onFocusChanged { uFocused = it.isFocused }
                        .onKeyEvent { ev ->
                            if (!noneAllowed && ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                        )) { setBlocked(blocked + allCategories.toSet()); true } else false
                        }
                        .clickable(enabled = !noneAllowed) { setBlocked(blocked + allCategories.toSet()) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Unselect All", style = MaterialTheme.typography.labelMedium) }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "${allCategories.size - blocked.size} of ${allCategories.size} allowed" +
                        if (categorySearch.isNotBlank()) " · showing ${filteredCategories.size} results" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Category checkboxes ───────────────────────────────────────────────
        items(filteredCategories) { cat ->
            val allowed = cat !in blocked
            var isFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && (
                                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) {
                            setBlocked(if (allowed) blocked + cat else blocked - cat); true
                        } else false
                    }
                    .clickable { setBlocked(if (allowed) blocked + cat else blocked - cat) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = allowed,
                    onCheckedChange = { setBlocked(if (it) blocked - cat else blocked + cat) }
                )
                Text(cat, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }

        // ── Save button ───────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            var saveFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!canSave) MaterialTheme.colorScheme.surfaceVariant
                        else if (saveFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.primary
                    )
                    .onFocusChanged { saveFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (canSave && ev.type == KeyEventType.KeyDown && (
                                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) {
                            viewModel.save(
                                existing     = existingProfile,
                                name         = name,
                                emoji        = emoji,
                                pin          = pin.ifEmpty { null },
                                removePin    = removePin,
                                isRestricted = isRestricted,
                                blockedTv    = blockedTv,
                                blockedMovies = blockedMovies,
                                blockedSeries = blockedSeries,
                                onDone       = onDone
                            )
                            true
                        } else false
                    }
                    .clickable(enabled = canSave) {
                        viewModel.save(
                            existing     = existingProfile,
                            name         = name,
                            emoji        = emoji,
                            pin          = pin.ifEmpty { null },
                            removePin    = removePin,
                            isRestricted = isRestricted,
                            blockedTv    = blockedTv,
                            blockedMovies = blockedMovies,
                            blockedSeries = blockedSeries,
                            onDone       = onDone
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (existingProfile == null) "Create Profile" else "Save Changes",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (!canSave) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}