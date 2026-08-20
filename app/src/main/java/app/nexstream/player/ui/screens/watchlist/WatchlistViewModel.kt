package app.nexstream.player.ui.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.data.sync.WatchlistSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val syncManager: WatchlistSyncManager,
    private val progressRepository: WatchProgressRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val progressMap: StateFlow<Map<String, Float>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptyMap())
            else progressRepository.getProgressFractions(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            profileManager.activeProfile
                .filterNotNull()
                .first()
                .let { profile ->
                    syncManager.syncFromServer(profile.id)
                    repository.pruneStaleAndBlockedWatchlistItems()
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
            syncManager.enqueuePushAdd(itemWithProfile)
        }
    }

    fun removeFromWatchlist(id: String, type: WatchlistType) {
        viewModelScope.launch {
            repository.removeFromWatchlist(id, activeProfileId)
            syncManager.enqueuePushRemove(id, type, activeProfileId)
        }
    }

    fun toggleWatchlist(item: WatchlistEntity, currentlyInList: Boolean) {
        if (currentlyInList) removeFromWatchlist(item.id, item.type)
        else addToWatchlist(item)
    }

    suspend fun getMovieById(id: String): MovieEntity? = repository.getMovieById(id)

    suspend fun getSeriesById(id: String): SeriesEntity? = repository.getSeriesById(id)

    suspend fun getChannelById(id: String): app.nexstream.player.data.local.entity.ChannelEntity? = repository.getChannelById(id)

    suspend fun getLocalEpisodes(seriesId: String): List<EpisodeEntity> =
        try { repository.getEpisodesForSeries(seriesId).first() } catch (_: Exception) { emptyList() }

    suspend fun getLocalSeasons(seriesId: String): List<Int> =
        try { repository.getSeasonsForSeries(seriesId).first() } catch (_: Exception) { emptyList() }

    suspend fun loadMovieDetails(movie: MovieEntity): MovieEntity? {
        val vodId = movie.id.removePrefix("${movie.playlistId}-")
        return repository.getMovieDetails(movie.playlistId, vodId)
    }

    suspend fun loadSeriesDetails(series: SeriesEntity) =
        repository.getSeriesDetails(series.playlistId, series.seriesId)

    suspend fun fetchMovieCertification(movieId: String, movieName: String) =
        repository.fetchCertificationForMovieSingle(movieId, movieName)

    suspend fun fetchMovieOriginalLanguage(movieId: String, movieName: String) =
        repository.fetchOriginalLanguageForMovieSingle(movieId, movieName)

    suspend fun fetchMovieRtData(movieId: String, movieName: String) =
        repository.fetchRtDataForMovieSingle(movieId, movieName)

    suspend fun fetchMovieTrailerUrl(movie: MovieEntity): String? {
        if (!movie.trailerUrl.isNullOrBlank()) return movie.trailerUrl
        return repository.fetchTrailerUrlForMovie(movie.name)
    }

    suspend fun fetchSeriesCertification(seriesId: String, seriesName: String) =
        repository.fetchCertificationForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesOriginalLanguage(seriesId: String, seriesName: String) =
        repository.fetchOriginalLanguageForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesTrailerUrl(seriesName: String) =
        repository.fetchTrailerUrlForSeries(seriesName)

    fun clearAllForType(selectedType: String?) {
        viewModelScope.launch {
            when (selectedType) {
                "Live TV" -> repository.clearWatchlistByType(WatchlistType.CHANNEL)
                "Movies"  -> repository.clearWatchlistByType(WatchlistType.MOVIE)
                "Series"  -> repository.clearWatchlistByType(WatchlistType.SERIES)
                "Music"   -> repository.clearWatchlistByType(WatchlistType.MUSIC)
                else      -> repository.clearWatchlistAll()
            }
        }
    }
}