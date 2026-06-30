package tf.monochrome.android.data.recommendations

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import tf.monochrome.android.data.api.QobuzIdRegistry
import tf.monochrome.android.data.analysis.AudioFeatureRepository
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.UnifiedTrack
import tf.monochrome.android.domain.usecase.toUnifiedTrackAuto
import javax.inject.Inject
import javax.inject.Singleton

/** A labelled row of resolved, playable recommendations for discovery/search. */
data class RecRow(val label: String, val tracks: List<UnifiedTrack>)

/**
 * Bridges the offline Spotify similarity engine to the live HiFi backends.
 *
 * The dataset has no streaming ids, so every recommendation is re-resolved to
 * a playable [Track] by searching the configured HiFi instance for
 * `"$title $artist"` and picking the best title+artist match — the same
 * approach DiscoveryFeedUseCase / SearchViewModel already use for their Qobuz
 * recommendation rows. Qobuz is tried first (its search also registers ids in
 * [QobuzIdRegistry], so [toUnifiedTrackAuto] routes navigation correctly),
 * falling back to the TIDAL pool.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val music: MusicRepository,
    private val engine: SpotifyRecommendationEngine,
    private val registry: QobuzIdRegistry,
    private val audioFeatures: AudioFeatureRepository,
) {
    /**
     * Playable "radio" continuation for a seed [Track] — the Spotify-style
     * autoplay queue extension. Returns up to [limit] tracks not already in
     * [excludeIds] (the current queue) nor the seed itself. Empty only when
     * neither source yields anything (caller then just stops).
     *
     * Hybrid by design: it blends the backend's own recommendations
     * ([MusicRepository.getRecommendations], which carries the
     * collaborative/catalog signal the audio-feature dataset lacks), measured
     * on-device feature matches for arbitrary played streams, and exact
     * Spotify-dataset matches when the seed exists in the bundled DB.
     */
    suspend fun radioTracks(seed: Track, excludeIds: Set<Long>, limit: Int = 20): List<Track> =
        coroutineScope {
            val backendDeferred = async {
                runCatching { music.getRecommendations(seed.id).getOrNull() }.getOrNull().orEmpty()
            }
            val measuredDeferred = async {
                measuredRadioTracks(seed, limit * 2)
            }
            val datasetDeferred = async {
                seed.primaryArtistName()?.let { artist ->
                    engine.similarTo(artist, seed.title, limit * 2)
                        .takeIf { it.isNotEmpty() }
                        ?.let { resolveTracks(it) }
                }.orEmpty()
            }
            val exclude = excludeIds + seed.id
            interleave(backendDeferred.await(), measuredDeferred.await(), datasetDeferred.await())
                .distinctBy { it.id }
                .filter { it.id !in exclude }
                .take(limit)
        }

    /** Round-robins ranked lists so the result alternates their sources. */
    private fun interleave(vararg lists: List<Track>): List<Track> {
        val out = ArrayList<Track>(lists.sumOf { it.size })
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) {
            for (list in lists) list.getOrNull(i)?.let { out.add(it) }
        }
        return out
    }

    /** A "More like <seedTitle>" row of resolved [UnifiedTrack]s, or null if nothing resolves. */
    suspend fun similarRow(label: String, seedArtist: String, seedTitle: String, perRow: Int): RecRow? {
        val recs = engine.similarTo(seedArtist, seedTitle, perRow * 2)
        if (recs.isEmpty()) return null
        val tracks = resolveTracks(recs).distinctBy { it.id }.take(perRow)
            .map { it.toUnifiedTrackAuto(registry) }
        return tracks.takeIf { it.isNotEmpty() }?.let { RecRow(label, it) }
    }

    /** One discovery/search row per genre, each filled with resolved dataset picks. */
    suspend fun genreRows(genres: List<String>, perRow: Int): List<RecRow> = coroutineScope {
        genres.map { genre ->
            async {
                val recs = engine.byGenre(genre, perRow * 2)
                val tracks = resolveTracks(recs).distinctBy { it.id }.take(perRow)
                    .map { it.toUnifiedTrackAuto(registry) }
                tracks.takeIf { it.isNotEmpty() }
                    ?.let { RecRow(genre.replaceFirstChar { c -> c.titlecase() }, it) }
            }
        }.awaitAll().filterNotNull()
    }

    /** Genres weighted toward the user's taste ([seedGenres]), padded with a varied sample. */
    suspend fun discoveryGenres(seedGenres: List<String>, count: Int, salt: Int): List<String> {
        val weighted = engine.genresFor(seedGenres, count)
        if (weighted.size >= count) return weighted
        val extra = engine.sampleGenres(count, salt).filter { it !in weighted }
        return (weighted + extra).take(count)
    }

    // --- resolution ---

    private suspend fun resolveTracks(recs: List<SpotifyRec>): List<Track> = coroutineScope {
        recs.map { rec -> async { resolveOne(rec.title, rec.artist) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun measuredRadioTracks(seed: Track, limit: Int): List<Track> {
        val artist = seed.primaryArtistName() ?: return emptyList()
        val key = audioFeatures.normKeyOf(artist, seed.title)
        val seedFeatures = audioFeatures.featuresForKey(key) ?: return emptyList()
        val recs = engine.similarToMeasured(seedFeatures, limit)
        return resolveTracks(recs)
    }

    private suspend fun resolveOne(title: String, artist: String): Track? =
        withTimeoutOrNull(RESOLVE_BUDGET_MS) {
            val query = "$title $artist"
            val qobuz = runCatching { music.searchQobuz(query).getOrNull() }.getOrNull()?.tracks
            bestMatch(qobuz, title, artist)?.let { return@withTimeoutOrNull it }
            val tidal = runCatching { music.search(query, 0, 5).getOrNull() }.getOrNull()?.tracks
            bestMatch(tidal, title, artist)
        }

    /** Prefer an exact (normalized) title match that shares the artist; never match on title alone. */
    private fun bestMatch(candidates: List<Track>?, title: String, artist: String): Track? {
        if (candidates.isNullOrEmpty()) return null
        val nTitle = SpotifyFeatureDb.normalize(title)
        val nArtist = SpotifyFeatureDb.normalize(artist)
        fun matchesArtist(track: Track): Boolean {
            val names = listOfNotNull(track.artist?.name) + track.artists.map { it.name }
            return names.any {
                val n = SpotifyFeatureDb.normalize(it)
                n.isNotEmpty() && (n.contains(nArtist) || nArtist.contains(n))
            }
        }
        return candidates.firstOrNull { SpotifyFeatureDb.normalize(it.title) == nTitle && matchesArtist(it) }
            ?: candidates.firstOrNull { SpotifyFeatureDb.normalize(it.title).contains(nTitle) && matchesArtist(it) }
    }

    private fun Track.primaryArtistName(): String? =
        (artist?.name ?: artists.firstOrNull()?.name)?.takeIf { it.isNotBlank() }

    private companion object {
        const val RESOLVE_BUDGET_MS = 6_000L
    }
}
