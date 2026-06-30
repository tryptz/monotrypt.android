package tf.monochrome.android.spotify.api.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tf.monochrome.android.spotify.api.spotifySearchOffset
import tf.monochrome.android.spotify.api.spotifySearchPageLimit

class SpotifyModelTest {
    @Test
    fun `SpotifyTrack exposes primary artist metadata`() {
        val track = SpotifyTrack(
            id = "spotify-track",
            name = "Song",
            artists = listOf(
                SpotifyArtist(id = "artist-1", name = "First Artist"),
                SpotifyArtist(id = "artist-2", name = "Second Artist"),
            ),
        )

        assertEquals("First Artist", track.primaryArtistName)
        assertEquals("artist-1", track.primaryArtistId)
        assertEquals("spotify-track", track.stableId)
        assertTrue(track.isUsable)
    }

    @Test
    fun `SpotifyTrack tolerates omitted optional fields`() {
        val track = SpotifyTrack()

        assertEquals("", track.stableId)
        assertEquals("", track.primaryArtistName)
        assertNull(track.externalIds)
        assertFalse(track.isUsable)
    }

    @Test
    fun `Spotify search page limit clamps to current maximum`() {
        assertEquals(1, spotifySearchPageLimit(0))
        assertEquals(5, spotifySearchPageLimit(5))
        assertEquals(10, spotifySearchPageLimit(50))
    }

    @Test
    fun `Spotify search offset never goes negative`() {
        assertEquals(0, spotifySearchOffset(-5))
        assertEquals(20, spotifySearchOffset(20))
    }
}
