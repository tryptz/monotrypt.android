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
import tf.monochrome.android.domain.model.Track
import javax.inject.Inject

@HiltViewModel
class MixViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val mixId: String = checkNotNull(savedStateHandle["id"])

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMix()
    }

    fun loadMix() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            musicRepository.getMix(mixId)
                .onSuccess { tracks ->
                    _tracks.value = tracks
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Failed to load mix"
                }

            _isLoading.value = false
        }
    }
}
