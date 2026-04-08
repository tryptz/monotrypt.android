package tf.monochrome.android.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

@Singleton
class SupabaseAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // TODO: Replace with your Supabase project URL and anon key
    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://your-project.supabase.co",
        supabaseKey = "your-anon-key"
    ) {
        install(Auth) {
            scheme = "tf.monochrome.android"
            host = "supabase-callback"
        }
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Listen to Supabase session changes and update user profile
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        _userProfile.value = user?.let {
                            UserProfile(
                                id = it.id,
                                displayName = it.userMetadata?.get("full_name")?.toString()
                                    ?.removeSurrounding("\"")?.ifBlank { null },
                                email = it.email,
                                photoUrl = it.userMetadata?.get("avatar_url")?.toString()
                                    ?.removeSurrounding("\"")?.ifBlank { null }
                            )
                        }
                        _isSigningIn.value = false
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _userProfile.value = null
                        _isSigningIn.value = false
                    }
                    is SessionStatus.LoadingFromStorage -> {
                        // Session is being restored from storage
                    }
                    is SessionStatus.NetworkError -> {
                        _isSigningIn.value = false
                    }
                }
            }
        }
    }

    /**
     * Check for an existing Supabase session on startup.
     * The session is automatically restored by the Auth plugin,
     * but this ensures the profile state is up to date.
     */
    suspend fun initialize() {
        try {
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            _userProfile.value = user?.let {
                UserProfile(
                    id = it.id,
                    displayName = it.userMetadata?.get("full_name")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null },
                    email = it.email,
                    photoUrl = it.userMetadata?.get("avatar_url")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null }
                )
            }
        } catch (_: Exception) {
            _userProfile.value = null
        }
    }

    /**
     * Sign in with Google via Supabase OAuth2 (opens browser)
     */
    suspend fun signInWithGoogle() {
        _isSigningIn.value = true
        _errorMessage.value = null
        try {
            supabase.auth.signInWith(Google)
            // OAuth opens a browser — the session status collector handles the rest
        } catch (e: Exception) {
            _errorMessage.value = "Google sign-in failed: ${e.message}"
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
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()
            val profile = user?.let {
                UserProfile(
                    id = it.id,
                    displayName = it.userMetadata?.get("full_name")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null },
                    email = it.email,
                    photoUrl = it.userMetadata?.get("avatar_url")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null }
                )
            } ?: throw Exception("Failed to get user after sign-in")
            _userProfile.value = profile
            Result.success(profile)
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
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // Auto sign-in after sign-up
            return signInWithEmail(email, password)
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
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user
            _userProfile.value = user?.let {
                UserProfile(
                    id = it.id,
                    displayName = it.userMetadata?.get("full_name")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null },
                    email = it.email,
                    photoUrl = it.userMetadata?.get("avatar_url")?.toString()
                        ?.removeSurrounding("\"")?.ifBlank { null }
                )
            }
        } catch (_: Exception) {
            // Don't show an error message on refresh if not signed in
        }
    }

    /**
     * Sign out, destroy current session
     */
    suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) { }
        _userProfile.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
