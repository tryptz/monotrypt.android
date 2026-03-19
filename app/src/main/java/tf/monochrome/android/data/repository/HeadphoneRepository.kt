package tf.monochrome.android.data.repository

import android.util.Log
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
    companion object {
        private const val TAG = "HeadphoneRepository"
    }

    /**
     * Get all available headphones as a Flow
     *
     * Fetches from API and caches. Returns Flow that emits once data is available.
     */
    fun getAllHeadphones(): Flow<List<Headphone>> = flow {
        try {
            val result = autoEqApi.fetchHeadphones()
            result.onSuccess { headphones ->
                Log.d(TAG, "Emitting ${headphones.size} headphones")
                emit(headphones)
            }.onFailure { error ->
                Log.e(TAG, "Error fetching headphones: ${error.message}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllHeadphones: ${e.message}")
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
                Log.d(TAG, "Found ${headphones.size} headphones matching '$query'")
                emit(headphones)
            }.onFailure { error ->
                Log.e(TAG, "Error searching headphones: ${error.message}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchHeadphones: ${e.message}")
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
                Log.d(TAG, "Found ${headphones.size} ${type} headphones")
                emit(headphones)
            }.onFailure { error ->
                Log.e(TAG, "Error fetching ${type} headphones: ${error.message}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getHeadphonesByType: ${e.message}")
            emit(emptyList())
        }
    }

    /**
     * Load measurement data for a specific headphone
     *
     * @param headphoneId The headphone folder name (e.g., "Apple AirPods Max")
     */
    fun loadHeadphoneMeasurement(headphoneId: String): Flow<Result<String>> = flow {
        try {
            Log.d(TAG, "Loading measurement for $headphoneId...")
            val result = autoEqApi.fetchHeadphoneMeasurement(headphoneId)
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading measurement: ${e.message}")
            emit(Result.failure(e))
        }
    }

    /**
     * Refresh headphone cache
     *
     * Forces a fresh fetch from GitHub on next request.
     */
    fun refreshCache() {
        Log.d(TAG, "Refreshing headphone cache")
        autoEqApi.clearCache()
    }
}
