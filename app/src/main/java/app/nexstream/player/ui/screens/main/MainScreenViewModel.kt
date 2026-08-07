package app.nexstream.player.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.data.sync.ProgressSyncManager
import app.nexstream.player.license.LicencePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistLoadState(val loaded: Boolean, val playlists: List<PlaylistEntity>)

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    val repository: PlaylistRepository,
    private val progressSyncManager: ProgressSyncManager,
    private val progressRepository: WatchProgressRepository,
    private val profileManager: ProfileManager,
    private val licencePreferences: LicencePreferences
) : ViewModel() {

    val isReseller: Boolean = licencePreferences.isResellerAssigned()

    // Single subscription — loaded + playlists are always from the same emission
    val playlistLoadState: StateFlow<PlaylistLoadState> = repository.getAllPlaylists()
        .map { PlaylistLoadState(loaded = true, playlists = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistLoadState(false, emptyList()))

    val playlists: StateFlow<List<PlaylistEntity>> = playlistLoadState
        .map { it.playlists }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlistsLoaded: StateFlow<Boolean> = playlistLoadState
        .map { it.loaded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChannels: StateFlow<List<ChannelEntity>> = playlists
        .flatMapLatest { playlistList ->
            if (playlistList.isEmpty()) flowOf(emptyList())
            else combine(
                playlistList.map { repository.getChannelsByPlaylist(it.id) }
            ) { arrays -> arrays.flatMap { it } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val channelCount: StateFlow<Int> = allChannels
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            // Wait for active profile to be available
            val profileId = profileManager.activeProfile.value?.id
                ?: profileManager.activeProfile.filterNotNull().first().id
            // Migrate existing progress from old entity columns into watch_progress
            migrateExistingProgress(profileId)
            // Then sync with server
            progressSyncManager.pullFromServer(profileId)
            progressSyncManager.pushAllToServer(profileId)
        }
        // Backfill certifications — delayed 2 min so startup I/O settles first on slow devices
        viewModelScope.launch { delay(120_000); runCatching { repository.fetchAllMissingCertifications() } }
    }

    private suspend fun migrateExistingProgress(profileId: String) {
        try {
            // One-time migration: copy lastPlayedPosition from movies into watch_progress
            val movies = try { repository.getAllMoviesWithProgress() } catch (_: Exception) { emptyList() }
            movies.forEach { movie ->
                progressRepository.saveMovieProgress(
                    profileId  = profileId,
                    movieId    = movie.id,
                    positionMs = movie.lastPlayedPosition
                )
            }
            // One-time migration: copy lastPlayedPosition from episodes into watch_progress
            val episodes = try { repository.getAllEpisodesWithProgress() } catch (_: Exception) { emptyList() }
            episodes.forEach { episode ->
                progressRepository.saveEpisodeProgress(
                    profileId  = profileId,
                    episodeId  = episode.id,
                    seriesId   = episode.seriesId,
                    positionMs = episode.lastPlayedPosition
                )
            }
        } catch (_: Exception) {
            // Migration is best-effort — new progress will still save correctly
        }
    }

    // Call when profile switches
    fun onProfileSwitched(profileId: String) {
        viewModelScope.launch {
            progressSyncManager.pullFromServer(profileId)
            progressSyncManager.pushAllToServer(profileId)
        }
    }
}