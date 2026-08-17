package app.nexstream.player.ui.screens.appearance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ProfileAppearanceDao
import app.nexstream.player.data.local.entity.ProfileAppearanceEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.ThemeMode
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.saveAspectRatio
import app.nexstream.player.ui.theme.saveEpgMiniPlayer
import app.nexstream.player.ui.theme.saveFontScale
import app.nexstream.player.ui.theme.saveFontWeight
import app.nexstream.player.ui.theme.saveKeyboardFontScale
import app.nexstream.player.ui.theme.saveThemeMode
import app.nexstream.player.ui.theme.saveUiStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val profileAppearanceDao: ProfileAppearanceDao,
    private val profileSyncManager: ProfileSyncManager,
) : ViewModel() {

    fun saveThemeMode(mode: ThemeMode) = viewModelScope.launch {
        context.saveThemeMode(mode)
        updateDb { copy(themeMode = mode.name) }
    }

    fun saveFontScale(scale: Float) = viewModelScope.launch {
        context.saveFontScale(scale)
        updateDb { copy(fontScale = scale) }
    }

    fun saveFontWeight(weight: String) = viewModelScope.launch {
        context.saveFontWeight(weight)
        updateDb { copy(fontWeight = weight) }
    }

    fun saveKeyboardFontScale(scale: Float) = viewModelScope.launch {
        context.saveKeyboardFontScale(scale)
        updateDb { copy(keyboardFontScale = scale) }
    }

    fun saveEpgMiniPlayer(enabled: Boolean) = viewModelScope.launch {
        context.saveEpgMiniPlayer(enabled)
        updateDb { copy(epgMiniPlayer = enabled) }
    }

    fun saveUiStyle(style: UiStyle) = viewModelScope.launch {
        context.saveUiStyle(style)
        updateDb { copy(uiStyle = style.name) }
    }

    fun saveAspectRatio(type: AspectRatioType, ratio: AspectRatio) = viewModelScope.launch {
        context.saveAspectRatio(type, ratio)
        updateDb {
            when (type) {
                AspectRatioType.TV     -> copy(tvAspectRatio = ratio.name)
                AspectRatioType.MOVIE  -> copy(movieAspectRatio = ratio.name)
                AspectRatioType.SERIES -> copy(seriesAspectRatio = ratio.name)
            }
        }
    }

    private suspend fun updateDb(update: ProfileAppearanceEntity.() -> ProfileAppearanceEntity) {
        val profileId = profileManager.activeProfile.value?.id ?: return
        withContext(Dispatchers.IO) {
            val current = profileAppearanceDao.getAppearance(profileId)
                ?: ProfileAppearanceEntity(profileId = profileId)
            profileAppearanceDao.upsertAppearance(current.update())
        }
        profileSyncManager.pushProfiles()
    }
}
