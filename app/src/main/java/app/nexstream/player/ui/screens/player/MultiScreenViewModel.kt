package app.nexstream.player.ui.screens.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MultiScreenSlot(
    val channel: ChannelEntity? = null,
    val isMuted: Boolean = true,
    val currentProgramme: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MultiScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val profileManager: ProfileManager,
) : ViewModel() {

    private val _slots = MutableStateFlow<List<MultiScreenSlot>>(emptyList())
    val slots: StateFlow<List<MultiScreenSlot>> = _slots.asStateFlow()

    // Which slot is being reconfigured (null = none, index = that slot)
    private val _configuringSlotIndex = MutableStateFlow<Int?>(null)
    val configuringSlotIndex: StateFlow<Int?> = _configuringSlotIndex.asStateFlow()

    // Channel selector state
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _showFavourites = MutableStateFlow(false)
    val showFavourites: StateFlow<Boolean> = _showFavourites.asStateFlow()

    val categories: StateFlow<List<String>> = repository.getChannelCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeProfileId: String
        get() = profileManager.activeProfile.value?.id ?: "default"

    val favouriteChannelIds: StateFlow<Set<String>> = profileManager.activeProfile
        .flatMapLatest { profile ->
            repository.getWatchlistItemsForProfile(profile?.id ?: "default")
                .map { items -> items.filter { it.type == WatchlistType.CHANNEL }.map { it.id }.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val displayedChannels: StateFlow<List<ChannelEntity>> = combine(
        repository.getAllChannels(),
        _selectedCategory,
        _showFavourites,
        favouriteChannelIds,
    ) { channels, cat, showFavs, favIds ->
        when {
            showFavs -> channels.filter { it.id in favIds }
            cat != null -> channels.filter { it.groupTitle == cat }
            else -> channels
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initWithChannel(channel: ChannelEntity) {
        _slots.value = listOf(MultiScreenSlot(channel = channel, isMuted = false))
        fetchProgramme(0, channel)
    }

    fun initWithChannelUrl(url: String, name: String) {
        viewModelScope.launch {
            val channel = repository.getAllChannels().first().firstOrNull { it.streamUrl == url }
                ?: app.nexstream.player.data.local.entity.ChannelEntity(
                    id = url, name = name, streamUrl = url,
                    logoUrl = null, groupTitle = null, epgChannelId = null, playlistId = "",
                )
            _slots.value = listOf(MultiScreenSlot(channel = channel, isMuted = false))
            fetchProgramme(0, channel)
        }
    }

    fun addSlot() {
        if (_slots.value.size >= 4) return
        val newIndex = _slots.value.size
        _slots.update { it + MultiScreenSlot() }
        _configuringSlotIndex.value = newIndex
    }

    fun openConfigureSlot(index: Int) {
        _configuringSlotIndex.value = index
        _selectedCategory.value = null
        _showFavourites.value = false
    }

    fun closeConfigureSlot() {
        _configuringSlotIndex.value = null
    }

    fun removeSlot(index: Int) {
        _slots.update { slots ->
            slots.filterIndexed { i, _ -> i != index }
        }
        _configuringSlotIndex.value = null
    }

    fun setSlotChannel(slotIndex: Int, channel: ChannelEntity) {
        _slots.update { slots ->
            slots.mapIndexed { i, slot ->
                if (i == slotIndex) slot.copy(channel = channel, currentProgramme = null) else slot
            }
        }
        _configuringSlotIndex.value = null
        fetchProgramme(slotIndex, channel)
    }

    fun toggleMute(index: Int) {
        _slots.update { slots ->
            slots.mapIndexed { i, slot ->
                if (i == index) slot.copy(isMuted = !slot.isMuted) else slot
            }
        }
    }

    fun makeMainSlot(index: Int) {
        if (index == 0 || index >= _slots.value.size) return
        _slots.update { slots ->
            val list = slots.toMutableList()
            val tmp = list[0]; list[0] = list[index]; list[index] = tmp
            list
        }
    }

    fun selectCategory(cat: String?) {
        _selectedCategory.value = cat
        _showFavourites.value = false
    }

    fun toggleFavourites() {
        _showFavourites.value = !_showFavourites.value
        if (_showFavourites.value) _selectedCategory.value = null
    }

    private fun fetchProgramme(slotIndex: Int, channel: ChannelEntity) {
        viewModelScope.launch {
            val epgId = channel.epgChannelId?.takeIf { it.isNotEmpty() } ?: channel.id
            repository.getCurrentProgram(epgId).first()?.let { prog ->
                _slots.update { slots ->
                    slots.mapIndexed { i, slot ->
                        if (i == slotIndex) slot.copy(currentProgramme = prog.title) else slot
                    }
                }
            }
        }
    }
}
