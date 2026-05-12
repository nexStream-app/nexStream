package app.nexstream.player.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AspectRatio(
    val label: String,
    val icon: String,
    val exoPlayerValue: Int
) {
    FILL(
        label = "Fill",
        icon = "⬛",
        exoPlayerValue = AspectRatioFrameLayout.RESIZE_MODE_FILL
    ),
    FIT(
        label = "Fit",
        icon = "🔲",
        exoPlayerValue = AspectRatioFrameLayout.RESIZE_MODE_FIT
    ),
    ZOOM(
        label = "Zoom",
        icon = "🔍",
        exoPlayerValue = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    ),
    ORIGINAL(
        label = "Original",
        icon = "📐",
        exoPlayerValue = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    )
}

enum class AspectRatioType {
    TV, MOVIE, SERIES
}

private val TV_ASPECT_KEY = stringPreferencesKey("aspect_ratio_tv")
private val MOVIE_ASPECT_KEY = stringPreferencesKey("aspect_ratio_movie")
private val SERIES_ASPECT_KEY = stringPreferencesKey("aspect_ratio_series")

fun Context.getAspectRatioFlow(type: AspectRatioType): Flow<AspectRatio> =
    themeDataStore.data.map { prefs ->
        val key = when (type) {
            AspectRatioType.TV -> TV_ASPECT_KEY
            AspectRatioType.MOVIE -> MOVIE_ASPECT_KEY
            AspectRatioType.SERIES -> SERIES_ASPECT_KEY
        }
        val default = when (type) {
            AspectRatioType.TV -> AspectRatio.FILL
            AspectRatioType.MOVIE -> AspectRatio.FIT
            AspectRatioType.SERIES -> AspectRatio.FIT
        }
        val name = prefs[key] ?: default.name
        try { AspectRatio.valueOf(name) } catch (e: Exception) { default }
    }

suspend fun Context.saveAspectRatio(type: AspectRatioType, ratio: AspectRatio) {
    themeDataStore.edit { prefs ->
        val key = when (type) {
            AspectRatioType.TV -> TV_ASPECT_KEY
            AspectRatioType.MOVIE -> MOVIE_ASPECT_KEY
            AspectRatioType.SERIES -> SERIES_ASPECT_KEY
        }
        prefs[key] = ratio.name
    }
}