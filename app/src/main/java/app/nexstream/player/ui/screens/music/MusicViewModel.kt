package app.nexstream.player.ui.screens.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.MusicTrackEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.LyricLine
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.ui.theme.getMusicFavouritesFlow
import app.nexstream.player.ui.theme.saveMusicFavourites
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class AlbumUi(
    val albumId: String?,
    val title: String,
    val artist: String,
    val artUrl: String?,
    val year: Int?,
    val trackCount: Int,
    val playlistId: String,
    val tracks: List<MusicTrackEntity>
) {
    val key: String get() = albumId ?: "$artist|$title"
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val jellyfinIds: Flow<List<String>> = repository.getAllPlaylists()
        .map { playlists -> playlists.filter { it.type == "JELLYFIN" }.map { it.id } }

    val artists: StateFlow<List<String>> = jellyfinIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList()) else repository.getMusicArtists(ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keep genres for backwards compat with MainScreen binding until we update it
    val genres: StateFlow<List<String>> = artists

    private val _selectedArtist = MutableStateFlow<String?>(null)

    private val allTracks: StateFlow<List<MusicTrackEntity>> = jellyfinIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList()) else repository.getMusicTracks(ids, null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumUi>> = combine(allTracks, _selectedArtist) { tracks, artist ->
        tracks
            .filter { artist == null || (it.albumArtist ?: it.artist) == artist }
            .groupBy { it.albumId ?: "${it.albumArtist ?: it.artist}|${it.album}" }
            .mapNotNull { (_, group) ->
                val first = group.first()
                val albumName = first.album ?: return@mapNotNull null
                AlbumUi(
                    albumId   = first.albumId,
                    title     = albumName,
                    artist    = first.albumArtist ?: first.artist ?: "Unknown Artist",
                    artUrl    = first.albumArtUrl,
                    year      = first.year,
                    trackCount = group.size,
                    playlistId = first.playlistId,
                    tracks    = group.sortedWith(compareBy(
                        { it.discNumber ?: 0 },
                        { it.trackNumber ?: 0 },
                        { it.title }
                    ))
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.year ?: 0 }, { it.title.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectArtist(artist: String?) { _selectedArtist.value = artist }

    // Keep selectGenre for backwards compat
    fun selectGenre(genre: String?) = selectArtist(genre)

    // ── Play queue ────────────────────────────────────────────────────────────
    private val _playQueue = MutableStateFlow<List<MusicTrackEntity>>(emptyList())
    val playQueue: StateFlow<List<MusicTrackEntity>> = _playQueue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    val currentTrack: StateFlow<MusicTrackEntity?> = combine(_playQueue, _queueIndex) { q, i ->
        q.getOrNull(i)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun playAlbum(album: AlbumUi, startTrackIndex: Int = 0) {
        _playQueue.value = album.tracks
        _queueIndex.value = startTrackIndex.coerceIn(0, (album.tracks.size - 1).coerceAtLeast(0))
    }

    fun addToQueue(track: MusicTrackEntity) {
        _playQueue.value = _playQueue.value + track
    }

    fun skipToIndex(index: Int) {
        _queueIndex.value = index.coerceIn(0, (_playQueue.value.size - 1).coerceAtLeast(0))
    }

    fun skipToTrack(newTrack: MusicTrackEntity, newIndex: Int) {
        _queueIndex.value = newIndex
    }

    fun clearPlayer() {
        _playQueue.value = emptyList()
        _queueIndex.value = 0
    }

    // ── Album Favourites ──────────────────────────────────────────────────────────
    private val _favouriteAlbumKeys = MutableStateFlow<Set<String>>(emptySet())
    val favouriteAlbumKeys: StateFlow<Set<String>> = _favouriteAlbumKeys.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.getMusicFavouritesFlow().first()?.let { stored ->
                if (stored.isNotBlank()) {
                    _favouriteAlbumKeys.value = stored.split(",").toSet()
                }
            }
            // Load persisted track favourites from the watchlist table
            val profileId = profileManager.activeProfile.filterNotNull().first().id
            repository.getWatchlistItemsForProfile(profileId).first()
                .filter { it.type == WatchlistType.MUSIC }
                .let { items -> _favouriteTrackIds.value = items.map { it.id }.toSet() }
        }
    }

    fun toggleFavouriteAlbum(album: AlbumUi) {
        _favouriteAlbumKeys.update {
            if (album.key in it) it - album.key else it + album.key
        }
        viewModelScope.launch(Dispatchers.IO) {
            context.saveMusicFavourites(_favouriteAlbumKeys.value.joinToString(","))
        }
    }

    fun isFavourite(album: AlbumUi) = album.key in _favouriteAlbumKeys.value

    // ── Track Favourites ──────────────────────────────────────────────────────────
    private val _favouriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteTrackIds: StateFlow<Set<String>> = _favouriteTrackIds.asStateFlow()

    fun toggleFavouriteTrack(track: MusicTrackEntity) {
        val newFavs = if (track.id in _favouriteTrackIds.value)
            _favouriteTrackIds.value - track.id
        else
            _favouriteTrackIds.value + track.id
        _favouriteTrackIds.value = newFavs
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = profileManager.activeProfile.value?.id ?: "default"
            if (track.id in newFavs) {
                repository.addToWatchlist(WatchlistEntity(
                    id        = track.id,
                    profileId = profileId,
                    type      = WatchlistType.MUSIC,
                    name      = track.title,
                    posterUrl = track.albumArtUrl,
                    streamUrl = track.streamUrl
                ))
            } else {
                repository.removeFromWatchlist(track.id, profileId)
            }
        }
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────
    private val _lyrics = MutableStateFlow<List<LyricLine>?>(null)
    val lyrics: StateFlow<List<LyricLine>?> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    fun loadLyrics(playlistId: String, jellyfinItemId: String) {
        if (jellyfinItemId.isBlank()) return
        _lyrics.value = null
        _lyricsLoading.value = true
        viewModelScope.launch {
            _lyrics.value = repository.fetchJellyfinLyrics(playlistId, jellyfinItemId)
            _lyricsLoading.value = false
        }
    }

    fun clearLyrics() { _lyrics.value = null }

    // ── Jellyfin token cache (primed on init for artwork URLs) ───────────────────
    private val _jellyfinTokens = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val jellyfinTokens: StateFlow<Map<String, Pair<String, String>>> = _jellyfinTokens.asStateFlow()

    private val tokenCache = mutableMapOf<String, Pair<String, String>>() // playlistId -> (host, token)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val jellyfinPlaylists = repository.getAllPlaylists().first()
                .filter { it.type == "JELLYFIN" }
            jellyfinPlaylists.forEach { playlist ->
                val pair = repository.getJellyfinTokenForPlaylist(playlist.id)
                if (pair != null) {
                    tokenCache[playlist.id] = pair
                    _jellyfinTokens.update { it + (playlist.id to pair) }
                }
            }
        }
    }

    fun getArtUrl(playlistId: String, albumId: String?, fallback: String? = null): String? {
        if (albumId.isNullOrBlank()) return fallback
        val (host, token) = _jellyfinTokens.value[playlistId] ?: return fallback
        return "${host}/Items/${albumId}/Images/Primary?api_key=${token}"
    }

    fun getBackdropUrl(playlistId: String, albumId: String?): String? {
        if (albumId.isNullOrBlank()) return null
        val (host, token) = _jellyfinTokens.value[playlistId] ?: return null
        return "${host}/Items/${albumId}/Images/Backdrop/0?api_key=${token}"
    }

    // ── Fresh stream URL (re-auth on first use, cache token per playlist) ─────
    suspend fun getFreshStreamUrl(track: MusicTrackEntity): String = withContext(Dispatchers.IO) {
        if (track.jellyfinItemId.isBlank()) return@withContext track.streamUrl
        val cached = tokenCache[track.playlistId]
        if (cached != null) {
            return@withContext "${cached.first}/Audio/${track.jellyfinItemId}/stream?static=true&api_key=${cached.second}"
        }
        val pair = repository.getJellyfinTokenForPlaylist(track.playlistId)
        return@withContext if (pair != null) {
            tokenCache[track.playlistId] = pair
            "${pair.first}/Audio/${track.jellyfinItemId}/stream?static=true&api_key=${pair.second}"
        } else {
            track.streamUrl
        }
    }

    fun invalidateTokenCache() { tokenCache.clear() }

    // ── Album description ─────────────────────────────────────────────────────
    private val _albumDescription = MutableStateFlow<String?>(null)
    val albumDescription: StateFlow<String?> = _albumDescription.asStateFlow()

    fun loadAlbumDescription(playlistId: String, albumId: String?) {
        if (albumId.isNullOrBlank()) return
        _albumDescription.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _albumDescription.value = repository.fetchJellyfinAlbumDescription(playlistId, albumId)
        }
    }
}
