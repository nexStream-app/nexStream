package app.nexstream.player
import android.app.Application
import androidx.work.Configuration
import app.nexstream.player.ui.theme.ThemeManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ChannelGroupSyncManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.SettingsSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import coil.Coil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
@HiltAndroidApp
class NexStreamApp : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var profileDao: ProfileDao

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var profileSyncManager: ProfileSyncManager
    @Inject lateinit var watchlistSyncManager: WatchlistSyncManager
    @Inject lateinit var channelGroupSyncManager: ChannelGroupSyncManager
    @Inject lateinit var settingsSyncManager: SettingsSyncManager
    @Inject
    lateinit var themeManager: ThemeManager
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    override fun onCreate() {
        super.onCreate()
        themeManager.init()
        profileManager.init()
        profileSyncManager.onSyncComplete = { profileManager.refreshAfterSync() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settingsSyncManager.syncFromServer()
            settingsSyncManager.pushSettings()
            profileSyncManager.syncFromServer()
            profileSyncManager.pushProfiles()
            // Re-pull profiles: catches any profiles pushed by other devices between our
            // initial pull and our push (closing the race window on startup).
            profileSyncManager.syncFromServer()
            watchlistSyncManager.pushAllToServer()
            channelGroupSyncManager.syncFromServer()
            channelGroupSyncManager.pushGroups()
            val profiles = profileDao.getAllProfilesOnce()
            profiles.forEach { profile -> watchlistSyncManager.syncFromServer(profile.id) }
            Coil.setImageLoader(imageLoader)
        }
        // Register FCM token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    profileSyncManager.sendFcmToken(token)
                }
            }
    }
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }
}