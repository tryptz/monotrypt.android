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
import tf.monochrome.android.domain.model.UnifiedTrack
import javax.inject.Inject

@HiltViewModel
class LocalAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localMediaRepository: LocalMediaRepository
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: 0L

    private val _album = MutableStateFlow<UnifiedAlbum?>(null)
    val album: StateFlow<UnifiedAlbum?> = _album.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val tracks: StateFlow<List<UnifiedTrack>> = localMediaRepository.getTracksByAlbum(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadAlbum()
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = localMediaRepository.getAlbumById(albumId)
                if (result != null) {
                    _album.value = result
                } else {
                    _error.value = "Album not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load album"
            }
            _isLoading.value = false
        }
    }

    fun retry() = loadAlbum()
}
