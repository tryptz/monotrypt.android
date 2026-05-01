package tf.monochrome.android.data.api

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry that records which numeric ids the app has seen come
 * out of the trypt-hifi (Qobuz) API.
 *
 * Why this exists: the navigation routes for AlbumDetail / ArtistDetail are
 * keyed by `Long`, but Qobuz's album-detail endpoint
 * (/api/get-album?album_id=<id>) takes the alphanumeric slug ("fr2agovnqpeka")
 * — not the numeric `qobuz_id`. We don't want to widen Album.id to String
 * across the whole app, so we record (qobuz_id -> slug) at search time and
 * the detail VM looks the slug back up at navigation time.
 *
 * Artist ids are already numeric on both ends; the artist set is only used
 * as a "this id came from Qobuz, don't try TIDAL first" hint.
 *
 * Lifecycle: process-scoped. Entries persist until process death. A user
 * who searches for an album, swipes away the app, then deep-links to the
 * detail screen would lose the Qobuz slug — but the existing flow always
 * navigates from a freshly-populated search list, so the gap isn't
 * reachable in practice.
 */
@Singleton
class QobuzIdRegistry @Inject constructor() {
    private val albumSlugs = ConcurrentHashMap<Long, String>()
    private val qobuzArtistIds: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    // Set of track ids we know to be Qobuz. PlayerViewModel.resolveAndPlay
    // consults this so a Qobuz-album track row plays via the QobuzCached
    // path instead of falling through to TIDAL streaming with a Qobuz id
    // that TIDAL either doesn't have or maps to a different track.
    private val qobuzTrackIds: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun registerAlbum(qobuzId: Long, slug: String) {
        if (slug.isBlank()) return
        albumSlugs[qobuzId] = slug
    }

    fun albumSlugFor(qobuzId: Long): String? = albumSlugs[qobuzId]

    fun registerArtist(id: Long) {
        qobuzArtistIds.add(id)
    }

    fun isQobuzArtist(id: Long): Boolean = id in qobuzArtistIds

    fun registerTrack(id: Long) {
        qobuzTrackIds.add(id)
    }

    fun isQobuzTrack(id: Long): Boolean = id in qobuzTrackIds
}
