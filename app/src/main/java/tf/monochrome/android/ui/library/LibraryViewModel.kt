package tf.monochrome.android.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.db.entity.UserPlaylistEntity
import tf.monochrome.android.data.repository.LibraryRepository
import tf.monochrome.android.domain.model.Album
import tf.monochrome.android.domain.model.Artist
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.data.import_.CsvPlaylistParser
import tf.monochrome.android.data.repository.MusicRepository
import android.net.Uri
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val csvPlaylistParser: CsvPlaylistParser,
    private val musicRepository: MusicRepository
) : ViewModel() {

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            libraryRepository.createPlaylist(name, description)
        }
    }

    fun importCsvPlaylist(uri: Uri, strictAlbumMatch: Boolean, name: String, description: String?) {
        viewModelScope.launch {
            val playlistId = libraryRepository.createPlaylist(name, description)
            val result = csvPlaylistParser.parseFromUri(uri)
            val parsedPlaylist = result.getOrNull() ?: return@launch
            
            for (csvTrack in parsedPlaylist.tracks) {
                val query = "${csvTrack.title} ${csvTrack.artist}"
                val results = musicRepository.searchTracks(query).getOrNull() ?: continue
                
                val bestMatch = if (strictAlbumMatch && csvTrack.album.isNotBlank()) {
                    results.find { it.album?.title?.equals(csvTrack.album, ignoreCase = true) == true }
                        ?: results.firstOrNull()
                } else {
                    results.firstOrNull()
                }

                if (bestMatch != null) {
                    libraryRepository.addTrackToPlaylist(playlistId, bestMatch)
                }
            }
        }
    }

    val favoriteTracks: StateFlow<List<Track>> = libraryRepository.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<Track>> = libraryRepository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteAlbums: StateFlow<List<Album>> = libraryRepository.getFavoriteAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteArtists: StateFlow<List<Artist>> = libraryRepository.getFavoriteArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<UserPlaylistEntity>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
