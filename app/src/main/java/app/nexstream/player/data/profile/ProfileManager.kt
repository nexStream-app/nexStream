package app.nexstream.player.data.profile

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.dao.ProfileAppearanceDao
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.entity.ProfileAppearanceEntity
import app.nexstream.player.data.local.entity.ProfileCategoryFilter
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.ThemeMode
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.saveAspectRatio
import app.nexstream.player.ui.theme.saveEpgMiniPlayer
import app.nexstream.player.ui.theme.saveFontScale
import app.nexstream.player.ui.theme.saveFontWeight
import app.nexstream.player.ui.theme.saveKeyboardFontScale
import app.nexstream.player.ui.theme.saveThemeMode
import app.nexstream.player.ui.theme.saveUiStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val PROFILE_EMOJIS = listOf(
    "🦁", "🐼", "🦊", "🐨", "🐸",
    "🦄", "🐯", "🐧", "🦋", "🐬",
    "🦅", "🐙", "🌟", "🎮", "🎬",
    "🏆", "🎵", "🚀", "⚽", "🎨"
)

@Singleton
class ProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProfileDao,
    private val profileAppearanceDao: ProfileAppearanceDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _blockedTvCategories = MutableStateFlow<Set<String>>(emptySet())
    val blockedTvCategories: StateFlow<Set<String>> = _blockedTvCategories.asStateFlow()
    private val prefs = context.getSharedPreferences("nexstream_profile", Context.MODE_PRIVATE)

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

    private val _profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val profiles: StateFlow<List<ProfileEntity>> = _profiles.asStateFlow()

    private val _initialSyncDone = MutableStateFlow(false)
    val initialSyncDone: StateFlow<Boolean> = _initialSyncDone.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun init() {
        scope.launch {
            ensureDefaultProfileExists()
            loadProfiles()
            restoreActiveProfile()
        }
        // Observe active profile's appearance in Room — re-applies to DataStore whenever
        // FCM sync updates the profile's appearance while it is already the active profile.
        scope.launch {
            _activeProfile
                .filterNotNull()
                .map { it.id }
                .distinctUntilChanged()
                .flatMapLatest { profileId -> profileAppearanceDao.getAppearanceFlow(profileId) }
                .distinctUntilChanged()
                .collect { _ ->
                    val activeId = _activeProfile.value?.id ?: return@collect
                    applyAppearanceToDataStore(activeId)
                }
        }
    }

    private suspend fun ensureDefaultProfileExists() {
        val existing = dao.getDefaultProfile()
        if (existing == null) {
            dao.upsertProfile(
                ProfileEntity(
                    id           = UUID.randomUUID().toString(),
                    name         = "Default",
                    emoji        = "🦁",
                    isDefault    = true,
                    isRestricted = false,
                    sortOrder    = 0
                )
            )
        }
    }

    private suspend fun loadProfiles() {
        _profiles.value = dao.getAllProfilesOnce()
    }

    private suspend fun restoreActiveProfile() {
        val savedId = prefs.getString("active_profile_id", null)
        val profile = if (savedId != null) dao.getProfileById(savedId) else null
        val active = profile ?: dao.getDefaultProfile()
        _activeProfile.value = active
        active?.let {
            _blockedTvCategories.value = dao.getBlockedCategories(it.id, "TV").toSet()
        }
    }

    suspend fun createProfile(
        name: String,
        emoji: String,
        pin: String? = null,
        isRestricted: Boolean = false,
        maxAgeRating: String? = null,
        allowNr: Boolean = true
    ): ProfileEntity {
        val count = dao.getProfileCount()
        val profile = ProfileEntity(
            id           = UUID.randomUUID().toString(),
            name         = name,
            emoji        = emoji,
            pinHash      = pin?.let { hashPin(it) },
            isDefault    = false,
            isRestricted = isRestricted,
            sortOrder    = count,
            maxAgeRating = maxAgeRating,
            allowNr      = allowNr
        )
        dao.upsertProfile(profile)
        loadProfiles()
        return profile
    }

    suspend fun updateProfile(
        profile: ProfileEntity,
        pin: String? = null,
        isRestricted: Boolean? = null
    ): ProfileEntity {
        val updated = profile.copy(
            pinHash      = pin?.let { hashPin(it) } ?: profile.pinHash,
            isRestricted = isRestricted ?: profile.isRestricted,
            updatedAt    = System.currentTimeMillis()
        )
        dao.upsertProfile(updated)
        loadProfiles()
        if (_activeProfile.value?.id == updated.id) {
            _activeProfile.value = updated
        }
        return updated
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteNonDefaultProfile(id)
        dao.deleteFiltersForProfile(id)
        loadProfiles()
        if (_activeProfile.value?.id == id) {
            _activeProfile.value = dao.getDefaultProfile()
        }
    }

    suspend fun setBlockedCategories(profileId: String, type: String, blocked: Set<String>) {
        dao.deleteFiltersForProfileAndType(profileId, type)
        val toAdd = blocked.map { name ->
            ProfileCategoryFilter(
                profileId    = profileId,
                categoryType = type,
                categoryName = name,
                isAllowed    = false
            )
        }
        if (toAdd.isNotEmpty()) dao.upsertFilters(toAdd)
        if (_activeProfile.value?.id == profileId && type == "TV") {
            _blockedTvCategories.value = blocked
        }
    }

    suspend fun getBlockedCategories(profileId: String, type: String): Set<String> {
        return dao.getBlockedCategories(profileId, type).toSet()
    }

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean {
        val hash = profile.pinHash ?: return true
        return hash == hashPin(pin)
    }

    fun hasPin(profile: ProfileEntity): Boolean = profile.pinHash != null

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun refreshProfiles() {
        scope.launch { loadProfiles() }
    }

    fun refreshAfterSync() {
        _initialSyncDone.value = true
        scope.launch {
            loadProfiles()
            val savedId = prefs.getString("active_profile_id", null)
            val updated = if (savedId != null) dao.getProfileById(savedId) else null
            val active  = updated ?: dao.getDefaultProfile()
            if (active != null) {
                _activeProfile.value = active
                val blocked = dao.getBlockedCategories(active.id, "TV").toSet()
                Log.d("ProfileSync", "refreshAfterSync: activeProfile=${active.name}, blockedCount=${blocked.size}")
                _blockedTvCategories.value = blocked
            }
        }
    }

    // 1. Add a cached blocked categories map (populated when profile switches):
    private val _blockedCategoriesCache = mutableMapOf<Pair<String, String>, Set<String>>()

    // 2. Add this method — returns from cache without any DB call:
    fun getBlockedCategoriesCached(profileId: String, type: String): Set<String> {
        return _blockedCategoriesCache[Pair(profileId, type)] ?: emptySet()
    }

    // 3. In setActiveProfile, populate the cache for all types:
    fun setActiveProfile(profile: ProfileEntity) {
        _activeProfile.value = profile
        prefs.edit().putString("active_profile_id", profile.id).apply()
        scope.launch {
            _blockedTvCategories.value = dao.getBlockedCategories(profile.id, "TV").toSet()
            // Populate cache for all types
            listOf("TV", "MOVIE", "SERIES").forEach { type ->
                _blockedCategoriesCache[Pair(profile.id, type)] =
                    dao.getBlockedCategories(profile.id, type).toSet()
            }
            // Apply per-profile appearance to DataStore
            applyAppearanceToDataStore(profile.id)
        }
    }

    private suspend fun applyAppearanceToDataStore(profileId: String) {
        val appearance = profileAppearanceDao.getAppearance(profileId)
            ?: ProfileAppearanceEntity(profileId = profileId)
        try {
            context.saveThemeMode(
                ThemeMode.entries.firstOrNull { it.name == appearance.themeMode } ?: ThemeMode.DARK
            )
            appearance.fontScale?.let { context.saveFontScale(it) }
            appearance.fontWeight?.let { context.saveFontWeight(it) }
            context.saveUiStyle(
                try { UiStyle.valueOf(appearance.uiStyle) } catch (_: Exception) { UiStyle.CLASSIC }
            )
            context.saveAspectRatio(
                AspectRatioType.TV,
                try { AspectRatio.valueOf(appearance.tvAspectRatio) } catch (_: Exception) { AspectRatio.FILL }
            )
            context.saveAspectRatio(
                AspectRatioType.MOVIE,
                try { AspectRatio.valueOf(appearance.movieAspectRatio) } catch (_: Exception) { AspectRatio.FIT }
            )
            context.saveAspectRatio(
                AspectRatioType.SERIES,
                try { AspectRatio.valueOf(appearance.seriesAspectRatio) } catch (_: Exception) { AspectRatio.FIT }
            )
            context.saveEpgMiniPlayer(appearance.epgMiniPlayer)
            context.saveKeyboardFontScale(appearance.keyboardFontScale)
        } catch (e: Exception) {
            Log.e("ProfileManager", "Failed to apply appearance for profile $profileId", e)
        }
    }
}