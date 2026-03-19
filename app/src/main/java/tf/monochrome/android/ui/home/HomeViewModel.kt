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
import tf.monochrome.android.domain.model.AiFilter
import tf.monochrome.android.domain.model.Track
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

    // Derived directly from the persisted preference so Settings and Home are always in sync.
    val isAiMode: StateFlow<Boolean> = preferences.aiRadioEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _activeFilters = MutableStateFlow(setOf(AiFilter.ALL))
    val activeFilters: StateFlow<Set<AiFilter>> = _activeFilters.asStateFlow()

    init {
        loadHome()
    }

    private fun loadHome() {
        viewModelScope.launch {
            _isLoading.value = true
            libraryRepository.getHistory().collect { tracks ->
                _recentTracks.value = tracks.take(20)
                _isLoading.value = false

                // Auto-load recommendations from the first recent track if available
                if (_recommendedTracks.value.isEmpty() && tracks.isNotEmpty()) {
                    loadRecommendations(tracks.first().id)
                }
            }
        }
    }

    /** Toggle AI mode and immediately reload recommendations for the new mode. */
    fun toggleAiMode() {
        viewModelScope.launch {
            val newValue = !preferences.aiRadioEnabled.first()
            preferences.setAiRadioEnabled(newValue)
            // Reload so the list reflects the newly selected mode straight away.
            loadRecommendations()
        }
    }

    fun toggleFilter(filter: AiFilter) {
        val current = _activeFilters.value
        if (filter == AiFilter.ALL) {
            _activeFilters.value = setOf(AiFilter.ALL)
        } else {
            val withoutAll = current - AiFilter.ALL
            val updated = if (withoutAll.contains(filter)) {
                withoutAll - filter
            } else {
                withoutAll + filter
            }
            _activeFilters.value = updated.ifEmpty { setOf(AiFilter.ALL) }
        }
    }

    fun loadRecommendations(seedTrackId: Long? = null) {
        // Read the current persisted value synchronously via the StateFlow snapshot so
        // this function stays non-suspending and callable from Compose event handlers.
        if (isAiMode.value) {
            loadAiRecommendations(seedTrackId)
        } else {
            loadRegularRecommendations(seedTrackId)
        }
    }

    private fun loadRegularRecommendations(seedTrackId: Long?) {
        viewModelScope.launch {
            _isRadioLoading.value = true
            try {
                val seed = seedTrackId
                    ?: _recentTracks.value.randomOrNull()?.id
                    ?: _recommendedTracks.value.randomOrNull()?.id

                if (seed != null) {
                    val tracks = musicRepository.getRecommendations(seed).getOrDefault(emptyList())
                    if (tracks.isNotEmpty()) {
                        _recommendedTracks.value = tracks
                    }
                }
            } catch (_: Exception) { }
            finally {
                _isRadioLoading.value = false
            }
        }
    }

    private fun loadAiRecommendations(seedTrackId: Long?) {
        viewModelScope.launch {
            _isRadioLoading.value = true
            try {
                val seedTrack = seedTrackId?.let { id ->
                    _recentTracks.value.find { it.id == id }
                        ?: _recommendedTracks.value.find { it.id == id }
                } ?: _recentTracks.value.firstOrNull()

                if (seedTrack != null) {
                    val result = musicRepository.getAiRecommendations(
                        seedTrack = seedTrack,
                        filters = _activeFilters.value
                    )
                    val tracks = result.getOrNull()
                    if (!tracks.isNullOrEmpty()) {
                        _recommendedTracks.value = tracks
                    } else {
                        // Fallback to regular recommendations without leaking the loading state.
                        loadRegularRecommendations(seedTrackId)
                    }
                }
            } catch (_: Exception) {
                // Fallback to regular recommendations on AI failure.
                loadRegularRecommendations(seedTrackId)
            } finally {
                // Only clear loading if we're NOT handing off to the fallback, which manages its
                // own loading state. We detect that by checking whether _isRadioLoading was already
                // set to false by the fallback (it won't be yet — the fallback is a new coroutine).
                // So we unconditionally clear it here; the fallback will set it true→false itself.
                _isRadioLoading.value = false
            }
        }
    }

    /**
     * Triggers a fresh load of recommendations for the current mode, then returns whatever
     * tracks are already in the queue so the player can start immediately while new results load.
     */
    fun startInfiniteRadio(): List<Track> {
        loadRecommendations()
        return _recommendedTracks.value.ifEmpty { _recentTracks.value }
    }
}
