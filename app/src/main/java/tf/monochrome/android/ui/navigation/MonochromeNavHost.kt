package tf.monochrome.android.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import tf.monochrome.android.ui.components.liquidGlass
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import tf.monochrome.android.radio.RadioEvent
import tf.monochrome.android.radio.RadioSeed
import tf.monochrome.android.radio.RadioViewModel
import tf.monochrome.android.ui.components.MiniPlayer
import tf.monochrome.android.ui.detail.AlbumDetailScreen
import tf.monochrome.android.ui.detail.ArtistDetailScreen
import tf.monochrome.android.ui.detail.LocalAlbumDetailScreen
import tf.monochrome.android.ui.detail.LocalArtistDetailScreen
import tf.monochrome.android.ui.detail.LocalGenreDetailScreen
import tf.monochrome.android.ui.eq.EqualizerScreen
import tf.monochrome.android.ui.eq.ParametricEqEditScreen
import tf.monochrome.android.ui.eq.ParametricEqScreen
import tf.monochrome.android.ui.home.HomeScreen
import tf.monochrome.android.ui.mixer.MixerScreen
import tf.monochrome.android.ui.library.LibraryScreen
import tf.monochrome.android.ui.library.DownloadsScreen
import tf.monochrome.android.ui.library.PlaylistScreen
import tf.monochrome.android.ui.player.NowPlayingScreen
import tf.monochrome.android.ui.player.PlayerViewModel
import tf.monochrome.android.ui.library.FolderBrowserScreen
import tf.monochrome.android.ui.profile.ProfileScreen
import tf.monochrome.android.ui.stats.ListeningStatsScreen
import tf.monochrome.android.ui.stats.StatsScreen
import tf.monochrome.android.ui.search.SearchScreen
import tf.monochrome.android.ui.settings.SettingsScreen
import tf.monochrome.android.ui.carmode.CarModeScreen
import tf.monochrome.android.ui.debug.DebugLogScreen
import tf.monochrome.android.ui.oxford.OxfordEffectsTabs
import tf.monochrome.android.ui.oxford.OxfordViewModel

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
    data object Downloads : Screen("downloads")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings?tab={tab}") {
        fun createRoute(tab: Int = 0) = "settings?tab=$tab"
    }
    data object Equalizer : Screen("equalizer")
    data object ParametricEq : Screen("parametric_eq")
    data object ParametricEqEdit : Screen("parametric_eq_edit")
    data object Profile : Screen("profile")
    data object Stats : Screen("stats")
    data object ListeningStats : Screen("listening_stats")
    data object FolderBrowser : Screen("folder/{folderPath}") {
        fun createRoute(folderPath: String) = "folder/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    data object LocalAlbumDetail : Screen("local_album/{albumId}") {
        fun createRoute(albumId: Long) = "local_album/$albumId"
    }
    data object LocalArtistDetail : Screen("local_artist/{artistId}") {
        fun createRoute(artistId: Long) = "local_artist/$artistId"
    }
    data object LocalGenreDetail : Screen("local_genre/{genre}") {
        fun createRoute(genre: String) = "local_genre/${java.net.URLEncoder.encode(genre, "UTF-8")}"
    }
    data object Mixer : Screen("mixer")
    data object CarMode : Screen("car_mode")
    data object Oxford : Screen("oxford?tab={tab}") {
        /** tab: 0 = Compressor, 1 = Inflator. */
        fun createRoute(tab: Int = 0) = "oxford?tab=$tab"
    }
    data object DebugLog : Screen("debug_log")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// The two main tab screens, in pager order
private val tabRoutes = listOf(Screen.Home.route, Screen.Library.route)

