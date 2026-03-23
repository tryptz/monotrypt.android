package tf.monochrome.android.data.collections.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectionManifest(
    val version: String = "1.3",
    @SerialName("author_id") val authorId: String,
    @SerialName("collection_link") val collectionLink: String? = null,
    @SerialName("project_link") val projectLink: String? = null,
    val encryption: EncryptionConfig,
    val artists: List<ManifestArtist> = emptyList(),
    val albums: List<ManifestAlbum> = emptyList(),
    val tracks: List<ManifestTrack> = emptyList()
)

@Serializable
data class EncryptionConfig(
    val type: String = "AES-256-GCM",
    val key: String
)

@Serializable
data class ManifestArtist(
    val uuid: String,
    @SerialName("tidal_id") val tidalId: String? = null,
    val isni: String? = null,
    val name: String,
    val bio: String? = null,
    val genres: List<String> = emptyList(),
    val images: List<ManifestImage> = emptyList(),
    val socials: Map<String, String> = emptyMap()
)

@Serializable
data class ManifestAlbum(
    val uuid: String,
    @SerialName("tidal_id") val tidalId: String? = null,
    val upc: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("number_of_tracks") val numberOfTracks: Int? = null,
    @SerialName("is_single") val isSingle: Boolean = false,
    val type: String? = null,
    val explicit: Boolean = false,
    val label: String? = null,
    val copyright: String? = null,
    val images: List<ManifestImage> = emptyList(),
    val genres: List<String> = emptyList(),
    @SerialName("quality_tags") val qualityTags: List<String> = emptyList(),
    @SerialName("artist_uuids") val artistUuids: List<String> = emptyList()
)

@Serializable
data class ManifestTrack(
    val uuid: String,
    @SerialName("tidal_id") val tidalId: String? = null,
    @SerialName("album_uuid") val albumUuid: String,
    val isrc: String? = null,
    val title: String,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("track_number") val trackNumber: Int = 1,
    @SerialName("volume_number") val volumeNumber: Int = 1,
    val version: String? = null,
    val explicit: Boolean = false,
    @SerialName("replay_gain") val replayGain: Float? = null,
    @SerialName("quality_tags") val qualityTags: List<String> = emptyList(),
    val cover: String? = null,
    @SerialName("file_hash") val fileHash: String? = null,
    @SerialName("basic_lyrics") val basicLyrics: String? = null,
    @SerialName("lrc_lyrics") val lrcLyrics: String? = null,
    @SerialName("ttml_lyrics") val ttmlLyrics: String? = null,
    @SerialName("direct_links") val directLinks: List<ManifestDirectLink> = emptyList(),
    @SerialName("artist_uuids") val artistUuids: List<String> = emptyList()
)

@Serializable
data class ManifestDirectLink(
    val url: String,
    val quality: String
)

@Serializable
data class ManifestImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)
