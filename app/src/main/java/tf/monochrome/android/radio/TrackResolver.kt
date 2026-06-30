package tf.monochrome.android.radio

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.first
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import tf.monochrome.android.data.api.QobuzIdRegistry
import tf.monochrome.android.data.local.repository.LocalMediaRepository
import tf.monochrome.android.data.recommendations.SpotifyFeatureDb
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.SourceType
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.UnifiedTrack
import tf.monochrome.android.domain.usecase.toQobuzUnifiedTrack
import tf.monochrome.android.player.UnifiedTrackRegistry
import tf.monochrome.android.spotify.api.model.SpotifyTrack
import javax.inject.Inject
import javax.inject.Singleton

enum class TrackSource { QOBUZ, LOCAL }

sealed class RadioTrackResult {
    data class Resolved(
        val source: TrackSource,
        val track: MediaItem,
        val unifiedTrack: UnifiedTrack,
        val reason: String,
        val score: Float,
    ) : RadioTrackResult()

    data class Skipped(
        val spotifyId: String,
        val reason: String,
    ) : RadioTrackResult()
}

data class RadioSkipEvent(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val reason: String,
)

@Singleton
class TrackResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val qobuzIdRegistry: QobuzIdRegistry,
    private val unifiedTrackRegistry: UnifiedTrackRegistry,
) {
    private val similarity = JaroWinklerSimilarity()

    suspend fun resolve(spotifyTrack: SpotifyTrack): RadioTrackResult {
        val artist = spotifyTrack.primaryArtistName
        val title = spotifyTrack.name
        val spotifyId = spotifyTrack.stableId.ifBlank { radioKey(title, artist, spotifyTrack.externalIds?.isrc) }
        val isrc = spotifyTrack.externalIds?.isrc?.takeIf { it.isNotBlank() }

        if (title.isBlank() || artist.isBlank()) {
            return RadioTrackResult.Skipped(spotifyId, "Missing title or artist")
        }

        isrc?.let { code ->
            localMediaRepository.findByIsrc(code)?.let { local ->
                val unified = registerUnified(local.asSpotifyRadio())
                return resolved(TrackSource.LOCAL, unified, spotifyId, "local_isrc", 100f)
            }
        }

        localMediaRepository.findByArtistTitle(artist = artist, title = title)?.let { local ->
            val unified = registerUnified(local.asSpotifyRadio())
            return resolved(TrackSource.LOCAL, unified, spotifyId, "local_exact", 90f)
        }

        isrc?.let { code ->
            val qobuz = runCatching { musicRepository.searchQobuz(code).getOrNull() }
                .getOrNull()
                ?.tracks
                ?.firstOrNull { it.title.isNotBlank() }
            if (qobuz != null) {
                val unified = registerQobuzTrack(qobuz)
                return resolved(TrackSource.QOBUZ, unified, spotifyId, "qobuz_isrc", 85f)
            }
        }

        val qobuzCandidates = runCatching { musicRepository.searchQobuz("$artist $title").getOrNull() }
            .getOrNull()
            ?.tracks
            .orEmpty()
            .take(5)
        bestQobuzMatch(qobuzCandidates, title, artist)?.let {
            val unified = registerQobuzTrack(it)
            return resolved(TrackSource.QOBUZ, unified, spotifyId, "qobuz_fuzzy", 70f)
        }

        val local = runCatching {
            bestLocalMatch(localMediaRepository.searchTracks(title).first(), title, artist)
        }.getOrNull()
        if (local != null) {
            val unified = registerUnified(local.asSpotifyRadio())
            return resolved(TrackSource.LOCAL, unified, spotifyId, "local_fuzzy", 60f)
        }

        return RadioTrackResult.Skipped(spotifyId, "No local or Qobuz match")
    }

    fun registerUnified(unifiedTrack: UnifiedTrack): UnifiedTrack {
        val legacy = unifiedTrack.toLegacyTrack()
        unifiedTrackRegistry.put(legacy.id, unifiedTrack)
        return unifiedTrack
    }

    private fun registerQobuzTrack(track: Track): UnifiedTrack {
        qobuzIdRegistry.registerTrack(track.id)
        track.artist?.id?.let { qobuzIdRegistry.registerArtist(it) }
        track.artists.forEach { qobuzIdRegistry.registerArtist(it.id) }
        val unified = track.toQobuzUnifiedTrack().asSpotifyRadio()
        unifiedTrackRegistry.put(unified.toLegacyTrack().id, unified)
        return unified
    }

    private fun resolved(
        source: TrackSource,
        unified: UnifiedTrack,
        spotifyId: String,
        reason: String,
        score: Float,
    ): RadioTrackResult.Resolved = RadioTrackResult.Resolved(
        source = source,
        track = unified.toRadioMediaItem(spotifyId),
        unifiedTrack = unified,
        reason = reason,
        score = score,
    )

    private fun bestQobuzMatch(
        candidates: List<Track>,
        title: String,
        artist: String,
    ): Track? =
        candidates
            .map { it to score(title, artist, it.title, it.displayArtist) }
            .filter { it.second >= MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

    private fun bestLocalMatch(
        candidates: List<UnifiedTrack>,
        title: String,
        artist: String,
    ): UnifiedTrack? =
        candidates
            .filter { it.sourceType == SourceType.LOCAL }
            .map { it to score(title, artist, it.title, it.artistName) }
            .filter { it.second >= MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

    private fun score(seedTitle: String, seedArtist: String, title: String, artist: String): Double {
        val seed = "${norm(seedTitle)} ${norm(seedArtist)}".trim()
        val candidate = "${norm(title)} ${norm(artist)}".trim()
        if (seed.isBlank() || candidate.isBlank()) return 0.0
        return similarity.apply(seed, candidate) ?: 0.0
    }

    private fun norm(value: String): String = SpotifyFeatureDb.normalize(
        value.substringBefore(" — ").substringBefore(" - ")
    )

    private fun UnifiedTrack.asSpotifyRadio(): UnifiedTrack =
        copy(qualityTags = (qualityTags.orEmpty() + RADIO_QUALITY_TAG).distinct())

    private fun UnifiedTrack.toRadioMediaItem(spotifyId: String): MediaItem {
        val extras = Bundle().apply {
            putString(RADIO_SOURCE_EXTRA_KEY, RADIO_SOURCE_SPOTIFY)
            putString(RADIO_SPOTIFY_ID_EXTRA_KEY, spotifyId)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artistName)
                    .setAlbumTitle(albumTitle)
                    .setArtworkUri(artworkUri?.toUri())
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private companion object {
        const val MATCH_THRESHOLD = 0.88
    }
}

fun radioKey(title: String?, artist: String?, isrc: String?): String = when {
    !isrc.isNullOrBlank() -> "isrc:${isrc.lowercase()}"
    else -> "text:${SpotifyFeatureDb.normalize(artist.orEmpty())}::${SpotifyFeatureDb.normalize(title.orEmpty())}"
}

const val RADIO_SOURCE_EXTRA_KEY = "radio_source"
const val RADIO_SOURCE_SPOTIFY = "spotify"
const val RADIO_SPOTIFY_ID_EXTRA_KEY = "spotify_id"
const val RADIO_QUALITY_TAG = "radio_source:spotify"
