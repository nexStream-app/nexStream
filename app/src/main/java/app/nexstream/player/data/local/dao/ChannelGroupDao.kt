package app.nexstream.player.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.ChannelGroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelGroupDao {
    @Query("SELECT * FROM channel_groups WHERE profile_id = :profileId ORDER BY sort_order ASC")
    fun getGroupsForProfile(profileId: String): Flow<List<ChannelGroupEntity>>

    @Query("SELECT * FROM channel_groups WHERE profile_id = :profileId ORDER BY sort_order ASC")
    suspend fun getGroupsForProfileOnce(profileId: String): List<ChannelGroupEntity>

    @Query("SELECT * FROM channel_groups ORDER BY profile_id ASC, sort_order ASC")
    suspend fun getAllGroupsOnce(): List<ChannelGroupEntity>

    @Query("SELECT * FROM channel_group_members WHERE groupId = :groupId ORDER BY sort_order ASC")
    suspend fun getMembersForGroup(groupId: String): List<ChannelGroupMemberEntity>

    @Query("SELECT channelId FROM channel_group_members WHERE groupId = :groupId")
    suspend fun getChannelIdsForGroup(groupId: String): List<String>

    @Query("SELECT channelId FROM channel_group_members WHERE groupId IN (:groupIds)")
    suspend fun getChannelIdsForGroups(groupIds: List<String>): List<String>

    @Upsert
    suspend fun upsertGroup(group: ChannelGroupEntity)

    @Query("DELETE FROM channel_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM channel_group_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: String)

    @Upsert
    suspend fun upsertMembers(members: List<ChannelGroupMemberEntity>)

    @Query("DELETE FROM channel_group_members WHERE groupId = :groupId AND channelId = :channelId")
    suspend fun removeMember(groupId: String, channelId: String)

    @Query("DELETE FROM channel_groups WHERE profile_id = :profileId")
    suspend fun deleteGroupsForProfile(profileId: String)

    @Query("DELETE FROM channel_group_members WHERE groupId IN (SELECT id FROM channel_groups WHERE profile_id = :profileId)")
    suspend fun deleteMembersForProfile(profileId: String)

    @Query("DELETE FROM channel_group_members WHERE channelId NOT IN (SELECT id FROM channels)")
    suspend fun pruneStaleMembers()
}
