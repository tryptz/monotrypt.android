package tf.monochrome.android.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tf.monochrome.android.data.api.HeadphoneAutoEqApi
import tf.monochrome.android.domain.model.Headphone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HeadphoneRepository - Manages headphone data from GitHub AutoEq repository
 *
 * Provides:
 * - Fetching list of available headphones
 * - Searching and filtering headphones
 * - Caching with TTL management
 * - Loading measurements for selected headphone
 */
@Singleton
class HeadphoneRepository @Inject constructor(
    private val autoEqApi: HeadphoneAutoEqApi
) {
    /**
     * Get all available headphones as a Flow
     *
     * Fetches from API and caches. Returns Flow that emits once data is available.
     */
    fun getAllHeadphones(): Flow<List<Headphone>> = flow {
        try {
            val result = autoEqApi.fetchHeadphones()
            result.onSuccess { headphones ->
                emit(headphones)
            }.onFailure {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Search headphones by name
     *
     * @param query Search query
     */
    fun searchHeadphones(query: String): Flow<List<Headphone>> = flow {
        try {
            if (query.isBlank()) {
                emit(emptyList())
                return@flow
            }

            val result = autoEqApi.searchHeadphones(query)
            result.onSuccess { headphones ->
                emit(headphones)
            }.onFailure {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Get headphones filtered by type
     *
     * @param type Headphone type: "over-ear", "in-ear", or "earbud"
     */
    fun getHeadphonesByType(type: String): Flow<List<Headphone>> = flow {
        try {
            val result = autoEqApi.getHeadphonesByType(type)
            result.onSuccess { headphones ->
                emit(headphones)
            }.onFailure {
                emit(emptyList())
            }
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Load measurement data for a specific headphone
     *
     * @param headphoneId The normalized (lowercased, underscored) id used for cache lookup.
     * @param headphoneName The original-case display name, needed for the
     *   case-sensitive GitHub fallback URLs. Defaults to the id when unknown.
     */
    fun loadHeadphoneMeasurement(
        headphoneId: String,
        headphoneName: String = ""
    ): Flow<Result<String>> = flow {
        try {
            val result = autoEqApi.fetchHeadphoneMeasurement(headphoneId, headphoneName)
            emit(result)
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Refresh headphone cache
     *
     * Forces a fresh fetch from GitHub on next request.
     */
    fun refreshCache() {
        autoEqApi.clearCache()
    }
}
