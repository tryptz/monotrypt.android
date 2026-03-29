package tf.monochrome.android.visualizer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledProjectMAssets(
    val rootDir: File,
    val presetDir: File,
    val textureDir: File,
    val catalogFile: File,
    val version: String
)

@Singleton
class ProjectMAssetInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val assetVersion = "v2"

    fun ensureInstalled(): InstalledProjectMAssets {
        val rootDir = File(context.filesDir, "projectm/$assetVersion")
        val versionFile = File(rootDir, ".asset-version")
        val presetDir = File(rootDir, "presets")
        val textureDir = File(rootDir, "textures")
        val catalogFile = File(rootDir, "catalog.json")

        if (!rootDir.exists() || versionFile.readTextOrNull() != assetVersion || !catalogFile.exists()) {
            rootDir.deleteRecursively()
            rootDir.mkdirs()
            copyAssetTree("projectm", rootDir)
            versionFile.writeText(assetVersion)
        }

        presetDir.mkdirs()
        textureDir.mkdirs()

        return InstalledProjectMAssets(
            rootDir = rootDir,
            presetDir = presetDir,
            textureDir = textureDir,
            catalogFile = catalogFile,
            version = assetVersion
        )
    }

    private fun copyAssetTree(assetPath: String, outputDir: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            outputDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                outputDir.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        outputDir.mkdirs()
        entries.forEach { child ->
            val childAssetPath = if (assetPath.isBlank()) child else "$assetPath/$child"
            copyAssetTree(childAssetPath, File(outputDir, child))
        }
    }

    private fun File.readTextOrNull(): String? = if (exists()) readText() else null
}
