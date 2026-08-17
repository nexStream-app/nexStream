package app.nexstream.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channel_groups", indices = [Index("profile_id")])
data class ChannelGroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id")                          val profileId: String,
    val name: String,
    @ColumnInfo(name = "sort_order",  defaultValue = "0")     val sortOrder: Int = 0,
    @ColumnInfo(name = "is_expanded", defaultValue = "1")     val isExpanded: Boolean = true,
    @ColumnInfo(name = "updated_at",  defaultValue = "0")     val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "channel_group_members", primaryKeys = ["groupId", "channelId"])
data class ChannelGroupMemberEntity(
    val groupId: String,
    val channelId: String,
    val channelName: String,
    val logoUrl: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)
