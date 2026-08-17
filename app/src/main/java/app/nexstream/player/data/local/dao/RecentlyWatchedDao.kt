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

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = :type")
    suspend fun clearByType(profileId: String, type: app.nexstream.player.data.local.entity.RecentlyWatchedType)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'CHANNEL' AND (SELECT COUNT(*) FROM channels) > 0 AND id NOT IN (SELECT id FROM channels)")
    suspend fun pruneStaleChannels(profileId: String)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'MOVIE' AND (SELECT COUNT(*) FROM movies) > 0 AND id NOT IN (SELECT id FROM movies)")
    suspend fun pruneStaleMovies(profileId: String)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'EPISODE' AND (SELECT COUNT(*) FROM series) > 0 AND seriesId NOT IN (SELECT id FROM series)")
    suspend fun pruneStaleEpisodes(profileId: String)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'CHANNEL' AND id IN (SELECT id FROM channels WHERE groupTitle IN (:blockedCategories))")
    suspend fun pruneBlockedChannels(profileId: String, blockedCategories: List<String>)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'MOVIE' AND id IN (SELECT id FROM movies WHERE certification IN (:blockedCerts))")
    suspend fun pruneAgeRestrictedMovies(profileId: String, blockedCerts: List<String>)

    @Query("DELETE FROM recently_watched WHERE profileId = :profileId AND type = 'EPISODE' AND seriesId IN (SELECT id FROM series WHERE certification IN (:blockedCerts))")
    suspend fun pruneAgeRestrictedEpisodes(profileId: String, blockedCerts: List<String>)
}