package tf.monochrome.android.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.ui.navigation.MonochromeNavHost
import tf.monochrome.android.ui.theme.MonochromeTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Force maximum available refresh rate (e.g. 120Hz)
        window.attributes = window.attributes.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val displayModes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    display?.supportedModes ?: emptyArray()
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.supportedModes
                }
                
                displayModes.maxByOrNull { it.refreshRate }?.let { bestMode ->
                    preferredDisplayModeId = bestMode.modeId
                }
            }
        }

        setContent {
            val themeName by preferences.theme.collectAsState(initial = "monochrome_dark")

            MonochromeTheme(themeName = themeName) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MonochromeNavHost()
                }
            }
        }
    }
}
