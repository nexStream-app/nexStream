package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM reminders ORDER BY startTime ASC")
    fun getAll(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders WHERE id = :id")
    fun exists(id: String): Flow<Int>

    @Query("DELETE FROM reminders WHERE startTime < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}