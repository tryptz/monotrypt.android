package tf.monochrome.android.spotify.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SpotifyAuthActivity : ComponentActivity() {
    @Inject lateinit var authManager: SpotifyAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (data?.scheme == "tryptify" && data.host == "spotify-callback") {
            lifecycleScope.launch {
                runCatching { authManager.handleAuthorizationRedirect(data) }
                    .onSuccess { Log.i("SpotifyAuth", "Authorization redirect handled: " + it) }
                    .onFailure { Log.w("SpotifyAuth", "Authorization redirect failed", it) }
                finish()
            }
            return
        }

        val authUri = runCatching { authManager.authorizationUri() }.getOrNull()
        if (authUri == null) {
            finish()
            return
        }
        Log.i("SpotifyAuth", "Launching Spotify authorization")
        runCatching {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(this, authUri)
            finish()
        }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, authUri)) }
            finish()
        }
    }
}
