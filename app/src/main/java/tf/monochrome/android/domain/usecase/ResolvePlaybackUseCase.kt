package tf.monochrome.android.domain.usecase

import tf.monochrome.android.domain.model.AudioCodec
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.PlaybackSource
import tf.monochrome.android.domain.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolvePlaybackUseCase @Inject constructor(
    private val crossSourceMatcher: CrossSourceMatcher
) {
    /**
     * Decides the best playback source for a given track.
     *
     * Priority:
     *   1. Local file (instant, zero network, best UX)
     *   2. Collection direct link (encrypted stream)
     *   3. Hi-Fi API stream (requires live network)
     *
     * Quality-aware: if local file is low quality but collection has
     * hi-res, and user prefers hi-res, prefer collection.
     */
    suspend fun resolve(
        track: UnifiedTrack,
        preferHighQuality: Boolean = true,
        hasNetwork: Boolean = true
    ): PlaybackSource {
        val alternatives = crossSourceMatcher.findAlternativeSources(track)
        val allSources = listOf(track.source) + alternatives

        // Local file - prefer if quality is acceptable
        val localSources = allSources.filterIsInstance<PlaybackSource.LocalFile>()
        if (localSources.isNotEmpty()) {
            val best = localSources.maxByOrNull { qualityScore(it) }!!
            if (!preferHighQuality || meetsQualityThreshold(best)) {
                return best
            }
        }

        // Collection direct - if available
        val collectionSources = allSources.filterIsInstance<PlaybackSource.CollectionDirect>()
        if (collectionSources.isNotEmpty() && hasNetwork) {
            return collectionSources.first()
        }

        // API stream - if network available
        val apiSources = allSources.filterIsInstance<PlaybackSource.HiFiApi>()
        if (apiSources.isNotEmpty() && hasNetwork) {
            return apiSources.first()
        }

        // Fallback: return any local source even if low quality, or original
        return localSources.firstOrNull() ?: track.source
    }

    private fun qualityScore(source: PlaybackSource.LocalFile): Int {
        val codecScore = when (source.codec) {
            AudioCodec.FLAC -> 100
            AudioCodec.ALAC -> 95
            AudioCodec.WAV -> 90
            AudioCodec.AIFF -> 90
            AudioCodec.OGG_VORBIS -> 60
            AudioCodec.OPUS -> 55
            AudioCodec.AAC -> 50
            AudioCodec.MP3 -> 40
            else -> 20
        }
        val bitDepthScore = (source.bitDepth ?: 16) * 2
        val sampleRateScore = source.sampleRate / 1000
        return codecScore + bitDepthScore + sampleRateScore
    }

    private fun meetsQualityThreshold(source: PlaybackSource.LocalFile): Boolean {
        // Consider lossless codecs as always meeting threshold
        return source.codec in setOf(
            AudioCodec.FLAC, AudioCodec.ALAC, AudioCodec.WAV, AudioCodec.AIFF, AudioCodec.APE
        )
    }
}
