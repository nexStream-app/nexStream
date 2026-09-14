package app.nexstream.player.ui.screens.recentlywatched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.data.sync.RecentlySyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentlyWatchedViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val progressRepository: WatchProgressRepository,
    val profileManager: ProfileManager,
    private val recentlySyncManager: RecentlySyncManager,
) : ViewModel() {

    val recentlyWatched: StateFlow<List<RecentlyWatchedEntity>> =
        repository.getRecentlyWatched()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val progressItemIds: StateFlow<Set<String>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptySet())
            else progressRepository.getProgressItemIds(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val progressMap: StateFlow<Map<String, Float>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptyMap())
            else progressRepository.getProgressFractions(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            repository.pruneStaleAndBlockedRecentItems()
            val pid = activeProfileId
            recentlySyncManager.syncFromServer(pid)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteRecentlyWatched(id) }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearRecentlyWatched()
            recentlySyncManager.enqueuePushClearAll(null, activeProfileId)
        }
    }

    fun clearAllForType(selectedType: String?) {
        viewModelScope.launch {
            when (selectedType) {
                "Live TV"  -> {
                    repository.clearRecentlyWatchedByType(RecentlyWatchedType.CHANNEL)
                    recentlySyncManager.enqueuePushClearAll(RecentlyWatchedType.CHANNEL, activeProfileId)
                }
                "Movies"   -> {
                    repository.clearRecentlyWatchedByType(RecentlyWatchedType.MOVIE)
                    recentlySyncManager.enqueuePushClearAll(RecentlyWatchedType.MOVIE, activeProfileId)
                }
                "Episodes" -> {
                    repository.clearRecentlyWatchedByType(RecentlyWatchedType.EPISODE)
                    recentlySyncManager.enqueuePushClearAll(RecentlyWatchedType.EPISODE, activeProfileId)
                }
                else       -> {
                    repository.clearRecentlyWatched()
                    recentlySyncManager.enqueuePushClearAll(null, activeProfileId)
                }
            }
        }
    }

    suspend fun getMovieById(id: String): MovieEntity? = repository.getMovieById(id)

    suspend fun getSeriesById(id: String): SeriesEntity? = repository.getSeriesById(id)

    suspend fun getChannelById(id: String): app.nexstream.player.data.local.entity.ChannelEntity? = repository.getChannelById(id)

    suspend fun getCurrentProgram(epgId: String): app.nexstream.player.data.local.entity.ProgramEntity? =
        try { repository.getCurrentProgram(epgId).first() } catch (_: Exception) { null }

    suspend fun getNextProgram(epgId: String): app.nexstream.player.data.local.entity.ProgramEntity? =
        try { repository.getNextProgram(epgId).first() } catch (_: Exception) { null }

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

    suspend fun fetchSeriesRtData(seriesId: String, seriesName: String) =
        repository.fetchRtDataForSeriesSingle(seriesId, seriesName)

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

    private val activeProfileId: String
        get() = profileManager.activeProfile.value?.id ?:
        profileManager.profiles.value.firstOrNull { it.isDefault }?.id ?:
        "default"

    suspend fun getEpisodeProgressMap(seriesId: String): Map<String, Long> =
        try { progressRepository.getEpisodeProgressForSeries(activeProfileId, seriesId).first() } catch (_: Exception) { emptyMap() }

    suspend fun getMoviePosition(movieId: String): Long =
        try { progressRepository.getMoviePosition(activeProfileId, movieId) } catch (_: Exception) { 0L }
}