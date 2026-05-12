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
}