package tf.monochrome.android.data.collections.parser

import kotlinx.serialization.json.Json
import tf.monochrome.android.data.collections.model.CollectionManifest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManifestParser @Inject constructor(
    private val json: Json
) {
    fun parse(manifestJson: String): Result<CollectionManifest> {
        return try {
            val manifest = json.decodeFromString<CollectionManifest>(manifestJson)
            validate(manifest)
            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validate(manifest: CollectionManifest) {
        require(manifest.authorId.isNotBlank()) { "Manifest must have an author_id" }
        require(manifest.encryption.key.isNotBlank()) { "Manifest must have an encryption key" }
        require(manifest.tracks.isNotEmpty()) { "Manifest must contain at least one track" }

        // Validate track-album references
        val albumUuids = manifest.albums.map { it.uuid }.toSet()
        for (track in manifest.tracks) {
            require(track.albumUuid in albumUuids) {
                "Track '${track.title}' references unknown album UUID: ${track.albumUuid}"
            }
        }

        // Validate artist references
        val artistUuids = manifest.artists.map { it.uuid }.toSet()
        for (track in manifest.tracks) {
            for (artistUuid in track.artistUuids) {
                require(artistUuid in artistUuids) {
                    "Track '${track.title}' references unknown artist UUID: $artistUuid"
                }
            }
        }
        for (album in manifest.albums) {
            for (artistUuid in album.artistUuids) {
                require(artistUuid in artistUuids) {
                    "Album '${album.title}' references unknown artist UUID: $artistUuid"
                }
            }
        }
    }
}
