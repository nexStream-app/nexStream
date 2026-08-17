package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.entity.WatchProgressEntity
import app.nexstream.player.data.remote.ProgressApiService
import app.nexstream.player.data.remote.ProgressBatchRequest
import app.nexstream.player.data.remote.ProgressItem
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.getCloudSyncEnabledFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressSyncManager @Inject constructor(
    private val api:                ProgressApiService,
    val progressRepository:         WatchProgressRepository,
    private val licencePreferences: LicencePreferences,
    @ApplicationContext private val context: Context,
) {
    private val tag = "ProgressSync"

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey()
        if (key == null) {
            Log.w(tag, "No licence key or trial sync key — skipping sync")
            return null
        }
        return "Bearer $key"
    }

    // ── Pull from server → write to Room ──────────────────────────────────────
    suspend fun pullFromServer(profileId: String) = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        Log.d(tag, "pullFromServer: profileId=$profileId")
        try {
            val response = api.getProgress(auth, profileId)
            Log.d(tag, "pullFromServer: HTTP ${response.code()} ${response.message()}")
            if (!response.isSuccessful) {
                Log.e(tag, "pullFromServer: failed body=${response.errorBody()?.string()}")
                return@withContext
            }
            val body = response.body()
            Log.d(tag, "pullFromServer: body=$body")
            val items = body?.items ?: run {
                Log.w(tag, "pullFromServer: null body or empty items")
                return@withContext
            }
            Log.d(tag, "pullFromServer: ${items.size} items received")

            val entities = items.mapNotNull { item ->
                val itemId     = item["item_id"]                     ?: return@mapNotNull null
                val itemType   = item["item_type"]                   ?: return@mapNotNull null
                val positionMs = item["position_ms"]?.toLongOrNull() ?: return@mapNotNull null
                WatchProgressEntity(
                    profileId  = profileId,
                    itemId     = itemId,
                    itemType   = itemType,
                    seriesId   = item["series_id"],
                    positionMs = positionMs,
                    updatedAt  = item["updated_at"]?.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                progressRepository.upsertAll(entities)
                Log.d(tag, "pullFromServer: saved ${entities.size} entries to Room")
            } else {
                Log.d(tag, "pullFromServer: no valid entries to save")
            }
        } catch (e: Exception) {
            Log.e(tag, "pullFromServer: exception", e)
        }
    }

    // ── Push all local progress → server ──────────────────────────────────────
    suspend fun pushAllToServer(profileId: String) = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        Log.d(tag, "pushAllToServer: profileId=$profileId")
        try {
            val movieItems   = progressRepository.getAllMovieProgress(profileId)
            val episodeItems = progressRepository.getAllEpisodeProgress(profileId)
            val allItems = (movieItems + episodeItems).map { entity ->
                ProgressItem(
                    item_id     = entity.itemId,
                    item_type   = entity.itemType,
                    series_id   = entity.seriesId,
                    position_ms = entity.positionMs,
                    duration_ms = 0L,
                    profile_id  = profileId,
                    updated_at  = entity.updatedAt
                )
            }
            Log.d(tag, "pushAllToServer: ${allItems.size} items to push")
            if (allItems.isEmpty()) return@withContext

            val response = api.pushBatch(
                auth   = auth,
                action = "batch",
                body   = ProgressBatchRequest(profile_id = profileId, items = allItems)
            )
            Log.d(tag, "pushAllToServer: HTTP ${response.code()} ${response.message()}")
            if (!response.isSuccessful) {
                Log.e(tag, "pushAllToServer: failed body=${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "pushAllToServer: exception", e)
        }
    }

    // ── Push single item on player close ──────────────────────────────────────
    suspend fun pushSingle(
        itemId:     String,
        itemType:   String,
        seriesId:   String?,
        positionMs: Long,
        durationMs: Long,
        profileId:  String
    ) = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        Log.d(tag, "pushSingle: $itemType $itemId pos=$positionMs profile=$profileId")
        try {
            val response = api.pushProgress(
                auth = auth,
                body = ProgressItem(
                    item_id     = itemId,
                    item_type   = itemType,
                    series_id   = seriesId,
                    position_ms = positionMs,
                    duration_ms = durationMs,
                    profile_id  = profileId,
                    updated_at  = System.currentTimeMillis()
                )
            )
            Log.d(tag, "pushSingle: HTTP ${response.code()} ${response.message()}")
            if (!response.isSuccessful) {
                Log.e(tag, "pushSingle: failed body=${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "pushSingle: exception", e)
        }
    }
}