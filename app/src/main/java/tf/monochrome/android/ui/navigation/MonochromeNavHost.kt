package tf.monochrome.android.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tf.monochrome.android.ui.components.MiniPlayer
import tf.monochrome.android.ui.detail.AlbumDetailScreen
import tf.monochrome.android.ui.detail.ArtistDetailScreen
import tf.monochrome.android.ui.detail.MixScreen
import tf.monochrome.android.ui.detail.MixScreen
import tf.monochrome.android.ui.home.HomeScreen
import tf.monochrome.android.ui.library.LibraryScreen
import tf.monochrome.android.ui.player.NowPlayingScreen
import tf.monochrome.android.ui.player.PlayerViewModel
import tf.monochrome.android.ui.search.SearchScreen
import tf.monochrome.android.ui.settings.SettingsScreen
import tf.monochrome.android.ui.library.PlaylistScreen
import tf.monochrome.android.ui.library.DownloadsScreen
import tf.monochrome.android.ui.profile.ProfileScreen
import tf.monochrome.android.data.auth.GoogleAuthManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.ui.platform.LocalContext

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistId}") {
        fun createRoute(artistId: Long) = "artist/$artistId"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Mix : Screen("mix/{mixId}") {
        fun createRoute(mixId: String) = "mix/$mixId"
    }
    data object Downloads : Screen("downloads")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Library, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
)

// No longer needed: auth is handled via ProfileViewModel

@Composable
fun MonochromeNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Shared PlayerViewModel scoped to the navigation graph (lives as long as the composable)
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val positionMs by playerViewModel.positionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()

    val showBottomBar = currentDestination?.route in listOf(
        Screen.Home.route, Screen.Search.route, Screen.Library.route
    )

    val showMiniPlayer = currentTrack != null && currentDestination?.route != Screen.NowPlaying.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(Screen.Search.route) {
                    SearchScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(Screen.Library.route) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) {
                    AlbumDetailScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.LongType })
                ) {
                    ArtistDetailScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    PlaylistScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(navController = navController)
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen(navController = navController)
                }
                composable(
                    route = Screen.Mix.route,
                    arguments = listOf(navArgument("mixId") { type = NavType.StringType })
                ) { backStackEntry ->
                    MixScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(navController = navController)
                }
            }

            // MiniPlayer overlay (positioned at bottom, above nav bar)
            if (showMiniPlayer) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onSkipNextClick = { playerViewModel.skipToNext() },
                        onClick = { navController.navigate(Screen.NowPlaying.route) }
                    )
                }
            }
        }
    }
}
