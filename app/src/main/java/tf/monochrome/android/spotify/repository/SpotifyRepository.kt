package tf.monochrome.android.spotify.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tf.monochrome.android.spotify.api.SpotifyApi
import tf.monochrome.android.spotify.api.SpotifyApiException
import tf.monochrome.android.spotify.api.SpotifyAuthRequiredException
import tf.monochrome.android.spotify.api.model.SpotifyArtistDetails
import tf.monochrome.android.spotify.api.model.SpotifyTrack
import tf.monochrome.android.spotify.auth.SpotifyAuthManager

sealed class SpotifyRadioFailure {
    data object NotConnected : SpotifyRadioFailure()
    data object ReauthRequired : SpotifyRadioFailure()
    data object PremiumRequired : SpotifyRadioFailure()
    data object ScopeMissing : SpotifyRadioFailure()
    data object RateLimited : SpotifyRadioFailure()
    data object EndpointUnavailable : SpotifyRadioFailure()
    data object NoResolvableTracks : SpotifyRadioFailure()
}

class SpotifyRepository(
    private val api: SpotifyApi,
    private val authManager: SpotifyAuthManager,
) {
    private val _failures = MutableSharedFlow<SpotifyRadioFailure>(extraBufferCapacity = 8)
    val failures: SharedFlow<SpotifyRadioFailure> = _failures.asSharedFlow()

    fun isAuthenticated(): Boolean = authManager.authState.value.isAuthenticated

    suspend fun hasUsableToken(): Boolean = authManager.validAccessToken() != null

    fun launchAuth() = authManager.launchAuthActivity()

    suspend fun searchTrack(artist: String, title: String): SpotifyTrack? {
        val cleanTitle = title.substringBefore(" — ").substringBefore(" - ").trim().ifBlank { title }
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        val query = "track:\"$cleanTitle\" artist:\"$cleanArtist\""
        return spotifyCall("searchTrack structured") { api.searchTrack(query) }
            ?: spotifyCall("searchTrack broad") { api.searchTrack("$cleanArtist $cleanTitle") }
    }

    suspend fun searchTracks(query: String, targetCount: Int = 30): List<SpotifyTrack> =
        spotifyCall("searchTracksPaged") { api.searchTracksPaged(query, targetCount) }.orEmpty()

    suspend fun getArtist(artistId: String): SpotifyArtistDetails? =
        spotifyCall("artist") { api.artist(artistId) }

    suspend fun getArtistTopTracksDisabled(artistId: String): List<SpotifyTrack> {
        Log.w(TAG, "Skipping artist top tracks for $artistId. Endpoint is unavailable in Spotify Development Mode.")
        _failures.tryEmit(SpotifyRadioFailure.EndpointUnavailable)
        return emptyList()
    }

    suspend fun searchArtistsByGenre(genre: String): List<SpotifyArtistDetails> =
        spotifyCall("searchArtistsByGenre") { api.searchArtists("genre:\"$genre\"") }.orEmpty()

    suspend fun getRecentlyPlayed(): List<SpotifyTrack> =
        spotifyCall("recentlyPlayed") { api.recentlyPlayed() }.orEmpty()

    suspend fun getTopTracks(): List<SpotifyTrack> =
        spotifyCall("topTracks") { api.topTracks() }.orEmpty()

    suspend fun getSavedTracks(): List<SpotifyTrack> =
        spotifyCall("savedTracks") { api.savedTracks() }.orEmpty()

    suspend fun getOwnedOrCollaborativePlaylistTracks(maxPlaylists: Int = 6): List<SpotifyTrack> =
        spotifyCall("playlistTracks") {
            val profile = runCatching { api.me() }.getOrNull()
            api.userPlaylists().items
                .filter { playlist ->
                    val ownerId = playlist.owner?.id
                    playlist.collaborative || (profile?.id != null && ownerId == profile.id)
                }
                .mapNotNull { it.id }
                .take(maxPlaylists)
                .flatMap { playlistId ->
                    runCatching { api.playlistItems(playlistId) }
                        .onFailure { Log.w(TAG, "Playlist items unavailable for $playlistId", it) }
                        .getOrDefault(emptyList())
                }
        }.orEmpty()

    suspend fun getCurrentlyPlaying(): SpotifyTrack? =
        spotifyCall("currentlyPlaying") { api.currentlyPlaying() }

    private suspend fun <T> spotifyCall(operation: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            val failure = e.toSpotifyRadioFailure()
            Log.w(TAG, operation + " failed: " + e.safeSpotifyMessage())
            failure?.let { _failures.tryEmit(it) }
            null
        }

    private fun Throwable.toSpotifyRadioFailure(): SpotifyRadioFailure? = when (this) {
        is SpotifyAuthRequiredException -> SpotifyRadioFailure.ReauthRequired
        is SpotifyApiException -> when {
            isOwnerPremiumRequired -> SpotifyRadioFailure.PremiumRequired
            isScopeMissing -> SpotifyRadioFailure.ScopeMissing
            isRateLimited -> SpotifyRadioFailure.RateLimited
            isEndpointUnavailable -> SpotifyRadioFailure.EndpointUnavailable
            else -> null
        }
        else -> null
    }

    private fun Throwable.safeSpotifyMessage(): String = when (this) {
        is SpotifyApiException -> message ?: "Spotify API error"
        is SpotifyAuthRequiredException -> message ?: "Spotify authorization is required"
        else -> this::class.java.simpleName + (message?.take(180)?.let { ": $it" } ?: "")
    }

    private companion object {
        const val TAG = "SpotifyApi"
    }
}
