package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_folders")
data class DeviceFolderEntity(
    @PrimaryKey val id: String,
    val path: String,
    val label: String,
    val includeSubfolders: Boolean,
    val createdAt: Long,
)
