package tf.monochrome.android.data.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.appwrite.Client
import io.appwrite.enums.OAuthProvider
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = Client(context)
        .setEndpoint("https://auth.monochrome.tf/v1")
        .setProject("auth-for-monochrome")

    private val account = Account(client)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Check for an existing Appwrite session on startup
     */
    suspend fun initialize() {
        try {
            val user = account.get()
            _userProfile.value = UserProfile(
                id = user.id,
                displayName = user.name.ifBlank { null },
                email = user.email.ifBlank { null },
                photoUrl = null // Appwrite doesn't return a photo URL by default
            )
        } catch (_: Exception) {
            _userProfile.value = null
        }
    }

    /**
     * Sign in with Google via Appwrite OAuth2 (opens browser)
     */
    suspend fun signInWithGoogle(activity: ComponentActivity) {
        _isSigningIn.value = true
        _errorMessage.value = null
        try {
            account.createOAuth2Token(
                activity = activity,
                provider = OAuthProvider.GOOGLE,
                success = "appwrite-callback-auth-for-monochrome://monochrome.tf",
                failure = "appwrite-callback-auth-for-monochrome://monochrome.tf"
            )
            // OAuth opens a browser — refreshUser() is called when the user
            // returns to ProfileScreen via LaunchedEffect
        } catch (e: Exception) {
            _errorMessage.value = "Google sign-in failed: ${e.message}"
        } finally {
            _isSigningIn.value = false
        }
    }

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        _isSigningIn.value = true
        _errorMessage.value = null
        return try {
            account.createEmailPasswordSession(email, password)
            val user = account.get()
            val profile = UserProfile(
                id = user.id,
                displayName = user.name.ifBlank { null },
                email = user.email.ifBlank { null },
                photoUrl = null
            )
            _userProfile.value = profile
            Result.success(profile)
        } catch (e: AppwriteException) {
            _errorMessage.value = e.message ?: "Login failed"
            Result.failure(e)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Login failed"
            Result.failure(e)
        } finally {
            _isSigningIn.value = false
        }
    }

    /**
     * Sign up with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<UserProfile> {
        _isSigningIn.value = true
        _errorMessage.value = null
        return try {
            account.create(userId = "unique()", email = email, password = password)
            // Auto sign-in after sign-up
            return signInWithEmail(email, password)
        } catch (e: AppwriteException) {
            _errorMessage.value = e.message ?: "Sign up failed"
            _isSigningIn.value = false
            Result.failure(e)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Sign up failed"
            _isSigningIn.value = false
            Result.failure(e)
        }
    }

    /**
     * Refresh user state (call after OAuth2 browser callback)
     */
    suspend fun refreshUser() {
        _isSigningIn.value = false
        try {
            val user = account.get()
            _userProfile.value = UserProfile(
                id = user.id,
                displayName = user.name.ifBlank { null },
                email = user.email.ifBlank { null },
                photoUrl = null
            )
        } catch (_: Exception) {
            // Don't show an error message on refresh if not signed in
        }
    }

    /**
     * Sign out, destroy current session
     */
    suspend fun signOut() {
        try {
            account.deleteSession("current")
        } catch (_: Exception) { }
        _userProfile.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
