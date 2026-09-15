package app.nexstream.player.data.repository

import app.nexstream.player.data.local.NexStreamDatabase
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.remote.M3UParser
import app.nexstream.player.data.remote.RtData
import app.nexstream.player.data.remote.XtreamApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.nexstream.player.data.local.entity.MovieEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.EpisodeEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.nexstream.player.data.local.dao.CategoryChannelCount
import app.nexstream.player.data.local.dao.RecentlyWatchedDao
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.JellyfinApiService
import app.nexstream.player.data.remote.JellyfinAuthBody
import app.nexstream.player.data.remote.XtreamAccountInfo
import app.nexstream.player.data.sync.RecentlySyncManager
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.license.PlaylistCrypto
import app.nexstream.player.data.remote.XtreamSeries
import app.nexstream.player.data.remote.XtreamStream
import app.nexstream.player.data.remote.XtreamVodStream
import com.google.gson.stream.JsonReader
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PlaylistRepository @Inject constructor(
    private val database: NexStreamDatabase,
    private val profileManager: ProfileManager,
    @ApplicationContext private val appContext: Context,
    private val licencePreferences: LicencePreferences,
    private val recentlySyncManager: RecentlySyncManager,
) {
    private val activeProfileId: String
        get() = profileManager.activeProfile.value?.id ?: "default"
    private val _isLoadingEPG = MutableStateFlow(false)
    val isLoadingEPG: StateFlow<Boolean> = _isLoadingEPG.asStateFlow()

    private val _epgProgramCount = MutableStateFlow(0)
    val epgProgramCount: StateFlow<Int> = _epgProgramCount.asStateFlow()

    private val _epgFetchError = MutableStateFlow<String?>(null)
    val epgFetchError: StateFlow<String?> = _epgFetchError.asStateFlow()

    private val _isLoadingVOD = MutableStateFlow(false)
    val isLoadingVOD: StateFlow<Boolean> = _isLoadingVOD.asStateFlow()

    private val _isLoadingSeries = MutableStateFlow(false)
    val isLoadingSeries: StateFlow<Boolean> = _isLoadingSeries.asStateFlow()

    // ── VOD progress counters ─────────────────────────────────────────────
    private val _vodLoadedCount = MutableStateFlow(0)
    val vodLoadedCount: StateFlow<Int> = _vodLoadedCount.asStateFlow()

    private val _vodTotalCount = MutableStateFlow(0)
    val vodTotalCount: StateFlow<Int> = _vodTotalCount.asStateFlow()

    // ── Channel / Series counters & sync-complete flag ────────────────────
    private val _channelImportedCount = MutableStateFlow(0)
    val channelImportedCount: StateFlow<Int> = _channelImportedCount.asStateFlow()

    private val _seriesLoadedCount = MutableStateFlow(0)
    val seriesLoadedCount: StateFlow<Int> = _seriesLoadedCount.asStateFlow()

    private val _isBackgroundSyncComplete = MutableStateFlow(false)
    val isBackgroundSyncComplete: StateFlow<Boolean> = _isBackgroundSyncComplete.asStateFlow()

    private val _isLoadingMusic = MutableStateFlow(false)
    val isLoadingMusic: StateFlow<Boolean> = _isLoadingMusic.asStateFlow()

    private val _musicLoadedCount = MutableStateFlow(0)
    val musicLoadedCount: StateFlow<Int> = _musicLoadedCount.asStateFlow()

    fun resetImportState() {
        _isBackgroundSyncComplete.value = false
        _channelImportedCount.value = 0
        _vodLoadedCount.value = 0
        _vodTotalCount.value = 0
        _seriesLoadedCount.value = 0
        _musicLoadedCount.value = 0
    }

    // ── FCM-delivered playlist assignment ────────────────────────────────────
    data class PlaylistAssignedEvent(
        val type: String,
        val username: String = "",
        val serverUrl: String = "",
        val password: String = "",
        val m3uUrl: String = "",
        val triggeredAt: Long = System.currentTimeMillis()
    )

    private val _pendingPlaylistAssignment = MutableStateFlow<PlaylistAssignedEvent?>(null)
    val pendingPlaylistAssignment: StateFlow<PlaylistAssignedEvent?> = _pendingPlaylistAssignment.asStateFlow()

    fun setPendingPlaylistAssignment(event: PlaylistAssignedEvent) {
        _pendingPlaylistAssignment.value = event
    }

    fun clearPendingPlaylistAssignment() {
        _pendingPlaylistAssignment.value = null
    }

    suspend fun pollForPendingPlaylist(deviceId: String, since: Long): PlaylistAssignedEvent? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://nexstream.uk/api/poll_playlist.php?device_id=$deviceId&since=$since"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout    = 5000
                if (conn.responseCode != 200) return@withContext null
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                if (!json.optBoolean("found")) return@withContext null
                val encPassword = json.optString("password")
                val licenceKey  = licencePreferences.getLicenceKey()
                val password    = if (licenceKey != null && encPassword.isNotEmpty())
                    PlaylistCrypto.decryptPassword(encPassword, licenceKey)
                else encPassword
                PlaylistAssignedEvent(
                    type      = json.optString("type", "xtream"),
                    username  = json.optString("username"),
                    serverUrl = json.optString("server_url"),
                    password  = password,
                    m3uUrl    = json.optString("m3u_url")
                )
            } catch (e: Exception) {
                null
            }
        }

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
        return database.playlistDao().getAllPlaylists()
    }

    fun getEnabledPlaylists(): Flow<List<PlaylistEntity>> {
        return database.playlistDao().getEnabledPlaylists()
    }

    suspend fun setPlaylistEnabled(playlistId: String, enabled: Boolean) {
        database.playlistDao().setEnabled(playlistId, enabled)
    }

    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>> {
        return database.channelDao().getChannelsByPlaylist(playlistId)
    }

    fun searchChannels(query: String): Flow<List<ChannelEntity>> {
        return database.channelDao().searchChannels(query)
    }

    fun getMoviesByPlaylist(playlistId: String): Flow<List<MovieEntity>> {
        return database.movieDao().getMoviesByPlaylist(playlistId)
    }

    suspend fun addM3UPlaylist(name: String, url: String): Result<String> {
        return try {
            val playlistId = UUID.randomUUID().toString()
            android.util.Log.d("PlaylistRepository", "Downloading M3U from: $url")
            val content = withContext(Dispatchers.IO) { URL(url).readText() }
            android.util.Log.d("PlaylistRepository", "Downloaded ${content.length} bytes")
            val channels = M3UParser.parse(content, playlistId)
            android.util.Log.d("PlaylistRepository", "Parsed ${channels.size} channels")
            val playlist = PlaylistEntity(id = playlistId, name = name, type = "M3U", url = url)
            database.playlistDao().insert(playlist)
            database.channelDao().insertAll(channels)
            android.util.Log.d("PlaylistRepository", "Successfully added playlist")
            _channelImportedCount.value = channels.size
            _isBackgroundSyncComplete.value = true
            Result.success(playlistId)
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Failed to add M3U playlist", e)
            Result.failure(e)
        }
    }

    private suspend fun streamVodItems(
        host: String,
        username: String,
        password: String,
        onBatch: suspend (List<XtreamVodStream>) -> Unit
    ) {
        val retrofit = buildRetrofit(host)
        val api = retrofit.create(XtreamApiService::class.java)
        val body = api.getVodStreamsRaw(username, password)
        val gson = Gson()

        withContext(Dispatchers.IO) {
            body.byteStream().bufferedReader().use { reader ->
                val jsonReader = JsonReader(reader)
                val batch = mutableListOf<XtreamVodStream>()

                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val item = gson.fromJson<XtreamVodStream>(jsonReader, XtreamVodStream::class.java)
                    batch.add(item)
                    if (batch.size >= 150) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onBatch(batch.toList())
                jsonReader.endArray()
            }
        }
    }

    private suspend fun streamSeriesItems(
        host: String,
        username: String,
        password: String,
        onBatch: suspend (List<XtreamSeries>) -> Unit
    ) {
        val retrofit = buildRetrofit(host)
        val api = retrofit.create(XtreamApiService::class.java)
        val body = api.getAllSeriesRaw(username, password)
        val gson = Gson()

        withContext(Dispatchers.IO) {
            body.byteStream().bufferedReader().use { reader ->
                val jsonReader = JsonReader(reader)
                val batch = mutableListOf<XtreamSeries>()

                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val item = gson.fromJson<XtreamSeries>(jsonReader, XtreamSeries::class.java)
                    batch.add(item)
                    if (batch.size >= 150) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onBatch(batch.toList())
                jsonReader.endArray()
            }
        }
    }

    private suspend fun streamLiveItems(
        host: String,
        username: String,
        password: String,
        onBatch: suspend (List<XtreamStream>) -> Unit
    ) {
        val retrofit = buildRetrofit(host)
        val api = retrofit.create(XtreamApiService::class.java)
        val body = api.getLiveStreamsRaw(username, password)
        val gson = Gson()

        withContext(Dispatchers.IO) {
            body.byteStream().bufferedReader().use { reader ->
                val jsonReader = JsonReader(reader)
                val batch = mutableListOf<XtreamStream>()

                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val item = gson.fromJson<XtreamStream>(jsonReader, XtreamStream::class.java)
                    batch.add(item)
                    if (batch.size >= 150) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onBatch(batch.toList())
                jsonReader.endArray()
            }
        }
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        database.playlistDao().insert(playlist)
    }

    suspend fun addXtreamPlaylist(
        username: String,
        host: String,
        password: String,
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PlaylistRepository", "Adding Xtream playlist: $host")
                val retrofit = buildRetrofit(host)
                val api = retrofit.create(XtreamApiService::class.java)

                val accountInfo = try {
                    api.getAccountInfo(username, password)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistRepository", "Failed to get account info", e)
                    null
                }

                val expiryDate = accountInfo?.userInfo?.expDate
                android.util.Log.d("PlaylistRepository", "Account expires: $expiryDate")

                val categories = api.getLiveCategories(username, password)
                android.util.Log.d("PlaylistRepository", "Fetched ${categories.size} categories")

                val playlistId = UUID.randomUUID().toString()
                val playlist = PlaylistEntity(
                    id = playlistId,
                    name = username,
                    url = host,
                    type = "XTREAM",
                    xtreamHost = host,
                    xtreamUsername = username,
                    xtreamPassword = password,
                    xtreamExpiry = expiryDate
                )

                database.playlistDao().insert(playlist)

                // Reset progress state for this import run
                _channelImportedCount.value = 0
                _vodLoadedCount.value = 0
                _vodTotalCount.value = 0
                _seriesLoadedCount.value = 0
                _isBackgroundSyncComplete.value = false

                var streamIndex = 0
                var totalChannels = 0
                streamLiveItems(host, username, password) { batch ->
                    val entities = batch.map { stream ->
                        ChannelEntity(
                            id = "$playlistId-${stream.streamId}",
                            name = stream.name.trim(),
                            streamUrl = "$host/live/$username/$password/${stream.streamId}.ts",
                            logoUrl = stream.icon,
                            groupTitle = categories.find { it.id == stream.categoryId }?.name?.trim(),
                            epgChannelId = stream.epgChannelId,
                            playlistId = playlistId,
                            sortIndex = streamIndex,
                            tvArchive = stream.tvArchive,
                            tvArchiveDuration = stream.tvArchiveDuration,
                            streamId = stream.streamId.toString()
                        ).also { streamIndex++ }
                    }
                    database.channelDao().insertAll(entities)
                    totalChannels += entities.size
                }
                android.util.Log.d("PlaylistRepository", "Channels stored — $totalChannels total")
                _channelImportedCount.value = totalChannels

                val firstTen = database.channelDao().getAllChannels().first().take(10)
                firstTen.forEach { channel ->
                    android.util.Log.d("CHANNEL_DEBUG", "Name: [${channel.name}] | EPG ID: [${channel.epgChannelId}]")
                }

                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {

                    // 1. EPG
                    _isLoadingEPG.value = true
                    _epgProgramCount.value = 0
                    _epgFetchError.value = null
                    try {
                        delay(5_000L)
                        fetchAndStoreEPG(host, username, password)
                        android.util.Log.d("PlaylistRepository", "EPG fetch completed")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "EPG fetch failed", e)
                        _epgFetchError.value = e.message ?: "Unknown error"
                    } finally {
                        _isLoadingEPG.value = false
                    }

                    kotlinx.coroutines.delay(5_000)

                    // 2. VOD
                    _isLoadingVOD.value = true
                    try {
                        fetchAndStoreMovies(playlistId, host, username, password)
                        android.util.Log.d("PlaylistRepository", "VOD fetch completed")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "VOD fetch failed", e)
                    } finally {
                        _isLoadingVOD.value = false
                    }

                    kotlinx.coroutines.delay(5_000)

                    // 3. Series
                    _isLoadingSeries.value = true
                    try {
                        fetchAndStoreSeries(playlistId, host, username, password)
                        android.util.Log.d("PlaylistRepository", "Series fetch completed")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "Series fetch failed", e)
                    } finally {
                        _isLoadingSeries.value = false
                    }

                    // All visible import steps done — let the user into the app.
                    // Icons, certifications and credits continue silently in parallel below.
                    _isBackgroundSyncComplete.value = true

                    app.nexstream.player.worker.TmdbMetadataWorker.enqueue(appContext)

                    kotlinx.coroutines.coroutineScope {
                        // 4. Icons — small head-start delay so we don't hammer the network
                        launch {
                            kotlinx.coroutines.delay(5_000)
                            try {
                                preloadChannelIcons(appContext)
                                android.util.Log.d("PlaylistRepository", "Icon preload complete")
                            } catch (e: Exception) {
                                android.util.Log.e("PlaylistRepository", "Icon preload failed", e)
                            }
                        }

                    }

                    android.util.Log.d("PlaylistRepository", "All background fetches complete")
                }

                Result.success(playlistId)
            } catch (e: Exception) {
                android.util.Log.e("PlaylistRepository", "Failed to add Xtream playlist", e)
                Result.failure(e)
            }
        }
    }

    // ── Jellyfin ──────────────────────────────────────────────────────────────
    suspend fun addJellyfinPlaylist(host: String, username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanHost = host.trimEnd('/')
                val retrofit = buildRetrofit("$cleanHost/")
                val api = retrofit.create(JellyfinApiService::class.java)

                val authHeader = "MediaBrowser Client=\"NexStream\", Device=\"Android TV\", DeviceId=\"android\", Version=\"1.0\""
                val authResponse = api.authenticate(authHeader, JellyfinAuthBody(username, password))
                val token = authResponse.accessToken
                val userId = authResponse.user.id

                val playlistId = UUID.randomUUID().toString()
                val playlist = PlaylistEntity(
                    id = playlistId,
                    name = authResponse.user.name.ifBlank { username },
                    url = cleanHost,
                    type = "JELLYFIN",
                    xtreamHost = cleanHost,
                    xtreamUsername = username,
                    xtreamPassword = password,
                )
                database.playlistDao().insert(playlist)

                _channelImportedCount.value = 0
                _vodLoadedCount.value = 0
                _seriesLoadedCount.value = 0
                _musicLoadedCount.value = 0
                _isBackgroundSyncComplete.value = false

                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        _isLoadingVOD.value = true
                        try {
                            var offset = 0
                            var total = 0
                            while (true) {
                                val page = api.getItems(
                                    token = token,
                                    userId = userId,
                                    types = "Movie",
                                    recursive = true,
                                    limit = 200,
                                    startIndex = offset
                                )
                                if (page.items.isEmpty()) break
                                val entities = page.items.map { item ->
                                    val posterUrl = if (item.imageTags?.containsKey("Primary") == true)
                                        "$cleanHost/Items/${item.id}/Images/Primary" else null
                                    val backdropUrl = if (item.backdropImageTags?.isNotEmpty() == true)
                                        "$cleanHost/Items/${item.id}/Images/Backdrop/0" else null
                                    MovieEntity(
                                        id = "$playlistId-${item.id}",
                                        name = item.name,
                                        streamUrl = "$cleanHost/Videos/${item.id}/stream?static=true&api_key=$token",
                                        posterUrl = posterUrl,
                                        backdropUrl = backdropUrl,
                                        plot = item.overview,
                                        cast = null,
                                        director = null,
                                        genre = item.genres?.firstOrNull(),
                                        releaseDate = item.productionYear?.toString(),
                                        rating = item.communityRating?.let { "%.1f".format(it) },
                                        duration = null,
                                        categoryId = null,
                                        categoryName = item.genres?.firstOrNull(),
                                        playlistId = playlistId,
                                        certification = item.officialRating,
                                    )
                                }
                                database.movieDao().insertAll(entities)
                                total += entities.size
                                _vodLoadedCount.value = total
                                if (page.items.size < 200) break
                                offset += 200
                            }
                        } finally {
                            _isLoadingVOD.value = false
                        }

                        _isLoadingSeries.value = true
                        try {
                            var offset = 0
                            var total = 0
                            while (true) {
                                val page = api.getItems(
                                    token = token,
                                    userId = userId,
                                    types = "Series",
                                    recursive = true,
                                    limit = 200,
                                    startIndex = offset
                                )
                                if (page.items.isEmpty()) break
                                val entities = page.items.map { item ->
                                    val posterUrl = if (item.imageTags?.containsKey("Primary") == true)
                                        "$cleanHost/Items/${item.id}/Images/Primary" else null
                                    val backdropUrl = if (item.backdropImageTags?.isNotEmpty() == true)
                                        "$cleanHost/Items/${item.id}/Images/Backdrop/0" else null
                                    SeriesEntity(
                                        id = "$playlistId-${item.id}",
                                        seriesId = item.id,
                                        name = item.name,
                                        posterUrl = posterUrl,
                                        backdropUrl = backdropUrl,
                                        plot = item.overview,
                                        cast = null,
                                        director = null,
                                        genre = item.genres?.firstOrNull(),
                                        releaseDate = item.productionYear?.toString(),
                                        rating = item.communityRating?.let { "%.1f".format(it) },
                                        categoryId = null,
                                        categoryName = item.genres?.firstOrNull(),
                                        playlistId = playlistId,
                                        certification = item.officialRating,
                                    )
                                }
                                database.seriesDao().insertAllSeries(entities)
                                total += entities.size
                                _seriesLoadedCount.value = total
                                if (page.items.size < 200) break
                                offset += 200
                            }
                        } finally {
                            _isLoadingSeries.value = false
                        }
                        _isLoadingMusic.value = true
                        try {
                            var musicOffset = 0
                            var musicTotal  = 0
                            while (true) {
                                val page = api.getItems(
                                    token = token,
                                    userId = userId,
                                    types = "Audio",
                                    recursive = true,
                                    fields = "Genres,Artists,AlbumArtist,Album,IndexNumber,ParentIndexNumber,HasLyrics,ProductionYear,ImageTags",
                                    limit = 200,
                                    startIndex = musicOffset
                                )
                                if (page.items.isEmpty()) break
                                val tracks = page.items.map { item ->
                                    app.nexstream.player.data.local.entity.MusicTrackEntity(
                                        id = "$playlistId-${item.id}",
                                        jellyfinItemId = item.id,
                                        title = item.name,
                                        artist = item.artists?.firstOrNull(),
                                        albumArtist = item.albumArtist,
                                        album = item.album,
                                        albumId = item.albumId,
                                        albumArtUrl = if (item.imageTags?.containsKey("Primary") == true)
                                            "$cleanHost/Items/${item.id}/Images/Primary" else null,
                                        streamUrl = "$cleanHost/Audio/${item.id}/stream?static=true&api_key=$token",
                                        playlistId = playlistId,
                                        genre = item.genres?.firstOrNull(),
                                        durationMs = 0L,
                                        trackNumber = item.indexNumber,
                                        discNumber = item.parentIndexNumber,
                                        year = item.productionYear,
                                        hasLyrics = item.hasLyrics ?: false
                                    )
                                }
                                database.musicDao().insertAll(tracks)
                                musicTotal += tracks.size
                                _musicLoadedCount.value = musicTotal
                                if (page.items.size < 200) break
                                musicOffset += 200
                            }
                        } finally {
                            _isLoadingMusic.value = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "Jellyfin background import failed", e)
                    } finally {
                        _isBackgroundSyncComplete.value = true
                    }
                }

                Result.success(playlistId)
            } catch (e: Exception) {
                android.util.Log.e("PlaylistRepository", "addJellyfinPlaylist failed", e)
                Result.failure(e)
            }
        }
    }

    // ── Plex ──────────────────────────────────────────────────────────────────

    suspend fun requestPlexPin(clientId: String): Result<Pair<Long, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val plexAuth = buildRetrofit("https://plex.tv/").create(app.nexstream.player.data.remote.PlexAuthApiService::class.java)
            val pin = plexAuth.createPin(clientId = clientId)
            Pair(pin.id, pin.code)
        }
    }

    suspend fun checkPlexPin(pinId: Long, clientId: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val plexAuth = buildRetrofit("https://plex.tv/").create(app.nexstream.player.data.remote.PlexAuthApiService::class.java)
            plexAuth.getPin(id = pinId, clientId = clientId).authToken
        }
    }

    suspend fun getPlexServers(token: String): Result<List<app.nexstream.player.data.remote.PlexDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val plexAuth = buildRetrofit("https://plex.tv/").create(app.nexstream.player.data.remote.PlexAuthApiService::class.java)
            plexAuth.getResources(token = token)
        }
    }

    suspend fun addPlexPlaylist(serverName: String, serverUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = serverUrl.trimEnd('/')
            val id = UUID.randomUUID().toString()
            val playlist = PlaylistEntity(
                id = id, name = serverName, url = cleanUrl,
                type = "PLEX", plexToken = token
            )
            database.playlistDao().insert(playlist)
            _channelImportedCount.value = 0
            _vodLoadedCount.value = 0
            _seriesLoadedCount.value = 0
            _musicLoadedCount.value = 0
            _isBackgroundSyncComplete.value = false
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                syncPlexLibrary(playlist)
            }
            id
        }
    }

    suspend fun syncPlexLibrary(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        val token = playlist.plexToken ?: return@withContext
        val baseUrl = playlist.url.trimEnd('/') + "/"
        val plexApi = buildRetrofit(baseUrl).create(app.nexstream.player.data.remote.PlexMediaApiService::class.java)

        try {
            val sections = plexApi.getLibrarySections(token = token)
                .mediaContainer?.directories ?: return@withContext

            for (section in sections) {
                when (section.type) {
                    "movie"  -> importPlexMovies(plexApi, playlist.id, section.key, token, baseUrl)
                    "show"   -> importPlexSeries(plexApi, playlist.id, section.key, token, baseUrl)
                    "artist" -> importPlexMusic(plexApi, playlist.id, section.key, token, baseUrl)
                }
            }
            _isBackgroundSyncComplete.value = true
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Plex sync failed", e)
            _isBackgroundSyncComplete.value = true
        }
    }

    private suspend fun importPlexMovies(
        api: app.nexstream.player.data.remote.PlexMediaApiService,
        playlistId: String,
        sectionKey: String,
        token: String,
        baseUrl: String
    ) {
        val base = baseUrl.trimEnd('/')
        var start = 0
        while (true) {
            val items = api.getSectionItems(sectionKey, token, start = start, size = 500)
                .mediaContainer?.metadata ?: break
            if (items.isEmpty()) break
            val movies = items.map { m ->
                val streamPath = m.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: ""
                val streamUrl = if (streamPath.isNotEmpty()) "$base$streamPath?X-Plex-Token=$token" else ""
                val posterUrl = if (m.thumb != null) "$base${m.thumb}?X-Plex-Token=$token" else null
                MovieEntity(
                    id           = "plex_${playlistId}_${m.ratingKey}",
                    playlistId   = playlistId,
                    name         = m.title,
                    streamUrl    = streamUrl,
                    posterUrl    = posterUrl,
                    backdropUrl  = null,
                    plot         = m.summary,
                    cast         = null,
                    director     = null,
                    genre        = null,
                    releaseDate  = m.year?.toString(),
                    rating       = null,
                    duration     = null,
                    categoryId   = null,
                    categoryName = "Movies",
                )
            }
            database.movieDao().insertAll(movies)
            _vodLoadedCount.value += movies.size
            if (items.size < 500) break
            start += 500
        }
    }

    private suspend fun importPlexSeries(
        api: app.nexstream.player.data.remote.PlexMediaApiService,
        playlistId: String,
        sectionKey: String,
        token: String,
        baseUrl: String
    ) {
        val base = baseUrl.trimEnd('/')
        var start = 0
        while (true) {
            val shows = api.getSectionItems(sectionKey, token, start = start, size = 500)
                .mediaContainer?.metadata ?: break
            if (shows.isEmpty()) break
            shows.forEach { show ->
                val seriesId = "plex_${playlistId}_${show.ratingKey}"
                val posterUrl = if (show.thumb != null) "$base${show.thumb}?X-Plex-Token=$token" else null
                database.seriesDao().insertAllSeries(listOf(
                    SeriesEntity(
                        id           = seriesId,
                        seriesId     = show.ratingKey,
                        name         = show.title,
                        posterUrl    = posterUrl,
                        backdropUrl  = null,
                        plot         = show.summary,
                        cast         = null,
                        director     = null,
                        genre        = null,
                        releaseDate  = show.year?.toString(),
                        rating       = null,
                        categoryId   = null,
                        categoryName = "Series",
                        playlistId   = playlistId,
                    )
                ))
                _seriesLoadedCount.value++

                // Fetch seasons
                val seasons = api.getChildren(show.ratingKey, token)
                    .mediaContainer?.metadata ?: return@forEach
                seasons.forEach { season ->
                    // Fetch episodes
                    val episodes = api.getChildren(season.ratingKey, token)
                        .mediaContainer?.metadata ?: return@forEach
                    val episodeEntities = episodes.mapIndexed { idx, ep ->
                        val streamPath = ep.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: ""
                        val streamUrl  = if (streamPath.isNotEmpty()) "$base$streamPath?X-Plex-Token=$token" else ""
                        EpisodeEntity(
                            id                 = "plex_${playlistId}_${ep.ratingKey}",
                            episodeId          = ep.ratingKey,
                            seriesId           = seriesId,
                            name               = ep.title,
                            seasonNum          = season.index ?: 1,
                            episodeNum         = ep.index ?: (idx + 1),
                            streamUrl          = streamUrl,
                            posterUrl          = null,
                            plot               = ep.summary,
                            duration           = null,
                            containerExtension = "",
                            playlistId         = playlistId,
                        )
                    }
                    if (episodeEntities.isNotEmpty())
                        database.seriesDao().insertAllEpisodes(episodeEntities)
                }
            }
            if (shows.size < 500) break
            start += 500
        }
    }

    private suspend fun importPlexMusic(
        api: app.nexstream.player.data.remote.PlexMediaApiService,
        playlistId: String,
        sectionKey: String,
        token: String,
        baseUrl: String
    ) {
        val base = baseUrl.trimEnd('/')
        var start = 0
        while (true) {
            val artists = api.getSectionItems(sectionKey, token, start = start, size = 200)
                .mediaContainer?.metadata ?: break
            if (artists.isEmpty()) break
            artists.forEach { artist ->
                val albums = api.getChildren(artist.ratingKey, token)
                    .mediaContainer?.metadata ?: return@forEach
                albums.forEach { album ->
                    val albumArtUrl = if (album.thumb != null) "$base${album.thumb}?X-Plex-Token=$token" else null
                    val tracks = api.getChildren(album.ratingKey, token)
                        .mediaContainer?.metadata ?: return@forEach
                    val trackEntities = tracks.map { track ->
                        val streamPath = track.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: ""
                        val streamUrl  = if (streamPath.isNotEmpty()) "$base$streamPath?X-Plex-Token=$token" else ""
                        app.nexstream.player.data.local.entity.MusicTrackEntity(
                            id             = "plex_${playlistId}_${track.ratingKey}",
                            jellyfinItemId = track.ratingKey,
                            title          = track.title,
                            artist         = artist.title,
                            album          = album.title,
                            albumId        = album.ratingKey,
                            albumArtUrl    = albumArtUrl,
                            streamUrl      = streamUrl,
                            playlistId     = playlistId,
                            durationMs     = track.duration ?: 0L,
                            trackNumber    = track.index
                        )
                    }
                    if (trackEntities.isNotEmpty())
                        database.musicDao().insertAll(trackEntities)
                    _musicLoadedCount.value += trackEntities.size
                }
            }
            if (artists.size < 200) break
            start += 200
        }
    }

    // ── VOD fetch ─────────────────────────────────────────────────────────────
    suspend fun fetchAndStoreMovies(
        playlistId: String,
        host: String,
        username: String,
        password: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val retrofit = buildRetrofit(host)
                val api = retrofit.create(XtreamApiService::class.java)

                val vodCategories = try {
                    api.getVodCategories(username, password)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistRepository", "Failed to fetch VOD categories", e)
                    emptyList()
                }

                _vodLoadedCount.value = 0
                // Sentinel keeps vodLoaded < vodTotal true while streaming.
                // Corrected to the real count in the finally block.
                _vodTotalCount.value = Int.MAX_VALUE

                // Snapshot existing certifications and addedAt so INSERT OR REPLACE doesn't wipe them.
                val existingCerts = database.movieDao().getExistingCertifications(playlistId)
                    .associate { it.id to it.certification }
                val existingMovieAddedAt = database.movieDao().getMovieAddedAtForPlaylist(playlistId)
                    .associate { it.id to it.addedAt }

                var batchIndex = 0
                var totalInserted = 0

                val importTime = System.currentTimeMillis()
                streamVodItems(host, username, password) { batch ->
                    val movies = batch.map { stream ->
                        val movieId = "$playlistId-${stream.streamId}"
                        val categoryName = vodCategories.find { it.id == stream.categoryId }?.name?.trim()
                        MovieEntity(
                            id = movieId,
                            name = stream.name,
                            streamUrl = "$host/movie/$username/$password/${stream.streamId}.${stream.containerExtension}",
                            posterUrl = stream.icon,
                            backdropUrl = stream.icon,
                            plot = stream.plot,
                            cast = stream.cast,
                            director = stream.director,
                            genre = stream.genre,
                            releaseDate = stream.releaseDate,
                            rating = stream.rating,
                            duration = stream.duration,
                            categoryId = stream.categoryId,
                            categoryName = categoryName,
                            playlistId = playlistId,
                            isFavourite = false,
                            certification = existingCerts[movieId],
                            addedAt = existingMovieAddedAt[movieId] ?: importTime,
                            originalLanguage = stream.originalLanguage
                        )
                    }

                    try {
                        database.movieDao().insertAll(movies)
                        totalInserted += movies.size
                        _vodLoadedCount.value = totalInserted
                        android.util.Log.d("PlaylistRepository", "VOD batch ${batchIndex + 1} ($totalInserted movies so far)")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "Failed VOD batch ${batchIndex + 1}", e)
                    }

                    batchIndex++
                }

                android.util.Log.d("PlaylistRepository", "VOD fetch complete — $totalInserted movies stored")

            } catch (e: Exception) {
                android.util.Log.e("PlaylistRepository", "fetchAndStoreMovies failed", e)
            } finally {
                // Set total == loaded so the loading condition clears cleanly
                _vodTotalCount.value = _vodLoadedCount.value
            }
        }
    }

    // ── Series fetch ──────────────────────────────────────────────────────────
    suspend fun fetchAndStoreSeries(
        playlistId: String,
        host: String,
        username: String,
        password: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val retrofit = buildRetrofit(host)
                val api = retrofit.create(XtreamApiService::class.java)

                val seriesCategories = try {
                    api.getSeriesCategories(username, password)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistRepository", "Failed to fetch series categories", e)
                    emptyList()
                }

                // Snapshot existing certifications and addedAt so INSERT OR REPLACE doesn't wipe them.
                val existingSeriesCerts = database.seriesDao().getExistingCertifications(playlistId)
                    .associate { it.id to it.certification }
                val existingSeriesAddedAt = database.seriesDao().getSeriesAddedAtForPlaylist(playlistId)
                    .associate { it.id to it.addedAt }

                _seriesLoadedCount.value = 0

                var batchIndex = 0
                var totalInserted = 0

                val seriesImportTime = System.currentTimeMillis()
                streamSeriesItems(host, username, password) { batch ->
                    val seriesEntities = batch.map { series ->
                        val seriesId2 = "$playlistId-${series.seriesId}"
                        val categoryName = seriesCategories.find { it.id == series.categoryId }?.name?.trim()
                        SeriesEntity(
                            id = seriesId2,
                            seriesId = series.seriesId,
                            name = series.name,
                            posterUrl = series.cover,
                            backdropUrl = series.backdropPath?.firstOrNull() ?: series.cover,
                            plot = series.plot,
                            cast = series.cast,
                            director = series.director,
                            genre = series.genre,
                            releaseDate = series.releaseDate,
                            rating = series.rating,
                            categoryId = series.categoryId,
                            categoryName = categoryName,
                            seasonCount = 0,
                            playlistId = playlistId,
                            certification = existingSeriesCerts[seriesId2],
                            originalLanguage = series.originalLanguage,
                            addedAt = existingSeriesAddedAt[seriesId2] ?: seriesImportTime
                        )
                    }

                    try {
                        database.seriesDao().insertAllSeries(seriesEntities)
                        totalInserted += seriesEntities.size
                        _seriesLoadedCount.value = totalInserted
                        android.util.Log.d("PlaylistRepository", "Series batch ${batchIndex + 1} ($totalInserted series so far)")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "Failed series batch ${batchIndex + 1}", e)
                    }

                    batchIndex++
                }

                android.util.Log.d("PlaylistRepository", "Series fetch complete — $totalInserted series stored")

            } catch (e: Exception) {
                android.util.Log.e("PlaylistRepository", "fetchAndStoreSeries failed", e)
            }
        }
    }

    suspend fun getSeriesDetails(playlistId: String, seriesId: String): Pair<SeriesEntity?, List<EpisodeEntity>> {
        return withContext(Dispatchers.IO) {
            try {
                val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return@withContext Pair(null, emptyList())

                if (playlist.type == "JELLYFIN") {
                    val cleanHost = (playlist.xtreamHost ?: playlist.url)?.trimEnd('/') ?: return@withContext Pair(null, emptyList())
                    val jellyfinApi = buildRetrofit("$cleanHost/").create(JellyfinApiService::class.java)
                    val authHeader = "MediaBrowser Client=\"NexStream\", Device=\"Android TV\", DeviceId=\"android\", Version=\"1.0\""
                    val auth = jellyfinApi.authenticate(authHeader, JellyfinAuthBody(
                        playlist.xtreamUsername ?: return@withContext Pair(null, emptyList()),
                        playlist.xtreamPassword ?: return@withContext Pair(null, emptyList())
                    ))
                    val token = auth.accessToken
                    val userId = auth.user.id

                    val episodes = mutableListOf<EpisodeEntity>()
                    var offset = 0
                    while (true) {
                        val page = jellyfinApi.getItems(
                            token = token,
                            userId = userId,
                            parentId = seriesId,
                            types = "Episode",
                            recursive = true,
                            limit = 200,
                            startIndex = offset,
                            sortBy = "ParentIndexNumber,IndexNumber",
                            sortOrder = "Ascending"
                        )
                        if (page.items.isEmpty()) break
                        page.items.forEach { item ->
                            val posterUrl = if (item.imageTags?.containsKey("Primary") == true)
                                "$cleanHost/Items/${item.id}/Images/Primary" else null
                            episodes.add(EpisodeEntity(
                                id = "$playlistId-${item.id}",
                                episodeId = item.id,
                                seriesId = "$playlistId-$seriesId",
                                name = item.name,
                                seasonNum = item.parentIndexNumber ?: 0,
                                episodeNum = item.indexNumber ?: 0,
                                streamUrl = "$cleanHost/Videos/${item.id}/stream?static=true&api_key=$token",
                                posterUrl = posterUrl,
                                plot = item.overview,
                                duration = null,
                                containerExtension = "",
                                playlistId = playlistId
                            ))
                        }
                        if (page.items.size < 200) break
                        offset += 200
                    }

                    val existingEpisodes = database.seriesDao()
                        .getEpisodesForSeries("$playlistId-$seriesId")
                        .first()
                        .associateBy { it.id }
                    val episodesWithPositions = episodes.map { ep ->
                        val existing = existingEpisodes[ep.id]
                        if (existing != null && existing.lastPlayedPosition > 0)
                            ep.copy(lastPlayedPosition = existing.lastPlayedPosition, lastPlayedTimestamp = existing.lastPlayedTimestamp)
                        else ep
                    }

                    if (episodesWithPositions.isNotEmpty()) {
                        database.seriesDao().insertAllEpisodes(episodesWithPositions)
                    }

                    val existingSeries = database.seriesDao().getSeriesById("$playlistId-$seriesId").first()
                    database.seriesDao().updateHasNewEpisodes("$playlistId-$seriesId", false)
                    val seasonCount = episodes.map { it.seasonNum }.distinct().size
                    val updatedSeries = existingSeries?.copy(seasonCount = seasonCount)
                    if (updatedSeries != null) {
                        database.seriesDao().insertAllSeries(listOf(updatedSeries))
                    }

                    return@withContext Pair(updatedSeries ?: existingSeries, episodesWithPositions)
                }

                val username = playlist.xtreamUsername ?: return@withContext Pair(null, emptyList())
                val password = playlist.xtreamPassword ?: return@withContext Pair(null, emptyList())

                val retrofit = buildRetrofit(playlist.url)
                val api = retrofit.create(XtreamApiService::class.java)

                val seriesInfo = api.getSeriesInfo(username, password, seriesId = seriesId)
                android.util.Log.d("EPISODES_DEBUG", "Series info received for $seriesId")
                android.util.Log.d("EPISODES_DEBUG", "Episodes map keys: ${seriesInfo.episodes?.keys}")
                android.util.Log.d("EPISODES_DEBUG", "Episodes map size: ${seriesInfo.episodes?.size}")
                seriesInfo.episodes?.forEach { (season, eps) ->
                    android.util.Log.d("EPISODES_DEBUG", "Season $season has ${eps.size} episodes")
                    eps.take(2).forEach { ep ->
                        android.util.Log.d("EPISODES_DEBUG", "  Episode: id=${ep.id} num=${ep.episodeNum} title=${ep.title}")
                    }
                }

                val existingSeries = database.seriesDao().getSeriesById("$playlistId-$seriesId").first()

                // Clear the new-episode badge when the user opens the series
                database.seriesDao().updateHasNewEpisodes("$playlistId-$seriesId", false)

                val episodes = mutableListOf<EpisodeEntity>()
                seriesInfo.episodes?.forEach { (seasonKey, episodeList) ->
                    val seasonNum = seasonKey.toIntOrNull() ?: 0
                    episodeList.forEach { ep ->
                        episodes.add(
                            EpisodeEntity(
                                id = "$playlistId-${ep.id}",
                                episodeId = ep.id,
                                seriesId = "$playlistId-$seriesId",
                                name = ep.title.ifBlank { "Episode ${ep.episodeNum}" },
                                seasonNum = seasonNum,
                                episodeNum = ep.episodeNum,
                                streamUrl = "${playlist.url}/series/$username/$password/${ep.id}.${ep.containerExtension}",
                                posterUrl = ep.info?.cover,
                                plot = ep.info?.plot,
                                duration = ep.info?.duration,
                                containerExtension = ep.containerExtension,
                                playlistId = playlistId
                            )
                        )
                    }
                }

                val existingEpisodes = database.seriesDao()
                    .getEpisodesForSeries("$playlistId-$seriesId")
                    .first()
                    .associateBy { it.id }

                val episodesWithPositions = episodes.map { episode ->
                    val existing = existingEpisodes[episode.id]
                    if (existing != null && existing.lastPlayedPosition > 0) {
                        episode.copy(
                            lastPlayedPosition = existing.lastPlayedPosition,
                            lastPlayedTimestamp = existing.lastPlayedTimestamp
                        )
                    } else episode
                }

                if (episodesWithPositions.isNotEmpty()) {
                    database.seriesDao().insertAllEpisodes(episodesWithPositions)
                }

                val seasonCount = seriesInfo.episodes?.keys?.size ?: 0
                val updatedSeries = existingSeries?.copy(
                    seasonCount      = seasonCount,
                    plot             = seriesInfo.info?.plot ?: existingSeries.plot,
                    cast             = seriesInfo.info?.cast ?: existingSeries.cast,
                    director         = seriesInfo.info?.director ?: existingSeries.director,
                    backdropUrl      = seriesInfo.info?.backdropPath?.firstOrNull() ?: existingSeries.backdropUrl,
                    originalLanguage = seriesInfo.info?.originalLanguage ?: existingSeries.originalLanguage
                )

                if (updatedSeries != null) {
                    database.seriesDao().insertAllSeries(listOf(updatedSeries))
                }

                Pair(updatedSeries ?: existingSeries, episodes)
            } catch (e: Exception) {
                android.util.Log.e("SeriesDetails", "Failed to get series details", e)
                android.util.Log.e("EPISODES_DEBUG", "getSeriesDetails FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                Pair(null, emptyList())
            }
        }
    }

    fun getSeriesByPlaylist(playlistId: String): Flow<List<SeriesEntity>> =
        database.seriesDao().getSeriesByPlaylist(playlistId)

    fun getSeriesCategories(): Flow<List<String>> =
        database.seriesDao().getAllCategories()

    fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeEntity>> =
        database.seriesDao().getEpisodesForSeries(seriesId)

    fun getEpisodesForSeason(seriesId: String, season: Int): Flow<List<EpisodeEntity>> =
        database.seriesDao().getEpisodesForSeason(seriesId, season)

    fun getSeasonsForSeries(seriesId: String): Flow<List<Int>> =
        database.seriesDao().getSeasonsForSeries(seriesId)

    suspend fun saveEpisodePlaybackPosition(episodeId: String, position: Long) {
        withContext(Dispatchers.IO) {
            try {
                database.seriesDao().updateEpisodePosition(
                    episodeId = episodeId,
                    position = position,
                    timestamp = System.currentTimeMillis()
                )
                android.util.Log.d("PlaybackPosition", "Saved episode position: $position for id: $episodeId")
            } catch (e: Exception) {
                android.util.Log.e("PlaybackPosition", "Failed to save episode position", e)
            }
        }
    }

    fun getWatchedEpisodeCount(seriesId: String): Flow<Int> =
        database.seriesDao().getWatchedEpisodeCount(seriesId)

    suspend fun clearEpisodePlaybackPosition(episodeId: String) {
        withContext(Dispatchers.IO) {
            try {
                database.seriesDao().clearEpisodePosition(episodeId)
                android.util.Log.d("PlaybackPosition", "Cleared episode position for id: $episodeId")
            } catch (e: Exception) {
                android.util.Log.e("PlaybackPosition", "Failed to clear episode position", e)
            }
        }
    }

    private fun buildRetrofit(host: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(if (host.endsWith("/")) host else "$host/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun deletePlaylist(playlistId: String) {
        database.channelDao().deleteByPlaylist(playlistId)
        database.movieDao().deleteByPlaylist(playlistId)
        database.seriesDao().deleteSeriesByPlaylist(playlistId)
        database.seriesDao().deleteEpisodesByPlaylist(playlistId)
        database.musicDao().deleteByPlaylist(playlistId)
        val playlist = database.playlistDao().getPlaylistById(playlistId)
        playlist?.let { database.playlistDao().delete(it) }
    }

    suspend fun updatePlaylistDetails(playlist: PlaylistEntity) {
        database.playlistDao().update(playlist)
    }

    suspend fun updatePlaylistSortIndex(playlistId: String, sortIndex: Int) {
        database.playlistDao().updateSortIndex(playlistId, sortIndex)
    }

    // ── Music ────────────────────────────────────────────────────────────────

    fun getMusicTracks(playlistIds: List<String>, genre: String?) =
        database.musicDao().getTracks(playlistIds, genre)

    fun getMusicGenres(playlistIds: List<String>) =
        database.musicDao().getGenres(playlistIds)

    fun getMusicArtists(playlistIds: List<String>) =
        database.musicDao().getArtists(playlistIds)

    suspend fun getJellyfinTokenForPlaylist(playlistId: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return@withContext null
                if (playlist.type != "JELLYFIN") return@withContext null
                val host = (playlist.xtreamHost ?: playlist.url)?.trimEnd('/') ?: return@withContext null
                val retrofit = buildRetrofit("$host/")
                val api = retrofit.create(JellyfinApiService::class.java)
                val authHeader = "MediaBrowser Client=\"NexStream\", Device=\"Android TV\", DeviceId=\"android\", Version=\"1.0\""
                val auth = api.authenticate(authHeader, JellyfinAuthBody(
                    playlist.xtreamUsername ?: return@withContext null,
                    playlist.xtreamPassword ?: return@withContext null
                ))
                Pair(host, auth.accessToken)
            } catch (e: Exception) {
                android.util.Log.w("PlaylistRepository", "Jellyfin re-auth failed: ${e.message}")
                null
            }
        }

    suspend fun fetchJellyfinLyrics(playlistId: String, jellyfinItemId: String): List<app.nexstream.player.data.remote.LyricLine>? =
        withContext(Dispatchers.IO) {
            if (jellyfinItemId.isBlank()) return@withContext null
            try {
                val (host, token) = getJellyfinTokenForPlaylist(playlistId) ?: return@withContext null
                val api = buildRetrofit("$host/").create(app.nexstream.player.data.remote.JellyfinApiService::class.java)
                api.getLyrics(token, jellyfinItemId).lyrics.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                android.util.Log.w("PlaylistRepository", "Lyrics fetch failed", e)
                null
            }
        }

    suspend fun fetchJellyfinAlbumDescription(playlistId: String, albumId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return@withContext null
                val cleanHost = (playlist.xtreamHost ?: playlist.url)?.trimEnd('/') ?: return@withContext null
                val api = buildRetrofit("$cleanHost/").create(JellyfinApiService::class.java)
                val authHeader = "MediaBrowser Client=\"NexStream\", Device=\"Android TV\", DeviceId=\"android\", Version=\"1.0\""
                val auth = api.authenticate(authHeader, JellyfinAuthBody(
                    playlist.xtreamUsername ?: "", playlist.xtreamPassword ?: ""))
                val response = api.getItems(
                    token = auth.accessToken,
                    userId = auth.user.id,
                    ids = albumId,
                    fields = "Overview",
                    limit = 1,
                    startIndex = 0
                )
                response.items.firstOrNull()?.overview?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                android.util.Log.w("PlaylistRepository", "Album description fetch failed", e)
                null
            }
        }

    suspend fun toggleFavourite(channel: ChannelEntity) {
        database.channelDao().update(channel.copy(isFavourite = !channel.isFavourite))
    }

    suspend fun fetchAndStoreEPG(host: String, username: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("EPG_DEBUG", "=== EPG FETCH START ===")
                val xmltvUrl = "$host/xmltv.php?username=$username&password=$password"

                val client = OkHttpClient.Builder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(xmltvUrl)
                    .header("User-Agent", "NexStream/1.0 XMLTV-Client")
                    .header("Accept", "application/xml, text/xml, */*")
                    .build()
                val httpResponse = client.newCall(request).execute()
                if (!httpResponse.isSuccessful) {
                    throw Exception("HTTP ${httpResponse.code} ${httpResponse.message}")
                }
                val responseBody = httpResponse.body ?: throw Exception("Empty response body")

                // Synchronous CF email-obfuscation decode. Cloudflare replaces email-like
                // channel IDs (e.g. BBC1@provider.com) with <a data-cfemail="ENCODED">...</a>
                // when the origin returns text/html Content-Type. XOR-decoded inline via a
                // custom InputStream wrapper — no background thread, no pipe deadlocks.
                val cfPattern = Regex(
                    """<a[^>]*?data-cfemail=['"]([0-9a-f]+)['"][^>]*?>.*?</a>""",
                    RegexOption.IGNORE_CASE
                )
                val inputStream = cfDecodeStream(responseBody.byteStream(), cfPattern)

                // Diagnostic: log DB epgChannelIds so we can compare with XMLTV channel attrs
                val sampleDbIds = database.channelDao().getAllChannels().first()
                    .mapNotNull { it.epgChannelId?.takeIf { id -> id.isNotEmpty() } }
                    .take(10)
                android.util.Log.d("EPG_DEBUG", "DB epgChannelIds (first 10): $sampleDbIds")

                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")

                val windowStart = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)  // 7 days back
                val windowEnd   = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)   // 7 days ahead

                val programs = mutableListOf<ProgramEntity>()
                var eventType = parser.eventType
                var currentChannel = ""
                var currentTitle = ""
                var currentDesc = ""
                var currentStart = ""
                var currentStop = ""
                var programCount = 0

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "programme" -> {
                                currentChannel = parser.getAttributeValue(null, "channel") ?: ""
                                currentStart   = parser.getAttributeValue(null, "start")   ?: ""
                                currentStop    = parser.getAttributeValue(null, "stop")    ?: ""
                                currentTitle   = ""
                                currentDesc    = ""
                                if (programCount < 5) {
                                    android.util.Log.d("EPG_DEBUG", "XMLTV channel attr: '$currentChannel'")
                                }
                            }
                            "title" -> currentTitle = parser.nextText()
                            "desc"  -> currentDesc  = parser.nextText()
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "programme" && currentChannel.isNotEmpty()) {
                                try {
                                    val startTime = parseXmltvTime(currentStart)
                                    val endTime   = parseXmltvTime(currentStop)
                                    if (startTime >= windowStart && startTime <= windowEnd && endTime > startTime) {
                                        programs.add(
                                            ProgramEntity(
                                                id          = "${currentChannel}_$startTime",
                                                channelId   = currentChannel,
                                                title       = currentTitle,
                                                description = currentDesc.ifEmpty { null },
                                                startTime   = startTime,
                                                endTime     = endTime,
                                                category    = null,
                                                icon        = null
                                            )
                                        )
                                        programCount++
                                        val runtime = Runtime.getRuntime()
                                        val availMb = runtime.freeMemory() / 1048576
                                        val batchSize = if (availMb < 100) 50 else 500

                                        if (programs.size >= batchSize) {
                                            database.programDao().insertAll(programs)
                                            _epgProgramCount.value = programCount
                                            android.util.Log.d("EPG_DEBUG", "Batch inserted — $programCount total")
                                            programs.clear()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    eventType = parser.next()
                }

                if (programs.isNotEmpty()) {
                    database.programDao().insertAll(programs)
                    programs.clear()
                }
                _epgProgramCount.value = programCount
                inputStream.close()
                httpResponse.close()
                android.util.Log.d("EPG_DEBUG", "=== EPG FETCH COMPLETE — $programCount programs ===")

            } catch (e: Exception) {
                android.util.Log.e("EPG_DEBUG", "=== EPG FETCH FAILED ===", e)
                throw e
            }
        }

        val nowMs = System.currentTimeMillis()
        val midnight = nowMs - (nowMs % (24L * 60 * 60 * 1000L))

        val gapFillers = mutableListOf<ProgramEntity>()
        val channelIds = database.channelDao().getAllChannels().first().mapNotNull {
            it.epgChannelId?.takeIf { id -> id.isNotEmpty() }
        }

        channelIds.forEach { channelId ->
            val firstProgram = database.programDao()
                .getProgramsForChannel(channelId, midnight)
                .first()
                .firstOrNull()

            if (firstProgram != null && firstProgram.startTime > midnight) {
                var cursor = midnight
                while (cursor < firstProgram.startTime) {
                    val end = minOf(cursor + 60 * 60 * 1000L, firstProgram.startTime)
                    gapFillers.add(ProgramEntity(
                        id = "gap-fill-$channelId-$cursor",
                        channelId = channelId,
                        title = "No Information Provided",
                        description = null,
                        startTime = cursor,
                        endTime = end,
                        category = null,
                        icon = null
                    ))
                    cursor = end
                }
            }
        }

        if (gapFillers.isNotEmpty()) {
            database.programDao().insertAll(gapFillers)
            android.util.Log.d("EPG_DEBUG", "Inserted ${gapFillers.size} gap-filler programmes")
        }
    }

    private fun decodeCfEmail(encoded: String): String =
        try {
            val bytes = encoded.chunked(2).map { it.toInt(16) }
            val key = bytes[0]
            bytes.drop(1).map { (it xor key).toChar() }.joinToString("")
        } catch (_: Exception) { "" }

    private fun cfDecodeStream(source: java.io.InputStream, cfPattern: Regex): java.io.InputStream {
        val reader = source.bufferedReader(Charsets.UTF_8)
        var lineBuffer = ByteArray(0)
        var linePos = 0
        var eof = false
        return object : java.io.InputStream() {
            private fun nextLine() {
                val line = reader.readLine() ?: run { eof = true; return }
                val decoded = if (line.contains("data-cfemail", ignoreCase = true)) {
                    cfPattern.replace(line) { mr -> decodeCfEmail(mr.groupValues[1]) }
                } else line
                lineBuffer = (decoded + "\n").toByteArray(Charsets.UTF_8)
                linePos = 0
            }
            override fun read(): Int {
                while (linePos >= lineBuffer.size) { if (eof) return -1; nextLine() }
                return lineBuffer[linePos++].toInt() and 0xFF
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                var written = 0
                while (written < len) {
                    while (linePos >= lineBuffer.size) {
                        if (eof) return if (written == 0) -1 else written
                        nextLine()
                    }
                    val available = lineBuffer.size - linePos
                    val toRead = minOf(available, len - written)
                    lineBuffer.copyInto(b, off + written, linePos, linePos + toRead)
                    linePos += toRead
                    written += toRead
                }
                return written
            }
            override fun close() { reader.close() }
        }
    }

    private fun parseXmltvTime(xmltvTime: String): Long {
        return try {
            val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            format.isLenient = false
            format.parse(xmltvTime.trim())?.time ?: 0L
        } catch (e: Exception) {
            try {
                val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(xmltvTime.trim().take(14))?.time ?: 0L
            } catch (e2: Exception) { 0L }
        }
    }

    suspend fun refreshAllEpg() {
        val playlists = getAllPlaylists().first()
        val xtreamPlaylists = playlists.filter {
            it.type == "XTREAM" &&
            !it.xtreamHost.isNullOrEmpty() &&
            !it.xtreamUsername.isNullOrEmpty() &&
            !it.xtreamPassword.isNullOrEmpty()
        }
        if (xtreamPlaylists.isEmpty()) {
            _epgFetchError.value = "No Xtream playlists found"
            return
        }
        _isLoadingEPG.value = true
        _epgProgramCount.value = 0
        _epgFetchError.value = null
        try {
            xtreamPlaylists.forEach { playlist ->
                fetchAndStoreEPG(playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
            }
        } catch (e: Exception) {
            _epgFetchError.value = e.message ?: "Refresh failed"
            android.util.Log.e("EPG_DEBUG", "refreshAllEpg failed", e)
        } finally {
            _isLoadingEPG.value = false
        }
    }

    fun getCurrentProgram(channelId: String): Flow<ProgramEntity?> =
        database.programDao().getCurrentProgram(channelId, System.currentTimeMillis())

    fun getNextProgram(channelId: String): Flow<ProgramEntity?> =
        database.programDao().getNextProgram(channelId, System.currentTimeMillis())

    fun getProgramsInTimeRange(channelId: String, startTime: Long, endTime: Long): Flow<List<ProgramEntity>> =
        database.programDao().getProgramsForChannel(channelId, startTime)

    fun getProgramsForChannel(channelId: String, currentTime: Long): Flow<List<ProgramEntity>> =
        database.programDao().getProgramsForChannel(channelId, currentTime)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllMovies(): Flow<List<MovieEntity>> {
        return getEnabledPlaylists().flatMapLatest { playlists ->
            if (playlists.isEmpty()) flowOf(emptyList())
            else combine(playlists.map { database.movieDao().getMoviesByPlaylist(it.id) }) { movieArrays ->
                movieArrays.flatMap { it }
            }
        }
    }

    fun getMoviesByCategory(category: String): Flow<List<MovieEntity>> =
        database.movieDao().getMoviesByCategory(category)

    fun getMovieCategories(): Flow<List<String>> =
        database.movieDao().getAllCategories()

    suspend fun cleanupOldPrograms(beforeTime: Long) {
        withContext(Dispatchers.IO) {
            database.programDao().deleteOldPrograms(beforeTime)
            android.util.Log.d("PlaylistRepository", "Cleaned up old EPG programs")
        }
    }

    fun getAllChannels(): Flow<List<ChannelEntity>> =
        database.channelDao().getEnabledChannels()

    suspend fun preloadChannelIcons(context: android.content.Context) {
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                // Limit to first 150 channels — enough to warm the visible EPG grid
                // without flooding the disk cache workers on low-RAM devices
                val channels = database.channelDao().getChannelIconUrls().take(150)
                android.util.Log.d("IconCache", "Preloading ${channels.size} channel icons")

                for (channel in channels) {
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(channel.logoUrl)
                        .memoryCacheKey(channel.logoUrl)
                        .diskCacheKey(channel.logoUrl)
                        .build()
                    // execute() awaits one at a time — no disk cache lock contention
                    imageLoader.execute(request)
                    kotlinx.coroutines.delay(50)
                }
                android.util.Log.d("IconCache", "Icon preload complete")
            } catch (e: Exception) {
                android.util.Log.e("IconCache", "Failed to preload icons", e)
            }
        }
    }

    suspend fun getMovieDetails(playlistId: String, vodId: String): MovieEntity? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("MovieDetails", "=== START getMovieDetails ===")
                val playlist = database.playlistDao().getPlaylistById(playlistId)
                if (playlist == null) {
                    android.util.Log.e("MovieDetails", "Playlist not found!")
                    return@withContext null
                }
                val username = playlist.xtreamUsername
                val password = playlist.xtreamPassword
                if (username == null || password == null) {
                    android.util.Log.e("MovieDetails", "Username or password is null!")
                    return@withContext null
                }
                val retrofit = buildRetrofit(playlist.url)
                val api = retrofit.create(XtreamApiService::class.java)
                val movieInfo = api.getMovieInfo(username, password, vodId = vodId)
                val existingMovie = database.movieDao().getMovieById("$playlistId-$vodId").first()
                if (existingMovie == null) {
                    android.util.Log.e("MovieDetails", "Movie not found in database!")
                    return@withContext null
                }
                existingMovie.copy(
                    plot        = movieInfo.info?.plot ?: movieInfo.movieData?.plot ?: existingMovie.plot,
                    cast        = movieInfo.info?.cast ?: movieInfo.movieData?.cast ?: existingMovie.cast,
                    director    = movieInfo.info?.director ?: movieInfo.movieData?.director ?: existingMovie.director,
                    genre       = movieInfo.info?.genre ?: movieInfo.movieData?.genre ?: existingMovie.genre,
                    rating      = movieInfo.info?.rating ?: movieInfo.movieData?.rating ?: existingMovie.rating,
                    duration    = movieInfo.info?.duration ?: existingMovie.duration,
                    releaseDate      = movieInfo.info?.releaseDate ?: movieInfo.movieData?.year ?: existingMovie.releaseDate,
                    backdropUrl      = movieInfo.info?.backdropPath?.firstOrNull() ?: existingMovie.backdropUrl,
                    originalLanguage = movieInfo.info?.originalLanguage ?: existingMovie.originalLanguage,
                    trailerUrl  = movieInfo.info?.youtubeTrailer?.takeIf { it.isNotBlank() }
                        ?.let { key -> if (key.startsWith("http")) key else "https://www.youtube.com/watch?v=$key" }
                )
            } catch (e: Exception) {
                android.util.Log.e("MovieDetails", "=== EXCEPTION: ${e.message} ===", e)
                null
            }
        }
    }

    suspend fun saveMoviePlaybackPosition(movieId: String, position: Long) {
        withContext(Dispatchers.IO) {
            try {
                val movie = database.movieDao().getMovieById(movieId).first() ?: return@withContext
                database.movieDao().update(movie.copy(
                    lastPlayedPosition  = position,
                    lastPlayedTimestamp = System.currentTimeMillis()
                ))
                android.util.Log.d("PlaybackPosition", "Saved position: $position for movie: ${movie.name}")
            } catch (e: Exception) {
                android.util.Log.e("PlaybackPosition", "Failed to save position", e)
            }
        }
    }

    suspend fun clearMoviePlaybackPosition(movieId: String) {
        withContext(Dispatchers.IO) {
            try {
                val movie = database.movieDao().getMovieById(movieId).first() ?: return@withContext
                database.movieDao().update(movie.copy(lastPlayedPosition = 0, lastPlayedTimestamp = 0))
                android.util.Log.d("PlaybackPosition", "Cleared position for movie: ${movie.name}")
            } catch (e: Exception) {
                android.util.Log.e("PlaybackPosition", "Failed to clear position", e)
            }
        }
    }

    fun getProgramsForChannelsInRange(
        channelIds: List<String>,
        startTime: Long,
        endTime: Long
    ): Flow<List<ProgramEntity>> =
        database.programDao().getProgramsForChannelsInRange(channelIds, startTime, endTime)

    suspend fun refreshChannels(playlistId: String, channels: List<ChannelEntity>) {
        withContext(Dispatchers.IO) {
            database.channelDao().deleteByPlaylist(playlistId)
            database.channelDao().insertAll(channels)
            database.channelGroupDao().pruneStaleMembers()
        }
    }

    suspend fun refreshChannelsAndEPG(playlistId: String, host: String, username: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                val retrofit = buildRetrofit(host)
                val api = retrofit.create(XtreamApiService::class.java)

                val streams = api.getLiveStreams(username, password)
                val categories = api.getLiveCategories(username, password)

                val channels = streams.mapIndexed { index, stream ->
                    ChannelEntity(
                        id = "$playlistId-${stream.streamId}",
                        name = stream.name,
                        streamUrl = "$host/live/$username/$password/${stream.streamId}.ts",
                        logoUrl = stream.icon,
                        groupTitle = categories.find { it.id == stream.categoryId }?.name?.trim(),
                        epgChannelId = stream.epgChannelId,
                        playlistId = playlistId,
                        sortIndex = index,
                        tvArchive = stream.tvArchive,
                        tvArchiveDuration = stream.tvArchiveDuration,
                        streamId = stream.streamId.toString()
                    )
                }

                database.channelDao().deleteByPlaylist(playlistId)
                database.channelDao().insertAll(channels)
                database.channelGroupDao().pruneStaleMembers()
                database.programDao().deleteAllPrograms()
                _isLoadingEPG.value = true
                _epgProgramCount.value = 0
                _epgFetchError.value = null
                try {
                    fetchAndStoreEPG(host, username, password)
                } catch (e: Exception) {
                    _epgFetchError.value = e.message ?: "Unknown error"
                    android.util.Log.e("PlaylistRepository", "EPG fetch failed in refreshChannelsAndEPG", e)
                } finally {
                    _isLoadingEPG.value = false
                }

            } catch (e: Exception) {
                android.util.Log.e("PlaylistRepository", "refreshChannelsAndEPG failed", e)
            }
        }
    }

    suspend fun getCatchUpListings(channel: ChannelEntity, streamId: Int): List<ProgramEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val playlists = getAllPlaylists().first()
                val playlist = playlists.firstOrNull { it.type == "XTREAM" } ?: return@withContext emptyList()
                val host = playlist.xtreamHost ?: return@withContext emptyList()
                val username = playlist.xtreamUsername ?: return@withContext emptyList()
                val password = playlist.xtreamPassword ?: return@withContext emptyList()

                val api = buildRetrofit(host).create(XtreamApiService::class.java)
                val response = api.getSimpleDataTable(username, password, streamId)
                val now = System.currentTimeMillis()

                response.epgListings?.mapNotNull { listing ->
                    try {
                        val startMs = (listing.startTimestamp ?: return@mapNotNull null) * 1000L
                        val endMs   = (listing.stopTimestamp  ?: return@mapNotNull null) * 1000L
                        if (endMs > startMs && endMs <= now) {
                            ProgramEntity(
                                id          = "catchup_${listing.id}",
                                channelId   = channel.epgChannelId ?: channel.id,
                                title       = listing.title
                                    ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT).toString(Charsets.UTF_8) }
                                    ?: return@mapNotNull null,
                                description = listing.description
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT).toString(Charsets.UTF_8) },
                                startTime   = startMs,
                                endTime     = endMs,
                                category    = null,
                                icon        = null
                            )
                        } else null
                    } catch (e: Exception) { null }
                }?.sortedByDescending { it.startTime } ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("CatchUp", "getCatchUpListings failed", e)
                emptyList()
            }
        }
    }

    fun setLoadingEPG(loading: Boolean)    { _isLoadingEPG.value    = loading }
    fun setLoadingVOD(loading: Boolean)    { _isLoadingVOD.value    = loading }
    fun setLoadingSeries(loading: Boolean) { _isLoadingSeries.value = loading }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentProgrammeForChannelUrl(channelUrl: String): Flow<ProgramEntity?> {
        return database.channelDao().getChannelByStreamUrl(channelUrl)
            .flatMapLatest { channel ->
                if (channel?.epgChannelId.isNullOrEmpty()) flowOf(null)
                else getCurrentProgram(channel!!.epgChannelId!!)
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getNextProgrammeForChannelUrl(channelUrl: String): Flow<ProgramEntity?> {
        return database.channelDao().getChannelByStreamUrl(channelUrl)
            .flatMapLatest { channel ->
                if (channel?.epgChannelId.isNullOrEmpty()) flowOf(null)
                else getNextProgram(channel!!.epgChannelId!!)
            }
    }

    fun searchPrograms(query: String): Flow<List<ProgramEntity>> =
        database.programDao().searchPrograms(query, System.currentTimeMillis())

    fun searchMovies(query: String): Flow<List<MovieEntity>> =
        database.movieDao().searchMovies(query)

    fun searchMoviesByPeople(query: String): Flow<List<MovieEntity>> =
        database.movieDao().searchMoviesByPeople("%$query%")

    fun searchSeries(query: String): Flow<List<SeriesEntity>> =
        database.seriesDao().searchSeries(query)

    fun searchSeriesByPeople(query: String): Flow<List<SeriesEntity>> =
        database.seriesDao().searchSeriesByPeople("%$query%")

    suspend fun findMoviesByTitle(title: String): List<MovieEntity> =
        database.movieDao().findMoviesByTitle(title)

    suspend fun findSeriesByTitle(title: String): List<SeriesEntity> =
        database.seriesDao().findSeriesByTitle(title)

    suspend fun findMoviesByTitlesBatch(lowerTitles: List<String>): List<MovieEntity> =
        database.movieDao().findMoviesByTitlesBatch(lowerTitles)

    suspend fun findSeriesByTitlesBatch(lowerTitles: List<String>): List<SeriesEntity> =
        database.seriesDao().findSeriesByTitlesBatch(lowerTitles)

    suspend fun getChannelByEpgId(epgChannelId: String): ChannelEntity? =
        database.channelDao().getChannelByEpgId(epgChannelId)

    fun getWatchlistItemsForProfile(profileId: String): Flow<List<WatchlistEntity>> =
        database.watchlistDao().getItemsByProfileId(profileId)

    fun isInWatchlistForProfile(id: String, profileId: String): Flow<Boolean> =
        database.watchlistDao().isInWatchlist(id, profileId)

    suspend fun addToWatchlist(item: WatchlistEntity) =
        database.watchlistDao().addToWatchlist(item)

    suspend fun removeFromWatchlist(id: String, profileId: String) =
        database.watchlistDao().removeFromWatchlist(id, profileId)

    fun getProgramsForChannelInRange(channelId: String, startTime: Long, endTime: Long): Flow<List<ProgramEntity>> =
        database.programDao().getProgramsForChannelInRange(channelId, startTime, endTime)

    suspend fun getMovieById(movieId: String): MovieEntity? =
        database.movieDao().getById(movieId)

    suspend fun getMovieByStreamUrl(streamUrl: String): MovieEntity? =
        database.movieDao().getByStreamUrl(streamUrl)

    suspend fun getMovieByXtreamStreamId(streamId: String): MovieEntity? =
        database.movieDao().getByXtreamStreamId(streamId)

    suspend fun getEpisodeById(episodeId: String): EpisodeEntity? =
        database.seriesDao().getEpisodeByIdOnce(episodeId)

    suspend fun getSeriesById(seriesId: String): SeriesEntity? =
        database.seriesDao().getById(seriesId)

    suspend fun getChannelById(channelId: String): app.nexstream.player.data.local.entity.ChannelEntity? =
        database.channelDao().getChannelById(channelId)

    suspend fun getChannelByStreamUrl(streamUrl: String): app.nexstream.player.data.local.entity.ChannelEntity? =
        database.channelDao().getChannelByStreamUrlOnce(streamUrl)

    suspend fun getChannelByName(name: String): app.nexstream.player.data.local.entity.ChannelEntity? =
        database.channelDao().getChannelByName(name)

    suspend fun getChannelByXtreamStreamId(streamId: String): app.nexstream.player.data.local.entity.ChannelEntity? =
        database.channelDao().getChannelByXtreamStreamId(streamId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecentlyWatched(): Flow<List<RecentlyWatchedEntity>> =
        profileManager.activeProfile.flatMapLatest { profile ->
            database.recentlyWatchedDao().getRecentlyWatched(profile?.id ?: "default")
        }

    suspend fun recordRecentlyWatchedChannel(channel: ChannelEntity) {
        withContext(Dispatchers.IO) {
            val entity = RecentlyWatchedEntity(
                id        = channel.id,
                profileId = activeProfileId,
                type      = RecentlyWatchedType.CHANNEL,
                name      = channel.name,
                subtitle  = null,
                streamUrl = channel.streamUrl,
                logoUrl   = channel.logoUrl
            )
            database.recentlyWatchedDao().insert(entity)
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
            recentlySyncManager.enqueuePushAdd(entity)
        }
    }

    suspend fun recordRecentlyWatchedMovie(movie: MovieEntity) {
        withContext(Dispatchers.IO) {
            val entity = RecentlyWatchedEntity(
                id        = movie.id,
                profileId = activeProfileId,
                type      = RecentlyWatchedType.MOVIE,
                name      = movie.name,
                subtitle  = null,
                streamUrl = movie.streamUrl,
                logoUrl   = movie.posterUrl,
                movieId   = movie.id
            )
            database.recentlyWatchedDao().insert(entity)
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
            recentlySyncManager.enqueuePushAdd(entity)
        }
    }

    suspend fun recordRecentlyWatchedEpisode(series: SeriesEntity, episode: EpisodeEntity) {
        withContext(Dispatchers.IO) {
            val entity = RecentlyWatchedEntity(
                id        = episode.id,
                profileId = activeProfileId,
                type      = RecentlyWatchedType.EPISODE,
                name      = series.name,
                subtitle  = "S${episode.seasonNum}E${episode.episodeNum} · ${episode.name}",
                streamUrl = episode.streamUrl,
                logoUrl   = series.posterUrl,
                seriesId  = series.id,
                episodeId = episode.id
            )
            database.recentlyWatchedDao().insert(entity)
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
            recentlySyncManager.enqueuePushAdd(entity)
        }
    }

    suspend fun deleteRecentlyWatched(id: String) {
        withContext(Dispatchers.IO) {
            database.recentlyWatchedDao().deleteById(id, activeProfileId)
            recentlySyncManager.enqueuePushRemove(id, activeProfileId)
        }
    }

    suspend fun clearRecentlyWatched() {
        withContext(Dispatchers.IO) { database.recentlyWatchedDao().clearAll(activeProfileId) }
    }

    suspend fun clearRecentlyWatchedByType(type: app.nexstream.player.data.local.entity.RecentlyWatchedType) {
        withContext(Dispatchers.IO) { database.recentlyWatchedDao().clearByType(activeProfileId, type) }
    }

    suspend fun clearWatchlistAll() {
        withContext(Dispatchers.IO) { database.watchlistDao().clearAll(activeProfileId) }
    }

    suspend fun clearWatchlistByType(type: app.nexstream.player.data.local.entity.WatchlistType) {
        withContext(Dispatchers.IO) { database.watchlistDao().clearByType(activeProfileId, type) }
    }

    suspend fun pruneStaleAndBlockedWatchlistItems() {
        withContext(Dispatchers.IO) {
            val profileId = activeProfileId
            val dao = database.watchlistDao()
            dao.pruneStaleChannels(profileId)
            dao.pruneStaleMovies(profileId)
            dao.pruneStaleSeries(profileId)
            val blockedCategories = profileManager.blockedTvCategories.value
            if (blockedCategories.isNotEmpty()) {
                dao.pruneBlockedChannels(profileId, blockedCategories.toList())
            }
            val blockedCerts = getBlockedCerts(profileManager.activeProfile.value?.maxAgeRating)
            if (blockedCerts.isNotEmpty()) {
                dao.pruneAgeRestrictedMovies(profileId, blockedCerts)
                dao.pruneAgeRestrictedSeries(profileId, blockedCerts)
            }
        }
    }

    suspend fun pruneStaleAndBlockedRecentItems() {
        withContext(Dispatchers.IO) {
            val profileId = activeProfileId
            val dao = database.recentlyWatchedDao()
            dao.pruneStaleChannels(profileId)
            dao.pruneStaleMovies(profileId)
            dao.pruneStaleEpisodes(profileId)
            val blockedCategories = profileManager.blockedTvCategories.value
            if (blockedCategories.isNotEmpty()) {
                dao.pruneBlockedChannels(profileId, blockedCategories.toList())
            }
            val blockedCerts = getBlockedCerts(profileManager.activeProfile.value?.maxAgeRating)
            if (blockedCerts.isNotEmpty()) {
                dao.pruneAgeRestrictedMovies(profileId, blockedCerts)
                dao.pruneAgeRestrictedEpisodes(profileId, blockedCerts)
            }
        }
    }

    private fun getBlockedCerts(maxAgeRating: String?): List<String> {
        if (maxAgeRating == null) return emptyList()
        val order = listOf("U", "PG", "12", "15", "18")
        val maxIndex = order.indexOf(maxAgeRating).takeIf { it >= 0 } ?: return emptyList()
        return order.drop(maxIndex + 1)
    }

    fun getChannelCategories(): Flow<List<String>> =
        database.channelDao().getAllCategories()

    fun getChannelCountsByCategory(): Flow<Map<String, Int>> =
        database.channelDao().getChannelCountsByCategory()
            .map { rows -> rows.associate { it.groupTitle to it.count } }

    fun getAllWatchedEpisodeCounts(): Flow<Map<String, Int>> =
        database.seriesDao().getAllWatchedEpisodeCounts()
            .map { rows -> rows.associate { it.seriesId to it.count } }

    fun getMovieGridItems(playlistId: String): Flow<List<MovieGridItem>> =
        database.movieDao().getMovieGridItems(playlistId)

    suspend fun getAllMoviesWithProgress() = database.movieDao().getAllWithProgress()
    suspend fun getAllEpisodesWithProgress() = database.seriesDao().getAllEpisodesWithProgress()

    suspend fun getAllMovieNames(): List<String> = database.movieDao().getAllMovieNames()
    suspend fun getAllSeriesNames(): List<String> = database.seriesDao().getAllSeriesNames()

    suspend fun getMovieByIdOnce(id: String) = database.movieDao().getMovieByIdOnce(id)
    fun getSeriesGridItems(playlistId: String): Flow<List<SeriesGridItem>> =
        database.seriesDao().getSeriesGridItems(playlistId)

    fun getSeriesGridItemsByCategory(playlistId: String, category: String): Flow<List<SeriesGridItem>> =
        database.seriesDao().getSeriesGridItemsByCategory(playlistId, category)

    fun getMovieGridItemsByCategory(playlistId: String, category: String): Flow<List<MovieGridItem>> =
        database.movieDao().getMovieGridItemsByCategory(playlistId, category)

    fun getBlockedCategoriesFlow(profileId: String, type: String): Flow<Set<String>> =
        database.profileDao().getBlockedCategoriesFlow(profileId, type)
            .map { it.toSet() }

    suspend fun getXtreamAccountInfo(
        host: String,
        username: String,
        password: String
    ): XtreamAccountInfo? {
        return try {
            buildRetrofit(host).create(XtreamApiService::class.java)
                .getAccountInfo(username, password)
        } catch (e: Exception) { null }
    }

    // ── OMDb ratings fetch (via nexstream.uk backend proxy) ──────────────────

    private suspend fun fetchOmdbData(title: String, type: String): org.json.JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(title, "UTF-8")
            val url = "https://nexstream.uk/api/omdb.php?title=$encoded&type=$type"
            val body = tmdbHttpClient.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute().body?.string() ?: return@withContext null
            val obj = org.json.JSONObject(body)
            if (obj.has("error")) null else obj
        } catch (_: Exception) { null }
    }

    suspend fun fetchRtDataForMovieSingle(movieId: String, movieName: String): RtData? =
        withContext(Dispatchers.IO) {
            try {
                val obj = fetchOmdbData(cleanTitleForTmdb(movieName), "movie") ?: return@withContext null
                val rtScore   = if (obj.isNull("rt_score"))  null else obj.getInt("rt_score")
                val metascore = if (obj.isNull("metascore")) null else obj.getInt("metascore")
                if (rtScore != null || metascore != null) {
                    database.movieDao().updateRtData(movieId, rtScore, null, null, metascore)
                }
                RtData(rtScore, null, null, metascore)
            } catch (e: Exception) {
                android.util.Log.w("OMDB", "fetchOmdb movie failed for $movieName: ${e.message}")
                null
            }
        }

    suspend fun fetchRtDataForSeriesSingle(seriesId: String, seriesName: String): RtData? =
        withContext(Dispatchers.IO) {
            try {
                val obj = fetchOmdbData(cleanTitleForTmdb(seriesName), "series") ?: return@withContext null
                val rtScore   = if (obj.isNull("rt_score"))  null else obj.getInt("rt_score")
                val metascore = if (obj.isNull("metascore")) null else obj.getInt("metascore")
                if (rtScore != null || metascore != null) {
                    database.seriesDao().updateRtData(seriesId, rtScore, metascore)
                }
                RtData(rtScore, null, null, metascore)
            } catch (e: Exception) {
                android.util.Log.w("OMDB", "fetchOmdb series failed for $seriesName: ${e.message}")
                null
            }
        }

    // ── TMDB certification fetch ──────────────────────────────────────────────

    private val tmdbHttpClient = OkHttpClient()
    // Prevents the startup cert sweep and a concurrent import cert sweep from running simultaneously
    private val tmdbBatchMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun fetchTmdbProxy(title: String, type: String): org.json.JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(title, "UTF-8")
            val url = "https://nexstream.uk/api/tmdb.php?title=$encoded&type=$type"
            val body = tmdbHttpClient.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute().body?.string() ?: return@withContext null
            val obj = org.json.JSONObject(body)
            if (obj.has("error")) null else obj
        } catch (_: Exception) { null }
    }

    suspend fun fetchAllMissingCertifications() = withContext(Dispatchers.IO) {
        tmdbBatchMutex.withLock {
            val playlists = database.playlistDao().getAllPlaylists().first()
            for (playlist in playlists) {
                fetchCertificationsForMovies(playlist.id)
                fetchCertificationsForSeries(playlist.id)
            }
        }
    }

    suspend fun fetchCertificationsForMovies(playlistId: String) = withContext(Dispatchers.IO) {
        val movies = database.movieDao().getMovieGridItems(playlistId).first()
            .filter { it.certification == null }
        android.util.Log.d("TMDB", "Certifications to fetch for ${movies.size} movies")
        if (movies.isEmpty()) return@withContext
        val certUpdates = mutableListOf<Pair<String, String>>()
        val dateUpdates = mutableListOf<Pair<String, String>>()
        val langUpdates = mutableListOf<Pair<String, String>>()
        for (movie in movies) {
            try {
                val obj  = fetchTmdbProxy(cleanTitleForTmdb(movie.name), "movie")
                val cert = obj?.optString("certification")?.takeIf { it.isNotEmpty() && it in KNOWN_CERTS } ?: "NR"
                val date = obj?.optString("release_date")?.takeIf { it.isNotEmpty() }
                val lang = obj?.optString("original_language")?.takeIf { it.isNotEmpty() }
                certUpdates += movie.id to cert
                if (date != null) dateUpdates += movie.id to date
                if (lang != null) langUpdates += movie.id to lang
                delay(500)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        database.withTransaction {
            for ((id, cert) in certUpdates) database.movieDao().updateCertification(id, cert)
            for ((id, date) in dateUpdates) database.movieDao().updateReleaseDate(id, date)
            for ((id, lang) in langUpdates) database.movieDao().updateOriginalLanguage(id, lang)
        }
    }

    suspend fun fetchCertificationForMovieSingle(movieId: String, movieName: String): String? = withContext(Dispatchers.IO) {
        try {
            val obj  = fetchTmdbProxy(cleanTitleForTmdb(movieName), "movie") ?: return@withContext null
            val cert = obj.optString("certification").takeIf { it.isNotEmpty() && it in KNOWN_CERTS } ?: "NR"
            val date = obj.optString("release_date").takeIf { it.isNotEmpty() }
            val lang = obj.optString("original_language").takeIf { it.isNotEmpty() }
            database.movieDao().updateCertification(movieId, cert)
            if (date != null) database.movieDao().updateReleaseDate(movieId, date)
            if (lang != null) database.movieDao().updateOriginalLanguage(movieId, lang)
            cert
        } catch (e: Exception) {
            android.util.Log.e("TMDB", "fetchCertMovie error for '$movieName': $e")
            null
        }
    }

    suspend fun fetchCertificationForSeriesSingle(seriesId: String, seriesName: String): String? = withContext(Dispatchers.IO) {
        try {
            val obj  = fetchTmdbProxy(cleanTitleForTmdb(seriesName), "tv") ?: return@withContext null
            val cert = obj.optString("certification").takeIf { it.isNotEmpty() && it in KNOWN_CERTS } ?: "NR"
            val date = obj.optString("release_date").takeIf { it.isNotEmpty() }
            val lang = obj.optString("original_language").takeIf { it.isNotEmpty() }
            database.seriesDao().updateCertification(seriesId, cert)
            if (date != null) database.seriesDao().updateReleaseDate(seriesId, date)
            if (lang != null) database.seriesDao().updateOriginalLanguage(seriesId, lang)
            cert
        } catch (e: Exception) {
            android.util.Log.e("TMDB", "fetchCertSingle error for '$seriesName': $e")
            null
        }
    }

    suspend fun fetchOriginalLanguageForMovieSingle(movieId: String, movieName: String): String? = withContext(Dispatchers.IO) {
        try {
            val obj  = fetchTmdbProxy(cleanTitleForTmdb(movieName), "movie") ?: return@withContext null
            val lang = obj.optString("original_language").takeIf { it.isNotEmpty() } ?: return@withContext null
            val cert = obj.optString("certification").takeIf { it.isNotEmpty() && it in KNOWN_CERTS }
            val date = obj.optString("release_date").takeIf { it.isNotEmpty() }
            database.movieDao().updateOriginalLanguage(movieId, lang)
            if (cert != null) database.movieDao().updateCertification(movieId, cert)
            if (date != null) database.movieDao().updateReleaseDate(movieId, date)
            lang
        } catch (e: Exception) {
            android.util.Log.e("TMDB", "fetchOrigLangMovie error for '$movieName': $e")
            null
        }
    }

    suspend fun fetchOriginalLanguageForSeriesSingle(seriesId: String, seriesName: String): String? = withContext(Dispatchers.IO) {
        try {
            val obj  = fetchTmdbProxy(cleanTitleForTmdb(seriesName), "tv") ?: return@withContext null
            val lang = obj.optString("original_language").takeIf { it.isNotEmpty() } ?: return@withContext null
            val cert = obj.optString("certification").takeIf { it.isNotEmpty() && it in KNOWN_CERTS }
            val date = obj.optString("release_date").takeIf { it.isNotEmpty() }
            database.seriesDao().updateOriginalLanguage(seriesId, lang)
            if (cert != null) database.seriesDao().updateCertification(seriesId, cert)
            if (date != null) database.seriesDao().updateReleaseDate(seriesId, date)
            lang
        } catch (e: Exception) {
            android.util.Log.e("TMDB", "fetchOrigLangSeries error for '$seriesName': $e")
            null
        }
    }

    suspend fun fetchCertificationsForSeries(playlistId: String) = withContext(Dispatchers.IO) {
        val seriesList = database.seriesDao().getSeriesGridItems(playlistId).first()
            .filter { it.certification == null }
        android.util.Log.d("TMDB", "Series certs to fetch: ${seriesList.size}")
        if (seriesList.isEmpty()) return@withContext
        val certUpdates = mutableListOf<Pair<String, String>>()
        val dateUpdates = mutableListOf<Pair<String, String>>()
        val langUpdates = mutableListOf<Pair<String, String>>()
        for (series in seriesList) {
            try {
                val obj  = fetchTmdbProxy(cleanTitleForTmdb(series.name), "tv")
                val cert = obj?.optString("certification")?.takeIf { it.isNotEmpty() && it in KNOWN_CERTS } ?: "NR"
                val date = obj?.optString("release_date")?.takeIf { it.isNotEmpty() }
                val lang = obj?.optString("original_language")?.takeIf { it.isNotEmpty() }
                certUpdates += series.id to cert
                if (date != null) dateUpdates += series.id to date
                if (lang != null) langUpdates += series.id to lang
                delay(500)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("TMDB", "cert error for '${series.name}': $e")
            }
        }
        database.withTransaction {
            for ((id, cert) in certUpdates) database.seriesDao().updateCertification(id, cert)
            for ((id, date) in dateUpdates) database.seriesDao().updateReleaseDate(id, date)
            for ((id, lang) in langUpdates) database.seriesDao().updateOriginalLanguage(id, lang)
        }
    }

    // ── TMDB trailer fetch ────────────────────────────────────────────────────

    suspend fun fetchTrailerUrlForMovie(movieName: String): String? = withContext(Dispatchers.IO) {
        try {
            val proxy = fetchTmdbProxy(movieName, "movie") ?: return@withContext null
            val key = proxy.optString("trailer_key").takeIf { it.isNotEmpty() } ?: return@withContext null
            "https://www.youtube.com/watch?v=$key"
        } catch (_: Exception) { null }
    }

    suspend fun fetchTrailerUrlForSeries(seriesName: String): String? = withContext(Dispatchers.IO) {
        try {
            val proxy = fetchTmdbProxy(seriesName, "tv") ?: return@withContext null
            val key = proxy.optString("trailer_key").takeIf { it.isNotEmpty() } ?: return@withContext null
            "https://www.youtube.com/watch?v=$key"
        } catch (_: Exception) { null }
    }

    // ── TMDB credits (cast + director) ────────────────────────────────────────

    suspend fun enrichMoviesWithTmdbCredits() = withContext(Dispatchers.IO) {
        val movies = database.movieDao().getMoviesWithoutCast()
        android.util.Log.d("TMDB", "Cast enrichment: ${movies.size} movies to process")
        if (movies.isEmpty()) return@withContext
        val updates = mutableListOf<Triple<String, String?, String?>>()
        for (movie in movies) {
            try {
                val proxy = fetchTmdbProxy(movie.name, "movie")
                if (proxy == null) {
                    updates += Triple(movie.id, "", null)
                    delay(260)
                    continue
                }
                val castArray = proxy.optJSONArray("cast")
                val castStr = castArray?.let {
                    (0 until minOf(it.length(), 8))
                        .mapNotNull { i -> it.getJSONObject(i).optString("name").takeIf { n -> n.isNotEmpty() } }
                        .joinToString(", ").takeIf { s -> s.isNotEmpty() }
                }
                val director = proxy.optString("director").takeIf { it.isNotEmpty() }
                updates += Triple(movie.id, castStr ?: "", director)
                delay(300)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("TMDB", "Cast enrichment failed for '${movie.name}': $e")
            }
        }
        database.withTransaction {
            for ((id, cast, director) in updates) database.movieDao().updateCastAndDirector(id, cast, director)
        }
        android.util.Log.d("TMDB", "Movie cast enrichment complete")
    }

    suspend fun enrichSeriesWithTmdbCredits() = withContext(Dispatchers.IO) {
        val seriesList = database.seriesDao().getSeriesWithoutCast()
        android.util.Log.d("TMDB", "Cast enrichment: ${seriesList.size} series to process")
        if (seriesList.isEmpty()) return@withContext
        val updates = mutableListOf<Triple<String, String?, String?>>()
        for (series in seriesList) {
            try {
                val proxy = fetchTmdbProxy(series.name, "tv")
                if (proxy == null) {
                    updates += Triple(series.id, "", null)
                    delay(260)
                    continue
                }
                val castArray = proxy.optJSONArray("cast")
                val castStr = castArray?.let {
                    (0 until minOf(it.length(), 8))
                        .mapNotNull { i -> it.getJSONObject(i).optString("name").takeIf { n -> n.isNotEmpty() } }
                        .joinToString(", ").takeIf { s -> s.isNotEmpty() }
                }
                val director = proxy.optString("director").takeIf { it.isNotEmpty() }
                updates += Triple(series.id, castStr ?: "", director)
                delay(300)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("TMDB", "Cast enrichment failed for '${series.name}': $e")
            }
        }
        database.withTransaction {
            for ((id, cast, director) in updates) database.seriesDao().updateCastAndDirector(id, cast, director)
        }
        android.util.Log.d("TMDB", "Series cast enrichment complete")
    }

    fun getSeriesIdsWithNewEpisodes(): Flow<List<String>> =
        database.seriesDao().getSeriesIdsWithNewEpisodes()

    suspend fun checkForNewEpisodesInWatchlist() = withContext(Dispatchers.IO) {
        val watchlistedSeries = database.watchlistDao().getAllItemsSuspend()
            .filter { it.type == app.nexstream.player.data.local.entity.WatchlistType.SERIES }

        for (item in watchlistedSeries) {
            val series = database.seriesDao().getById(item.id) ?: continue
            val cachedCount = database.seriesDao().getEpisodeCountForSeries(series.id)
            if (cachedCount == 0) continue

            val playlist = database.playlistDao().getPlaylistById(series.playlistId) ?: continue
            if (playlist.type != "XTREAM") continue
            val host     = playlist.xtreamHost ?: continue
            val username = playlist.xtreamUsername ?: continue
            val password = playlist.xtreamPassword ?: continue
            if (host.isBlank() || username.isBlank() || password.isBlank()) continue

            try {
                val api = buildRetrofit(host).create(XtreamApiService::class.java)
                val info = api.getSeriesInfo(username, password, seriesId = series.seriesId)
                val apiCount = info.episodes?.values?.sumOf { it.size } ?: 0
                if (apiCount > cachedCount) {
                    database.seriesDao().updateHasNewEpisodes(series.id, true)
                    android.util.Log.d("ContentRefresh", "New episodes for '${series.name}': $cachedCount → $apiCount")
                }
            } catch (e: Exception) {
                android.util.Log.w("ContentRefresh", "Episode check failed for '${series.name}': ${e.message}")
            }
        }
    }

    companion object {
        private val KNOWN_CERTS = setOf("U", "PG", "12", "12A", "15", "18", "R18", "G", "PG-13", "R", "NC-17")
    }

    private fun cleanTitleForTmdb(title: String): String {
        var s = title.trim()
        // Take after the last pipe — strips all "4K | UK | FHD | " style IPTV prefixes
        if (s.contains('|')) s = s.substringAfterLast('|').trim()
        // Strip any remaining short group-label prefix: "UK: ", "4K: ", "USA: "
        s = s.replace(Regex("^[A-Z0-9]{2,5}\\s*[:|]\\s*"), "")
        // Strip trailing quality tags
        s = s.replace(Regex("\\s+(4K|UHD|FHD|HD|SDR|HDR|HEVC|BluRay|BRRip|WEBRip|WEB-DL|DVDRip)$", RegexOption.IGNORE_CASE), "")
        // Strip trailing year suffixes: (2024)  [2024]
        s = s.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
        s = s.replace(Regex("\\s*\\[\\d{4}\\]\\s*$"), "")
        return s.trim()
    }

}