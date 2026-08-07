package app.nexstream.player.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nexstream.player.ui.screens.loading.LoadingScreen
import app.nexstream.player.ui.screens.main.MainScreen
import app.nexstream.player.ui.screens.main.MainScreenViewModel
import app.nexstream.player.ui.screens.onboarding.OnboardingStyleScreen
import app.nexstream.player.ui.screens.player.PlayerScreen
import app.nexstream.player.ui.screens.playlist.AddPlaylistScreen
import app.nexstream.player.ui.screens.settings.PlayerSettingsScreen
import app.nexstream.player.ui.theme.getOnboardingDoneFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Loading     : Screen("loading")
    object Main        : Screen("main")
    object AddPlaylist : Screen("add_playlist")
    object Onboarding  : Screen("onboarding")
}

@Composable
fun NexStreamNavGraph(
    pendingPlayUrl:       String? = null,
    pendingPlayName:      String? = null,
    onPendingPlayConsumed: () -> Unit = {}
) {
    val navController   = rememberNavController()
    val context         = LocalContext.current.applicationContext
    val onboardingDone  by context.getOnboardingDoneFlow().collectAsState(initial = null)

    NavHost(
        navController = navController,
        startDestination = Screen.Loading.route
    ) {
        composable(Screen.Loading.route) {
            LoadingScreen(
                onLoadingComplete = { hasPlaylists ->
                    val destination = if (hasPlaylists) Screen.Main.route else Screen.AddPlaylist.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToPlayer = { url ->
                    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encoded")
                },
                onNavigateToAddPlaylist = {
                    navController.navigate(Screen.AddPlaylist.route)
                },
                pendingPlayUrl       = pendingPlayUrl,
                pendingPlayName      = pendingPlayName,
                onPendingPlayConsumed = onPendingPlayConsumed
            )
        }

        composable(Screen.AddPlaylist.route) {
            val isFirstRun = navController.previousBackStackEntry == null
            AddPlaylistScreen(
                isFirstRun = isFirstRun,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else if (onboardingDone != true) {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.AddPlaylist.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.AddPlaylist.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingStyleScreen(
                onComplete = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable("settings_player") {
            PlayerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "player/{channelUrl}",
            arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encoded    = backStackEntry.arguments?.getString("channelUrl") ?: ""
            val channelUrl = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
            PlayerScreen(
                channelUrl = channelUrl,
                onBack     = { navController.popBackStack() }
            )
        }
    }
}