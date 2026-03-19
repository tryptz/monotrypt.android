package tf.monochrome.android.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import tf.monochrome.android.data.api.HiFiApiClient
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.TrackStream
import tf.monochrome.android.domain.model.buildCoverUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver @Inject constructor(
    private val repository: MusicRepository
) {
    suspend fun resolveMediaItem(track: Track): Pair<MediaItem, TrackStream?> {
        val streamResult = repository.getTrackStream(track.id)
        val trackStream = streamResult.getOrNull()

        val mediaItem = if (trackStream != null) {
            buildMediaItem(track, trackStream.streamUrl, trackStream.isDash)
        } else {
            // Return a placeholder MediaItem - playback will fail but won't crash
            buildMediaItem(track, "", false)
        }

        return Pair(mediaItem, trackStream)
    }

    private fun buildMediaItem(track: Track, streamUrl: String, isDash: Boolean): MediaItem {
        val artworkUri = track.album?.cover?.let { cover ->
            Uri.parse(buildCoverUrl(cover, 640))
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayArtist)
            .setAlbumTitle(track.album?.title)
            .setArtworkUri(artworkUri)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.volumeNumber)
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadata)

        if (streamUrl.isNotBlank()) {
            if (isDash) {
                // For DASH, the streamUrl is raw MPD XML - we need to serve it differently
                builder.setUri(Uri.EMPTY)
                    .setMimeType("application/dash+xml")
            } else {
                builder.setUri(Uri.parse(streamUrl))
            }
        }

        return builder.build()
    }
}
