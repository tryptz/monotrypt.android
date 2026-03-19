package tf.monochrome.android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.AlbumDetail
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: 0L

    private val _albumDetail = MutableStateFlow<AlbumDetail?>(null)
    val albumDetail: StateFlow<AlbumDetail?> = _albumDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadAlbum()
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getAlbum(albumId)
                .onSuccess { _albumDetail.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load album" }
            _isLoading.value = false
        }
    }

    fun retry() = loadAlbum()
}
