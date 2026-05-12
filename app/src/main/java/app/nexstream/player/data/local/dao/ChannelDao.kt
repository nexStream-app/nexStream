package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow
import app.nexstream.player.data.local.entity.ChannelIconProjection

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavourite = 1 ORDER BY sortIndex ASC")
    fun getFavouriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY sortIndex ASC")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Update
    suspend fun update(channel: ChannelEntity)

    @Query("SELECT * FROM channels ORDER BY sortIndex ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE streamUrl = :streamUrl LIMIT 1")
    fun getChannelByStreamUrl(streamUrl: String): Flow<ChannelEntity?>
    @Query("SELECT DISTINCT groupTitle FROM channels WHERE groupTitle IS NOT NULL AND groupTitle != '' ORDER BY groupTitle ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT id, logoUrl FROM channels WHERE logoUrl IS NOT NULL AND logoUrl != ''")
    suspend fun getChannelIconUrls(): List<ChannelIconProjection>
}