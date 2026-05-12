package app.nexstream.player.data.sync

// ── HOW TO WIRE PROGRESS SYNC ─────────────────────────────────────────────────
//
// 1. Register ProgressApiService in your Hilt NetworkModule alongside WatchlistApiService:
//
//    @Provides @Singleton
//    fun provideProgressApiService(retrofit: Retrofit): ProgressApiService =
//        retrofit.create(ProgressApiService::class.java)
//
// 2. Add ProgressSyncManager to whichever ViewModel already calls
//    WatchlistSyncManager.syncFromServer() — inject it alongside WatchlistSyncManager.
//    Then add two calls:
//
//    // On app open / profile switch — pull server → Room, then push Room → server
//    viewModelScope.launch {
//        progressSyncManager.pullFromServer(profileId)
//        progressSyncManager.pushAllToServer(profileId)
//    }
//
//    // Pull again on profile switch (same place you call watchlistSyncManager.syncFromServer):
//    fun onProfileSwitched(profileId: String) {
//        viewModelScope.launch {
//            watchlistSyncManager.syncFromServer(profileId)
//            progressSyncManager.pullFromServer(profileId)
//            progressSyncManager.pushAllToServer(profileId)
//        }
//    }
//
// 3. Add these two queries to PlaylistRepository (or MovieDao / SeriesDao):
//
//    // All movies with progress (lastPlayedPosition > 0)
//    @Query("SELECT * FROM movies WHERE lastPlayedPosition > 0")
//    suspend fun getAllMoviesWithProgress(): List<MovieEntity>
//
//    // All episodes with progress
//    @Query("SELECT * FROM episodes WHERE lastPlayedPosition > 0")
//    suspend fun getAllEpisodesWithProgress(): List<EpisodeEntity>
//
//    Expose them from PlaylistRepository:
//    suspend fun getAllMoviesWithProgress() = movieDao.getAllMoviesWithProgress()
//    suspend fun getAllEpisodesWithProgress() = seriesDao.getAllEpisodesWithProgress()
//
// 4. Add lastPlayedTimestamp to MovieEntity if not already present:
//    val lastPlayedTimestamp: Long? = null
//    And update saveMoviePlaybackPosition to also set this field.
//
// 5. LicencePreferences.getActiveProfileId() — add this if not present:
//    fun getActiveProfileId(): String? = prefs.getString("active_profile_id", "default")
//    fun saveActiveProfileId(id: String) = prefs.edit().putString("active_profile_id", id).apply()
//    Call saveActiveProfileId() whenever a profile is switched.
//
// That's it. PlayerViewModel.saveEpisodePositionSync / savePlaybackPositionSync
// already call progressSyncManager.pushSingle() on player close.