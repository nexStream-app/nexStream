package app.nexstream.player.service

import app.nexstream.player.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.sync.ChannelGroupSyncManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.RecentlySyncManager
import app.nexstream.player.data.sync.SettingsSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.license.PlaylistCrypto
import app.nexstream.player.license.TrialManager
import app.nexstream.player.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

@AndroidEntryPoint
class NexStreamFirebaseService : FirebaseMessagingService() {

    @Inject lateinit var channelGroupSyncManager: ChannelGroupSyncManager
    @Inject lateinit var settingsSyncManager: SettingsSyncManager
    @Inject lateinit var profileSyncManager: ProfileSyncManager
    @Inject lateinit var watchlistSyncManager: WatchlistSyncManager
    @Inject lateinit var recentlySyncManager: RecentlySyncManager
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var licenceManager: LicenceManager
    @Inject lateinit var trialManager: TrialManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var adminNotificationManager: AdminNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received: ${message.data}")

        val type = message.data["type"] ?: "sync"
        when (type) {
            "profile_sync", "sync" -> {
                scope.launch {
                    profileSyncManager.syncFromServer()
                    profileManager.refreshAfterSync()
                    // Also refresh watchlist for the active profile
                    profileManager.activeProfile.value?.id?.let { pid ->
                        watchlistSyncManager.syncFromServer(pid)
                    }
                    Log.d("FCM", "Profile + watchlist sync complete")
                }
            }
            "watchlist_sync" -> {
                scope.launch {
                    val pid = profileManager.activeProfile.value?.id ?: return@launch
                    watchlistSyncManager.syncFromServer(pid)
                    Log.d("FCM", "Watchlist sync triggered for profile $pid")
                }
            }
            "recently_watched_sync" -> {
                scope.launch {
                    val pid = profileManager.activeProfile.value?.id ?: return@launch
                    recentlySyncManager.syncFromServer(pid)
                    Log.d("FCM", "Recently watched sync triggered for profile $pid")
                }
            }
            "channel_group_sync" -> {
                scope.launch {
                    channelGroupSyncManager.syncFromServer()
                    Log.d("FCM", "Channel group sync triggered")
                }
            }
            "settings_sync" -> {
                scope.launch {
                    settingsSyncManager.syncFromServer()
                    Log.d("FCM", "Settings sync triggered")
                }
            }
            "theme_refresh" -> {
                scope.launch {
                    themeManager.refresh()
                    Log.d("FCM", "Theme refresh triggered")
                }
            }
            "licence_assigned" -> {
                scope.launch {
                    val activated = trialManager.checkAndActivateAssignedLicence(licenceManager.getDeviceId())
                    if (activated) {
                        licenceManager.notifyActivated()
                        themeManager.refresh()
                        // Re-register FCM token now that we have a licence key, so the backend
                        // links this token to a user_id and future reseller theme pushes reach us.
                        reRegisterFcmToken()
                        Log.d("FCM", "licence_assigned: activated and notified")
                    } else {
                        Log.d("FCM", "licence_assigned: no licence found for this device")
                    }
                }
            }
            "admin_broadcast" -> {
                val title = message.notification?.title ?: message.data["title"] ?: "nexStream"
                val body  = message.notification?.body  ?: message.data["body"]  ?: ""
                adminNotificationManager.emit(AdminMessage(title, body))
                showAdminNotification(title, body)
            }
            "playlist_assigned" -> {
                val d = message.data
                val playlistType = d["playlist_type"] ?: run {
                    Log.w("FCM", "playlist_assigned missing playlist_type")
                    return
                }
                Log.d("FCM", "Playlist assigned via FCM: type=$playlistType")
                val licenceKey   = licenceManager.getStoredLicenceKey()
                val encPassword  = d["password"] ?: ""
                val password = if (licenceKey != null && encPassword.isNotEmpty())
                    PlaylistCrypto.decryptPassword(encPassword, licenceKey)
                else encPassword
                playlistRepository.setPendingPlaylistAssignment(
                    PlaylistRepository.PlaylistAssignedEvent(
                        type      = playlistType,
                        username  = d["username"]   ?: "",
                        serverUrl = d["server_url"] ?: "",
                        password  = password,
                        m3uUrl    = d["m3u_url"]    ?: ""
                    )
                )
            }
        }
    }

    private fun showAdminNotification(title: String, body: String) {
        val channelId = "nexstream_admin"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Announcements", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        scope.launch {
            profileSyncManager.sendFcmToken(token, licenceManager.getDeviceId())
        }
    }

    private suspend fun reRegisterFcmToken() {
        try {
            val task = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            val token = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                task.addOnSuccessListener { cont.resume(it, null) }
                    .addOnFailureListener { cont.resume(null, null) }
            } ?: return
            profileSyncManager.sendFcmToken(token, licenceManager.getDeviceId())
            Log.d("FCM", "FCM token re-registered after licence activation")
        } catch (e: Exception) {
            Log.w("FCM", "Failed to re-register FCM token", e)
        }
    }
}