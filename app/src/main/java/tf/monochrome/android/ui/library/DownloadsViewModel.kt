package tf.monochrome.android.ui.library

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.db.dao.DownloadDao
import tf.monochrome.android.data.db.entity.DownloadedTrackEntity
import tf.monochrome.android.domain.model.AudioCodec
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.PlaybackSource
import tf.monochrome.android.domain.model.SourceType
import tf.monochrome.android.domain.model.UnifiedTrack
import java.io.File
import javax.inject.Inject

/**
 * Loose grouping of downloaded tracks for the Albums section. Album rows are
 * keyed by (albumTitle, artistName) so a tracks-only download (album = null)
 * collapses into a synthetic "Singles" bucket.
 */
data class DownloadedAlbumGroup(
    val title: String,
    val artistName: String,
    val cover: String?,
    val trackCount: Int,
    val tracks: List<DownloadedTrackEntity>,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val appCtx: android.app.Application,
    private val downloadDao: DownloadDao
) : ViewModel() {

    val downloadedTracks: StateFlow<List<DownloadedTrackEntity>> =
        downloadDao.getDownloadedTracks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumGroups: StateFlow<List<DownloadedAlbumGroup>> =
        downloadDao.getDownloadedTracks()
            .map { entities ->
                entities
                    .groupBy { (it.albumTitle ?: SINGLES_LABEL) to it.artistName }
                    .map { (key, list) ->
                        val cover = list.firstNotNullOfOrNull { it.albumCover }
                        DownloadedAlbumGroup(
                            title = key.first,
                            artistName = key.second,
                            cover = cover,
                            trackCount = list.size,
                            // Within the album sort by trackNumber-equivalent
                            // proxy (downloadedAt) so the play order is stable.
                            tracks = list.sortedBy { it.downloadedAt },
                        )
                    }
                    .sortedBy { it.title.lowercase() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(track: DownloadedTrackEntity) {
        viewModelScope.launch {
            if (track.filePath.startsWith("content://")) {
                try {
                    val uri = track.filePath.toUri()
                    val docFile = DocumentFile.fromSingleUri(appCtx, uri)
                    docFile?.delete()
                } catch (e: Exception) {
                    // Ignore exceptions during content deletion
                }
            } else {
                val file = File(track.filePath)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteDownloadedTrack(track.id)
        }
    }

    companion object {
        const val SINGLES_LABEL = "Singles"
    }
}

// File-private extension: map a downloaded entity onto the unified track shape
// the player expects. Codec/sample-rate are inferred from the saved quality
// label; ExoPlayer sniffs the actual container so these are advisory.
fun DownloadedTrackEntity.toUnifiedTrack(): UnifiedTrack {
    val quality = runCatching { AudioQuality.valueOf(quality) }.getOrDefault(AudioQuality.LOSSLESS)
    val (codec, sampleRate, bitDepth) = when (quality) {
        AudioQuality.HI_RES -> Triple(AudioCodec.FLAC, 96_000, 24)
        AudioQuality.LOSSLESS -> Triple(AudioCodec.FLAC, 44_100, 16)
        AudioQuality.HIGH, AudioQuality.LOW -> Triple(AudioCodec.MP3, 44_100, null)
    }
    return UnifiedTrack(
        id = "download_$id",
        title = title,
        durationSeconds = duration,
        artistName = artistName.ifBlank { "Unknown Artist" },
        artistNames = listOfNotNull(artistName.takeIf { it.isNotBlank() }),
        albumArtistName = artistName.takeIf { it.isNotBlank() },
        albumTitle = albumTitle,
        albumId = albumTitle?.let { "download_album_${it.hashCode()}" },
        artworkUri = albumCover,
        source = PlaybackSource.LocalFile(
            filePath = filePath,
            codec = codec,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
        ),
        sourceType = SourceType.LOCAL,
    )
}
