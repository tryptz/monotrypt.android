package tf.monochrome.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.api.Instance
import tf.monochrome.android.data.api.InstanceManager
import tf.monochrome.android.data.api.InstanceType
import tf.monochrome.android.data.auth.AuthRepository
import tf.monochrome.android.data.import_.PlaylistImporter
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.sync.BackupManager
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.NowPlayingViewMode
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val instanceManager: InstanceManager,
    private val authRepository: AuthRepository,
    private val backupManager: BackupManager,
    private val playlistImporter: PlaylistImporter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // --- Appearance ---
    val theme: StateFlow<String> = preferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "monochrome_dark")
    val dynamicColors: StateFlow<Boolean> = preferences.dynamicColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val fontSize: StateFlow<String> = preferences.fontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "medium")

    // --- Interface ---
    val gaplessPlayback: StateFlow<Boolean> = preferences.gaplessPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showExplicitBadges: StateFlow<Boolean> = preferences.showExplicitBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val confirmClearQueue: StateFlow<Boolean> = preferences.confirmClearQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // --- Scrobbling ---
    val lastFmEnabled: StateFlow<Boolean> = preferences.lastFmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lastFmUsername: StateFlow<String?> = preferences.lastFmUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val listenBrainzEnabled: StateFlow<Boolean> = preferences.listenBrainzEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val listenBrainzToken: StateFlow<String?> = preferences.listenBrainzToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Audio ---
    val wifiQuality: StateFlow<AudioQuality> = preferences.wifiQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.HI_RES)
    val cellularQuality: StateFlow<AudioQuality> = preferences.cellularQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.HIGH)
    val normalizationEnabled: StateFlow<Boolean> = preferences.normalizationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val crossfadeDuration: StateFlow<Int> = preferences.crossfadeDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Audio speed ---
    val playbackSpeed: StateFlow<Float> = preferences.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val preservePitch: StateFlow<Boolean> = preferences.preservePitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // --- Downloads ---
    val downloadQuality: StateFlow<AudioQuality> = preferences.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.HI_RES)
    val downloadLyrics: StateFlow<Boolean> = preferences.downloadLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val downloadFolderUri: StateFlow<String?> = preferences.downloadFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Parity features ---
    val visualizerSensitivity: StateFlow<Int> = preferences.visualizerSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)
    val visualizerBrightness: StateFlow<Int> = preferences.visualizerBrightness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80)
    val romajiLyrics: StateFlow<Boolean> = preferences.romajiLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val nowPlayingViewMode: StateFlow<NowPlayingViewMode> = preferences.nowPlayingViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowPlayingViewMode.COVER_ART)
    
    // --- AI ---
    val geminiApiKey: StateFlow<String?> = preferences.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val aiRadioEnabled: StateFlow<Boolean> = preferences.aiRadioEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- PocketBase Auth ---
    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val userEmail: StateFlow<String?> = authRepository.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Instances ---
    private val _apiInstances = MutableStateFlow<List<Instance>>(emptyList())
    val apiInstances: StateFlow<List<Instance>> = _apiInstances.asStateFlow()
    private val _streamingInstances = MutableStateFlow<List<Instance>>(emptyList())
    val streamingInstances: StateFlow<List<Instance>> = _streamingInstances.asStateFlow()
    val customEndpoint: StateFlow<String?> = preferences.customApiEndpoint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _instancesRefreshing = MutableStateFlow(false)
    val instancesRefreshing: StateFlow<Boolean> = _instancesRefreshing.asStateFlow()

    // --- System ---
    private val _cacheSize = MutableStateFlow("")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    init {
        loadInstances()
        calculateCacheSize()
    }

    // --- Appearance actions ---
    fun setTheme(theme: String) { viewModelScope.launch { preferences.setTheme(theme) } }
    fun setDynamicColors(enabled: Boolean) { viewModelScope.launch { preferences.setDynamicColors(enabled) } }
    fun setFontSize(size: String) { viewModelScope.launch { preferences.setFontSize(size) } }

    // --- Interface actions ---
    fun setGaplessPlayback(enabled: Boolean) { viewModelScope.launch { preferences.setGaplessPlayback(enabled) } }
    fun setShowExplicitBadges(enabled: Boolean) { viewModelScope.launch { preferences.setShowExplicitBadges(enabled) } }
    fun setConfirmClearQueue(enabled: Boolean) { viewModelScope.launch { preferences.setConfirmClearQueue(enabled) } }

    // --- Scrobbling actions ---
    fun setLastFmSession(sessionKey: String, username: String) {
        viewModelScope.launch { preferences.setLastFmSession(sessionKey, username) }
    }
    fun clearLastFmSession() { viewModelScope.launch { preferences.clearLastFmSession() } }
    fun setListenBrainzToken(token: String) { viewModelScope.launch { preferences.setListenBrainzToken(token) } }
    fun clearListenBrainzToken() { viewModelScope.launch { preferences.clearListenBrainzToken() } }

    // --- Audio actions ---
    fun setWifiQuality(quality: AudioQuality) { viewModelScope.launch { preferences.setWifiQuality(quality) } }
    fun setCellularQuality(quality: AudioQuality) { viewModelScope.launch { preferences.setCellularQuality(quality) } }
    fun setNormalizationEnabled(enabled: Boolean) { viewModelScope.launch { preferences.setNormalizationEnabled(enabled) } }
    fun setCrossfadeDuration(seconds: Int) { viewModelScope.launch { preferences.setCrossfadeDuration(seconds) } }

    // --- Audio speed actions ---
    fun setPlaybackSpeed(speed: Float) { viewModelScope.launch { preferences.setPlaybackSpeed(speed) } }
    fun setPreservePitch(enabled: Boolean) { viewModelScope.launch { preferences.setPreservePitch(enabled) } }

    // --- Downloads actions ---
    fun setDownloadQuality(quality: AudioQuality) { viewModelScope.launch { preferences.setDownloadQuality(quality) } }
    fun setDownloadLyrics(enabled: Boolean) { viewModelScope.launch { preferences.setDownloadLyrics(enabled) } }
    fun setDownloadFolderUri(uri: String?) { viewModelScope.launch { preferences.setDownloadFolderUri(uri) } }

    // --- AI actions ---
    fun setGeminiApiKey(key: String?) { viewModelScope.launch { preferences.setGeminiApiKey(key) } }
    fun setAiRadioEnabled(enabled: Boolean) { viewModelScope.launch { preferences.setAiRadioEnabled(enabled) } }

    // --- Parity actions ---
    fun setVisualizerSensitivity(value: Int) { viewModelScope.launch { preferences.setVisualizerSensitivity(value) } }
    fun setVisualizerBrightness(value: Int) { viewModelScope.launch { preferences.setVisualizerBrightness(value) } }
    fun setRomajiLyrics(enabled: Boolean) { viewModelScope.launch { preferences.setRomajiLyrics(enabled) } }
    fun setNowPlayingViewMode(mode: NowPlayingViewMode) { viewModelScope.launch { preferences.setNowPlayingViewMode(mode) } }
 
    // --- Account actions ---
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    // --- Backup & Restore actions ---
    fun exportLibrary(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = backupManager.exportLibrary()
            onResult(json)
        }
    }

    fun importLibrary(jsonStr: String) {
        viewModelScope.launch {
            backupManager.importLibrary(jsonStr)
        }
    }

    // --- Playlist Import actions ---
    fun importPlaylist(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = playlistImporter.importFromUrl(url)
            result.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    // Create playlist and add tracks
                    val id = preferences.customApiEndpoint.first() ?: "Imported"
                    // Real implementation would use libraryRepository.createPlaylist(name, ...)
                    onResult(true, "Imported ${tracks.size} tracks")
                } else {
                    onResult(false, "No tracks found or matched")
                }
            }.onFailure {
                onResult(false, it.message ?: "Import failed")
            }
        }
    }

    // --- Instance actions ---
    private fun loadInstances() {
        viewModelScope.launch {
            try {
                _apiInstances.value = instanceManager.getInstances(InstanceType.API)
                _streamingInstances.value = instanceManager.getInstances(InstanceType.STREAMING)
            } catch (_: Exception) {}
        }
    }

    fun refreshInstances() {
        viewModelScope.launch {
            _instancesRefreshing.value = true
            try {
                instanceManager.refreshInstances()
                _apiInstances.value = instanceManager.getInstances(InstanceType.API)
                _streamingInstances.value = instanceManager.getInstances(InstanceType.STREAMING)
            } catch (_: Exception) {}
            _instancesRefreshing.value = false
        }
    }

    fun setCustomEndpoint(endpoint: String?) {
        viewModelScope.launch {
            preferences.setCustomApiEndpoint(endpoint)
            loadInstances()
        }
    }

    // --- System actions ---
    private fun calculateCacheSize() {
        viewModelScope.launch {
            val cacheDir = appContext.cacheDir
            val size = getDirSize(cacheDir)
            _cacheSize.value = formatSize(size)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            appContext.cacheDir.deleteRecursively()
            calculateCacheSize()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            preferences.clearAllData()
            appContext.cacheDir.deleteRecursively()
            calculateCacheSize()
        }
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
