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

    @Query("SELECT * FROM channels ORDER BY sortIndex ASC")
    suspend fun getAllChannelsOnce(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE streamUrl = :streamUrl LIMIT 1")
    fun getChannelByStreamUrl(streamUrl: String): Flow<ChannelEntity?>
    @Query("SELECT groupTitle FROM channels WHERE groupTitle IS NOT NULL AND groupTitle != '' GROUP BY groupTitle ORDER BY MIN(sortIndex) ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT groupTitle, COUNT(*) as count FROM channels WHERE groupTitle IS NOT NULL AND groupTitle != '' GROUP BY groupTitle")
    fun getChannelCountsByCategory(): Flow<List<CategoryChannelCount>>

    @Query("SELECT id, logoUrl FROM channels WHERE logoUrl IS NOT NULL AND logoUrl != ''")
    suspend fun getChannelIconUrls(): List<ChannelIconProjection>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE epgChannelId = :epgChannelId LIMIT 1")
    suspend fun getChannelByEpgId(epgChannelId: String): ChannelEntity?
}

data class CategoryChannelCount(val groupTitle: String, val count: Int)