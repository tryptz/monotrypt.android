package tf.monochrome.android.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import tf.monochrome.android.data.cache.QobuzStreamCacheManager
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.CollectionDirectLink
import tf.monochrome.android.domain.model.PlaybackSource
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.TrackStream
import tf.monochrome.android.domain.model.UnifiedTrack
import tf.monochrome.android.domain.model.buildCoverUrl
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedMedia(
    val mediaItem: MediaItem,
    val trackStream: TrackStream? = null,
    val isLocalFile: Boolean = false,
    val isEncrypted: Boolean = false,
    val encryptionKey: String? = null,
    val isDash: Boolean = false,
    // False when stream resolution failed and the item has no playable URI.
    // Callers must skip rather than feed it to ExoPlayer; otherwise
    // FileDataSource opens an empty path → ENOENT, or
    // DefaultMediaSourceFactory NPEs on a null localConfiguration.
    val isPlayable: Boolean = true,
)

@Singleton
class StreamResolver @Inject constructor(
    private val repository: MusicRepository,
    private val qobuzCache: QobuzStreamCacheManager,
) {
    private fun normalizeArtworkUri(raw: String?): Uri? {
        if (raw.isNullOrBlank()) return null
        val parsed = raw.toUri()
        return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(raw)) else parsed
    }

    // Legacy method for existing Track model. Returns (null, null) when the
    // stream couldn't be resolved — callers must skip instead of feeding an
    // empty MediaItem to ExoPlayer.
    suspend fun resolveMediaItem(track: Track): Pair<MediaItem?, TrackStream?> {
        val streamResult = repository.getTrackStream(track.id)
        val trackStream = streamResult.getOrNull()
            ?: return Pair(null, null)

        // For non-DASH streams the URL must be non-blank — DASH carries its
        // payload inline via base64-encoded MPD and the URL is therefore
        // intentionally empty at this stage; PlaybackService rebuilds the
        // DashMediaSource separately.
        if (!trackStream.isDash && trackStream.streamUrl.isBlank()) {
            return Pair(null, trackStream)
        }

        return Pair(buildMediaItem(track, trackStream.streamUrl, trackStream.isDash), trackStream)
    }

    // New method for UnifiedTrack
    @OptIn(UnstableApi::class)
    suspend fun resolveUnifiedTrack(track: UnifiedTrack): ResolvedMedia {
        return when (val source = track.source) {
            is PlaybackSource.LocalFile -> resolveLocalFile(track, source)
            is PlaybackSource.CollectionDirect -> resolveCollectionDirect(track, source)
            is PlaybackSource.HiFiApi -> resolveHiFiApi(track, source)
            is PlaybackSource.QobuzCached -> resolveQobuzCached(track, source)
        }
    }

    // Qobuz resolution = "fetch via /api/download-music, park in app cache,
    // play from local file". The cache manager dedupes concurrent plays of
    // the same track and evicts oldest entries when over the size cap. If
    // Qobuz isn't configured or the fetch fails, mark the result not playable
    // so PlaybackService can skip it instead of handing ExoPlayer a
    // FileDataSource with an empty path (ENOENT spam).
    private suspend fun resolveQobuzCached(
        track: UnifiedTrack,
        source: PlaybackSource.QobuzCached,
    ): ResolvedMedia {
        val cachedFile = qobuzCache.getOrFetch(source.qobuzId, source.preferredQuality)

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(normalizeArtworkUri(track.artworkUri))
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .apply { cachedFile?.let { setUri(Uri.fromFile(it)) } }
            .setMediaMetadata(metadata)
            .build()

        return ResolvedMedia(
            mediaItem = mediaItem,
            isLocalFile = cachedFile != null,
            isPlayable = cachedFile != null,
        )
    }

    private fun resolveLocalFile(
        track: UnifiedTrack,
        source: PlaybackSource.LocalFile
    ): ResolvedMedia {
        // DownloadWorker stores filePath either as an absolute filesystem path
        // (internal storage) or as a content:// URI string (when the user
        // picked a SAF folder). Wrapping a content:// path with File(...) +
        // Uri.fromFile produces a malformed file:// URI that ExoPlayer can't
        // open and DefaultMediaSourceFactory NPEs on. Detect the scheme and
        // route accordingly.
        val uri = if (source.filePath.startsWith("content://") ||
            source.filePath.startsWith("file://")
        ) {
            source.filePath.toUri()
        } else {
            Uri.fromFile(File(source.filePath))
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(normalizeArtworkUri(track.artworkUri))
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        return ResolvedMedia(
            mediaItem = mediaItem,
            isLocalFile = true
        )
    }

    private fun resolveCollectionDirect(
        track: UnifiedTrack,
        source: PlaybackSource.CollectionDirect
    ): ResolvedMedia {
        val bestLink = selectBestLink(source.directLinks, source.preferredQuality.apiValue)

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(normalizeArtworkUri(track.artworkUri))
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .apply { bestLink?.url?.takeIf { it.isNotBlank() }?.let { setUri(it.toUri()) } }
            .setMediaMetadata(metadata)
            .build()

        return ResolvedMedia(
            mediaItem = mediaItem,
            isEncrypted = true,
            encryptionKey = source.encryptionKey,
            isPlayable = bestLink?.url?.isNotBlank() == true,
        )
    }

    private suspend fun resolveHiFiApi(
        track: UnifiedTrack,
        source: PlaybackSource.HiFiApi
    ): ResolvedMedia {
        val streamResult = repository.getTrackStream(source.tidalId)
        val trackStream = streamResult.getOrNull()

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(normalizeArtworkUri(track.artworkUri))
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .apply {
                if (trackStream != null && trackStream.streamUrl.isNotBlank() && !trackStream.isDash) {
                    setUri(trackStream.streamUrl.toUri())
                }
            }
            .build()

        // DASH carries its MPD inline (PlaybackService rebuilds the source),
        // so an unset URI is fine in that case. Otherwise we need a real URL.
        val isDash = trackStream?.isDash == true
        val isPlayable = trackStream != null &&
            (isDash || trackStream.streamUrl.isNotBlank())

        return ResolvedMedia(
            mediaItem = mediaItem,
            trackStream = trackStream,
            isDash = isDash,
            isPlayable = isPlayable,
        )
    }

    private fun selectBestLink(
        links: List<CollectionDirectLink>,
        preferredQuality: String
    ): CollectionDirectLink? {
        // Try preferred quality first
        links.firstOrNull { it.quality == preferredQuality }?.let { return it }

        // Quality priority order
        val qualityOrder = listOf("HI_RES_LOSSLESS", "HI_RES", "LOSSLESS", "HIGH", "LOW")
        for (quality in qualityOrder) {
            links.firstOrNull { it.quality == quality }?.let { return it }
        }

        return links.firstOrNull()
    }

    private fun buildMediaItem(track: Track, streamUrl: String, isDash: Boolean): MediaItem {
        val artworkUri = track.album?.cover?.let { cover ->
            buildCoverUrl(cover, 640).toUri()
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

        // DASH has no progressive URL — PlaybackService synthesises a
        // data: URI at play time. For everything else, attach the URL.
        if (!isDash && streamUrl.isNotBlank()) {
            builder.setUri(streamUrl.toUri())
        }

        return builder.build()
    }
}
