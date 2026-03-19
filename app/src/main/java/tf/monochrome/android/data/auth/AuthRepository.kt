package tf.monochrome.android.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.sync.PocketBaseClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth repository coordinating Appwrite authentication and PocketBase data sync.
 * Authentication is handled entirely by Appwrite (GoogleAuthManager).
 * PocketBase is used only for cloud data storage, accessed with the Appwrite user ID.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val pocketBaseClient: PocketBaseClient,
    private val preferences: PreferencesManager
) {
    val isLoggedIn: Flow<Boolean> = googleAuthManager.userProfile.map { it != null }
    val userEmail: Flow<String?> = googleAuthManager.userProfile.map { it?.email }

    /**
     * Get the current Appwrite user ID (used as firebase_id in PocketBase).
     */
    fun getAppwriteUserId(): String? = googleAuthManager.userProfile.value?.id

    /**
     * Ensure the user has a PocketBase DB_users record.
     * Called after successful Appwrite sign-in.
     */
    suspend fun ensurePocketBaseRecord(): Result<Unit> {
        val uid = getAppwriteUserId() ?: return Result.failure(Exception("Not signed in"))
        return try {
            val record = pocketBaseClient.getUserRecord(uid)
            if (record != null) {
                // Store user info in preferences for offline access
                val profile = googleAuthManager.userProfile.value
                preferences.setPocketBaseAuth(
                    token = "", // No PocketBase token needed
                    userId = uid,
                    email = profile?.email ?: ""
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create PocketBase record"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        googleAuthManager.signOut()
        pocketBaseClient.clearCache()
        preferences.clearPocketBaseAuth()
    }
}
