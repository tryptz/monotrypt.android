package tf.monochrome.android.spotify.api.model

import kotlinx.serialization.Serializable

/**
 * Models for the Spotify radio engine. Spotify is used only as a taste/metadata
 * signal; all candidates must still resolve to a local or Qobuz playback source.
 */

@Serializable
data class SpotifyArtistDetails(
    val id: String? = null,
    val name: String = "",
    val genres: List<String> = emptyList(),
)

@Serializable
data class SpotifyArtistSearchResponse(
    val artists: SpotifyArtistPage? = null,
)

@Serializable
data class SpotifyArtistPage(
    val items: List<SpotifyArtistDetails> = emptyList(),
)

@Serializable
data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyPlayHistoryItem> = emptyList(),
)

@Serializable
data class SpotifyPlayHistoryItem(
    val track: SpotifyTrack? = null,
)
