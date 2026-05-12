package app.nexstream.player.ui.screens.recentlywatched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentlyWatchedViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    val recentlyWatched: StateFlow<List<RecentlyWatchedEntity>> =
        repository.getRecentlyWatched()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteRecentlyWatched(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearRecentlyWatched() }
    }
}