package app.nexstream.player.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NexStreamFirebaseService : FirebaseMessagingService() {

    @Inject lateinit var profileSyncManager: ProfileSyncManager
    @Inject lateinit var profileManager: ProfileManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received: ${message.data}")

        val type = message.data["type"] ?: "sync"
        when (type) {
            "profile_sync", "sync" -> {
                scope.launch {
                    profileSyncManager.syncFromServer()
                    // syncFromServer() is a suspend fun that completes before returning,
                    // so refreshAfterSync() runs after DB is written
                    profileManager.refreshAfterSync()
                    Log.d("FCM", "Profile sync and refresh complete")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        scope.launch {
            profileSyncManager.sendFcmToken(token)
        }
    }
}