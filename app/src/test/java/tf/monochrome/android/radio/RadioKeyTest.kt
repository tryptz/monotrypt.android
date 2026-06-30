package tf.monochrome.android.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioKeyTest {
    @Test
    fun `radioKey prefers ISRC over text metadata`() {
        assertEquals("isrc:usabc1234567", radioKey("Song", "Artist", "USABC1234567"))
    }

    @Test
    fun `radioKey normalizes artist and title for duplicate filtering`() {
        assertEquals(
            "text:theartist::songtitle",
            radioKey(" Song   Title ", "The Artist", null)
        )
    }
}
