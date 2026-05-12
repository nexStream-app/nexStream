package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyWatchedDao {

    @Query("SELECT * FROM recently_watched WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT 20")
    fun getRecentlyWatched(profileId: String): Flow<List<RecentlyWatchedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RecentlyWatchedEntity)

    @Query("DELETE FROM recently_watched WHERE id = :id AND profileId = :profileId")
    suspend fun deleteById(id: String, profileId: String)

    @Query("""
        DELETE FROM recently_watched 
        WHERE profileId = :profileId 
        AND id NOT IN (
            SELECT id FROM recently_watched 
            WHERE profileId = :profileId 
            ORDER BY watchedAt DESC LIMIT 20
        )
    """)
    suspend fun trimToLimit(profileId: String)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)
}