package tf.monochrome.android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.local.repository.LocalMediaRepository
import tf.monochrome.android.domain.model.UnifiedAlbum
import tf.monochrome.android.domain.model.UnifiedArtist
import tf.monochrome.android.domain.model.UnifiedTrack
import javax.inject.Inject

@HiltViewModel
class LocalArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localMediaRepository: LocalMediaRepository
) : ViewModel() {

    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: 0L

    private val _artist = MutableStateFlow<UnifiedArtist?>(null)
    val artist: StateFlow<UnifiedArtist?> = _artist.asStateFlow()

    private val _albums = MutableStateFlow<List<UnifiedAlbum>>(emptyList())
    val albums: StateFlow<List<UnifiedAlbum>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val tracks: StateFlow<List<UnifiedTrack>> = localMediaRepository.getTracksByArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadArtist()
    }

    private fun loadArtist() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = localMediaRepository.getArtistById(artistId)
                if (result != null) {
                    _artist.value = result
                    // Load albums by this artist's name
                    localMediaRepository.getAlbumsByArtist(result.name).collect { albumList ->
                        _albums.value = albumList
                    }
                } else {
                    _error.value = "Artist not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load artist"
            }
            _isLoading.value = false
        }
    }

    fun retry() = loadArtist()
}
