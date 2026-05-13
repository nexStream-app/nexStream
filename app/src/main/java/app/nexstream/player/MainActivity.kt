package app.nexstream.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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
import app.nexstream.player.data.profile.ProfileManager
import dagger.hilt.android.AndroidEntryPoint
import app.nexstream.player.ui.screens.appearance.ThemeMode
import app.nexstream.player.ui.screens.appearance.getThemeModeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import androidx.compose.material3.Text

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: PlaylistRepository
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var trialManager: TrialManager
    @Inject lateinit var licenceManager: LicenceManager
    @Inject lateinit var syncManager: WatchlistSyncManager

    private val themeViewModel: ThemeViewModel by viewModels()

    private val reminderOverlayData = mutableStateOf<ReminderOverlayData?>(null)
    private val pendingPlayUrl      = mutableStateOf<String?>(null)
    private val pendingPlayName     = mutableStateOf<String?>(null)

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
        } else {
            ContextCompat.registerReceiver(this, reminderReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        EpgRefreshWorker.schedule(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val sevenDaysAgo = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
            repository.cleanupOldPrograms(sevenDaysAgo)
            val profileId = profileManager.activeProfile.value?.id ?: "default"
            syncManager.syncFromServer(profileId)
        }

        // Read the saved theme preference synchronously before the first frame so
        // NexStreamThemeProvider starts with the correct dark/light mode and avoids a
        // one-frame flash on the profile selection screen (and anywhere else themed colours
        // appear before DataStore emits asynchronously).
        val initialThemeMode = runBlocking {
            try { applicationContext.getThemeModeFlow().first() } catch (_: Exception) { ThemeMode.SYSTEM }
        }

        setContent {
            NexStreamThemeProvider(themeViewModel = themeViewModel, initialThemeMode = initialThemeMode) {
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

                            // Show profile selector if 2+ profiles and not yet chosen this session
                            if (profiles.size >= 2 && !profileChosen) {
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(reminderReceiver)
    }
}