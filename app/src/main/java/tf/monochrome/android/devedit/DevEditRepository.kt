package tf.monochrome.android.devedit

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the DevEdit layout to a JSON file in the app's internal
 * storage ([Context.getFilesDir]). Failures are swallowed so a corrupt or
 * missing file simply yields an empty layout.
 */
@Singleton
class DevEditRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun load(): DevEditLayout = try {
        if (file.exists()) {
            json.decodeFromString(DevEditLayout.serializer(), file.readText())
        } else {
            DevEditLayout()
        }
    } catch (_: Exception) {
        DevEditLayout()
    }

    fun save(layout: DevEditLayout) {
        try {
            file.writeText(json.encodeToString(DevEditLayout.serializer(), layout))
        } catch (_: Exception) {
            // Best-effort; ignore IO failures.
        }
    }

    private companion object {
        const val FILE_NAME = "devedit_layout.json"
    }
}
