package app.nexstream.player.data.repository

import app.nexstream.player.data.local.dao.WatchProgressDao
import app.nexstream.player.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ── Add this to PlaylistRepository or use standalone ─────────────────────────
// Inject WatchProgressDao and expose these methods.
// All progress is now per-profile via WatchProgressDao.
// The old saveMoviePlaybackPosition / saveEpisodePlaybackPosition methods on
// PlaylistRepository should delegate here instead of writing to MovieEntity directly.

@Singleton
class WatchProgressRepository @Inject constructor(
    private val dao: WatchProgressDao
) {

    // ── Save / clear ──────────────────────────────────────────────────────────

    suspend fun saveMovieProgress(profileId: String, movieId: String, positionMs: Long, durationMs: Long = 0L) {
        dao.upsert(WatchProgressEntity(
            profileId  = profileId,
            itemId     = movieId,
            itemType   = "movie",
            seriesId   = null,
            positionMs = positionMs,
            durationMs = durationMs
        ))
    }

    suspend fun saveEpisodeProgress(
        profileId:  String,
        episodeId:  String,
        seriesId:   String?,
        positionMs: Long,
        durationMs: Long = 0L
    ) {
        dao.upsert(WatchProgressEntity(
            profileId  = profileId,
            itemId     = episodeId,
            itemType   = "episode",
            seriesId   = seriesId,
            positionMs = positionMs,
            durationMs = durationMs
        ))
    }

    suspend fun clearMovieProgress(profileId: String, movieId: String) {
        dao.clearProgress(profileId, movieId)
    }

    suspend fun clearEpisodeProgress(profileId: String, episodeId: String) {
        dao.clearProgress(profileId, episodeId)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getMoviePosition(profileId: String, movieId: String): Long =
        dao.getProgress(profileId, movieId)?.positionMs ?: 0L

    suspend fun getEpisodePosition(profileId: String, episodeId: String): Long =
        dao.getProgress(profileId, episodeId)?.positionMs ?: 0L

    // ── CatchUp progress ──────────────────────────────────────────────────────
    // Uses the catchup stream URL as itemId with itemType = "catchup"

    suspend fun saveCatchupProgress(profileId: String, url: String, positionMs: Long, durationMs: Long = 0L) {
        dao.upsert(WatchProgressEntity(
            profileId  = profileId,
            itemId     = url,
            itemType   = "catchup",
            seriesId   = null,
            positionMs = positionMs,
            durationMs = durationMs
        ))
    }

    suspend fun clearCatchupProgress(profileId: String, url: String) {
        dao.clearProgress(profileId, url)
    }

    suspend fun getCatchupPosition(profileId: String, url: String): Long =
        dao.getProgress(profileId, url)?.positionMs ?: 0L

    // Set of itemIds that have progress > 0 — used for continue badge on grid
    fun getProgressItemIds(profileId: String): Flow<Set<String>> =
        dao.getAllProgressItemIds(profileId).map { it.toSet() }

    // Map of itemId → progress fraction (0f..0.97f) — used for progress bars on cards
    fun getProgressFractions(profileId: String): Flow<Map<String, Float>> =
        dao.getAllProgressWithDuration(profileId).map { items ->
            items.associate { it.itemId to (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 0.97f) }
        }

    // Watched episode counts per series — used for series grid badge
    fun getWatchedCountsForProfile(profileId: String): Flow<Map<String, Int>> =
        dao.getAllWatchedCountsForProfile(profileId).map { rows ->
            rows.associate { it.seriesId to it.count }
        }

    // Episode progress for a specific series — used in SeriesDetailsDialog
    fun getEpisodeProgressForSeries(
        profileId: String,
        seriesId: String
    ): Flow<Map<String, Long>> =
        dao.getEpisodeProgressForSeries(profileId, seriesId).map { rows ->
            rows.associate { it.itemId to it.positionMs }
        }

    // All progress for batch server push
    suspend fun getAllMovieProgress(profileId: String): List<WatchProgressEntity> =
        dao.getAllMovieProgress(profileId)

    suspend fun getAllEpisodeProgress(profileId: String): List<WatchProgressEntity> =
        dao.getAllEpisodeProgress(profileId)

    // Batch upsert — used by server pull
    suspend fun upsertAll(items: List<WatchProgressEntity>) =
        dao.upsertAll(items)
}