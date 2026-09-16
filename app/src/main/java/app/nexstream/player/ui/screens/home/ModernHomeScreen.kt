package app.nexstream.player.ui.screens.home

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.screens.epg.ReminderViewModel
import app.nexstream.player.ui.screens.picks.PickItem
import app.nexstream.player.ui.screens.picks.PicksViewModel
import app.nexstream.player.ui.screens.recentlywatched.RecentlyWatchedViewModel

@Composable
fun ModernHomeScreen(
    firstItemFocusRequester: FocusRequester? = null,
    homeRestoreTick: Int = 0,
    onPickSelected: (PickItem) -> Unit = {},
    onPlayerLaunch: (
        url: String,
        movieId: String?,
        episodeId: String?,
        seriesId: String?,
        startPos: Long,
        title: String?,
        subtitle: String?,
        description: String?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onChannelPlay: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onGoToEpg: (channelName: String) -> Unit = {},
    sportsEditTick: Int = 0,
    picksViewModel: PicksViewModel = hiltViewModel(),
    recentlyWatchedViewModel: RecentlyWatchedViewModel = hiltViewModel(),
    homePageViewModel: HomePageViewModel = hiltViewModel(),
    reminderViewModel: ReminderViewModel = hiltViewModel(),
) {
    val picksGroups              by picksViewModel.groups.collectAsState()
    val recentlyWatched          by recentlyWatchedViewModel.recentlyWatched.collectAsState()
    val filteredSportsEvents      by homePageViewModel.filteredSportsEvents.collectAsState()
    val currentUkMinutes          by homePageViewModel.currentUkMinutes.collectAsState()
    val selectedSportCategory     by homePageViewModel.selectedSportCategory.collectAsState()
    val isLoadingSports            by homePageViewModel.isLoadingSports.collectAsState()
    val reminderIds                by reminderViewModel.reminderIds.collectAsState()
    val allSportsCategories        by homePageViewModel.sportsCategories.collectAsState()
    val sportsHiddenCategories     by homePageViewModel.sportsHiddenCategories.collectAsState()
    val sportsCategoryOrder        by homePageViewModel.sportsCategoryOrder.collectAsState()

    ModernHomeContent(
        firstItemFocusRequester  = firstItemFocusRequester,
        homeRestoreTick          = homeRestoreTick,
        continueWatching         = recentlyWatched,
        picksGroups              = picksGroups,
        sportsEvents             = filteredSportsEvents,
        selectedSportCategory    = selectedSportCategory,
        currentUkMinutes         = currentUkMinutes,
        isLoadingSports          = isLoadingSports,
        reminderIds              = reminderIds,
        allSportsCategories      = allSportsCategories,
        sportsHiddenCategories   = sportsHiddenCategories,
        sportsCategoryOrder      = sportsCategoryOrder,
        onGoToEpg                = onGoToEpg,
        onContinueWatchingClick  = { item ->
            onPlayerLaunch(
                item.streamUrl,
                item.movieId,
                item.episodeId,
                item.seriesId,
                0L,
                item.name,
                item.subtitle,
                null
            )
        },
        onPickClick              = { pick -> onPickSelected(pick) },
        onSportChannelClick      = onChannelPlay,
        onSaveSportsPreferences  = { hidden, order ->
            homePageViewModel.saveSportsPreferences(hidden, order)
        },
        onSetSportReminder       = { channelId, channelName, streamUrl, title, startMs ->
            reminderViewModel.setReminder(channelId, channelName, streamUrl, title, startMs)
        },
        onCancelSportReminder    = { channelId, startMs ->
            reminderViewModel.cancelReminder(channelId, startMs)
        },
        sportsEditTick           = sportsEditTick,
    )
}
