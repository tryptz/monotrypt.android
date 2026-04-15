package tf.monochrome.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tf.monochrome.android.audio.eq.FrequencyTargets
import tf.monochrome.android.data.db.dao.EqPresetDao
import tf.monochrome.android.data.db.entity.EqPresetEntity
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.EqPreset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing EQ presets
 *
 * Combines built-in presets with user-created custom presets stored in the database.
 * Handles serialization/deserialization of complex data types (bands, target curves).
 */
@Singleton
class EqRepository @Inject constructor(
    private val eqPresetDao: EqPresetDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get all presets (custom + built-in)
     * Combines database presets with default presets
     */
    fun getAllPresets(): Flow<List<EqPreset>> = eqPresetDao.getAllPresets().map { dbPresets ->
        val customPresets = dbPresets.map { it.toDomain() }
        val allPresets = getDefaultPresets() + customPresets
        allPresets.sortedWith(compareBy({ !it.isCustom }, { -it.updatedAt }))
    }

    /**
     * Get only custom user-created presets
     */
    fun getCustomPresets(): Flow<List<EqPreset>> = eqPresetDao.getCustomPresets().map { dbPresets ->
        dbPresets.map { it.toDomain() }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Get only built-in presets
     */
    fun getBuiltInPresets(): Flow<List<EqPreset>> =
        kotlinx.coroutines.flow.flowOf(
            getDefaultPresets().sortedBy { it.name }
        )

    /**
     * Get a preset by ID (either built-in or custom)
     */
    suspend fun getPresetById(presetId: String): EqPreset? {
        // Check database first
        val dbPreset = eqPresetDao.getPresetById(presetId)
        if (dbPreset != null) {
            return dbPreset.toDomain()
        }

        // Check built-in presets
        return getDefaultPresets().find { it.id == presetId }
    }

    /**
     * Get preset as a Flow for reactive updates (database only)
     */
    fun getPresetByIdFlow(presetId: String): Flow<EqPreset?> = eqPresetDao.getPresetByIdFlow(presetId).map { dbPreset ->
        dbPreset?.toDomain() ?: getDefaultPresets().find { it.id == presetId }
    }

    /**
     * Save a new preset or update existing
     */
    suspend fun savePreset(preset: EqPreset) {
        val entity = preset.toEntity()
        eqPresetDao.insertPreset(entity)
    }

    /**
     * Update an existing preset
     */
    suspend fun updatePreset(preset: EqPreset) {
        val entity = preset.toEntity()
        eqPresetDao.updatePreset(entity)
    }

    /**
     * Delete a preset by ID
     */
    suspend fun deletePreset(presetId: String) {
        // Can only delete custom presets from database
        eqPresetDao.deletePreset(presetId)
    }

    /**
     * Search presets by name
     */
    fun searchPresets(query: String): Flow<List<EqPreset>> = eqPresetDao.searchPresets(query).map { dbResults ->
        val customPresets = dbResults.map { it.toDomain() }
        val builtIn = getDefaultPresets().filter { preset ->
            preset.name.contains(query, ignoreCase = true) ||
                    preset.description.contains(query, ignoreCase = true)
        }
        (customPresets + builtIn).sortedWith(compareBy({ !it.isCustom }, { -it.updatedAt }))
    }

    /**
     * Get presets for a specific target curve
     */
    fun getPresetsByTarget(targetId: String): Flow<List<EqPreset>> = eqPresetDao.getPresetsByTarget(targetId).map { dbPresets ->
        val customPresets = dbPresets.map { it.toDomain() }
        val builtIn = getDefaultPresets().filter { it.targetId == targetId }
        (builtIn + customPresets).sortedWith(compareBy({ !it.isCustom }, { -it.updatedAt }))
    }

    /**
     * Get count of custom presets
     */
    fun getCustomPresetCount(): Flow<Int> = eqPresetDao.getCustomPresetCount()

    /**
     * Create a preset from AutoEQ calculation result
     */
    suspend fun createAutoEqPreset(
        name: String,
        bands: List<EqBand>,
        preamp: Float = 0f,
        targetId: String = "harman_oe_2018",
        headphoneName: String = ""
    ): EqPreset {
        val preset = EqPreset(
            id = "custom_autoeq_${System.currentTimeMillis()}",
            name = name,
            description = "AutoEQ calculated for $headphoneName",
            bands = bands,
            preamp = preamp,
            targetId = targetId,
            targetName = FrequencyTargets.getTargetById(targetId)?.label ?: "Unknown",
            isCustom = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        savePreset(preset)
        return preset
    }

    /**
     * Load default/built-in presets
     * These can be expanded later or loaded from files
     */
    private fun getDefaultPresets(): List<EqPreset> {
        return emptyList()  // Start empty, can be populated later
        // TODO: Add default presets for Harman, Diffuse Field, etc.
    }

    // ===== Conversion Helpers =====

    /**
     * Convert EqPresetEntity to domain model
     */
    private fun EqPresetEntity.toDomain(): EqPreset {
        return EqPreset(
            id = id,
            name = name,
            description = description,
            bands = try {
                json.decodeFromString<List<EqBand>>(bandsJson)
            } catch (e: Exception) {
                emptyList()
            },
            preamp = preamp,
            targetId = targetId,
            targetName = targetName,
            isCustom = isCustom,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Convert domain model to EqPresetEntity
     */
    private fun EqPreset.toEntity(): EqPresetEntity {
        return EqPresetEntity(
            id = id,
            name = name,
            description = description,
            bandsJson = try {
                json.encodeToString(bands)
            } catch (e: Exception) {
                "[]"
            },
            preamp = preamp,
            targetId = targetId,
            targetName = targetName,
            isCustom = isCustom,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            eqType = 0  // AutoEQ
        )
    }
}
