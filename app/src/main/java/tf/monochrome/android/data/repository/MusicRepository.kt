package tf.monochrome.android.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import tf.monochrome.android.data.ai.AudioSnippetFetcher
import tf.monochrome.android.data.ai.GeminiClient
import tf.monochrome.android.data.api.HiFiApiClient
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.AiFilter
import tf.monochrome.android.domain.model.Album
import tf.monochrome.android.domain.model.AlbumDetail
import tf.monochrome.android.domain.model.Artist
import tf.monochrome.android.domain.model.ArtistDetail
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.Lyrics
import tf.monochrome.android.domain.model.Playlist
import tf.monochrome.android.domain.model.SearchResult
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.TrackStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val apiClient: HiFiApiClient,
    private val preferences: PreferencesManager,
    private val geminiClient: GeminiClient,
    private val audioSnippetFetcher: AudioSnippetFetcher,
    @ApplicationContext private val context: Context
) {
    // --- Search ---

    suspend fun search(query: String): Result<SearchResult> = runCatching {
        apiClient.search(query)
    }

    suspend fun searchQobuz(query: String): Result<SearchResult> = runCatching {
        apiClient.searchQobuz(query)
    }

    /**
     * Qobuz album-detail fetch. Returns Result.failure when the Qobuz instance
     * isn't configured or the lookup fails so the detail VM can fall back to
     * the TIDAL path cleanly.
     */
    suspend fun getQobuzAlbum(albumSlug: String): Result<AlbumDetail> = runCatching {
        apiClient.getQobuzAlbum(albumSlug)
            ?: throw IllegalStateException("Qobuz album not available: $albumSlug")
    }

    suspend fun getQobuzArtist(artistId: Long): Result<ArtistDetail> = runCatching {
        apiClient.getQobuzArtist(artistId)
            ?: throw IllegalStateException("Qobuz artist not available: $artistId")
    }

    suspend fun searchTracks(query: String): Result<List<Track>> = runCatching {
        apiClient.searchTracks(query)
    }

    suspend fun searchAlbums(query: String): Result<List<Album>> = runCatching {
        apiClient.searchAlbums(query)
    }

    suspend fun searchArtists(query: String): Result<List<Artist>> = runCatching {
        apiClient.searchArtists(query)
    }

    suspend fun searchPlaylists(query: String): Result<List<Playlist>> = runCatching {
        apiClient.searchPlaylists(query)
    }

    // --- Detail ---

    suspend fun getAlbum(albumId: Long): Result<AlbumDetail> = runCatching {
        apiClient.getAlbum(albumId)
    }

    suspend fun getArtist(artistId: Long): Result<ArtistDetail> = runCatching {
        apiClient.getArtist(artistId)
    }

    suspend fun getPlaylist(playlistId: String): Result<Playlist> = runCatching {
        apiClient.getPlaylist(playlistId)
    }

    // --- Streaming ---

    suspend fun getTrackStream(trackId: Long): Result<TrackStream> = runCatching {
        val quality = getEffectiveQuality()
        apiClient.getTrackStream(trackId, quality)
    }

    suspend fun getTrackStream(trackId: Long, quality: AudioQuality): Result<TrackStream> = runCatching {
        apiClient.getTrackStream(trackId, quality)
    }

    // --- Recommendations ---

    suspend fun getRecommendations(trackId: Long): Result<List<Track>> = runCatching {
        apiClient.getRecommendations(trackId)
    }

    suspend fun getMix(mixId: String): Result<List<Track>> = runCatching {
        apiClient.getMix(mixId)
    }

    // --- AI Recommendations ---

    suspend fun getAiRecommendations(
        seedTrack: Track,
        filters: Set<AiFilter>
    ): Result<List<Track>> = runCatching {
        // Get stream URL for the seed track
        val stream = apiClient.getTrackStream(seedTrack.id, getEffectiveQuality())
        require(stream.streamUrl.isNotBlank()) { "No stream URL available" }

        // Fetch audio snippet
        val snippet = audioSnippetFetcher.fetchSnippet(stream.streamUrl)

        // Build context string from track metadata
        val trackContext = buildString {
            append("\"${seedTrack.title}\"")
            seedTrack.artist?.let { append(" by ${it.name}") }
            seedTrack.album?.let { append(" from album \"${it.title}\"") }
            seedTrack.album?.releaseDate?.let { append(" (${it.take(4)})") }
        }

        // Get API key
        val apiKey = preferences.geminiApiKey.first()
        require(!apiKey.isNullOrBlank()) { "Gemini API key not configured" }

        // Get search queries from Gemini
        val queries = geminiClient.getAudioRecommendations(
            audioBytes = snippet.bytes,
            mimeType = snippet.mimeType,
            trackContext = trackContext,
            filters = filters,
            apiKey = apiKey
        )

        // Search for each query in parallel and collect results
        coroutineScope {
            queries.take(8).map { query ->
                async {
                    runCatching { apiClient.searchTracks(query) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
            .flatten()
            .distinctBy { it.id }
            .filter { it.id != seedTrack.id }
            .take(20)
    }

    // --- Lyrics ---

    suspend fun getLyrics(trackId: Long): Result<Lyrics?> = runCatching {
        val romajiEnabled = preferences.romajiLyrics.first()
        apiClient.getLyrics(trackId, romajiEnabled)
    }

    // --- Quality ---

    private suspend fun getEffectiveQuality(): AudioQuality {
        return if (isOnWifi()) {
            preferences.wifiQuality.first()
        } else {
            preferences.cellularQuality.first()
        }
    }

    private fun isOnWifi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
