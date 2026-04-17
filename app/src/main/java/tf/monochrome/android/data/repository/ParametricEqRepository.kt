package tf.monochrome.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tf.monochrome.android.data.db.dao.EqPresetDao
import tf.monochrome.android.data.db.entity.EqPresetEntity
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.EqPreset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user-created Parametric EQ presets (eqType = 1).
 * Shares the `eq_presets` table with AutoEQ but is isolated by the eqType filter.
 */
@Singleton
class ParametricEqRepository @Inject constructor(
    private val eqPresetDao: EqPresetDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllPresets(): Flow<List<EqPreset>> =
        eqPresetDao.getAllParametricPresets().map { list -> list.map { it.toDomain() } }

    suspend fun getPresetById(presetId: String): EqPreset? =
        eqPresetDao.getPresetById(presetId)?.takeIf { it.eqType == 1 }?.toDomain()

    suspend fun savePreset(preset: EqPreset) {
        eqPresetDao.insertPreset(preset.toEntity())
    }

    suspend fun deletePreset(presetId: String) {
        eqPresetDao.deletePreset(presetId)
    }

    fun searchPresets(query: String): Flow<List<EqPreset>> =
        eqPresetDao.searchParametricPresets(query).map { list -> list.map { it.toDomain() } }

    fun getCustomPresetCount(): Flow<Int> = eqPresetDao.getCustomParametricPresetCount()

    private fun EqPresetEntity.toDomain(): EqPreset = EqPreset(
        id = id,
        name = name,
        description = description,
        bands = try { json.decodeFromString<List<EqBand>>(bandsJson) } catch (_: Exception) { emptyList() },
        preamp = preamp,
        targetId = targetId,
        targetName = targetName,
        isCustom = isCustom,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun EqPreset.toEntity(): EqPresetEntity = EqPresetEntity(
        id = id,
        name = name,
        description = description,
        bandsJson = try { json.encodeToString(bands) } catch (_: Exception) { "[]" },
        preamp = preamp,
        targetId = "",
        targetName = "",
        isCustom = isCustom,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        eqType = 1
    )
}
