package tf.monochrome.android.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response shape for the trypt-hifi (Qobuz) instance, captured from a real
// /api/get-music?q=... call. Top-level envelope is { success, data: { query,
// albums?, tracks?, artists? } } where each section paginates its own items.
// Only the section relevant to the user's active tab is populated; the others
// are absent. We accept all three on the same envelope so a single parser
// works regardless of which tab was searched.
@Serializable
data class QobuzSearchEnvelope(
    val success: Boolean = false,
    val data: QobuzSearchData? = null,
)

@Serializable
data class QobuzSearchData(
    val query: String? = null,
    val albums: QobuzPaginated<QobuzAlbumItem>? = null,
    val tracks: QobuzPaginated<QobuzTrackItem>? = null,
    val artists: QobuzPaginated<QobuzArtistItem>? = null,
)

@Serializable
data class QobuzPaginated<T>(
    val limit: Int? = null,
    val offset: Int? = null,
    val total: Int? = null,
    val items: List<T> = emptyList(),
)

@Serializable
data class QobuzAlbumItem(
    val id: String? = null,                // alphanumeric slug (e.g. "h8lxgr12m97wc")
    @SerialName("qobuz_id") val qobuzId: Long? = null,  // numeric id used by /api/download-music
    val title: String = "",
    val version: String? = null,
    val artist: QobuzArtistRef? = null,
    val artists: List<QobuzArtistRef> = emptyList(),
    val image: QobuzImage? = null,
    val duration: Int? = null,
    @SerialName("tracks_count") val tracksCount: Int? = null,
    @SerialName("release_date_original") val releaseDateOriginal: String? = null,
    @SerialName("parental_warning") val parentalWarning: Boolean = false,
    val downloadable: Boolean = true,
    val streamable: Boolean = true,
    val hires: Boolean = false,
    @SerialName("hires_streamable") val hiresStreamable: Boolean = false,
    @SerialName("maximum_bit_depth") val maximumBitDepth: Int? = null,
    @SerialName("maximum_sampling_rate") val maximumSamplingRate: Double? = null,
    val genre: QobuzGenre? = null,
    val label: QobuzLabel? = null,
    val upc: String? = null,
    val url: String? = null,
)

@Serializable
data class QobuzTrackItem(
    val id: Long? = null,
    val title: String = "",
    val version: String? = null,
    val duration: Int? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("media_number") val mediaNumber: Int? = null,
    // Real response uses "performer" as a single object {id, name} for the
    // primary credited artist on a track. There's also "performers" (string)
    // and "composer" (single object) which we keep optional.
    val performer: QobuzPerson? = null,
    val composer: QobuzPerson? = null,
    val performers: String? = null,
    val album: QobuzAlbumItem? = null,
    @SerialName("audio_info") val audioInfo: QobuzTrackAudioInfo? = null,
    @SerialName("parental_warning") val parentalWarning: Boolean = false,
    val downloadable: Boolean = true,
    val streamable: Boolean = true,
    val hires: Boolean = false,
    @SerialName("hires_streamable") val hiresStreamable: Boolean = false,
    @SerialName("maximum_bit_depth") val maximumBitDepth: Int? = null,
    @SerialName("maximum_sampling_rate") val maximumSamplingRate: Double? = null,
    val isrc: String? = null,
)

// Lightweight credit (track-level performer/composer). Response uses just
// {id, name} here, distinct from the richer QobuzArtistRef on albums.
@Serializable
data class QobuzPerson(
    val id: Long? = null,
    val name: String = "",
)

@Serializable
data class QobuzTrackAudioInfo(
    @SerialName("replaygain_track_gain") val replayGainTrackGain: Float? = null,
    @SerialName("replaygain_track_peak") val replayGainTrackPeak: Float? = null,
    @SerialName("replaygain_album_gain") val replayGainAlbumGain: Float? = null,
    @SerialName("replaygain_album_peak") val replayGainAlbumPeak: Float? = null,
)

@Serializable
data class QobuzArtistItem(
    val id: Long? = null,
    val name: String = "",
    val slug: String? = null,
    val image: QobuzImage? = null,
    val picture: String? = null,
    @SerialName("albums_count") val albumsCount: Int? = null,
)

@Serializable
data class QobuzArtistRef(
    val id: Long? = null,
    val name: String = "",
    val slug: String? = null,
    val image: QobuzImage? = null,
    val picture: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("albums_count") val albumsCount: Int? = null,
)

// Album covers: small/thumbnail/large/back. Artist images: small/medium/
// large/extralarge/mega. Both are accepted on the same type because
// ignoreUnknownKeys = true silently drops the missing fields per source.
@Serializable
data class QobuzImage(
    val small: String? = null,
    val thumbnail: String? = null,
    val large: String? = null,
    val back: String? = null,
    val medium: String? = null,
    val extralarge: String? = null,
    val mega: String? = null,
)

@Serializable
data class QobuzGenre(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class QobuzLabel(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
)

// Response shape for /api/download-music?track_id=...&quality=...
// Captured response is small (~0.2 KB) and contains a per-request HMAC-signed
// stream URL. Field name hasn't been confirmed yet, so the parser tries
// several common keys; this model covers the one most likely to match.
@Serializable
data class QobuzDownloadEnvelope(
    val success: Boolean = false,
    val data: QobuzDownloadData? = null,
    // Some hifi-api forks emit the URL at the top level instead of under data.
    val url: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)

@Serializable
data class QobuzDownloadData(
    val url: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val format: String? = null,
    val mime_type: String? = null,
    val sampling_rate: Double? = null,
    val bit_depth: Int? = null,
)
