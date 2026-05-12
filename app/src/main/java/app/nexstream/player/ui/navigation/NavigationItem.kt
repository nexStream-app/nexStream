package app.nexstream.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object TV : NavigationItem("tv", "TV", Icons.Default.Tv)
    object Guide : NavigationItem("guide", "Guide", Icons.Default.CalendarToday)
    object Movies : NavigationItem("movies", "Movies", Icons.Default.Movie)
    object Series : NavigationItem("series", "TV Shows", Icons.Default.Tv)
    object MyList : NavigationItem("mylist", "My List", Icons.Default.Bookmark)
    object Settings : NavigationItem("settings", "Settings", Icons.Default.Settings)
}

fun getAllNavigationItems() = listOf(
    NavigationItem.TV,
    NavigationItem.Guide,
    NavigationItem.Movies,
    NavigationItem.Series,
    NavigationItem.MyList,
    NavigationItem.Settings
)
