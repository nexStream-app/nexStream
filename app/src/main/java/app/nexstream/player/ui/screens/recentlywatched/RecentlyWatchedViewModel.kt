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

    init {
        viewModelScope.launch { repository.pruneStaleAndBlockedRecentItems() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteRecentlyWatched(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearRecentlyWatched() }
    }

    fun clearAllForType(selectedType: String?) {
        viewModelScope.launch {
            when (selectedType) {
                "Live TV"  -> repository.clearRecentlyWatchedByType(RecentlyWatchedType.CHANNEL)
                "Movies"   -> repository.clearRecentlyWatchedByType(RecentlyWatchedType.MOVIE)
                "Episodes" -> repository.clearRecentlyWatchedByType(RecentlyWatchedType.EPISODE)
                else       -> repository.clearRecentlyWatched()
            }
        }
    }

    suspend fun getMovieById(id: String): MovieEntity? = repository.getMovieById(id)

    suspend fun getSeriesById(id: String): SeriesEntity? = repository.getSeriesById(id)

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
}