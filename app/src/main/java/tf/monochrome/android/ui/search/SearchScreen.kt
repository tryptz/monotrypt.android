package tf.monochrome.android.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import tf.monochrome.android.ui.components.AlbumItem
import tf.monochrome.android.ui.components.ArtistItem
import tf.monochrome.android.ui.components.AddToPlaylistSheet
import tf.monochrome.android.ui.components.CreatePlaylistDialog
import tf.monochrome.android.ui.components.LoadingScreen
import tf.monochrome.android.ui.components.SectionHeader
import tf.monochrome.android.ui.components.TrackContextMenu
import tf.monochrome.android.ui.components.TrackItem
import tf.monochrome.android.ui.components.liquidGlass
import tf.monochrome.android.ui.navigation.Screen
import tf.monochrome.android.ui.player.PlayerViewModel
import tf.monochrome.android.ui.theme.MonoDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()

    var showContextMenuForTrack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<tf.monochrome.android.domain.model.Track?>(null) }
    var showAddToPlaylistForTrack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<tf.monochrome.android.domain.model.Track?>(null) }
    var showCreatePlaylistDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = {
                        Text(
                            "Search tracks, albums, artists…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
            },
            expanded = false,
            onExpandedChange = {},
            colors = SearchBarDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .liquidGlass(shape = MonoDimens.shapePill)
        ) {}

        when {
            isSearching -> LoadingScreen()
            query.isBlank() -> {
                Text(
                    text = "Search for your favorite music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (artists.isNotEmpty()) {
                        item { SectionHeader(title = "Artists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(artists.take(10)) { artist ->
                                    ArtistItem(
                                        artist = artist,
                                        onClick = {
                                            navController.navigate(
                                                Screen.ArtistDetail.createRoute(artist.id)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item { SectionHeader(title = "Albums") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums.take(10)) { album ->
                                    AlbumItem(
                                        album = album,
                                        onClick = {
                                            navController.navigate(
                                                Screen.AlbumDetail.createRoute(album.id)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        item { SectionHeader(title = "Tracks") }
                        items(tracks.take(20)) { track ->
                            TrackItem(
                                track = track,
                                isLiked = favoriteTrackIds.contains(track.id),
                                onLikeClick = { playerViewModel.toggleFavorite(track) },
                                onClick = {
                                    playerViewModel.playTrack(track, tracks)
                                },
                                onLongClick = {
                                    showContextMenuForTrack = track
                                },
                                onMoreClick = {
                                    showContextMenuForTrack = track
                                },
                                onAlbumClick = track.album?.id?.let { albumId ->
                                    { navController.navigate(Screen.AlbumDetail.createRoute(albumId)) }
                                }
                            )
                        }
                    }

                    if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                        item {
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
