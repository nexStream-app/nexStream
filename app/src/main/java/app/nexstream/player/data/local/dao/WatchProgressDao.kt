package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WatchProgressEntity>)

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun getProgress(profileId: String, itemId: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND itemType = 'movie' AND positionMs > 0")
    suspend fun getAllMovieProgress(profileId: String): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND itemType = 'episode' AND positionMs > 0")
    suspend fun getAllEpisodeProgress(profileId: String): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND seriesId = :seriesId AND positionMs > 0")
    fun getEpisodeProgressForSeries(profileId: String, seriesId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT COUNT(*) FROM watch_progress WHERE profileId = :profileId AND seriesId = :seriesId AND positionMs > 0")
    fun getWatchedEpisodeCount(profileId: String, seriesId: String): Flow<Int>

    // Map of seriesId → count for all series at once (for grid badges)
    @Query("""
        SELECT seriesId, COUNT(*) as count 
        FROM watch_progress 
        WHERE profileId = :profileId AND itemType = 'episode' AND positionMs > 0 AND seriesId IS NOT NULL
        GROUP BY seriesId
    """)
    fun getAllWatchedCountsForProfile(profileId: String): Flow<List<WatchedCountRow>>

    // All item IDs with progress > 0 for a profile (for continue badge on movie grid)
    @Query("SELECT itemId FROM watch_progress WHERE profileId = :profileId AND positionMs > 0")
    fun getAllProgressItemIds(profileId: String): Flow<List<String>>

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun clearProgress(profileId: String, itemId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun clearAllForProfile(profileId: String)

    data class WatchedCountRow(val seriesId: String, val count: Int)
}