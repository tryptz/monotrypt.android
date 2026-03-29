package tf.monochrome.android.domain.usecase

import tf.monochrome.android.data.collections.repository.CollectionRepository
import tf.monochrome.android.data.local.repository.LocalMediaRepository
import tf.monochrome.android.domain.model.PlaybackSource
import tf.monochrome.android.domain.model.UnifiedTrack
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CrossSourceMatcher @Inject constructor(
    private val localMediaRepository: LocalMediaRepository,
    private val collectionRepository: CollectionRepository
) {
    /**
     * Find alternative playback sources for a track across all sources.
     * Returns sources ranked by priority (local first, then collection, then API).
     */
    suspend fun findAlternativeSources(track: UnifiedTrack): List<PlaybackSource> {
        val sources = mutableListOf<PlaybackSource>()

        // Tier 1: ISRC match (highest confidence)
        val isrc = track.isrc
        if (isrc != null) {
            // Check local
            localMediaRepository.findByIsrc(isrc)?.let { localTrack ->
                if (localTrack.source != track.source) {
                    sources.add(localTrack.source)
                }
            }

            // Check collections
            collectionRepository.findTrackByIsrc(isrc)?.let { colTrack ->
                if (colTrack.source != track.source) {
                    sources.add(colTrack.source)
                }
            }
        }

        return sources
    }

    companion object {
        fun normalizeForMatching(text: String): String {
            return Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFC)
        }

        fun fuzzyMatch(
            title1: String, artist1: String, duration1: Int,
            title2: String, artist2: String, duration2: Int
        ): Boolean {
            val normalTitle1 = normalizeForMatching(title1)
            val normalTitle2 = normalizeForMatching(title2)
            val normalArtist1 = normalizeForMatching(artist1)
            val normalArtist2 = normalizeForMatching(artist2)

            return normalTitle1 == normalTitle2 &&
                normalArtist1 == normalArtist2 &&
                abs(duration1 - duration2) < 3
        }
    }
}
