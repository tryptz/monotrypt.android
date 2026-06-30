package tf.monochrome.android.spotify.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val name: String = "",
    val uri: String? = null,
    val artists: List<SpotifyArtist> = emptyList(),
    @SerialName("external_ids")
    val externalIds: SpotifyExternalIds? = null,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    val album: SpotifyAlbum? = null,
) {
    val stableId: String
        get() = id.orEmpty()

    val primaryArtistName: String
        get() = artists.firstOrNull()?.name.orEmpty()

    val primaryArtistId: String?
        get() = artists.firstOrNull()?.id

    val isUsable: Boolean
        get() = !id.isNullOrBlank() && name.isNotBlank()
}

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class SpotifyExternalIds(
    val isrc: String? = null,
)

@Serializable
data class SpotifyAlbum(
    val id: String? = null,
    val name: String? = null,
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
data class SpotifyImage(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
data class SpotifyTrackSearchResponse(
    val tracks: SpotifyTrackPage? = null,
)

@Serializable
data class SpotifyTrackPage(
    val items: List<SpotifyTrack> = emptyList(),
)

@Serializable
data class SpotifyCurrentlyPlayingResponse(
    val item: SpotifyTrack? = null,
)

@Serializable
data class SpotifyTopTracksResponse(
    val items: List<SpotifyTrack> = emptyList(),
)

@Serializable
data class SpotifySavedTracksResponse(
    val items: List<SpotifySavedTrackItem> = emptyList(),
)

@Serializable
data class SpotifySavedTrackItem(
    val track: SpotifyTrack? = null,
)

@Serializable
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylistSummary> = emptyList(),
)

@Serializable
data class SpotifyPlaylistSummary(
    val id: String? = null,
    val name: String = "",
    val collaborative: Boolean = false,
    val owner: SpotifyOwner? = null,
)

@Serializable
data class SpotifyOwner(
    val id: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
)

@Serializable
data class SpotifyPlaylistItemsResponse(
    val items: List<SpotifyPlaylistItem> = emptyList(),
)

@Serializable
data class SpotifyPlaylistItem(
    val item: SpotifyTrack? = null,
    val track: SpotifyTrack? = null,
)

@Serializable
data class SpotifyUserProfile(
    val id: String? = null,
    val email: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
)
