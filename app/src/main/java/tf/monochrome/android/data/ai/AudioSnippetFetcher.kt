package tf.monochrome.android.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

data class AudioSnippet(
    val bytes: ByteArray,
    val mimeType: String
)

@Singleton
class AudioSnippetFetcher @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val MAX_BYTES = 512 * 1024 // 512KB cap
    }

    suspend fun fetchSnippet(streamUrl: String): AudioSnippet {
        val response = httpClient.get(streamUrl) {
            header("Range", "bytes=0-${MAX_BYTES - 1}")
        }

        val bytes = response.bodyAsBytes()
        val ct = response.contentType()
        val mimeType = ct?.toString()?.split(";")?.firstOrNull()?.trim()
            ?: inferMimeType(streamUrl)

        return AudioSnippet(
            bytes = if (bytes.size > MAX_BYTES) bytes.copyOf(MAX_BYTES) else bytes,
            mimeType = mimeType
        )
    }

    private fun inferMimeType(url: String): String {
        return when {
            url.contains(".flac", ignoreCase = true) -> "audio/flac"
            url.contains(".mp4", ignoreCase = true) || url.contains(".m4a", ignoreCase = true) -> "audio/mp4"
            url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
            url.contains(".wav", ignoreCase = true) -> "audio/wav"
            else -> "audio/mpeg"
        }
    }
}
