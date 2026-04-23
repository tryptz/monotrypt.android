package tf.monochrome.android.domain.usecase

import tf.monochrome.android.data.collections.repository.CollectionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend fun import(manifestJson: String): Result<String> {
        return collectionRepository.importManifest(manifestJson)
    }

    suspend fun delete(collectionId: String) {
        collectionRepository.deleteCollection(collectionId)
    }
}
