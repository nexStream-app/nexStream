package app.nexstream.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import app.nexstream.player.license.AppAccessState
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.license.TrialManager
import app.nexstream.player.ui.components.ReminderOverlay
import app.nexstream.player.ui.components.ReminderOverlayData
import app.nexstream.player.ui.navigation.NexStreamNavGraph
import app.nexstream.player.ui.screens.profile.ProfileSelectScreen
import app.nexstream.player.ui.screens.trial.TrialExpiredScreen
import app.nexstream.player.ui.theme.NexStreamThemeProvider
import app.nexstream.player.ui.theme.ThemeViewModel
import app.nexstream.player.worker.EpgRefreshWorker
import app.nexstream.player.worker.ReminderWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.update.AutoUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material3.Text

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val isPlayerActive  = mutableStateOf(false)
        val isInPipMode     = mutableStateOf(false)
        // Incremented by PiP stop action → PlayerScreen stops playback
        val stopPipSignal   = kotlinx.coroutines.flow.MutableStateFlow(0)
        const val ACTION_PIP_STOP = "app.nexstream.player.PIP_STOP"
    }

    @Inject lateinit var repository: PlaylistRepository
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var trialManager: TrialManager
    @Inject lateinit var licenceManager: LicenceManager
    @Inject lateinit var syncManager: WatchlistSyncManager
    @Inject lateinit var profileSyncManager: ProfileSyncManager

    private val themeViewModel: ThemeViewModel by viewModels()

    private val reminderOverlayData = mutableStateOf<ReminderOverlayData?>(null)
    private val pendingPlayUrl      = mutableStateOf<String?>(null)
    private val pendingPlayName     = mutableStateOf<String?>(null)

    private val pipStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_PIP_STOP) return
            stopPipSignal.value++
            moveTaskToBack(true)
        }
    }

    private val reminderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ReminderWorker.ACTION_SHOW_REMINDER) return
            val data = ReminderOverlayData(
                reminderId   = intent.getStringExtra(ReminderWorker.KEY_REMINDER_ID)   ?: return,
                channelName  = intent.getStringExtra(ReminderWorker.KEY_CHANNEL_NAME)  ?: return,
                programTitle = intent.getStringExtra(ReminderWorker.KEY_PROGRAM_TITLE) ?: return,
                streamUrl    = intent.getStringExtra(ReminderWorker.KEY_STREAM_URL)    ?: return,
                startTime    = intent.getLongExtra(ReminderWorker.KEY_START_TIME, 0L)
            )
            reminderOverlayData.value = data
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val filter = IntentFilter(ReminderWorker.ACTION_SHOW_REMINDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(reminderReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(pipStopReceiver, IntentFilter(ACTION_PIP_STOP), Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, reminderReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this, pipStopReceiver, IntentFilter(ACTION_PIP_STOP), ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        EpgRefreshWorker.schedule(this)

        lifecycleScope.launch {
            AutoUpdateManager.checkAndPrompt(this@MainActivity)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val sevenDaysAgo = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
            repository.cleanupOldPrograms(sevenDaysAgo)
            val profileId = profileManager.activeProfile.filterNotNull().first().id
            syncManager.syncFromServer(profileId)
            syncManager.pushAllToServer()
        }

        setContent {
            NexStreamThemeProvider(themeViewModel = themeViewModel) {
                var accessState by remember { mutableStateOf(AppAccessState.LOADING) }
                val overlayData by reminderOverlayData

                // Read app name from theme - works for both default and reseller
                val themes  by themeViewModel.themes.collectAsState()
                val appName  = themes.dark.identity.appName
                val appFont  = themes.dark.identity.fontFamily

                LaunchedEffect(Unit) {
                    val checkDeferred = async {
                        trialManager.checkAccessState(licenceManager.getDeviceId())
                    }
                    accessState = checkDeferred.await()
                }

                // After licence confirmed, sync profiles + watchlist (key not available on startup).
                // Delayed 10s to avoid piling I/O on top of playlist load and progress migration.
                LaunchedEffect(accessState) {
                    if (accessState == AppAccessState.TRIAL_ACTIVE || accessState == AppAccessState.LICENSED) {
                        launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(10_000)
                            profileSyncManager.syncFromServer()
                            profileManager.refreshAfterSync()
                            kotlinx.coroutines.delay(5_000)
                            profileManager.profiles.value.forEach { profile ->
                                syncManager.syncFromServer(profile.id)
                                kotlinx.coroutines.delay(2_000)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (accessState) {
                        AppAccessState.LOADING -> {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = appName,
                                    color      = MaterialTheme.colorScheme.primary,
                                    fontSize   = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = appFont
                                )
                            }
                        }
                        AppAccessState.TRIAL_EXPIRED -> {
                            TrialExpiredScreen(
                                onLicenceActivated = { accessState = AppAccessState.LICENSED }
                            )
                        }
                        AppAccessState.TRIAL_ACTIVE,
                        AppAccessState.LICENSED -> {
                            val playUrl      by pendingPlayUrl
                            val playName     by pendingPlayName
                            val profiles     by profileManager.profiles.collectAsState()
                            val activeProfile by profileManager.activeProfile.collectAsState()
                            var profileChosen by remember { mutableStateOf(false) }
                            val playlists    by repository.getAllPlaylists().collectAsState(initial = emptyList())
                            val hasPlaylists  = playlists.isNotEmpty()

                            // Show profile selector only after playlists are set up, to avoid
                            // intercepting first-run flow where profiles synced before playlist added
                            val singleProfileNeedsPin = profiles.size == 1 && profiles[0].pinHash != null
                            if (hasPlaylists && (profiles.size >= 2 || singleProfileNeedsPin) && !profileChosen) {
                                ProfileSelectScreen(
                                    profiles = profiles,
                                    appName  = appName,
                                    onProfileSelected = { profile ->
                                        profileManager.setActiveProfile(profile)
                                        profileChosen = true
                                    }
                                )
                            } else {
                                NexStreamNavGraph(
                                    pendingPlayUrl        = playUrl,
                                    pendingPlayName       = playName,
                                    onPendingPlayConsumed = {
                                        pendingPlayUrl.value  = null
                                        pendingPlayName.value = null
                                    }
                                )
                            }
                        }
                    }

                    if (overlayData != null) {
                        ReminderOverlay(
                            data      = overlayData!!,
                            onDismiss = { reminderOverlayData.value = null },
                            onSnooze  = {
                                val d = reminderOverlayData.value ?: return@ReminderOverlay
                                reminderOverlayData.value = null
                                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                                    .setInitialDelay(60L, TimeUnit.SECONDS)
                                    .setInputData(workDataOf(
                                        ReminderWorker.KEY_REMINDER_ID   to d.reminderId,
                                        ReminderWorker.KEY_CHANNEL_NAME  to d.channelName,
                                        ReminderWorker.KEY_PROGRAM_TITLE to d.programTitle,
                                        ReminderWorker.KEY_STREAM_URL    to d.streamUrl,
                                        ReminderWorker.KEY_START_TIME    to d.startTime
                                    ))
                                    .addTag("reminder_snooze")
                                    .build()
                                WorkManager.getInstance(this@MainActivity).enqueue(request)
                            },
                            onWatchNow = { streamUrl, channelName ->
                                reminderOverlayData.value = null
                                pendingPlayUrl.value  = streamUrl
                                pendingPlayName.value = channelName
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        themeViewModel.themeManager.checkForUpdates()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPipMode.value = isInPictureInPictureMode
        }
        // Pull watchlist from server on foreground so cross-device adds appear
        profileManager.activeProfile.value?.id?.let { profileId ->
            lifecycleScope.launch(Dispatchers.IO) {
                syncManager.syncFromServer(profileId)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isTV = packageManager.hasSystemFeature("android.software.leanback")
            if (!isTV) {
                val stopIntent = android.app.PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_PIP_STOP).setPackage(packageName),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                val stopIcon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause)
                val stopAction = android.app.RemoteAction(
                    stopIcon, "Stop", "Stop playback", stopIntent
                )
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(listOf(stopAction))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setCloseAction(
                        android.app.RemoteAction(stopIcon, "Close", "Stop playback and close PiP", stopIntent)
                    )
                }
                enterPictureInPictureMode(paramsBuilder.build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(reminderReceiver)
        unregisterReceiver(pipStopReceiver)
    }
}