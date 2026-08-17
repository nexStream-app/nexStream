package app.nexstream.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ChannelDao
import app.nexstream.player.data.local.dao.ChannelGroupDao
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.ChannelGroupMemberEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ChannelGroupSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChannelGroupsViewModel @Inject constructor(
    private val channelGroupDao: ChannelGroupDao,
    private val channelDao: ChannelDao,
    private val profileManager: ProfileManager,
    private val channelGroupSyncManager: ChannelGroupSyncManager
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<ChannelGroupEntity>> = profileManager.activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> channelGroupDao.getGroupsForProfile(profile.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createGroup(name: String) = viewModelScope.launch {
        val profileId = profileManager.activeProfile.value?.id ?: return@launch
        val sortOrder = groups.value.size
        withContext(Dispatchers.IO) {
            channelGroupDao.upsertGroup(
                ChannelGroupEntity(
                    id        = UUID.randomUUID().toString(),
                    profileId = profileId,
                    name      = name,
                    sortOrder = sortOrder
                )
            )
        }
        channelGroupSyncManager.enqueuePush()
    }

    fun renameGroup(group: ChannelGroupEntity, newName: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            channelGroupDao.upsertGroup(group.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
        channelGroupSyncManager.enqueuePush()
    }

    fun deleteGroup(group: ChannelGroupEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            channelGroupDao.deleteMembersForGroup(group.id)
            channelGroupDao.deleteGroup(group.id)
        }
        channelGroupSyncManager.enqueueDelete(group.id)
    }

    suspend fun getMembersForGroup(groupId: String): List<ChannelGroupMemberEntity> =
        withContext(Dispatchers.IO) { channelGroupDao.getMembersForGroup(groupId) }

    suspend fun getAllChannels(): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val blocked = profileManager.blockedTvCategories.value
        channelDao.getAllChannelsOnce().filter { ch ->
            ch.groupTitle == null || ch.groupTitle !in blocked
        }
    }

    fun addChannelToGroup(group: ChannelGroupEntity, channel: ChannelEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            channelGroupDao.upsertMembers(listOf(
                ChannelGroupMemberEntity(
                    groupId     = group.id,
                    channelId   = channel.id,
                    channelName = channel.name,
                    logoUrl     = channel.logoUrl,
                    sortOrder   = 0
                )
            ))
        }
        channelGroupSyncManager.enqueuePush()
    }

    fun removeChannelFromGroup(groupId: String, channelId: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { channelGroupDao.removeMember(groupId, channelId) }
        channelGroupSyncManager.enqueuePush()
    }
}
