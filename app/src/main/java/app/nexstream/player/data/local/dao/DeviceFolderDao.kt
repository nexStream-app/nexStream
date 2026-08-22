package app.nexstream.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.nexstream.player.data.local.entity.DeviceFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceFolderDao {
    @Query("SELECT * FROM device_folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<DeviceFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: DeviceFolderEntity)

    @Query("DELETE FROM device_folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM device_folders")
    fun getFolderCount(): Flow<Int>
}
