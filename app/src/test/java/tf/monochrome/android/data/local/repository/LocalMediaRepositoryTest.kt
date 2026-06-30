package tf.monochrome.android.data.local.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaRepositoryTest {
    @Test
    fun `escapeLike escapes sqlite wildcard characters`() {
        assertEquals("100\\% Real\\_Song", LocalMediaRepository.escapeLike("100% Real_Song"))
    }

    @Test
    fun `escapeLike escapes backslash before wildcards`() {
        assertEquals("a\\\\b\\%c\\_d", LocalMediaRepository.escapeLike("a\\b%c_d"))
    }
}
