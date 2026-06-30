package tf.monochrome.android.spotify.api

import tf.monochrome.android.spotify.auth.SpotifyAuthManager
import tf.monochrome.android.spotify.auth.SpotifyRefreshResult
import javax.inject.Inject
import javax.inject.Singleton

class SpotifyAuthRequiredException(
    message: String = "Spotify authorization is required",
) : IllegalStateException(message)

@Singleton
class SpotifyApiClient @Inject constructor(
    private val authManager: SpotifyAuthManager,
) {
    suspend fun accessTokenOrThrow(): String =
        authManager.validAccessToken() ?: throw SpotifyAuthRequiredException()

    suspend fun refreshToken(): Boolean =
        authManager.refreshAccessToken() is SpotifyRefreshResult.Refreshed

    suspend fun refreshAccessToken(): SpotifyRefreshResult = authManager.refreshAccessToken()
}