@Composable
fun MonochromeNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val radioViewModel: RadioViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(radioViewModel) {
        radioViewModel.events.collect { event ->
            when (event) {
                is RadioEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    // Position/duration tick every 250 ms. Keep them as State<Long> and read
    // only inside the draw-scope progress lambda below — reading `.value` here
    // would recompose the entire nav host (pager + HomeScreen + LibraryScreen)
    // four times a second.
    val positionState = playerViewModel.positionMs.collectAsState()
    val durationState = playerViewModel.durationMs.collectAsState()
    val progressProvider = remember(positionState, durationState) {
        {
            val d = durationState.value
            if (d > 0) positionState.value.toFloat() / d.toFloat() else 0f
        }
    }

    // True when the user is on one of the three main tab screens
    val isOnMainTab = currentDestination?.route in tabRoutes

    val showMiniPlayer = currentTrack != null
        && currentDestination?.route != Screen.NowPlaying.route
        && currentDestination?.route != Screen.Mixer.route

    // Pager state for the two main tabs
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // When the user swipes the pager, keep the NavController in sync.
    LaunchedEffect(pagerState.currentPage) {
        if (isOnMainTab) {
            val route = tabRoutes[pagerState.currentPage]
            if (currentDestination?.route != route) {
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val themeBackground = MaterialTheme.colorScheme.background

    val hazeState = rememberHazeState()

    // System bar heights for overlapping content
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 0: Background ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeBackground)
        )

        // ── Layer 1: Full-screen content (draws behind bars) ─────────────
        // Detail screens reserve nav bar + mini-player height so content
        // isn't hidden behind the floating mini player. Main tabs (pager)
        // handle their own bottom contentPadding already.
        val miniPlayerReserve = 72.dp
        val detailBottomInset = navBarHeight + if (showMiniPlayer) miniPlayerReserve else 0.dp

        // One SaveableStateHolder keeps each tab's subtree state (selected
        // Library sub-tab, LazyColumn scroll offsets, text field input, etc.)
        // alive across the pager being torn down when the user enters a detail
        // screen and rebuilt when they come back. Without this wrapper,
        // `rememberSaveable` inside the tabs loses its entry the moment
        // `isOnMainTab` flips false and the pager is dropped from composition,
        // so coming back to Library would reset to the Overview sub-tab and
        // scroll to the top.
        val tabStateHolder = rememberSaveableStateHolder()

        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            // Pager for main tabs — fills entire screen
            if (isOnMainTab) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0
                ) { page ->
                    // Key by route, not page index — `Screen.Home.route` /
                    // `Screen.Library.route` survive even if the pager order
                    // ever changes. SaveableStateProvider persists every
                    // rememberSaveable inside the lambda across pager recreate.
                    val key = tabRoutes[page]
                    tabStateHolder.SaveableStateProvider(key) {
                        when (page) {
                            0 -> tf.monochrome.android.devedit.DevEditScreen("home") {
                                HomeScreen(navController = navController, playerViewModel = playerViewModel)
                            }
                            1 -> tf.monochrome.android.devedit.DevEditScreen("library") {
                                LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                            }
                        }
                    }
                }
            }

            // Detail / overlay screens
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(bottom = detailBottomInset),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() }
            ) {
                // Tab stubs – content is rendered by the pager above
                composable(Screen.Home.route) { }
                composable(Screen.Library.route) { }
                composable(Screen.Search.route) {
                    tf.monochrome.android.devedit.DevEditScreen("search") {
                        SearchScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }

                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("album_detail") {
                        AlbumDetailScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.LongType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("artist_detail") {
                        ArtistDetailScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("playlist_detail") {
                        PlaylistScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(navController = navController, playerViewModel = playerViewModel)
                }
                composable(
                    route = Screen.Settings.route,
                    arguments = listOf(navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    })
                ) { backStackEntry ->
                    val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                    SettingsScreen(navController = navController, initialTab = tab)
                }
                composable(Screen.Equalizer.route) {
                    tf.monochrome.android.devedit.DevEditScreen("equalizer") {
                        EqualizerScreen(navController = navController)
                    }
                }
                composable(Screen.ParametricEq.route) {
                    tf.monochrome.android.devedit.DevEditScreen("parametric_eq") {
                        ParametricEqScreen(navController = navController)
                    }
                }
                composable(Screen.ParametricEqEdit.route) {
                    tf.monochrome.android.devedit.DevEditScreen("parametric_eq_edit") {
                        ParametricEqEditScreen(navController = navController)
                    }
                }
                composable(Screen.Mixer.route) {
                    tf.monochrome.android.devedit.DevEditScreen("mixer") {
                        MixerScreen(
                            navController = navController,
                            viewModel = hiltViewModel()
                        )
                    }
                }
                composable(Screen.CarMode.route) {
                    tf.monochrome.android.devedit.DevEditScreen("car_mode") {
                        CarModeScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }
                composable(Screen.DebugLog.route) {
                    tf.monochrome.android.devedit.DevEditScreen("debug_log") {
                        DebugLogScreen(navController = navController)
                    }
                }
                composable(
                    route = Screen.Oxford.route,
                    arguments = listOf(navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    })
                ) { backStackEntry ->
                    val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                    val vm: OxfordViewModel = hiltViewModel()
                    tf.monochrome.android.devedit.DevEditScreen("oxford") {
                        OxfordEffectsTabs(
                            inflator = vm.inflator,
                            compressor = vm.compressor,
                            initialTab = tab,
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize().padding(top = statusBarHeight),
                        )
                    }
                }
                composable(Screen.Downloads.route) {
                    tf.monochrome.android.devedit.DevEditScreen("downloads") {
                        DownloadsScreen(navController = navController)
                    }
                }
                composable(Screen.Profile.route) {
                    tf.monochrome.android.devedit.DevEditScreen("profile") {
                        ProfileScreen(navController = navController)
                    }
                }
                composable(Screen.Stats.route) {
                    tf.monochrome.android.devedit.DevEditScreen("stats") {
                        StatsScreen(navController = navController)
                    }
                }
                composable(Screen.ListeningStats.route) {
                    tf.monochrome.android.devedit.DevEditScreen("listening_stats") {
                        ListeningStatsScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    route = Screen.FolderBrowser.route,
                    arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
                ) { backStackEntry ->
                    val folderPath = java.net.URLDecoder.decode(
                        backStackEntry.arguments?.getString("folderPath") ?: "", "UTF-8"
                    )
                    tf.monochrome.android.devedit.DevEditScreen("folder_browser") {
                        FolderBrowserScreen(
                            folderPath = folderPath,
                            navController = navController,
                            onPlayTrack = { track, queue ->
                                playerViewModel.playUnifiedTrack(track, queue)
                            },
                            onPlayAll = { tracks ->
                                playerViewModel.playAllUnified(tracks)
                            }
                        )
                    }
                }
                composable(
                    route = Screen.LocalAlbumDetail.route,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("local_album_detail") {
                        LocalAlbumDetailScreen(
                            navController = navController,
                            onPlayTrack = { track, queue ->
                                playerViewModel.playUnifiedTrack(track, queue)
                            },
                            onPlayAll = { tracks ->
                                playerViewModel.playAllUnified(tracks)
                            },
                            onShuffleAll = { tracks ->
                                playerViewModel.shufflePlayUnified(tracks)
                            }
                        )
                    }
                }
                composable(
                    route = Screen.LocalArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.LongType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("local_artist_detail") {
                        LocalArtistDetailScreen(
                            navController = navController,
                            onPlayTrack = { track, queue ->
                                playerViewModel.playUnifiedTrack(track, queue)
                            },
                            onPlayAll = { tracks ->
                                playerViewModel.playAllUnified(tracks)
                            },
                            onShuffleAll = { tracks ->
                                playerViewModel.shufflePlayUnified(tracks)
                            }
                        )
                    }
                }
                composable(
                    route = Screen.LocalGenreDetail.route,
                    arguments = listOf(navArgument("genre") { type = NavType.StringType })
                ) {
                    tf.monochrome.android.devedit.DevEditScreen("local_genre_detail") {
                        LocalGenreDetailScreen(
                            navController = navController,
                            onPlayTrack = { track, queue ->
                                playerViewModel.playUnifiedTrack(track, queue)
                            },
                            onPlayAll = { tracks ->
                                playerViewModel.playAllUnified(tracks)
                            },
                            onShuffleAll = { tracks ->
                                playerViewModel.shufflePlayUnified(tracks)
                            }
                        )
                    }
                }
            }
        }

        // ── Layer 2: Navigation bar + mini player (overlays content) ──
        if (isOnMainTab) {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                // Mini player — sits below nav bar with its own space
                if (showMiniPlayer) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progressProvider = progressProvider,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onSkipNextClick = { playerViewModel.skipToNext() },
                        onSkipPreviousClick = { playerViewModel.skipToPrevious() },
                        onClick = { navController.navigate(Screen.NowPlaying.route) },
                        onStartSessionRadio = { radioViewModel.startRadio(RadioSeed.FromListeningSession) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        hazeState = hazeState
                    )
                }

                // Fill the system nav bar area
                Spacer(modifier = Modifier.height(navBarHeight))
            }
        } else if (showMiniPlayer) {
            // Mini player on non-tab screens — pad above system nav bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarHeight)
            ) {
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    progressProvider = progressProvider,
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onSkipNextClick = { playerViewModel.skipToNext() },
                    onSkipPreviousClick = { playerViewModel.skipToPrevious() },
                    onClick = { navController.navigate(Screen.NowPlaying.route) },
                    onStartSessionRadio = { radioViewModel.startRadio(RadioSeed.FromListeningSession) },
                    hazeState = hazeState
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarHeight + if (showMiniPlayer) 88.dp else 16.dp)
                .padding(horizontal = 16.dp),
        )

        // ── Layer 3: Download progress pill + monitor (global chrome) ──
        val downloadCenter: tf.monochrome.android.ui.downloads.DownloadCenterViewModel = hiltViewModel()
        val activeDownloads by downloadCenter.active.collectAsState()
        var pillHidden by rememberSaveable { mutableStateOf(false) }
        var showDownloadsMonitor by rememberSaveable { mutableStateOf(false) }
        // Re-show the pill whenever a fresh batch of downloads begins.
        LaunchedEffect(activeDownloads.isNotEmpty()) {
            if (activeDownloads.isNotEmpty()) pillHidden = false
        }
        val onChromeScreen = currentDestination?.route != Screen.NowPlaying.route &&
            currentDestination?.route != Screen.Mixer.route
        if (onChromeScreen && activeDownloads.isNotEmpty() && !pillHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarHeight + if (showMiniPlayer) 80.dp else 12.dp)
                    .padding(horizontal = 8.dp)
            ) {
                tf.monochrome.android.ui.downloads.DownloadProgressPill(
                    downloads = activeDownloads,
                    onClick = { showDownloadsMonitor = true },
                    onHide = { pillHidden = true },
                )
            }
        }
        if (showDownloadsMonitor) {
            tf.monochrome.android.ui.downloads.DownloadsMonitorSheet(
                downloads = activeDownloads,
                onCancel = downloadCenter::cancel,
                onCancelAll = downloadCenter::cancelAll,
                onDismiss = { showDownloadsMonitor = false },
            )
        }

        LaunchedEffect(navController) {
            AppDeepLinkRouter.uris.collect { uri ->
                routeForAppLink(uri)?.let { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

private fun routeForAppLink(uri: android.net.Uri): String? {
    if (uri.scheme != "https" || uri.host != "monochrome.tf") return null
    val segments = uri.pathSegments.filter { it.isNotBlank() }
    val head = segments.firstOrNull()?.lowercase() ?: return Screen.Home.route
    return when (head) {
        "album" -> segments.getOrNull(1)?.toLongOrNull()?.let(Screen.AlbumDetail::createRoute)
        "artist" -> segments.getOrNull(1)?.toLongOrNull()?.let(Screen.ArtistDetail::createRoute)
        "playlist" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
            Screen.PlaylistDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8"))
        }
        "local-album", "local_album" ->
            segments.getOrNull(1)?.toLongOrNull()?.let(Screen.LocalAlbumDetail::createRoute)
        "local-artist", "local_artist" ->
            segments.getOrNull(1)?.toLongOrNull()?.let(Screen.LocalArtistDetail::createRoute)
        "local-genre", "local_genre" ->
            segments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(Screen.LocalGenreDetail::createRoute)
        "folder" -> segments.drop(1).joinToString("/").takeIf { it.isNotBlank() }
            ?.let(Screen.FolderBrowser::createRoute)
        "search" -> Screen.Search.route
        "settings" -> uri.getQueryParameter("tab")?.toIntOrNull()
            ?.let(Screen.Settings::createRoute) ?: Screen.Settings.createRoute()
        "profile" -> Screen.Profile.route
        "stats" -> Screen.Stats.route
        "listening-stats", "listening_stats" -> Screen.ListeningStats.route
        "now-playing", "now_playing" -> Screen.NowPlaying.route
        "equalizer", "eq" -> Screen.Equalizer.route
        "parametric-eq", "parametric_eq" -> Screen.ParametricEq.route
        "mixer" -> Screen.Mixer.route
        "car-mode", "car_mode" -> Screen.CarMode.route
        "downloads" -> Screen.Downloads.route
        else -> null
    }
}
