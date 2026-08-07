package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getAllWatchlistItems(profileId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND type = :type ORDER BY addedAt DESC")
    fun getWatchlistByType(profileId: String, type: WatchlistType): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id AND profileId = :profileId)")
    fun isInWatchlist(id: String, profileId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id AND profileId = :profileId")
    suspend fun removeFromWatchlist(id: String, profileId: String)

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getItemsByProfileId(profileId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun getAllItemsSuspend(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND type = :type ORDER BY addedAt DESC")
    suspend fun getItemsSuspendByProfileAndType(profileId: String, type: WatchlistType): List<WatchlistEntity>

    @Query("DELETE FROM watchlist WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = :type")
    suspend fun clearByType(profileId: String, type: WatchlistType)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'CHANNEL' AND (SELECT COUNT(*) FROM channels) > 0 AND id NOT IN (SELECT id FROM channels)")
    suspend fun pruneStaleChannels(profileId: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'MOVIE' AND (SELECT COUNT(*) FROM movies) > 0 AND id NOT IN (SELECT id FROM movies)")
    suspend fun pruneStaleMovies(profileId: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'SERIES' AND (SELECT COUNT(*) FROM series) > 0 AND id NOT IN (SELECT id FROM series)")
    suspend fun pruneStaleSeries(profileId: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'CHANNEL' AND id IN (SELECT id FROM channels WHERE groupTitle IN (:blockedCategories))")
    suspend fun pruneBlockedChannels(profileId: String, blockedCategories: List<String>)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'MOVIE' AND id IN (SELECT id FROM movies WHERE certification IN (:blockedCerts))")
    suspend fun pruneAgeRestrictedMovies(profileId: String, blockedCerts: List<String>)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = 'SERIES' AND id IN (SELECT id FROM series WHERE certification IN (:blockedCerts))")
    suspend fun pruneAgeRestrictedSeries(profileId: String, blockedCerts: List<String>)
}