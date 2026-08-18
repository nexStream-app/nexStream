package app.nexstream.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "pin_hash")     val pinHash: String? = null,
    @ColumnInfo(name = "is_default")   val isDefault: Boolean = false,
    @ColumnInfo(name = "sort_order")   val sortOrder: Int = 0,
    @ColumnInfo(name = "updated_at")   val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_restricted")        val isRestricted: Boolean = false,
    @ColumnInfo(name = "max_age_rating")       val maxAgeRating: String? = null,
    @ColumnInfo(name = "allow_nr")             val allowNr: Boolean = true,
    @ColumnInfo(name = "sync_cloud_enabled")   val syncCloudEnabled: Boolean = true,
    @ColumnInfo(name = "sync_channel_folders") val syncChannelFolders: Boolean = true,
    @ColumnInfo(name = "sync_appearance")      val syncAppearance: Boolean = true,
    @ColumnInfo(name = "sync_player_settings") val syncPlayerSettings: Boolean = true,
)

@Entity(tableName = "profile_category_filters", primaryKeys = ["profileId", "categoryType", "categoryName"])
data class ProfileCategoryFilter(
    val profileId: String,
    val categoryType: String,
    val categoryName: String,
    val isAllowed: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)