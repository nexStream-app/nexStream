package app.nexstream.player.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.RtData
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class PersonResult(
    val name: String,
    val imageUrl: String?,
    val tmdbId: Int?
)

data class PersonCredit(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val mediaType: String,
    val localMovie: app.nexstream.player.data.local.entity.MovieEntity? = null,
    val localSeries: app.nexstream.player.data.local.entity.SeriesEntity? = null
)

data class PersonDetail(
    val biography: String?,
    val birthday: String?,
    val birthplace: String?,
    val knownFor: String?,
    val profileUrl: String?,
    val knownForCredits: List<PersonCredit>
)

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val programmes: List<ProgramEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val peopleMovies: List<MovieEntity> = emptyList(),
    val peopleSeries: List<SeriesEntity> = emptyList(),
    val people: List<PersonResult> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    @Inject lateinit var profileManager: ProfileManager

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val httpClient = OkHttpClient()

    // Caches keyed by person name / TMDB person ID
    private val personCache       = ConcurrentHashMap<String, PersonResult>()
    // Full detail result cache: tmdbId → PersonDetail (avoids re-fetching on re-open)
    private val personDetailCache = ConcurrentHashMap<Int, PersonDetail>()

    // Filmography-based movie/series results populated via nexstream.uk backend people lookup
    private val _filmographyMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    private val _filmographySeries = MutableStateFlow<List<SeriesEntity>>(emptyList())
    private var filmographyFetchJob: Job? = null

    init {
        _query
            .debounce(600)
            .map { it.trim() }
            .onEach { q ->
                filmographyFetchJob?.cancel()
                _filmographyMovies.value = emptyList()
                _filmographySeries.value = emptyList()
                if (q.length < 2) return@onEach
                filmographyFetchJob = viewModelScope.launch(Dispatchers.IO) {
                    // Collect all people names matching the query from local DB cast/director fields
                    val peopleMovies = try { repository.searchMoviesByPeople(q).first() } catch (_: Exception) { emptyList() }
                    val peopleSeries = try { repository.searchSeriesByPeople(q).first() } catch (_: Exception) { emptyList() }
                    val qLower = q.lowercase()
                    val allPeopleNames = buildSet<String> {
                        fun addNames(raw: String?) {
                            raw?.split(",")?.forEach { n ->
                                val t = n.trim()
                                if (t.isNotEmpty() && t.lowercase().contains(qLower)) add(t)
                            }
                        }
                        peopleMovies.forEach { m -> addNames(m.cast); addNames(m.director) }
                        peopleSeries.forEach { s -> addNames(s.cast); addNames(s.director) }
                    }
                    // Fall back to the raw query if the local DB has no cast/director data
                    val namesToSearch = allPeopleNames.take(5).ifEmpty { setOf(q) }

                    val seenMovieIds  = mutableSetOf<String>()
                    val seenSeriesIds = mutableSetOf<String>()
                    val allMovies     = mutableListOf<MovieEntity>()
                    val allSeries     = mutableListOf<SeriesEntity>()

                    for (personName in namesToSearch) {
                        val person = fetchPersonResult(personName)
                        if (person.tmdbId == null) continue
                        val detail = fetchPersonDetail(person.tmdbId) ?: continue
                        for (credit in detail.knownForCredits) {
                            credit.localMovie?.let  { if (seenMovieIds.add(it.id))  allMovies.add(it) }
                            credit.localSeries?.let { if (seenSeriesIds.add(it.id)) allSeries.add(it) }
                        }
                    }
                    _filmographyMovies.value = allMovies
                    _filmographySeries.value = allSeries
                }
            }
            .launchIn(viewModelScope)
    }

    private val _selectedPerson       = MutableStateFlow<PersonResult?>(null)
    val selectedPerson: StateFlow<PersonResult?> = _selectedPerson.asStateFlow()

    private val _personDetail         = MutableStateFlow<PersonDetail?>(null)
    val personDetail: StateFlow<PersonDetail?> = _personDetail.asStateFlow()

    private val _personDetailLoading  = MutableStateFlow(false)
    val personDetailLoading: StateFlow<Boolean> = _personDetailLoading.asStateFlow()

    fun selectPerson(person: PersonResult?) {
        _selectedPerson.value = person
        _personDetail.value   = null
        if (person != null) {
            viewModelScope.launch {
                _personDetailLoading.value = true
                // Resolve TMDB ID on demand — search flow no longer pre-fetches it
                val resolved = if (person.tmdbId != null) {
                    person
                } else {
                    withContext(Dispatchers.IO) { fetchPersonResult(person.name) }.also { fetched ->
                        if (fetched.tmdbId != null) _selectedPerson.value = fetched
                    }
                }
                if (resolved.tmdbId != null) {
                    _personDetail.value = fetchPersonDetail(resolved.tmdbId)
                }
                _personDetailLoading.value = false
            }
        }
    }

    // ── TMDB fetch helpers ────────────────────────────────────────────────────

    private fun fetchPersonResult(name: String): PersonResult {
        personCache[name]?.let { return it }
        return try {
            val enc  = java.net.URLEncoder.encode(name, "UTF-8")
            val body = httpClient.newCall(
                Request.Builder()
                    .url("https://nexstream.uk/api/people.php?name=$enc")
                    .build()
            ).execute().body?.string() ?: return PersonResult(name, null, null)
            val json = org.json.JSONObject(body)
            if (json.has("error")) return PersonResult(name, null, null)
            val person = json.optJSONObject("person") ?: return PersonResult(name, null, null)
            val tmdbId     = person.optInt("tmdb_person_id").takeIf { it != 0 }
            val profileUrl = person.optString("profile_url").takeIf { it.isNotEmpty() }
            PersonResult(name, profileUrl, tmdbId)
                .also { personCache[name] = it }
        } catch (_: Exception) { PersonResult(name, null, null) }
    }

    private suspend fun fetchPersonDetail(tmdbId: Int): PersonDetail? {
        personDetailCache[tmdbId]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val body = httpClient.newCall(
                    Request.Builder()
                        .url("https://nexstream.uk/api/people.php?person_id=$tmdbId")
                        .build()
                ).execute().body?.string() ?: return@withContext null
                val json = org.json.JSONObject(body)
                if (json.has("error")) return@withContext null
                val personObj      = json.optJSONObject("person")        ?: return@withContext null
                val filmographyArr = json.optJSONArray("filmography")

                val profileUrl = personObj.optString("profile_url").takeIf { it.isNotEmpty() }
                    ?.replace("w185", "w300")

                val movieCredits  = mutableListOf<PersonCredit>()
                val seriesCredits = mutableListOf<PersonCredit>()
                val seenLocalIds  = mutableSetOf<String>()
                if (filmographyArr != null) {
                    for (i in 0 until filmographyArr.length()) {
                        if (movieCredits.size >= 8 && seriesCredits.size >= 8) break
                        val credit    = filmographyArr.getJSONObject(i)
                        val title     = credit.optString("title").takeIf { it.isNotEmpty() } ?: continue
                        val mediaType = credit.optString("media_type")
                        val posterUrl = credit.optString("poster_url").takeIf { it.isNotEmpty() }
                        val creditId  = credit.optInt("tmdb_id")
                        if (mediaType == "movie" && movieCredits.size < 8) {
                            val local = repository.findMoviesByTitle(title).firstOrNull()
                            if (local != null && seenLocalIds.add("m_${local.id}")) {
                                movieCredits += PersonCredit(
                                    id         = creditId,
                                    title      = local.name,
                                    posterUrl  = local.posterUrl ?: posterUrl,
                                    mediaType  = "movie",
                                    localMovie = local
                                )
                            }
                        } else if (mediaType != "movie" && seriesCredits.size < 8) {
                            val local = repository.findSeriesByTitle(title).firstOrNull()
                            if (local != null && seenLocalIds.add("s_${local.id}")) {
                                seriesCredits += PersonCredit(
                                    id          = creditId,
                                    title       = local.name,
                                    posterUrl   = local.posterUrl ?: posterUrl,
                                    mediaType   = "tv",
                                    localSeries = local
                                )
                            }
                        }
                    }
                }
                val knownForCredits = movieCredits + seriesCredits

                PersonDetail(
                    biography       = personObj.optString("biography").takeIf { it.isNotEmpty() },
                    birthday        = personObj.optString("birthday").takeIf { it.isNotEmpty() },
                    birthplace      = null,
                    knownFor        = personObj.optString("known_for_department").takeIf { it.isNotEmpty() },
                    profileUrl      = profileUrl,
                    knownForCredits = knownForCredits
                        .sortedByDescending { it.localMovie?.rating?.toFloatOrNull() ?: it.localSeries?.rating?.toFloatOrNull() ?: 0f }
                ).also { personDetailCache[tmdbId] = it }
            } catch (_: Exception) { null }
        }
    }

    // ── Search flow ───────────────────────────────────────────────────────────

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults> = _query
        .debounce(300)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flow {
                    emit(SearchResults(isLoading = true))
                    emitAll(
                        combine(
                            combine(repository.searchChannels(q), repository.searchPrograms(q)) { c, p -> Pair(c, p) },
                            combine(repository.searchMovies(q), repository.searchSeries(q)) { m, s -> Pair(m, s) },
                            combine(repository.searchMoviesByPeople(q), _filmographyMovies) { local, filmog ->
                                (local + filmog).distinctBy { it.id }
                            },
                            combine(repository.searchSeriesByPeople(q), _filmographySeries) { local, filmog ->
                                (local + filmog).distinctBy { it.id }
                            }
                        ) { cp, ms, mergedPeopleMovies, mergedPeopleSeries ->
                            val profile      = profileManager.activeProfile.value
                            val maxAgeRating = profile?.maxAgeRating
                            val allowNr      = profile?.allowNr ?: true
                            val blockedTv    = profileManager.blockedTvCategories.value

                            val qLower = q.lowercase()
                            val extractedPeople = buildSet {
                                fun addNames(raw: String?) {
                                    raw?.split(",")?.forEach { name ->
                                        val trimmed = name.trim()
                                        if (trimmed.isNotEmpty() && trimmed.lowercase().contains(qLower)) add(trimmed)
                                    }
                                }
                                mergedPeopleMovies.forEach { m -> addNames(m.cast); addNames(m.director) }
                                mergedPeopleSeries.forEach { s -> addNames(s.cast); addNames(s.director) }
                            }.sorted()

                            val filteredChannels = cp.first.filter { ch ->
                                ch.groupTitle !in blockedTv
                            }
                            val filteredMovies = ms.first.filter { m ->
                                isAllowedByAgeRating(m.certification, maxAgeRating, allowNr)
                            }
                            val filteredSeries = ms.second.filter { s ->
                                isAllowedByAgeRating(s.certification, maxAgeRating, allowNr)
                            }

                            SearchResults(
                                channels     = filteredChannels,
                                programmes   = cp.second,
                                movies       = filteredMovies,
                                series       = filteredSeries,
                                peopleMovies = mergedPeopleMovies.filter { m -> isAllowedByAgeRating(m.certification, maxAgeRating, allowNr) },
                                peopleSeries = mergedPeopleSeries.filter { s -> isAllowedByAgeRating(s.certification, maxAgeRating, allowNr) },
                                people       = extractedPeople.map { name ->
                                    personCache[name] ?: PersonResult(name, null, null)
                                },
                                isLoading    = false
                            )
                        }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    private fun isAllowedByAgeRating(certification: String?, maxAgeRating: String?, allowNr: Boolean = true): Boolean {
        if (certification == "NR") return allowNr
        if (maxAgeRating == null) return true
        if (certification == null) return true
        val order = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
        val certOrder = order[certification] ?: return true
        val maxOrder  = order[maxAgeRating]  ?: return true
        return certOrder <= maxOrder
    }

    // ── Lazy person image loading ─────────────────────────────────────────────
    private val _personImageUrls = MutableStateFlow<Map<String, String?>>(emptyMap())
    val personImageUrls: StateFlow<Map<String, String?>> = _personImageUrls.asStateFlow()

    private val fetchingImages = mutableSetOf<String>()

    fun loadPersonImage(name: String) {
        if (name in fetchingImages) return
        personCache[name]?.imageUrl?.let { url ->
            if (url != null) { _personImageUrls.update { it + (name to url) }; return }
        }
        if (personCache[name]?.imageUrl != null) return
        fetchingImages.add(name)
        viewModelScope.launch(Dispatchers.IO) {
            val result = fetchPersonResult(name)
            if (result.imageUrl != null) {
                _personImageUrls.update { it + (name to result.imageUrl) }
            }
            fetchingImages.remove(name)
        }
    }

    fun onQueryChange(query: String) { _query.value = query }
    fun clearQuery()                 { _query.value = "" }

    suspend fun getChannelByEpgId(epgChannelId: String): ChannelEntity? =
        repository.getChannelByEpgId(epgChannelId)

    suspend fun getCurrentProgram(epgChannelId: String): ProgramEntity? =
        repository.getCurrentProgram(epgChannelId).firstOrNull()

    suspend fun getNextProgram(epgChannelId: String): ProgramEntity? =
        repository.getNextProgram(epgChannelId).firstOrNull()

    suspend fun getSeriesById(id: String): app.nexstream.player.data.local.entity.SeriesEntity? =
        repository.getSeriesById(id)

    // Resolves a series ID to one that actually has episodes in the DB.
    // Needed when a series is found by title (e.g. from people dialog) and the returned entity
    // ID doesn't match the series ID under which episodes were stored (multi-playlist edge case).
    suspend fun resolveSeriesId(seriesId: String): String {
        val haEpisodes = try { repository.getEpisodesForSeries(seriesId).first().isNotEmpty() } catch (_: Exception) { false }
        if (haEpisodes) return seriesId
        val series = repository.getSeriesById(seriesId) ?: return seriesId
        val candidates = try { repository.findSeriesByTitle(series.name) } catch (_: Exception) { return seriesId }
        for (s in candidates) {
            if (s.id == seriesId) continue
            val hasEp = try { repository.getEpisodesForSeries(s.id).first().isNotEmpty() } catch (_: Exception) { false }
            if (hasEp) return s.id
        }
        return seriesId
    }

    suspend fun getLocalEpisodes(seriesId: String): List<app.nexstream.player.data.local.entity.EpisodeEntity> =
        try { repository.getEpisodesForSeries(seriesId).first() } catch (_: Exception) { emptyList() }

    suspend fun getLocalSeasons(seriesId: String): List<Int> =
        try { repository.getSeasonsForSeries(seriesId).first() } catch (_: Exception) { emptyList() }

    // ── TMDB meta-fetch helpers ───────────────────────────────────────────────
    suspend fun fetchMovieCertification(movieId: String, movieName: String): String? =
        repository.fetchCertificationForMovieSingle(movieId, movieName)

    suspend fun fetchMovieOriginalLanguage(movieId: String, movieName: String): String? =
        repository.fetchOriginalLanguageForMovieSingle(movieId, movieName)

    suspend fun fetchMovieRtData(movieId: String, movieName: String): RtData? =
        repository.fetchRtDataForMovieSingle(movieId, movieName)

    suspend fun fetchMovieTrailerUrl(movie: MovieEntity): String? {
        if (!movie.trailerUrl.isNullOrBlank()) return movie.trailerUrl
        return repository.fetchTrailerUrlForMovie(movie.name)
    }

    suspend fun fetchSeriesCertification(seriesId: String, seriesName: String): String? =
        repository.fetchCertificationForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesOriginalLanguage(seriesId: String, seriesName: String): String? =
        repository.fetchOriginalLanguageForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesTrailerUrl(seriesName: String): String? =
        repository.fetchTrailerUrlForSeries(seriesName)

    suspend fun loadSeriesDetails(series: SeriesEntity): Pair<SeriesEntity?, List<EpisodeEntity>> =
        repository.getSeriesDetails(series.playlistId, series.seriesId)

    suspend fun loadMovieDetails(movie: MovieEntity): MovieEntity? {
        val vodId = movie.id.removePrefix("${movie.playlistId}-")
        return repository.getMovieDetails(movie.playlistId, vodId)
    }
}
