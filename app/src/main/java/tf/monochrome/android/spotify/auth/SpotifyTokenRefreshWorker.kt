package tf.monochrome.android.spotify.auth

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SpotifyTokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authManager: SpotifyAuthManager,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!authManager.authState.value.isAuthenticated) return Result.success()
        return when (authManager.refreshAccessToken()) {
            SpotifyRefreshResult.Refreshed -> Result.success()
            SpotifyRefreshResult.ReauthRequired -> Result.success()
            is SpotifyRefreshResult.Failed -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK = "spotify_token_refresh"
    }
}
