package tf.monochrome.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val artist: Artist? = null,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val audioQuality: String? = null,
    val explicit: Boolean = false,
    val trackNumber: Int? = null,
    val volumeNumber: Int? = null,
    val popularity: Int? = null,
    val type: String = "track",
    val isUnavailable: Boolean? = null,
    val streamStartDate: String? = null
) {
    val displayArtist: String
        get() = artist?.name ?: artists.joinToString(", ") { it.name }

    val formattedDuration: String
        get() {
            val minutes = duration / 60
            val seconds = duration % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val coverUrl: String?
        get() = album?.coverUrl
}

@Serializable
data class Album(
    val id: Long,
    val title: String,
    val artist: Artist? = null,
    val artists: List<Artist> = emptyList(),
    val numberOfTracks: Int? = null,
    val releaseDate: String? = null,
    val cover: String? = null,
    val explicit: Boolean = false,
    val type: String? = null, // ALBUM, EP, SINGLE
    val duration: Int? = null
) {
    val coverUrl: String?
        get() = cover?.let { buildCoverUrl(it, 640) }

    val thumbnailUrl: String?
        get() = cover?.let { buildCoverUrl(it, 320) }

    val releaseYear: String?
        get() = releaseDate?.take(4)

    val displayArtist: String
        get() = artist?.name ?: artists.joinToString(", ") { it.name }
}

@Serializable
data class Artist(
    val id: Long,
    val name: String,
    val picture: String? = null,
    val artistTypes: List<String>? = null
) {
    val pictureUrl: String?
        get() = picture?.let { buildCoverUrl(it, 480) }
}

@Serializable
data class Playlist(
    val uuid: String,
    val title: String,
    val description: String? = null,
    val numberOfTracks: Int? = null,
    val duration: Int? = null,
    val cover: String? = null,
    val creator: PlaylistCreator? = null,
    val tracks: List<Track> = emptyList()
) {
    val coverUrl: String?
        get() = cover?.let { buildCoverUrl(it, 640) }
}

@Serializable
data class PlaylistCreator(
    val id: Long? = null,
    val name: String? = null
)

data class AlbumDetail(
    val album: Album,
    val tracks: List<Track>
)

data class ArtistDetail(
    val artist: Artist,
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val eps: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val unreleasedTracks: List<Track> = emptyList(),
    val similarArtists: List<Artist> = emptyList()
)

data class SearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

data class TrackStream(
    val track: Track,
    val streamUrl: String,
    val isDash: Boolean = false,
    val replayGain: ReplayGainValues? = null
)

data class ReplayGainValues(
    val trackReplayGain: Double? = null,
    val trackPeakAmplitude: Double? = null,
    val albumReplayGain: Double? = null,
    val albumPeakAmplitude: Double? = null
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false
)

enum class AudioQuality(val apiValue: String, val displayName: String) {
    LOW("LOW", "Low (96 kbps)"),
    HIGH("HIGH", "High (320 kbps)"),
    LOSSLESS("LOSSLESS", "Lossless (FLAC)"),
    HI_RES("HI_RES_LOSSLESS", "Hi-Res (24-bit FLAC)")
}

enum class RepeatMode {
    OFF, ONE, ALL
}

enum class ReplayGainMode {
    OFF, TRACK, ALBUM
}

enum class NowPlayingViewMode(val displayName: String) {
    COVER_ART("Cover Art"),
    LYRICS("Lyrics"),
    QUEUE("Queue"),
    VISUALIZER("Visualizer")
}

fun buildCoverUrl(coverId: String, size: Int): String {
    val formatted = coverId.replace("-", "/")
    return "https://resources.tidal.com/images/$formatted/${size}x${size}.jpg"
}
