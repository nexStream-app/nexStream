package app.nexstream.player.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val programmes: List<ProgramEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults> = _query
        .debounce(300)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                combine(
                    repository.searchChannels(q),
                    repository.searchPrograms(q),
                    repository.searchMovies(q),
                    repository.searchSeries(q)
                ) { channels, programmes, movies, series ->
                    SearchResults(
                        channels = channels,
                        programmes = programmes,
                        movies = movies,
                        series = series,
                        isLoading = false
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun clearQuery() {
        _query.value = ""
    }
}
