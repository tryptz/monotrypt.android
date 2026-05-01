package tf.monochrome.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.Album
import tf.monochrome.android.domain.model.Artist
import tf.monochrome.android.domain.model.PlaybackSource
import tf.monochrome.android.domain.model.Playlist
import tf.monochrome.android.domain.model.SourceType
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.UnifiedTrack
import tf.monochrome.android.domain.usecase.SearchUnifiedLibraryUseCase

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val unifiedLibrarySearch: SearchUnifiedLibraryUseCase,
    private val preferences: PreferencesManager
) : ViewModel() {

    /**
     * Search relevance favors title matches first, then artist and album context,
     * and finally applies a small source boost so locally playable results edge out
     * remote ones when textual relevance is otherwise identical.
     */
    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
        // Hard ceiling on the Qobuz fan-out so the TIDAL flow can never be
        // blocked behind a slow or hung Qobuz instance (coroutineScope waits
        // for all children, so an unbounded child would freeze the whole
        // search). HiFiApiClient.searchQobuz already times out per
        // sub-request — this is a belt-and-suspenders ceiling.
        private const val QOBUZ_BUDGET_MS = 7_000L
        private const val SCORE_WEIGHT_PRIMARY = 4_000
        private const val SCORE_WEIGHT_SECONDARY = 1_800
        private const val SCORE_WEIGHT_TERTIARY = 1_200
        private const val SCORE_PENALTY_PREFIX_MATCH = 500
        private const val SCORE_PENALTY_TOKEN_PREFIX_MATCH = 1_000
        private const val SCORE_PENALTY_SUBSTRING_MATCH = 1_500
        private const val SOURCE_BOOST_LOCAL = 90
        private const val SOURCE_BOOST_COLLECTION = 70
        private const val SOURCE_BOOST_API = 50
        private const val DEFAULT_ARTIST_NAME = "Unknown Artist"
    }

    enum class SearchTypeFilter(val label: String) {
        ALL("All"),
        TRACKS("Tracks"),
        ALBUMS("Albums"),
        ARTISTS("Artists"),
        PLAYLISTS("Playlists")
    }

    enum class SearchSourceFilter(val label: String, val sourceType: SourceType?) {
        ALL("All", null),
        TIDAL("TIDAL", SourceType.API),
        QOBUZ("Qobuz", SourceType.QOBUZ),
        LOCAL("Local", SourceType.LOCAL),
        COLLECTION("Collection", SourceType.COLLECTION)
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _allTracks = MutableStateFlow<List<UnifiedTrack>>(emptyList())
    private val _allAlbums = MutableStateFlow<List<Album>>(emptyList())
    private val _allArtists = MutableStateFlow<List<Artist>>(emptyList())
    private val _allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())

    private val _selectedType = MutableStateFlow(SearchTypeFilter.ALL)
    val selectedType: StateFlow<SearchTypeFilter> = _selectedType.asStateFlow()

    private val _selectedSource = MutableStateFlow(SearchSourceFilter.ALL)
    val selectedSource: StateFlow<SearchSourceFilter> = _selectedSource.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val searchHistory: StateFlow<List<String>> = preferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<UnifiedTrack>> = combine(
        _allTracks,
        _selectedType,
        _selectedSource
    ) { trackResults, type, source ->
        if (type != SearchTypeFilter.ALL && type != SearchTypeFilter.TRACKS) {
            emptyList()
        } else if (source == SearchSourceFilter.ALL) {
            trackResults
        } else {
            trackResults.filter { it.sourceType == source.sourceType }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = combine(_allAlbums, _selectedType) { albumResults, type ->
        if (type == SearchTypeFilter.ALL || type == SearchTypeFilter.ALBUMS) albumResults else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = combine(_allArtists, _selectedType) { artistResults, type ->
        if (type == SearchTypeFilter.ALL || type == SearchTypeFilter.ARTISTS) artistResults else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = combine(_allPlaylists, _selectedType) { playlistResults, type ->
        if (type == SearchTypeFilter.ALL || type == SearchTypeFilter.PLAYLISTS) playlistResults else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showSourceFilter: StateFlow<Boolean> = selectedType
        .combine(query) { type, currentQuery ->
            currentQuery.isNotBlank() && (type == SearchTypeFilter.ALL || type == SearchTypeFilter.TRACKS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            clearResults()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            performSearch(newQuery.trim())
        }
    }

    fun submitSearch() {
        val currentQuery = _query.value.trim()
        if (currentQuery.isBlank()) {
            clearResults()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(currentQuery)
        }
    }

    fun selectHistoryQuery(historyQuery: String) {
        _query.value = historyQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(historyQuery)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            preferences.clearSearchHistory()
        }
    }

    fun setSelectedType(type: SearchTypeFilter) {
        _selectedType.value = type
    }

    fun setSelectedSource(source: SearchSourceFilter) {
        _selectedSource.value = source
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        val trimmedQuery = query.trim()
        // TIDAL, Qobuz, and the local/collection library all run in parallel.
        // Qobuz failures (instance unset, network error, schema mismatch) are
        // swallowed so the existing TIDAL flow keeps working unchanged.
        val (searchResult, qobuzResult, unifiedResultsResult) = coroutineScope {
            val apiDeferred = async { runCatching { repository.search(trimmedQuery) } }
            val qobuzDeferred = async {
                withTimeoutOrNull(QOBUZ_BUDGET_MS) {
                    runCatching { repository.searchQobuz(trimmedQuery) }
                }
            }
            val libraryDeferred = async { runCatching { unifiedLibrarySearch.search(trimmedQuery).first() } }
            Triple(apiDeferred.await(), qobuzDeferred.await(), libraryDeferred.await())
        }
        val unifiedResults = unifiedResultsResult.getOrNull()

        val qobuzAvailable = qobuzResult?.isSuccess == true
        if (searchResult.isFailure && unifiedResults == null && !qobuzAvailable) {
            clearResults()
            _isSearching.value = false
            return
        }

        val localAndCollectionTracks = listOfNotNull(
            unifiedResults?.localTracks,
            unifiedResults?.collectionTracks
        ).flatten()

        val qobuzSearch = qobuzResult?.getOrNull()?.getOrNull()
        val qobuzTracks = qobuzSearch?.tracks?.map { it.toQobuzUnifiedTrack() } ?: emptyList()
        // Qobuz album/artist detail endpoints aren't wired yet — clicking a
        // Qobuz album would route through getAlbum(qobuzId) on the TIDAL pool
        // (or Dev Mode override) and either return HTML (SPA index) or 404,
        // surfacing as a JSON parse error in the detail screen. Drop them
        // from the merged results until /api/get-album and /api/get-artist
        // are wired. Tracks are still surfaced because their playback path
        // (PlaybackSource.QobuzCached → /api/download-music → app cache)
        // doesn't depend on a detail endpoint.

        if (searchResult.getOrNull()?.isSuccess == true) {
            val result = searchResult.getOrThrow().getOrThrow()
            _allTracks.value = scoreTracks(
                query = trimmedQuery,
                tracks = localAndCollectionTracks +
                    result.tracks.map { it.toUnifiedTrack() } +
                    qobuzTracks
            )
            _allAlbums.value = scoreItems(trimmedQuery, result.albums) { listOf(it.title, it.displayArtist) }
            _allArtists.value = scoreItems(trimmedQuery, result.artists) { listOf(it.name) }
            _allPlaylists.value = scoreItems(trimmedQuery, result.playlists) {
                listOfNotNull(it.title, it.creator?.name, it.description)
            }
            preferences.addSearchHistoryQuery(trimmedQuery)
        } else {
            // TIDAL is down — still surface Qobuz tracks + local results so
            // search doesn't feel broken when the public TIDAL pool is
            // unreachable.
            _allTracks.value = scoreTracks(
                query = trimmedQuery,
                tracks = localAndCollectionTracks + qobuzTracks
            )
            _allAlbums.value = emptyList()
            _allArtists.value = emptyList()
            _allPlaylists.value = emptyList()
        }
        _isSearching.value = false
    }

    private fun clearResults() {
        _allTracks.value = emptyList()
        _allAlbums.value = emptyList()
        _allArtists.value = emptyList()
        _allPlaylists.value = emptyList()
        _isSearching.value = false
    }

    private fun Track.toUnifiedTrack(): UnifiedTrack = UnifiedTrack(
        id = "api_$id",
        title = title,
        durationSeconds = duration,
        trackNumber = trackNumber,
        discNumber = volumeNumber,
        explicit = explicit,
        artistName = displayArtist.ifBlank { DEFAULT_ARTIST_NAME },
        artistNames = artists.map { it.name }.ifEmpty { listOfNotNull(artist?.name) },
        albumArtistName = artist?.name,
        albumTitle = album?.title,
        albumId = album?.id?.toString(),
        artworkUri = coverUrl,
        source = PlaybackSource.HiFiApi(tidalId = id),
        sourceType = SourceType.API
    )

    // Qobuz tracks share the Track shape with TIDAL but are tagged so the UI
    // can label them and so the existing dedup (distinctBy id) doesn't collapse
    // a Qobuz hit onto the same numeric ID from TIDAL. Source is QobuzCached
    // — playback fetches the file via /api/download-music into the app cache
    // and ExoPlayer plays from the local file.
    private fun Track.toQobuzUnifiedTrack(): UnifiedTrack = UnifiedTrack(
        id = "qobuz_$id",
        title = title,
        durationSeconds = duration,
        trackNumber = trackNumber,
        discNumber = volumeNumber,
        explicit = explicit,
        artistName = displayArtist.ifBlank { DEFAULT_ARTIST_NAME },
        artistNames = artists.map { it.name }.ifEmpty { listOfNotNull(artist?.name) },
        albumArtistName = artist?.name,
        albumTitle = album?.title,
        albumId = album?.id?.toString(),
        artworkUri = coverUrl,
        source = PlaybackSource.QobuzCached(qobuzId = id),
        sourceType = SourceType.QOBUZ,
    )

    private fun scoreTracks(
        query: String,
        tracks: List<UnifiedTrack>
    ): List<UnifiedTrack> = tracks
        .distinctBy { it.id }
        .sortedByDescending { track ->
            scoreField(query, track.title, SCORE_WEIGHT_PRIMARY) +
                scoreField(query, track.artistName, SCORE_WEIGHT_SECONDARY) +
                scoreField(query, track.albumTitle, SCORE_WEIGHT_TERTIARY) +
                sourceBoost(track.sourceType)
        }

    private fun <T> scoreItems(
        query: String,
        items: List<T>,
        fields: (T) -> List<String?>
    ): List<T> = items.sortedByDescending { item ->
        fields(item).mapIndexed { index, value ->
            scoreField(query, value, when (index) {
                0 -> SCORE_WEIGHT_PRIMARY
                1 -> SCORE_WEIGHT_SECONDARY
                else -> SCORE_WEIGHT_TERTIARY
            })
        }.sum()
    }

    private fun sourceBoost(sourceType: SourceType): Int = when (sourceType) {
        SourceType.LOCAL -> SOURCE_BOOST_LOCAL
        SourceType.COLLECTION -> SOURCE_BOOST_COLLECTION
        SourceType.API -> SOURCE_BOOST_API
        // Qobuz is download-only — keep below TIDAL streaming so taps default
        // to a playable result when both sources surface the same query.
        SourceType.QOBUZ -> SOURCE_BOOST_API - 5
    }

    private fun scoreField(query: String, rawValue: String?, baseScore: Int): Int {
        val normalizedQuery = query.normalized()
        val normalizedValue = rawValue?.normalized().orEmpty()
        if (normalizedQuery.isBlank() || normalizedValue.isBlank()) return 0
        return when {
            normalizedValue == normalizedQuery -> baseScore
            normalizedValue.startsWith(normalizedQuery) -> baseScore - SCORE_PENALTY_PREFIX_MATCH
            normalizedValue.tokenStartsWith(normalizedQuery) -> baseScore - SCORE_PENALTY_TOKEN_PREFIX_MATCH
            normalizedValue.contains(normalizedQuery) -> baseScore - SCORE_PENALTY_SUBSTRING_MATCH
            else -> 0
        }
    }

    private fun String.normalized(): String = lowercase().trim()

    private fun String.tokenStartsWith(query: String): Boolean =
        splitToSequence(' ', '-', '_', '/', '.', '(', ')').any { token -> token.startsWith(query) }
}
