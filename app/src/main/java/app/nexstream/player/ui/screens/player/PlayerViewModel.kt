package app.nexstream.player.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.data.sync.ProgressSyncManager
import app.nexstream.player.license.TrialManager
import app.nexstream.player.subtitle.SubtitleManager
import app.nexstream.player.subtitle.SubtitlePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository:         PlaylistRepository,
    private val progressRepository: WatchProgressRepository,
    private val progressSync:       ProgressSyncManager,
    val trialManager:               TrialManager,
    val subtitleManager:            SubtitleManager,
    val subtitlePreferences:        SubtitlePreferences
) : ViewModel() {

    suspend fun getMovieById(movieId: String)     = repository.getMovieByIdOnce(movieId)
    suspend fun getMovieDetails(playlistId: String, vodId: String) = repository.getMovieDetails(playlistId, vodId)
    suspend fun getEpisodeById(episodeId: String) = repository.getEpisodeById(episodeId)
    suspend fun getSeriesById(seriesId: String)   = repository.getSeriesById(seriesId)

    // ── Movie progress ────────────────────────────────────────────────────────

    fun savePlaybackPosition(movieId: String, positionMs: Long, profileId: String, durationMs: Long = 0L) {
        viewModelScope.launch {
            progressRepository.saveMovieProgress(profileId, movieId, positionMs, durationMs)
        }
    }

    fun savePlaybackPositionSync(
        movieId:    String,
        positionMs: Long,
        durationMs: Long    = 0L,
        profileId:  String  = "default"
    ) {
        runBlocking {
            progressRepository.saveMovieProgress(profileId, movieId, positionMs)
        }
        viewModelScope.launch {
            progressSync.pushSingle(
                itemId     = movieId,
                itemType   = "movie",
                seriesId   = null,
                positionMs = positionMs,
                durationMs = durationMs,
                profileId  = profileId
            )
        }
    }

    fun clearPlaybackPosition(movieId: String, profileId: String) {
        viewModelScope.launch {
            progressRepository.clearMovieProgress(profileId, movieId)
        }
    }

    // ── Episode progress ──────────────────────────────────────────────────────

    fun saveEpisodePosition(episodeId: String, positionMs: Long, profileId: String, seriesId: String? = null, durationMs: Long = 0L) {
        viewModelScope.launch {
            progressRepository.saveEpisodeProgress(profileId, episodeId, seriesId, positionMs, durationMs)
        }
    }

    fun saveEpisodePositionSync(
        episodeId:  String,
        positionMs: Long,
        durationMs: Long    = 0L,
        seriesId:   String? = null,
        profileId:  String  = "default"
    ) {
        runBlocking {
            progressRepository.saveEpisodeProgress(profileId, episodeId, seriesId, positionMs)
        }
        viewModelScope.launch {
            progressSync.pushSingle(
                itemId     = episodeId,
                itemType   = "episode",
                seriesId   = seriesId,
                positionMs = positionMs,
                durationMs = durationMs,
                profileId  = profileId
            )
        }
    }

    fun clearEpisodePosition(episodeId: String, profileId: String) {
        viewModelScope.launch {
            progressRepository.clearEpisodeProgress(profileId, episodeId)
        }
    }

    // ── Resume position lookup ────────────────────────────────────────────────
    // Called by PlayerScreen before starting playback to get the right start position

    suspend fun getMovieResumePosition(movieId: String, profileId: String): Long =
        progressRepository.getMoviePosition(profileId, movieId)

    suspend fun getEpisodeResumePosition(episodeId: String, profileId: String): Long =
        progressRepository.getEpisodePosition(profileId, episodeId)

    fun getCurrentProgrammeForUrl(channelUrl: String): Flow<ProgramEntity?> =
        repository.getCurrentProgrammeForChannelUrl(channelUrl)
}