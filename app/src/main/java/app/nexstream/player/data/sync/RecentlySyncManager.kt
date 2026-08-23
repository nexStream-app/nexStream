package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.dao.RecentlyWatchedDao
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.remote.RecentlyWatchedApiService
import app.nexstream.player.data.remote.RecentlyWatchedDeleteRequest
import app.nexstream.player.data.remote.RecentlyWatchedItemRequest
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
class RecentlySyncManager @Inject constructor(
    private val api: RecentlyWatchedApiService,
    private val dao: RecentlyWatchedDao,
    private val licencePreferences: LicencePreferences,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context,
) {
    private val tag = "RecentlySync"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueuePushAdd(entity: RecentlyWatchedEntity) { syncScope.launch { pushAdd(entity) } }
    fun enqueuePushRemove(id: String, profileId: String) { syncScope.launch { pushRemove(id, profileId) } }

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey() ?: return null
        return "Bearer $key"
    }

    suspend fun syncFromServer(profileId: String) = withContext(Dispatchers.IO) {
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) { Log.w(tag, "syncFromServer: cloud sync disabled"); return@withContext }
        val auth = authHeader() ?: run { Log.w(tag, "syncFromServer: no auth key"); return@withContext }
        try {
            val response = api.getList(auth, profileId)
            Log.i(tag, "syncFromServer: profile=$profileId HTTP ${response.code()}")
            if (!response.isSuccessful) return@withContext
            val items = response.body()?.items ?: return@withContext
            val localItems = dao.getRecentlyWatchedSuspend(profileId)
            val localById  = localItems.associateBy { it.id }

            // Upsert server items
            var saved = 0
            items.forEach { item ->
                val entity = mapToEntity(item, profileId) ?: return@forEach
                dao.insert(entity)
                saved++
            }

            // Remove local items absent from server
            val serverIds = items.mapNotNull { it["item_id"] }.toSet()
            var deleted = 0
            localItems.filter { it.id !in serverIds }.forEach { stale ->
                dao.deleteById(stale.id, profileId)
                deleted++
            }
            Log.i(tag, "syncFromServer: saved=$saved deleted=$deleted")
        } catch (e: Exception) {
            Log.e(tag, "syncFromServer: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private suspend fun pushAdd(entity: RecentlyWatchedEntity) = withContext(Dispatchers.IO) {
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            api.addItem(auth, RecentlyWatchedItemRequest(
                item_id    = entity.id,
                type       = entity.type.name,
                name       = entity.name,
                subtitle   = entity.subtitle,
                stream_url = entity.streamUrl,
                logo_url   = entity.logoUrl,
                series_id  = entity.seriesId,
                episode_id = entity.episodeId,
                movie_id   = entity.movieId,
                watched_at = entity.watchedAt,
                profile_id = entity.profileId
            ))
        } catch (e: Exception) {
            Log.e(tag, "pushAdd: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private suspend fun pushRemove(id: String, profileId: String) = withContext(Dispatchers.IO) {
        val cloudEnabled = context.getCloudSyncEnabledFlow().first()
        if (!cloudEnabled) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            api.removeItem(auth, RecentlyWatchedDeleteRequest(item_id = id, profile_id = profileId))
        } catch (e: Exception) {
            Log.e(tag, "pushRemove: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun mapToEntity(item: Map<String, String?>, profileId: String): RecentlyWatchedEntity? {
        val id   = item["item_id"] ?: return null
        val type = try { RecentlyWatchedType.valueOf(item["type"] ?: "") } catch (_: Exception) { return null }
        return RecentlyWatchedEntity(
            id        = id,
            profileId = profileId,
            type      = type,
            name      = item["name"] ?: return null,
            subtitle  = item["subtitle"],
            streamUrl = item["stream_url"] ?: "",
            logoUrl   = item["logo_url"],
            seriesId  = item["series_id"],
            episodeId = item["episode_id"],
            movieId   = item["movie_id"],
            watchedAt = item["watched_at"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }
}
