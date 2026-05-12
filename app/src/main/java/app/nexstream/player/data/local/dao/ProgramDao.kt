package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.RoomWarnings

@Dao
interface ProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("SELECT * FROM programs WHERE channelId = :channelId AND endTime > :currentTime ORDER BY startTime ASC LIMIT 100")
    fun getProgramsForChannel(channelId: String, currentTime: Long): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    fun getCurrentProgram(channelId: String, currentTime: Long): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    fun getNextProgram(channelId: String, currentTime: Long): Flow<ProgramEntity?>

    @Query("DELETE FROM programs WHERE endTime < :currentTime")
    suspend fun deleteOldPrograms(currentTime: Long)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Query("SELECT * FROM programs WHERE channelId IN (:channelIds) AND endTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getProgramsForChannelsInRange(channelIds: List<String>, startTime: Long, endTime: Long): Flow<List<ProgramEntity>>

    // ADD to ProgramDao:
    @Query("SELECT * FROM programs WHERE title LIKE '%' || :query || '%' ORDER BY startTime DESC LIMIT 50")
    fun searchPrograms(query: String): Flow<List<ProgramEntity>>

    @Query("DELETE FROM programs")
    suspend fun deleteAllPrograms()

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("""
    SELECT id, title, startTime, endTime, channelId, description 
    FROM programs 
    WHERE channelId = :channelId 
    AND startTime >= :startTime 
    AND startTime <= :endTime
    ORDER BY startTime DESC
""")
    fun getProgramsForChannelInRange(
        channelId: String,
        startTime: Long,
        endTime: Long
    ): Flow<List<ProgramEntity>>

}