package tf.monochrome.android.visualizer

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import tf.monochrome.android.domain.model.VisualizerPreset

@Singleton
class ProjectMPresetCatalog @Inject constructor(
    private val json: Json
) {
    fun load(catalogFile: File): List<VisualizerPreset> {
        if (!catalogFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<VisualizerPreset>>(catalogFile.readText())
        }.getOrElse { emptyList() }
    }
}
