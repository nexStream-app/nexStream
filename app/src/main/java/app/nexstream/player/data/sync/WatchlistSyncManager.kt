package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.dao.WatchlistDao
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.AddChannelRequest
import app.nexstream.player.data.remote.AddSeriesRequest
import app.nexstream.player.data.remote.AddVodRequest
import app.nexstream.player.data.remote.DeleteRequest
import app.nexstream.player.data.remote.WatchlistApiService
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.getCloudSyncEnabledFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistSyncManager @Inject constructor(
    private val api: WatchlistApiService,
    private val dao: WatchlistDao,
    private val licencePreferences: LicencePreferences,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context,
) {
    private val tag = "WatchlistSync"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fire-and-forget push variants — survive ViewModel teardown because they run on
    // the singleton's own scope rather than the calling ViewModel's viewModelScope.
    fun enqueuePushAdd(item: WatchlistEntity) { syncScope.launch { pushAdd(item) } }
    fun enqueuePushRemove(id: String, type: WatchlistType, profileId: String) {
        syncScope.launch { pushRemove(id, type, profileId) }
    }

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey() ?: return null
        return "Bearer $key"
    }

    private fun keySource(): String = when {
        licencePreferences.getLicenceKey() != null -> "LICENCE"
        licencePreferences.getTrialSyncKey() != null -> "TRIAL"
        else -> "NONE"
    }

    private fun maskedKey(): String {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey() ?: return "<null>"
        return if (key.length > 8) "${key.take(4)}…${key.takeLast(4)}" else "***"
    }

    private fun deviceId(): String = licencePreferences.getOrCreateStableDeviceId()

    private fun logContext(profileId: String? = null) {
        val cloudEnabled = runCatching {
            kotlinx.coroutines.runBlocking { context.getCloudSyncEnabledFlow().first() }
        }.getOrDefault(false)
        Log.i(tag, "=== SYNC CONTEXT ===")
        Log.i(tag, "  device      : ${deviceId()}")
        Log.i(tag, "  keySource   : ${keySource()}")
        Log.i(tag, "  key(masked) : ${maskedKey()}")
        if (profileId != null) Log.i(tag, "  profileId   : $profileId")
        Log.i(tag, "  cloudSync   : $cloudEnabled")
    }

    // Push all local watchlist items to the server. Called on startup to ensure
    // the server is up-to-date with whatever is stored locally on this device.
    suspend fun pushAllToServer() = withContext(Dispatchers.IO) {
        Log.i(tag, "pushAllToServer: starting")
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) { Log.w(tag, "pushAllToServer: cloud sync DISABLED — skipping"); return@withContext }
        val auth = authHeader()
        if (auth == null) { Log.e(tag, "pushAllToServer: authHeader null (keySource=${keySource()}) — skipping"); return@withContext }
        Log.i(tag, "pushAllToServer: device=${deviceId()}, key=${maskedKey()}, source=${keySource()}")
        try {
            val items = dao.getAllItemsSuspend()
            Log.i(tag, "pushAllToServer: ${items.size} local items to push")
            items.groupBy { it.type }.forEach { (type, group) ->
                Log.i(tag, "  → $type: ${group.size} items (profileIds: ${group.map { it.profileId }.distinct()})")
            }
            items.forEach { item -> pushAdd(item) }
            Log.i(tag, "pushAllToServer: done")
        } catch (e: Exception) {
            Log.e(tag, "pushAllToServer: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    suspend fun syncFromServer(profileId: String) = withContext(Dispatchers.IO) {
        Log.i(tag, "syncFromServer: starting for profileId=$profileId")
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) { Log.w(tag, "syncFromServer: cloud sync DISABLED — skipping"); return@withContext }
        val auth = authHeader()
        if (auth == null) { Log.e(tag, "syncFromServer: authHeader null (keySource=${keySource()}) — skipping"); return@withContext }
        Log.i(tag, "syncFromServer: device=${deviceId()}, key=${maskedKey()}, source=${keySource()}, profile=$profileId")
        try {
            listOf("channels", "vod", "series").forEach { type ->
                val watchlistType = when (type) {
                    "channels" -> WatchlistType.CHANNEL
                    "vod"      -> WatchlistType.MOVIE
                    "series"   -> WatchlistType.SERIES
                    else       -> return@forEach
                }
                val idField = when (type) {
                    "channels" -> "channel_id"
                    "vod"      -> "vod_id"
                    "series"   -> "series_id"
                    else       -> return@forEach
                }
                Log.d(tag, "syncFromServer: GETting $type …")
                val response = api.getList(auth, type, profileId)
                Log.i(tag, "syncFromServer: $type → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val items = response.body()?.items ?: run {
                        Log.w(tag, "syncFromServer: $type body null or empty items list"); return@forEach
                    }
                    Log.i(tag, "syncFromServer: $type → ${items.size} items from server")
                    var saved = 0; var skipped = 0

                    // Fetch local items first so we can deduplicate by name (IDs differ across devices)
                    val localItems  = dao.getItemsSuspendByProfileAndType(profileId, watchlistType)
                    val localById   = localItems.associateBy { it.id }
                    val localByName = localItems.associateBy { it.name.trim().lowercase() }

                    // Map server items to entities
                    val serverEntities = items.mapNotNull { item ->
                        val itemProfileId = resolveProfileId(item["profile_id"], profileId)
                        mapServerItemToEntity(item, type, itemProfileId).also { entity ->
                            if (entity == null) Log.w(tag, "syncFromServer: $type — null entity for item=$item")
                        }
                    }

                    // Upsert, guarding against cross-device ID divergence causing duplicates
                    serverEntities.forEach { entity ->
                        val nameKey = entity.name.trim().lowercase()
                        if (localById.containsKey(entity.id) || !localByName.containsKey(nameKey)) {
                            dao.addToWatchlist(entity); saved++
                        } else {
                            Log.d(tag, "syncFromServer: $type — skipping '${entity.name}' (exists locally under different ID)")
                            skipped++
                        }
                    }

                    // Remove local items absent from server by both ID and name
                    val serverIds   = serverEntities.map { it.id }.toSet()
                    val serverNames = serverEntities.map { it.name.trim().lowercase() }.toSet()
                    var deleted = 0
                    localItems.filter { local ->
                        local.id !in serverIds && local.name.trim().lowercase() !in serverNames
                    }.forEach { stale ->
                        dao.removeFromWatchlist(stale.id, stale.profileId)
                        deleted++
                    }
                    Log.i(tag, "syncFromServer: $type → saved=$saved, skipped=$skipped, deleted=$deleted")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "<no body>"
                    Log.e(tag, "syncFromServer: $type FAILED ${response.code()} — $errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "syncFromServer: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
        }
        Log.i(tag, "syncFromServer: complete")
    }

    suspend fun pushAdd(item: WatchlistEntity) = withContext(Dispatchers.IO) {
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) return@withContext
        val auth = authHeader() ?: run {
            Log.e(tag, "pushAdd: authHeader null — cannot push ${item.type} ${item.id}")
            return@withContext
        }
        try {
            val response = when (item.type) {
                WatchlistType.CHANNEL -> api.addChannel(
                    auth = auth,
                    type = "channels",
                    body = AddChannelRequest(
                        channel_id   = item.id,
                        channel_name = item.name,
                        stream_url   = item.streamUrl ?: "",
                        logo_url     = item.posterUrl,
                        category     = null,
                        profile_id   = item.profileId
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
                        profile_id = item.profileId
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
                        profile_id = item.profileId
                    )
                )
                WatchlistType.MUSIC -> return@withContext
            }
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "<no body>"
                Log.e(tag, "pushAdd: ${item.type} ${item.id} → HTTP ${response.code()} FAILED — $errorBody")
            }
        } catch (e: Exception) {
            Log.e(tag, "pushAdd: EXCEPTION pushing ${item.type} ${item.id}: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    suspend fun pushRemove(id: String, type: WatchlistType, profileId: String) = withContext(Dispatchers.IO) {
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) return@withContext
        val auth = authHeader() ?: run {
            Log.e(tag, "pushRemove: authHeader null — cannot remove $type $id")
            return@withContext
        }
        val apiType = when (type) {
            WatchlistType.CHANNEL -> "channels"
            WatchlistType.MOVIE   -> "vod"
            WatchlistType.SERIES  -> "series"
            WatchlistType.MUSIC   -> return@withContext
        }
        Log.d(tag, "pushRemove: $type id=$id profile=$profileId")
        try {
            val response = api.removeItem(
                auth = auth,
                type = apiType,
                body = DeleteRequest(item_id = id, profile_id = profileId)
            )
            if (response.isSuccessful) {
                Log.d(tag, "pushRemove: $type $id → HTTP ${response.code()} OK")
            } else {
                val errorBody = response.errorBody()?.string() ?: "<no body>"
                Log.e(tag, "pushRemove: $type $id → HTTP ${response.code()} FAILED — $errorBody")
            }
        } catch (e: Exception) {
            Log.e(tag, "pushRemove: EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun resolveProfileId(serverProfileId: String?, fallbackProfileId: String): String {
        if (serverProfileId.isNullOrBlank() || serverProfileId == "default") return fallbackProfileId
        val knownIds = profileManager.profiles.value.map { it.id }.toSet()
        return if (serverProfileId in knownIds) serverProfileId else fallbackProfileId
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
