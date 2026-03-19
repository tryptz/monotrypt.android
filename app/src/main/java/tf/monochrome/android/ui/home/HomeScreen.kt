package tf.monochrome.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import tf.monochrome.android.domain.model.AiFilter
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.ui.components.AddToPlaylistSheet
import tf.monochrome.android.ui.components.CreatePlaylistDialog
import tf.monochrome.android.ui.components.LoadingScreen
import tf.monochrome.android.ui.components.SectionHeader
import tf.monochrome.android.ui.components.TrackContextMenu
import tf.monochrome.android.ui.components.TrackItem
import tf.monochrome.android.ui.player.PlayerViewModel
import tf.monochrome.android.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentTracks by viewModel.recentTracks.collectAsState()
    val recommendedTracks by viewModel.recommendedTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRadioLoading by viewModel.isRadioLoading.collectAsState()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()
    val isAiMode by viewModel.isAiMode.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()

    var showContextMenuForTrack by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Track?>(null)
    }
    var showAddToPlaylistForTrack by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Track?>(null)
    }
    var showCreatePlaylistDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    showContextMenuForTrack?.let { track ->
        TrackContextMenu(
            track = track,
            isLiked = favoriteTrackIds.contains(track.id),
            onDismiss = { showContextMenuForTrack = null },
            onPlayNext = { playerViewModel.playNext(track) },
            onAddToQueue = { playerViewModel.addToQueue(listOf(track)) },
            onToggleLike = { playerViewModel.toggleFavorite(track) },
            onAddToPlaylist = { showAddToPlaylistForTrack = track },
            onDownloadTrack = { playerViewModel.downloadTrack(track) },
            onGoToAlbum = track.album?.id?.let { albumId ->
                { navController.navigate(Screen.AlbumDetail.createRoute(albumId)) }
            },
            onGoToArtist = track.artist?.id?.let { artistId ->
                { navController.navigate(Screen.ArtistDetail.createRoute(artistId)) }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onSubmit = { name, description ->
                playerViewModel.createPlaylist(name, description)
                showCreatePlaylistDialog = false
            }
        )
    }

    showAddToPlaylistForTrack?.let { track ->
        AddToPlaylistSheet(
            track = track,
            playlists = playlists,
            onDismiss = { showAddToPlaylistForTrack = null },
            onPlaylistSelected = { playlist ->
                playerViewModel.addTrackToPlaylist(playlist.id, track)
                showAddToPlaylistForTrack = null
            },
            onCreateNew = {
                showAddToPlaylistForTrack = null
                showCreatePlaylistDialog = true
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Monochrome",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            actions = {
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (isLoading) {
            LoadingScreen()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // ── Recommended Songs + Infinite Radio ─────────────────
                item {
                    SectionHeader(
                        title = if (isAiMode) "AI Recommended" else "Recommended Songs"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    InfiniteRadioButton(
                        isLoading = isRadioLoading,
                        isAiMode = isAiMode,
                        onStartRadio = {
                            val tracks = viewModel.startInfiniteRadio()
                            if (tracks.isNotEmpty()) {
                                playerViewModel.playTrack(tracks.first(), tracks)
                            }
                        },
                        onRefresh = { viewModel.loadRecommendations() },
                        onToggleAi = { viewModel.toggleAiMode() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ── AI Filter Chips ────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = isAiMode,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        AiFilterChips(
                            activeFilters = activeFilters,
                            onToggleFilter = { viewModel.toggleFilter(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (recommendedTracks.isNotEmpty()) {
                    items(recommendedTracks) { track ->
                        TrackItem(
                            track = track,
                            isLiked = favoriteTrackIds.contains(track.id),
                            onLikeClick = { playerViewModel.toggleFavorite(track) },
                            onClick = { playerViewModel.playTrack(track, recommendedTracks) },
                            onLongClick = { showContextMenuForTrack = track },
                            onMoreClick = { showContextMenuForTrack = track },
                            onAlbumClick = track.album?.id?.let { albumId ->
                                { navController.navigate(Screen.AlbumDetail.createRoute(albumId)) }
                            }
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Play some music to get personalized recommendations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // ── Recently Played ────────────────────────────────────
                if (recentTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader(title = "Jump Back In")
                    }
                    items(recentTracks) { track ->
                        TrackItem(
                            track = track,
                            isLiked = favoriteTrackIds.contains(track.id),
                            onLikeClick = { playerViewModel.toggleFavorite(track) },
                            onClick = { playerViewModel.playTrack(track, recentTracks) },
                            onLongClick = { showContextMenuForTrack = track },
                            onMoreClick = { showContextMenuForTrack = track }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfiniteRadioButton(
    isLoading: Boolean,
    isAiMode: Boolean,
    onStartRadio: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAi: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isLoading) 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "refresh_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ElevatedButton(
            onClick = onStartRadio,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                if (isAiMode) Icons.Default.AutoAwesome else Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isAiMode) "Start AI Radio" else "Start Infinite Radio",
                style = MaterialTheme.typography.labelLarge
            )
        }

        IconButton(
            onClick = onToggleAi,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isAiMode) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isAiMode) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = if (isAiMode) "Disable AI" else "Enable AI",
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh recommendations",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiFilterChips(
    activeFilters: Set<AiFilter>,
    onToggleFilter: (AiFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiFilter.entries.forEach { filter ->
            FilterChip(
                selected = activeFilters.contains(filter),
                onClick = { onToggleFilter(filter) },
                label = {
                    Text(filter.displayName, style = MaterialTheme.typography.labelMedium)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    }
}
