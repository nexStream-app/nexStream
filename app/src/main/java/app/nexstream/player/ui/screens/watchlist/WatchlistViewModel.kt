package app.nexstream.player.ui.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.sync.WatchlistSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.flatMapLatest

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val syncManager: WatchlistSyncManager,
    val profileManager: ProfileManager
) : ViewModel() {

    private val activeProfileId: String
        get() = profileManager.activeProfile.value?.id ?:
        profileManager.profiles.value.firstOrNull { it.isDefault }?.id ?:
        "default"

    // Reactive to profile changes
    val allItems: StateFlow<List<WatchlistEntity>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            val profileId = profile?.id ?: "default"
            repository.getWatchlistItemsForProfile(profileId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchlistIds: StateFlow<Set<String>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            val profileId = profile?.id ?: "default"
            repository.getWatchlistItemsForProfile(profileId)
                .map { items -> items.map { it.id }.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        viewModelScope.launch {
            profileManager.activeProfile
                .filterNotNull()
                .first()
                .let { profile ->
                    syncManager.syncFromServer(profile.id)
                }
        }
    }

    fun isInWatchlist(id: String): Flow<Boolean> =
        repository.isInWatchlistForProfile(id, activeProfileId)

    fun addToWatchlist(item: WatchlistEntity) {
        viewModelScope.launch {
            android.util.Log.d("WATCHLIST", "activeProfile = ${profileManager.activeProfile.value?.id}")
            android.util.Log.d("WATCHLIST", "activeProfile name = ${profileManager.activeProfile.value?.name}")
            android.util.Log.d("WATCHLIST", "item.profileId = ${item.profileId}")
            val itemWithProfile = item.copy(profileId = activeProfileId)
            android.util.Log.d("WATCHLIST", "saving with profileId = ${itemWithProfile.profileId}")
            repository.addToWatchlist(itemWithProfile)
            syncManager.pushAdd(itemWithProfile)
        }
    }

    fun removeFromWatchlist(id: String, type: WatchlistType) {
        viewModelScope.launch {
            repository.removeFromWatchlist(id, activeProfileId)
            syncManager.pushRemove(id, type, activeProfileId)  // ADD activeProfileId
        }
    }

    fun toggleWatchlist(item: WatchlistEntity, currentlyInList: Boolean) {
        if (currentlyInList) removeFromWatchlist(item.id, item.type)
        else addToWatchlist(item)
    }
}