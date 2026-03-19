package tf.monochrome.android.ui.profile

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tf.monochrome.android.data.auth.AuthRepository
import tf.monochrome.android.data.auth.GoogleAuthManager
import tf.monochrome.android.data.auth.UserProfile
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = authManager.userProfile
    val isSigningIn: StateFlow<Boolean> = authManager.isSigningIn
    val errorMessage: StateFlow<String?> = authManager.errorMessage

    fun refreshUser() {
        viewModelScope.launch {
            authManager.refreshUser()
            // If we have an Appwrite session, ensure PocketBase record exists
            if (authManager.userProfile.value != null) {
                authRepository.ensurePocketBaseRecord()
            }
        }
    }

    fun signInWithGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            authManager.signInWithGoogle(activity)
            // The actual sync happens in refreshUser() when the activity resumes
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            val result = authManager.signInWithEmail(email, password)
            result.onSuccess {
                authRepository.ensurePocketBaseRecord()
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            val result = authManager.signUpWithEmail(email, password)
            result.onSuccess {
                authRepository.ensurePocketBaseRecord()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearError() {
        authManager.clearError()
    }
}
