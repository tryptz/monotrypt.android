package tf.monochrome.android.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.sync.PocketBaseClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth repository coordinating Supabase authentication and PocketBase data sync.
 * Authentication is handled entirely by Supabase (SupabaseAuthManager).
 * PocketBase is used only for cloud data storage, accessed with the Supabase user ID.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val supabaseAuthManager: SupabaseAuthManager,
    private val pocketBaseClient: PocketBaseClient,
    private val preferences: PreferencesManager
) {
    val isLoggedIn: Flow<Boolean> = supabaseAuthManager.userProfile.map { it != null }
    val userEmail: Flow<String?> = supabaseAuthManager.userProfile.map { it?.email }

    /**
     * Get the current Supabase user ID (used as firebase_id in PocketBase).
     */
    fun getSupabaseUserId(): String? = supabaseAuthManager.userProfile.value?.id

    /**
     * Ensure the user has a PocketBase DB_users record.
     * Called after successful Supabase sign-in.
     */
    suspend fun ensurePocketBaseRecord(): Result<Unit> {
        val uid = getSupabaseUserId() ?: return Result.failure(Exception("Not signed in"))
        return try {
            val record = pocketBaseClient.getUserRecord(uid)
            if (record != null) {
                val profile = supabaseAuthManager.userProfile.value
                preferences.setPocketBaseAuth(
                    token = "",
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
        supabaseAuthManager.signOut()
        pocketBaseClient.clearCache()
        preferences.clearPocketBaseAuth()
    }
}
