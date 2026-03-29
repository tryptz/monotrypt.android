package tf.monochrome.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tf.monochrome.android.audio.dsp.model.MixPreset
import tf.monochrome.android.data.db.dao.MixPresetDao
import tf.monochrome.android.data.db.entity.MixPresetEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MixPresetRepository @Inject constructor(
    private val dao: MixPresetDao
) {
    fun getAllPresets(): Flow<List<MixPreset>> = dao.getAllPresets().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getPresetById(id: Long): MixPreset? =
        dao.getPresetById(id)?.toDomain()

    suspend fun savePreset(preset: MixPreset): Long =
        dao.upsert(preset.toEntity())

    suspend fun deletePreset(id: Long) = dao.delete(id)

    fun getPresetCount(): Flow<Int> = dao.getPresetCount()

    private fun MixPresetEntity.toDomain() = MixPreset(
        id = id,
        name = name,
        stateJson = stateJson,
        isCustom = isCustom,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MixPreset.toEntity() = MixPresetEntity(
        id = id,
        name = name,
        stateJson = stateJson,
        isCustom = isCustom,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
