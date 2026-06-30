package tf.monochrome.android.spotify.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import tf.monochrome.android.BuildConfig
import tf.monochrome.android.spotify.api.model.SpotifyTokenResponse
import tf.monochrome.android.spotify.api.model.SpotifyUserProfile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyAuthState(
    val isAuthenticated: Boolean = false,
    val accountEmail: String? = null,
)

sealed class SpotifyRefreshResult {
    data object Refreshed : SpotifyRefreshResult()
    data object ReauthRequired : SpotifyRefreshResult()
    data class Failed(val throwable: Throwable) : SpotifyRefreshResult()
}

@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val json: Json,
) {
    private val prefs by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _authState = MutableStateFlow(loadState())
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    fun authorizationUri(): Uri {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        require(clientId.isNotBlank()) { "Spotify client id is not configured" }

        val verifier = randomUrlSafeString(64)
        val state = randomUrlSafeString(24)
        prefs.edit {
            putString(KEY_PENDING_VERIFIER, verifier)
            putString(KEY_PENDING_STATE, state)
        }

        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge(verifier))
            .appendQueryParameter("scope", SPOTIFY_SCOPES)
            .appendQueryParameter("state", state)
            .build()
    }

    suspend fun handleAuthorizationRedirect(uri: Uri): Boolean {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) return false

        val code = uri.getQueryParameter("code") ?: return false
        val state = uri.getQueryParameter("state") ?: return false
        val expectedState = prefs.getString(KEY_PENDING_STATE, null) ?: return false
        if (state != expectedState) return false
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return false

        val response = exchangeCode(code, verifier)
        storeToken(response, authorizedAtMs = System.currentTimeMillis())
        prefs.edit {
            remove(KEY_PENDING_VERIFIER)
            remove(KEY_PENDING_STATE)
        }
        refreshProfileEmail(response.accessToken)
        enqueueRefreshWork()
        return true
    }

    suspend fun validAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (!token.isNullOrBlank() && expiresAt - System.currentTimeMillis() > REFRESH_SLOP_MS) {
            return token
        }
        return when (refreshAccessToken()) {
            SpotifyRefreshResult.Refreshed -> prefs.getString(KEY_ACCESS_TOKEN, null)
            SpotifyRefreshResult.ReauthRequired,
            is SpotifyRefreshResult.Failed -> null
        }
    }

    suspend fun refreshIfNeeded(force: Boolean = false): Boolean {
        if (!force) {
            val token = prefs.getString(KEY_ACCESS_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
            if (!token.isNullOrBlank() && expiresAt - System.currentTimeMillis() > REFRESH_SLOP_MS) {
                return true
            }
        }
        return refreshAccessToken() is SpotifyRefreshResult.Refreshed
    }

    suspend fun refreshAccessToken(): SpotifyRefreshResult {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            ?: return SpotifyRefreshResult.ReauthRequired

        return runCatching {
            val response = httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                },
            )
            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.BadRequest && body.contains("invalid_grant", ignoreCase = true)) {
                clearTokens()
                return SpotifyRefreshResult.ReauthRequired
            }
            if (response.status.value !in 200..299) {
                return SpotifyRefreshResult.Failed(
                    IllegalStateException("Spotify refresh failed: HTTP ${response.status.value}")
                )
            }
            val tokenResponse = json.decodeFromString<SpotifyTokenResponse>(body)
            storeToken(tokenResponse, fallbackRefreshToken = refreshToken)
            refreshProfileEmail(tokenResponse.accessToken)
            SpotifyRefreshResult.Refreshed
        }.getOrElse { SpotifyRefreshResult.Failed(it) }
    }

    fun disconnect() {
        clearTokens()
    }

    fun launchAuthActivity() {
        context.startActivity(
            Intent(context, SpotifyAuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun authorizedAtMs(): Long = prefs.getLong(KEY_AUTHORIZED_AT_MS, 0L)

    private suspend fun exchangeCode(code: String, verifier: String): SpotifyTokenResponse {
        val response = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", REDIRECT_URI)
                append("code_verifier", verifier)
            },
        )
        return json.decodeFromString(response.bodyAsText())
    }

    private fun storeToken(
        response: SpotifyTokenResponse,
        fallbackRefreshToken: String? = null,
        authorizedAtMs: Long? = null,
    ) {
        val refreshToken = response.refreshToken ?: fallbackRefreshToken
        val shouldStoreAuthorizedAt = authorizedAtMs != null || !prefs.contains(KEY_AUTHORIZED_AT_MS)
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            putLong(KEY_EXPIRES_AT_MS, System.currentTimeMillis() + response.expiresIn * 1000L)
            if (shouldStoreAuthorizedAt) {
                putLong(KEY_AUTHORIZED_AT_MS, authorizedAtMs ?: System.currentTimeMillis())
            }
        }
        _authState.value = loadState()
    }

    private fun clearTokens() {
        prefs.edit { clear() }
        _authState.value = SpotifyAuthState()
        WorkManager.getInstance(context).cancelUniqueWork(SpotifyTokenRefreshWorker.UNIQUE_WORK)
    }

    private suspend fun refreshProfileEmail(accessToken: String) {
        runCatching {
            val response = httpClient.get("https://api.spotify.com/v1/me") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            json.decodeFromString<SpotifyUserProfile>(response.bodyAsText())
        }.getOrNull()?.let { profile ->
            val label = profile.email ?: profile.displayName
            prefs.edit {
                if (label.isNullOrBlank()) remove(KEY_ACCOUNT_EMAIL)
                else putString(KEY_ACCOUNT_EMAIL, label)
            }
            _authState.value = loadState()
        }
    }

    private fun enqueueRefreshWork() {
        val request = PeriodicWorkRequestBuilder<SpotifyTokenRefreshWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SpotifyTokenRefreshWorker.UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun loadState(): SpotifyAuthState =
        SpotifyAuthState(
            isAuthenticated = !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank(),
            accountEmail = prefs.getString(KEY_ACCOUNT_EMAIL, null),
        )

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun randomUrlSafeString(bytes: Int): String {
        val raw = ByteArray(bytes)
        SecureRandom().nextBytes(raw)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val REDIRECT_URI = "tryptify://spotify-callback"
        private const val PREFS_NAME = "spotify_auth"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val REFRESH_SLOP_MS = 60_000L
        private const val SPOTIFY_SCOPES =
            "user-read-currently-playing user-read-recently-played user-read-playback-state user-read-email user-top-read user-library-read playlist-read-private playlist-read-collaborative"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_AUTHORIZED_AT_MS = "spotify_authorized_at_ms"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}
