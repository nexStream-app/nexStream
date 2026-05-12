package app.nexstream.player.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    val repository: PlaylistRepository,
    val progressRepository: WatchProgressRepository
) : ViewModel() {

    @Inject lateinit var profileManager: ProfileManager

    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Uses SeriesGridItem (slim projection) — only id/name/posterUrl/categoryName/playlistId/seasonCount
    // Much faster than loading full SeriesEntity with plot/cast/director/rating for every row
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allSeries: StateFlow<List<SeriesGridItem>> = playlists
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { repository.getSeriesGridItems(it.id) }) { arrays ->
                arrays.flatMap { it }
            }.debounce(300) // Prevent recomposition on every batch insert during fetch
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedEpisodeCounts: StateFlow<Map<String, Int>> = repository.getAllWatchedEpisodeCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategories(): Flow<List<String>> = profileManager.activeProfile.flatMapLatest { profile ->
        if (profile == null) repository.getSeriesCategories()
        else combine(
            repository.getSeriesCategories(),
            repository.getBlockedCategoriesFlow(profile.id, "SERIES")
        ) { cats, blocked -> cats.filter { it !in blocked } }
    }

    fun getSeriesByCategory(category: String?): Flow<List<SeriesGridItem>> =
        if (category == null) allSeries
        else playlists.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { repository.getSeriesGridItemsByCategory(it.id, category) }) { arrays ->
                arrays.flatMap { it }
            }.debounce(300)
        }

    fun getProgressItemIds(profileId: String) = progressRepository.getProgressItemIds(profileId)
    fun getWatchedCountsForProfile(profileId: String) = progressRepository.getWatchedCountsForProfile(profileId)

    fun getFilteredCategories(allCategories: List<String>, type: String): List<String> {
        val activeProfile = profileManager.activeProfile.value ?: return allCategories
        val blocked = profileManager.getBlockedCategoriesCached(activeProfile.id, type)
        return allCategories.filter { it !in blocked }
    }

    suspend fun loadSeriesDetails(playlistId: String, seriesId: String): Pair<SeriesEntity?, List<EpisodeEntity>> =
        repository.getSeriesDetails(playlistId, seriesId)

    suspend fun saveEpisodePosition(episodeId: String, position: Long) =
        repository.saveEpisodePlaybackPosition(episodeId, position)

    suspend fun clearEpisodePosition(episodeId: String) =
        repository.clearEpisodePlaybackPosition(episodeId)

    fun getWatchedEpisodeCount(seriesId: String): Flow<Int> =
        repository.getWatchedEpisodeCount(seriesId)

    suspend fun getEpisodesFromDb(seriesId: String): List<EpisodeEntity> =
        repository.getEpisodesForSeries(seriesId).first()
}