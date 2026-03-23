package tf.monochrome.android.data.import_

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class CsvPlaylist(
    val title: String,
    val tracks: List<CsvTrack>
)

data class CsvTrack(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

@Singleton
class CsvPlaylistParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun parseFromUri(uri: Uri): Result<CsvPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = getFileName(uri) ?: "Imported Playlist"
            val tracks = mutableListOf<CsvTrack>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                
                // Read header to find column indices
                val headerLine = reader.readLine() ?: throw Exception("Empty file")
                val headers = parseCsvLine(headerLine).map { 
                    it.trim().lowercase().replace("\"", "") 
                }

                val titleIdx = headers.indexOfFirst { it == "track name" }
                val artistIdx = headers.indexOfFirst { it == "artist name(s)" }
                val albumIdx = headers.indexOfFirst { it == "album name" }
                val durationIdx = headers.indexOfFirst { it == "track duration (ms)" }

                if (titleIdx == -1 || artistIdx == -1) {
                    throw Exception("Missing required columns in CSV (Track Name, Artist Name(s))")
                }

                var line = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val columns = parseCsvLine(line)
                        if (columns.size > maxOf(titleIdx, artistIdx)) {
                            val title = columns[titleIdx].trim()
                            if (title.isNotBlank()) {
                                tracks.add(
                                    CsvTrack(
                                        title = title,
                                        artist = columns.getOrNull(artistIdx)?.trim() ?: "",
                                        album = if (albumIdx != -1) columns.getOrNull(albumIdx)?.trim() ?: "" else "",
                                        durationMs = if (durationIdx != -1) columns.getOrNull(durationIdx)?.trim()?.toLongOrNull() ?: 0L else 0L
                                    )
                                )
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }

            CsvPlaylist(title = fileName.substringBeforeLast("."), tracks = tracks)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val currentField = StringBuilder()
        
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    // Remove enclosing quotes if present
                    var field = currentField.toString().trim()
                    if (field.startsWith("\"") && field.endsWith("\"") && field.length >= 2) {
                        field = field.substring(1, field.length - 1)
                    }
                    result.add(field)
                    currentField.clear()
                }
                else -> currentField.append(char)
            }
        }
        
        var field = currentField.toString().trim()
        if (field.startsWith("\"") && field.endsWith("\"") && field.length >= 2) {
            field = field.substring(1, field.length - 1)
        }
        result.add(field)

        return result
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }
}
