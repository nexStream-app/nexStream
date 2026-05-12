package app.nexstream.player.data.repository

import app.nexstream.player.data.local.NexStreamDatabase
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.remote.M3UParser
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
import app.nexstream.player.data.local.dao.RecentlyWatchedDao
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.XtreamAccountInfo
import app.nexstream.player.data.remote.XtreamSeries
import app.nexstream.player.data.remote.XtreamStream
import app.nexstream.player.data.remote.XtreamVodStream
import com.google.gson.stream.JsonReader
import com.google.gson.Gson
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepository @Inject constructor(
    private val database: NexStreamDatabase,
    private val profileManager: ProfileManager,
    @ApplicationContext private val appContext: Context
) {
    private val activeProfileId: String
        get() = profileManager.activeProfile.value?.id ?: "default"
    private val _isLoadingEPG = MutableStateFlow(false)
    val isLoadingEPG: StateFlow<Boolean> = _isLoadingEPG.asStateFlow()

    private val _isLoadingVOD = MutableStateFlow(false)
    val isLoadingVOD: StateFlow<Boolean> = _isLoadingVOD.asStateFlow()

    private val _isLoadingSeries = MutableStateFlow(false)
    val isLoadingSeries: StateFlow<Boolean> = _isLoadingSeries.asStateFlow()

    // ── VOD progress counters ─────────────────────────────────────────────
    private val _vodLoadedCount = MutableStateFlow(0)
    val vodLoadedCount: StateFlow<Int> = _vodLoadedCount.asStateFlow()

    private val _vodTotalCount = MutableStateFlow(0)
    val vodTotalCount: StateFlow<Int> = _vodTotalCount.asStateFlow()

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
        return database.playlistDao().getAllPlaylists()
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

    private interface XmltvService {
        @GET
        @Streaming
        suspend fun getXMLTV(@Url url: String): ResponseBody
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
                android.util.Log.d("PlaylistRepository", "Channels stored — $totalChannels total, app ready")

                val firstTen = database.channelDao().getAllChannels().first().take(10)
                firstTen.forEach { channel ->
                    android.util.Log.d("CHANNEL_DEBUG", "Name: [${channel.name}] | EPG ID: [${channel.epgChannelId}]")
                }

                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {

                    // 1. EPG
                    _isLoadingEPG.value = true
                    try {
                        fetchAndStoreEPG(host, username, password)
                        android.util.Log.d("PlaylistRepository", "EPG fetch completed")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "EPG fetch failed", e)
                    } finally {
                        _isLoadingEPG.value = false
                    }

                    System.gc()
                    kotlinx.coroutines.delay(2_000)

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

                    System.gc()
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

                    System.gc()
                    kotlinx.coroutines.delay(2_000)

                    // 4. Icons
                    try {
                        preloadChannelIcons(appContext)
                        android.util.Log.d("PlaylistRepository", "Icon preload complete")
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistRepository", "Icon preload failed", e)
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

                var batchIndex = 0
                var totalInserted = 0

                streamVodItems(host, username, password) { batch ->
                    val movies = batch.map { stream ->
                        val categoryName = vodCategories.find { it.id == stream.categoryId }?.name?.trim()
                        MovieEntity(
                            id = "$playlistId-${stream.streamId}",
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
                            isFavourite = false
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

                var batchIndex = 0
                var totalInserted = 0

                streamSeriesItems(host, username, password) { batch ->
                    val seriesEntities = batch.map { series ->
                        val categoryName = seriesCategories.find { it.id == series.categoryId }?.name?.trim()
                        SeriesEntity(
                            id = "$playlistId-${series.seriesId}",
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
                            playlistId = playlistId
                        )
                    }

                    try {
                        database.seriesDao().insertAllSeries(seriesEntities)
                        totalInserted += seriesEntities.size
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
                    seasonCount = seasonCount,
                    plot = seriesInfo.info?.plot ?: existingSeries.plot,
                    cast = seriesInfo.info?.cast ?: existingSeries.cast,
                    director = seriesInfo.info?.director ?: existingSeries.director,
                    backdropUrl = seriesInfo.info?.backdropPath?.firstOrNull() ?: existingSeries.backdropUrl
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
        val playlist = database.playlistDao().getPlaylistById(playlistId)
        playlist?.let { database.playlistDao().delete(it) }
    }

    suspend fun toggleFavourite(channel: ChannelEntity) {
        database.channelDao().update(channel.copy(isFavourite = !channel.isFavourite))
    }

    suspend fun fetchAndStoreEPG(host: String, username: String, password: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("EPG_DEBUG", "=== EPG FETCH START ===")
                val xmltvUrl = "$host/xmltv.php?username=$username&password=$password"

                val retrofit = Retrofit.Builder()
                    .baseUrl(host)
                    .client(
                        OkHttpClient.Builder()
                            .readTimeout(60, TimeUnit.SECONDS)
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .build()
                    )
                    .build()

                val response = retrofit.create(XmltvService::class.java).getXMLTV(xmltvUrl)
                val inputStream = response.byteStream()

                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")

                val windowStart = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)  // 7 days back
                val windowEnd   = System.currentTimeMillis() + (2 * 24 * 60 * 60 * 1000L)   // 2 days ahead

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
                                        if (programs.size >= 500) {
                                            database.programDao().insertAll(programs)
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
                inputStream.close()
                android.util.Log.d("EPG_DEBUG", "=== EPG FETCH COMPLETE — $programCount programs ===")

            } catch (e: Exception) {
                android.util.Log.e("EPG_DEBUG", "=== EPG FETCH FAILED ===", e)
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
        return getAllPlaylists().flatMapLatest { playlists ->
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
        database.channelDao().getAllChannels()

    suspend fun preloadChannelIcons(context: android.content.Context) {
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                val channels = database.channelDao().getChannelIconUrls()
                android.util.Log.d("IconCache", "Preloading ${channels.size} channel icons")

                val batches = channels.chunked(10)
                for ((batchIndex, batch) in batches.withIndex()) {
                    for (channel in batch) {
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .memoryCacheKey(channel.logoUrl)
                            .diskCacheKey(channel.logoUrl)
                            .build()
                        imageLoader.enqueue(request)
                    }
                    kotlinx.coroutines.delay(500)
                    if ((batchIndex + 1) % 5 == 0) {
                        System.gc()
                        kotlinx.coroutines.delay(1_000)
                    }
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
                    releaseDate = movieInfo.info?.releaseDate ?: movieInfo.movieData?.year ?: existingMovie.releaseDate,
                    backdropUrl = movieInfo.info?.backdropPath?.firstOrNull() ?: existingMovie.backdropUrl
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
                database.programDao().deleteAllPrograms()
                fetchAndStoreEPG(host, username, password)

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

    fun searchPrograms(query: String): Flow<List<ProgramEntity>> =
        database.programDao().searchPrograms(query)

    fun searchMovies(query: String): Flow<List<MovieEntity>> =
        database.movieDao().searchMovies(query)

    fun searchSeries(query: String): Flow<List<SeriesEntity>> =
        database.seriesDao().searchSeries(query)

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

    suspend fun getEpisodeById(episodeId: String): EpisodeEntity? =
        database.seriesDao().getEpisodeByIdOnce(episodeId)

    suspend fun getSeriesById(seriesId: String): SeriesEntity? =
        database.seriesDao().getById(seriesId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecentlyWatched(): Flow<List<RecentlyWatchedEntity>> =
        profileManager.activeProfile.flatMapLatest { profile ->
            database.recentlyWatchedDao().getRecentlyWatched(profile?.id ?: "default")
        }

    suspend fun recordRecentlyWatchedChannel(channel: ChannelEntity) {
        withContext(Dispatchers.IO) {
            database.recentlyWatchedDao().insert(
                RecentlyWatchedEntity(
                    id        = channel.id,
                    profileId = activeProfileId,
                    type      = RecentlyWatchedType.CHANNEL,
                    name      = channel.name,
                    subtitle  = null,
                    streamUrl = channel.streamUrl,
                    logoUrl   = channel.logoUrl
                )
            )
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
        }
    }

    suspend fun recordRecentlyWatchedMovie(movie: MovieEntity) {
        withContext(Dispatchers.IO) {
            database.recentlyWatchedDao().insert(
                RecentlyWatchedEntity(
                    id        = movie.id,
                    profileId = activeProfileId,
                    type      = RecentlyWatchedType.MOVIE,
                    name      = movie.name,
                    subtitle  = null,
                    streamUrl = movie.streamUrl,
                    logoUrl   = movie.posterUrl,
                    movieId   = movie.id
                )
            )
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
        }
    }

    suspend fun recordRecentlyWatchedEpisode(series: SeriesEntity, episode: EpisodeEntity) {
        withContext(Dispatchers.IO) {
            database.recentlyWatchedDao().insert(
                RecentlyWatchedEntity(
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
            )
            database.recentlyWatchedDao().trimToLimit(activeProfileId)
        }
    }

    suspend fun deleteRecentlyWatched(id: String) {
        withContext(Dispatchers.IO) { database.recentlyWatchedDao().deleteById(id, activeProfileId) }
    }

    suspend fun clearRecentlyWatched() {
        withContext(Dispatchers.IO) { database.recentlyWatchedDao().clearAll(activeProfileId) }
    }

    fun getChannelCategories(): Flow<List<String>> =
        database.channelDao().getAllCategories()

    fun getAllWatchedEpisodeCounts(): Flow<Map<String, Int>> =
        database.seriesDao().getAllWatchedEpisodeCounts()
            .map { rows -> rows.associate { it.seriesId to it.count } }

    fun getMovieGridItems(playlistId: String): Flow<List<MovieGridItem>> =
        database.movieDao().getMovieGridItems(playlistId)

    suspend fun getAllMoviesWithProgress() = database.movieDao().getAllWithProgress()
    suspend fun getAllEpisodesWithProgress() = database.seriesDao().getAllEpisodesWithProgress()

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
}