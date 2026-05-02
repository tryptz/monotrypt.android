package tf.monochrome.android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tf.monochrome.android.data.api.QobuzIdRegistry
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.ArtistDetail
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val qobuzIdRegistry: QobuzIdRegistry,
) : ViewModel() {

    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: 0L

    private val _artistDetail = MutableStateFlow<ArtistDetail?>(null)
    val artistDetail: StateFlow<ArtistDetail?> = _artistDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadArtist()
    }

    /**
     * Qobuz-aware load — same pattern as AlbumDetailViewModel. When the
     * registry has tagged this id as Qobuz (i.e. the user navigated from a
     * Qobuz search hit or top-track row), hit /api/get-artist on trypt-hifi
     * first; on failure, or for non-Qobuz ids, fall through to the TIDAL
     * pool's /artist endpoint. Result-wrapped at both layers so a parse
     * failure surfaces as a clean error string instead of a crash.
     */
    private fun loadArtist() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val qobuzResult = if (qobuzIdRegistry.isQobuzArtist(artistId)) {
                repository.getQobuzArtist(artistId)
            } else null

            val finalResult = if (qobuzResult?.isSuccess == true) {
                qobuzResult
            } else {
                repository.getArtist(artistId)
            }

            finalResult
                .onSuccess { _artistDetail.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load artist" }
            _isLoading.value = false
        }
    }

    fun retry() = loadArtist()
}
