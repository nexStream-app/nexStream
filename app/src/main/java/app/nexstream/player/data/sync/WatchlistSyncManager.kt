package app.nexstream.player.data.sync

import android.util.Log
import app.nexstream.player.data.local.dao.WatchlistDao
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.remote.AddChannelRequest
import app.nexstream.player.data.remote.AddSeriesRequest
import app.nexstream.player.data.remote.AddVodRequest
import app.nexstream.player.data.remote.DeleteRequest
import app.nexstream.player.data.remote.WatchlistApiService
import app.nexstream.player.license.LicencePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistSyncManager @Inject constructor(
    private val api: WatchlistApiService,
    private val dao: WatchlistDao,
    private val licencePreferences: LicencePreferences
) {
    private val tag = "WatchlistSync"

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: return null
        return "Bearer $key"
    }

    suspend fun syncFromServer(profileId: String) = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext
        try {
            listOf("channels", "vod", "series").forEach { type ->
                val response = api.getList(auth, type, profileId) // ADD profileId
                if (response.isSuccessful) {
                    val items = response.body()?.items ?: return@forEach
                    items.forEach { item ->
                        val entity = mapServerItemToEntity(item, type, profileId) ?: return@forEach
                        dao.addToWatchlist(entity)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Sync from server failed", e)
        }
    }

    suspend fun pushAdd(item: WatchlistEntity) = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext
        try {
            when (item.type) {
                WatchlistType.CHANNEL -> api.addChannel(
                    auth = auth,
                    type = "channels",
                    body = AddChannelRequest(
                        channel_id   = item.id,
                        channel_name = item.name,
                        stream_url   = item.streamUrl ?: "",
                        logo_url     = item.posterUrl,
                        category     = null,
                        profile_id   = item.profileId  // ADD THIS
                    )
                )
                WatchlistType.MOVIE -> api.addVod(
                    auth = auth,
                    type = "vod",
                    body = AddVodRequest(
                        vod_id     = item.id,
                        title      = item.name,
                        stream_url = item.streamUrl ?: "",
                        poster_url = item.posterUrl,
                        category   = null,
                        profile_id = item.profileId  // ADD THIS
                    )
                )
                WatchlistType.SERIES -> api.addSeries(
                    auth = auth,
                    type = "series",
                    body = AddSeriesRequest(
                        series_id  = item.id,
                        title      = item.name,
                        poster_url = item.posterUrl,
                        category   = null,
                        profile_id = item.profileId  // ADD THIS
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Push add failed for ${item.id}", e)
        }
    }

    suspend fun pushRemove(id: String, type: WatchlistType, profileId: String) = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext
        val apiType = when (type) {
            WatchlistType.CHANNEL -> "channels"
            WatchlistType.MOVIE   -> "vod"
            WatchlistType.SERIES  -> "series"
        }
        try {
            api.removeItem(
                auth    = auth,
                type    = apiType,
                body    = DeleteRequest(
                    item_id    = id,
                    profile_id = profileId  // ADD THIS
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Push remove failed for $id", e)
        }
    }

    private fun mapServerItemToEntity(
        item: Map<String, String?>,
        type: String,
        profileId: String
    ): WatchlistEntity? {
        return when (type) {
            "channels" -> WatchlistEntity(
                id        = item["channel_id"] ?: return null,
                profileId = profileId,
                type      = WatchlistType.CHANNEL,
                name      = item["channel_name"] ?: return null,
                streamUrl = item["stream_url"],
                posterUrl = item["logo_url"],
                addedAt   = item["added_at"]?.toLongOrNull() ?: System.currentTimeMillis()
            )
            "vod" -> WatchlistEntity(
                id        = item["vod_id"] ?: return null,
                profileId = profileId,
                type      = WatchlistType.MOVIE,
                name      = item["title"] ?: return null,
                streamUrl = item["stream_url"],
                posterUrl = item["poster_url"],
                addedAt   = item["added_at"]?.toLongOrNull() ?: System.currentTimeMillis()
            )
            "series" -> WatchlistEntity(
                id        = item["series_id"] ?: return null,
                profileId = profileId,
                type      = WatchlistType.SERIES,
                name      = item["title"] ?: return null,
                streamUrl = null,
                posterUrl = item["poster_url"],
                addedAt   = item["added_at"]?.toLongOrNull() ?: System.currentTimeMillis()
            )
            else -> null
        }
    }
}