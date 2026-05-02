package tf.monochrome.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.repository.LibraryRepository
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _recentTracks = MutableStateFlow<List<Track>>(emptyList())
    val recentTracks: StateFlow<List<Track>> = _recentTracks.asStateFlow()

    private val _recommendedTracks = MutableStateFlow<List<Track>>(emptyList())
    val recommendedTracks: StateFlow<List<Track>> = _recommendedTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRadioLoading = MutableStateFlow(false)
    val isRadioLoading: StateFlow<Boolean> = _isRadioLoading.asStateFlow()


    init {
        loadHome()
    }

    private fun loadHome() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load instantly from cache
            try {
                val cachedJson = preferences.homeRecommendationsCache.first()
                if (!cachedJson.isNullOrBlank()) {
                    val tracks = Json { ignoreUnknownKeys = true }.decodeFromString<List<Track>>(cachedJson)
                    _recommendedTracks.value = tracks
                }
            } catch (_: Exception) {}

            try {
                val tracks = libraryRepository.getHistory().first()
                _recentTracks.value = tracks.take(20)
                _isLoading.value = false

                // Auto-load recommendations explicitly from history
                if (_recommendedTracks.value.isEmpty()) {
                    loadRecommendations()
                }
            } catch (e: Exception) {
                // Fallback: show empty state with recommendations
                _isLoading.value = false
                if (_recommendedTracks.value.isEmpty()) {
                    loadRecommendations()
                }
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isRadioLoading.value = true
            try {
                // Personalize home screen by focusing exclusively on user's past music choices
                val historyFields = libraryRepository.getHistory().first()
                if (historyFields.isNotEmpty()) {
                    // Generate a personalized mix directly from history
                    val mix = historyFields.shuffled().take(50)
                    _recommendedTracks.value = mix
                    preferences.setHomeRecommendationsCache(Json.encodeToString(mix))
                } else {
                    // Fallback for new user with empty history
                    val tracks = musicRepository.searchTracks("pop").getOrDefault(emptyList())
                    if (tracks.isNotEmpty()) {
                        _recommendedTracks.value = tracks
                        preferences.setHomeRecommendationsCache(Json.encodeToString(tracks))
                    }
                }
            } catch (_: Exception) { }
            finally {
                _isRadioLoading.value = false
            }
        }
    }

    /**
     * Triggers a fresh load of personalized history recommendations, then returns whatever
     * tracks are already available so the player can start immediately.
     */
    fun startHistoryMix(): List<Track> {
        loadRecommendations()
        return _recommendedTracks.value.ifEmpty { _recentTracks.value }
    }
}
