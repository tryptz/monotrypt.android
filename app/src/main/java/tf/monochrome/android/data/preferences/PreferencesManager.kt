package tf.monochrome.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.NowPlayingViewMode
import tf.monochrome.android.domain.model.ReplayGainMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "monochrome_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        // Audio quality
        private val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        private val CELLULAR_QUALITY = stringPreferencesKey("cellular_quality")

        // ReplayGain
        private val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        private val REPLAY_GAIN_PREAMP = doublePreferencesKey("replay_gain_preamp")

        // Player state
        private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        private val REPEAT_MODE = intPreferencesKey("repeat_mode")
        private val VOLUME = doublePreferencesKey("volume")

        // Instance cache
        private val INSTANCES_CACHE = stringPreferencesKey("instances_cache")
        private val INSTANCES_CACHE_TIMESTAMP = longPreferencesKey("instances_cache_timestamp")

        // Theme
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")

        // Scrobbling
        private val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        private val LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        private val LASTFM_ENABLED = booleanPreferencesKey("lastfm_enabled")
        private val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
        private val LISTENBRAINZ_ENABLED = booleanPreferencesKey("listenbrainz_enabled")

        // Custom API endpoint
        private val CUSTOM_API_ENDPOINT = stringPreferencesKey("custom_api_endpoint")

        // Interface
        private val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        private val SHOW_EXPLICIT_BADGES = booleanPreferencesKey("show_explicit_badges")
        private val CONFIRM_CLEAR_QUEUE = booleanPreferencesKey("confirm_clear_queue")

        // Audio extras
        private val NORMALIZATION_ENABLED = booleanPreferencesKey("normalization_enabled")
        private val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")

        // Downloads
        private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        private val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")

        // Playback speed
        private val PLAYBACK_SPEED = stringPreferencesKey("playback_speed")
        private val PRESERVE_PITCH = booleanPreferencesKey("preserve_pitch")

        // Appearance extras
        private val FONT_SIZE = stringPreferencesKey("font_size")

        // Google Auth
        private val GOOGLE_USER_ID = stringPreferencesKey("google_user_id")
        private val GOOGLE_DISPLAY_NAME = stringPreferencesKey("google_display_name")
        private val GOOGLE_EMAIL = stringPreferencesKey("google_email")
        private val GOOGLE_PHOTO_URL = stringPreferencesKey("google_photo_url")

        // Parity features
        private val VISUALIZER_SENSITIVITY = intPreferencesKey("visualizer_sensitivity")
        private val VISUALIZER_BRIGHTNESS = intPreferencesKey("visualizer_brightness")
        private val ROMAJI_LYRICS = booleanPreferencesKey("romaji_lyrics")
        private val DOWNLOAD_LYRICS = booleanPreferencesKey("download_lyrics")
        private val NOW_PLAYING_VIEW_MODE = stringPreferencesKey("now_playing_view_mode")
        
        // AI
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val AI_RADIO_ENABLED = booleanPreferencesKey("ai_radio_enabled")

        // PocketBase
        private val POCKETBASE_TOKEN = stringPreferencesKey("pocketbase_token")
        private val POCKETBASE_USER_ID = stringPreferencesKey("pocketbase_user_id")
        private val POCKETBASE_EMAIL = stringPreferencesKey("pocketbase_email")
        // Home screen cache
        private val HOME_RECOMMENDATIONS_CACHE = stringPreferencesKey("home_recommendations_cache")

        // EQ / AutoEQ
        private val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val EQ_ACTIVE_PRESET_ID = stringPreferencesKey("eq_active_preset_id")
        private val EQ_TARGET_ID = stringPreferencesKey("eq_target_id")
        private val EQ_PREAMP = doublePreferencesKey("eq_preamp")
        private val EQ_BANDS_JSON = stringPreferencesKey("eq_bands_json")
    }

    // Audio Quality
    val wifiQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[WIFI_QUALITY]?.let { AudioQuality.valueOf(it) } ?: AudioQuality.HI_RES
    }

    val cellularQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[CELLULAR_QUALITY]?.let { AudioQuality.valueOf(it) } ?: AudioQuality.HIGH
    }

    suspend fun setWifiQuality(quality: AudioQuality) {
        dataStore.edit { it[WIFI_QUALITY] = quality.name }
    }

    suspend fun setCellularQuality(quality: AudioQuality) {
        dataStore.edit { it[CELLULAR_QUALITY] = quality.name }
    }

    // ReplayGain
    val replayGainMode: Flow<ReplayGainMode> = dataStore.data.map { prefs ->
        prefs[REPLAY_GAIN_MODE]?.let { ReplayGainMode.valueOf(it) } ?: ReplayGainMode.OFF
    }

    val replayGainPreamp: Flow<Double> = dataStore.data.map { prefs ->
        prefs[REPLAY_GAIN_PREAMP] ?: 0.0
    }

    suspend fun setReplayGainMode(mode: ReplayGainMode) {
        dataStore.edit { it[REPLAY_GAIN_MODE] = mode.name }
    }

    suspend fun setReplayGainPreamp(preamp: Double) {
        dataStore.edit { it[REPLAY_GAIN_PREAMP] = preamp }
    }

    // Player state
    val shuffleEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHUFFLE_ENABLED] ?: false
    }

    val repeatMode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[REPEAT_MODE] ?: 0
    }

    val volume: Flow<Double> = dataStore.data.map { prefs ->
        prefs[VOLUME] ?: 1.0
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        dataStore.edit { it[REPEAT_MODE] = mode }
    }

    suspend fun setVolume(volume: Double) {
        dataStore.edit { it[VOLUME] = volume }
    }

    // Instance cache
    val instancesCache: Flow<String?> = dataStore.data.map { prefs ->
        prefs[INSTANCES_CACHE]
    }

    val instancesCacheTimestamp: Flow<Long> = dataStore.data.map { prefs ->
        prefs[INSTANCES_CACHE_TIMESTAMP] ?: 0L
    }

    suspend fun saveInstancesCache(json: String) {
        dataStore.edit {
            it[INSTANCES_CACHE] = json
            it[INSTANCES_CACHE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    // Theme
    val theme: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME] ?: "monochrome_dark"
    }

    val dynamicColors: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DYNAMIC_COLORS] ?: false
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[THEME] = theme }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLORS] = enabled }
    }

    // Scrobbling - Last.fm
    val lastFmSessionKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[LASTFM_SESSION_KEY]
    }

    val lastFmUsername: Flow<String?> = dataStore.data.map { prefs ->
        prefs[LASTFM_USERNAME]
    }

    val lastFmEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LASTFM_ENABLED] ?: false
    }

    suspend fun setLastFmSession(sessionKey: String, username: String) {
        dataStore.edit {
            it[LASTFM_SESSION_KEY] = sessionKey
            it[LASTFM_USERNAME] = username
            it[LASTFM_ENABLED] = true
        }
    }

    suspend fun clearLastFmSession() {
        dataStore.edit {
            it.remove(LASTFM_SESSION_KEY)
            it.remove(LASTFM_USERNAME)
            it[LASTFM_ENABLED] = false
        }
    }

    // Scrobbling - ListenBrainz
    val listenBrainzToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[LISTENBRAINZ_TOKEN]
    }

    val listenBrainzEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LISTENBRAINZ_ENABLED] ?: false
    }

    suspend fun setListenBrainzToken(token: String) {
        dataStore.edit {
            it[LISTENBRAINZ_TOKEN] = token
            it[LISTENBRAINZ_ENABLED] = true
        }
    }

    suspend fun clearListenBrainzToken() {
        dataStore.edit {
            it.remove(LISTENBRAINZ_TOKEN)
            it[LISTENBRAINZ_ENABLED] = false
        }
    }

    // Custom API
    val customApiEndpoint: Flow<String?> = dataStore.data.map { prefs ->
        prefs[CUSTOM_API_ENDPOINT]
    }

    suspend fun setCustomApiEndpoint(endpoint: String?) {
        dataStore.edit {
            if (endpoint != null) {
                it[CUSTOM_API_ENDPOINT] = endpoint
            } else {
                it.remove(CUSTOM_API_ENDPOINT)
            }
        }
    }

    // --- Interface ---
    val gaplessPlayback: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GAPLESS_PLAYBACK] ?: true
    }
    suspend fun setGaplessPlayback(enabled: Boolean) {
        dataStore.edit { it[GAPLESS_PLAYBACK] = enabled }
    }

    val showExplicitBadges: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_EXPLICIT_BADGES] ?: true
    }
    suspend fun setShowExplicitBadges(enabled: Boolean) {
        dataStore.edit { it[SHOW_EXPLICIT_BADGES] = enabled }
    }

    val confirmClearQueue: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CONFIRM_CLEAR_QUEUE] ?: true
    }
    suspend fun setConfirmClearQueue(enabled: Boolean) {
        dataStore.edit { it[CONFIRM_CLEAR_QUEUE] = enabled }
    }

    // --- Audio extras ---
    val normalizationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NORMALIZATION_ENABLED] ?: false
    }
    suspend fun setNormalizationEnabled(enabled: Boolean) {
        dataStore.edit { it[NORMALIZATION_ENABLED] = enabled }
    }

    val crossfadeDuration: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CROSSFADE_DURATION] ?: 0
    }
    suspend fun setCrossfadeDuration(seconds: Int) {
        dataStore.edit { it[CROSSFADE_DURATION] = seconds }
    }

    // --- Downloads ---
    val downloadQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_QUALITY]?.let { AudioQuality.valueOf(it) } ?: AudioQuality.HI_RES
    }
    suspend fun setDownloadQuality(quality: AudioQuality) {
        dataStore.edit { it[DOWNLOAD_QUALITY] = quality.name }
    }

    val downloadFolderUri: Flow<String?> = dataStore.data.map { it[DOWNLOAD_FOLDER_URI] }
    suspend fun setDownloadFolderUri(uri: String?) {
        dataStore.edit {
            if (uri != null) it[DOWNLOAD_FOLDER_URI] = uri
            else it.remove(DOWNLOAD_FOLDER_URI)
        }
    }

    // --- Playback speed ---
    val playbackSpeed: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PLAYBACK_SPEED]?.toFloatOrNull() ?: 1.0f
    }
    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[PLAYBACK_SPEED] = speed.toString() }
    }

    val preservePitch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PRESERVE_PITCH] ?: true
    }
    suspend fun setPreservePitch(enabled: Boolean) {
        dataStore.edit { it[PRESERVE_PITCH] = enabled }
    }

    // --- Font size ---
    val fontSize: Flow<String> = dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: "medium"
    }
    suspend fun setFontSize(size: String) {
        dataStore.edit { it[FONT_SIZE] = size }
    }

    // --- Google Auth ---
    val googleUserId: Flow<String?> = dataStore.data.map { it[GOOGLE_USER_ID] }
    val googleDisplayName: Flow<String?> = dataStore.data.map { it[GOOGLE_DISPLAY_NAME] }
    val googleEmail: Flow<String?> = dataStore.data.map { it[GOOGLE_EMAIL] }
    val googlePhotoUrl: Flow<String?> = dataStore.data.map { it[GOOGLE_PHOTO_URL] }

    suspend fun setGoogleProfile(userId: String, displayName: String?, email: String?, photoUrl: String?) {
        dataStore.edit {
            it[GOOGLE_USER_ID] = userId
            displayName?.let { name -> it[GOOGLE_DISPLAY_NAME] = name }
            email?.let { e -> it[GOOGLE_EMAIL] = e }
            photoUrl?.let { url -> it[GOOGLE_PHOTO_URL] = url }
        }
    }

    suspend fun clearGoogleProfile() {
        dataStore.edit {
            it.remove(GOOGLE_USER_ID)
            it.remove(GOOGLE_DISPLAY_NAME)
            it.remove(GOOGLE_EMAIL)
            it.remove(GOOGLE_PHOTO_URL)
        }
    }

    // --- Parity features ---
    val visualizerSensitivity: Flow<Int> = dataStore.data.map { it[VISUALIZER_SENSITIVITY] ?: 50 }
    val visualizerBrightness: Flow<Int> = dataStore.data.map { it[VISUALIZER_BRIGHTNESS] ?: 80 }
    val romajiLyrics: Flow<Boolean> = dataStore.data.map { it[ROMAJI_LYRICS] ?: false }
    val downloadLyrics: Flow<Boolean> = dataStore.data.map { it[DOWNLOAD_LYRICS] ?: false }

    suspend fun setVisualizerSensitivity(value: Int) {
        dataStore.edit { it[VISUALIZER_SENSITIVITY] = value }
    }
    suspend fun setVisualizerBrightness(value: Int) {
        dataStore.edit { it[VISUALIZER_BRIGHTNESS] = value }
    }
    suspend fun setRomajiLyrics(enabled: Boolean) {
        dataStore.edit { it[ROMAJI_LYRICS] = enabled }
    }
    suspend fun setDownloadLyrics(enabled: Boolean) {
        dataStore.edit { it[DOWNLOAD_LYRICS] = enabled }
    }

    val nowPlayingViewMode: Flow<NowPlayingViewMode> = dataStore.data.map { prefs ->
        prefs[NOW_PLAYING_VIEW_MODE]?.let { NowPlayingViewMode.valueOf(it) } ?: NowPlayingViewMode.COVER_ART
    }
    suspend fun setNowPlayingViewMode(mode: NowPlayingViewMode) {
        dataStore.edit { it[NOW_PLAYING_VIEW_MODE] = mode.name }
    }

    // --- AI ---
    val geminiApiKey: Flow<String?> = dataStore.data.map { it[GEMINI_API_KEY] }
    val aiRadioEnabled: Flow<Boolean> = dataStore.data.map { it[AI_RADIO_ENABLED] ?: false }

    suspend fun setGeminiApiKey(key: String?) {
        dataStore.edit {
            if (key.isNullOrBlank()) it.remove(GEMINI_API_KEY)
            else it[GEMINI_API_KEY] = key
        }
    }

    suspend fun setAiRadioEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_RADIO_ENABLED] = enabled }
    }

    // --- Home Screen Cache ---
    val homeRecommendationsCache: Flow<String?> = dataStore.data.map { it[HOME_RECOMMENDATIONS_CACHE] }

    suspend fun setHomeRecommendationsCache(json: String?) {
        dataStore.edit {
            if (json.isNullOrBlank()) it.remove(HOME_RECOMMENDATIONS_CACHE)
            else it[HOME_RECOMMENDATIONS_CACHE] = json
        }
    }

    // --- PocketBase ---
    val pocketBaseToken: Flow<String?> = dataStore.data.map { it[POCKETBASE_TOKEN] }
    val pocketBaseUserId: Flow<String?> = dataStore.data.map { it[POCKETBASE_USER_ID] }
    val pocketBaseEmail: Flow<String?> = dataStore.data.map { it[POCKETBASE_EMAIL] }

    suspend fun setPocketBaseAuth(token: String, userId: String, email: String) {
        dataStore.edit {
            it[POCKETBASE_TOKEN] = token
            it[POCKETBASE_USER_ID] = userId
            it[POCKETBASE_EMAIL] = email
        }
    }

    suspend fun clearPocketBaseAuth() {
        dataStore.edit {
            it.remove(POCKETBASE_TOKEN)
            it.remove(POCKETBASE_USER_ID)
            it.remove(POCKETBASE_EMAIL)
        }
    }

    // --- EQ / AutoEQ ---
    val eqEnabled: Flow<Boolean> = dataStore.data.map { it[EQ_ENABLED] ?: false }
    val eqActivePresetId: Flow<String?> = dataStore.data.map { it[EQ_ACTIVE_PRESET_ID] }
    val eqTargetId: Flow<String> = dataStore.data.map { it[EQ_TARGET_ID] ?: "harman_oe_2018" }
    val eqPreamp: Flow<Double> = dataStore.data.map { it[EQ_PREAMP] ?: 0.0 }
    val eqBandsJson: Flow<String?> = dataStore.data.map { it[EQ_BANDS_JSON] }

    suspend fun setEqEnabled(enabled: Boolean) {
        dataStore.edit { it[EQ_ENABLED] = enabled }
    }

    suspend fun setEqActivePreset(presetId: String?) {
        dataStore.edit {
            if (presetId != null) {
                it[EQ_ACTIVE_PRESET_ID] = presetId
            } else {
                it.remove(EQ_ACTIVE_PRESET_ID)
            }
        }
    }

    suspend fun setEqTarget(targetId: String) {
        dataStore.edit { it[EQ_TARGET_ID] = targetId }
    }

    suspend fun setEqPreamp(preamp: Double) {
        dataStore.edit { it[EQ_PREAMP] = preamp }
    }

    suspend fun setEqBands(bandsJson: String?) {
        dataStore.edit {
            if (bandsJson != null) {
                it[EQ_BANDS_JSON] = bandsJson
            } else {
                it.remove(EQ_BANDS_JSON)
            }
        }
    }

    // --- Clear all prefs (System) ---
    suspend fun clearAllData() {
        dataStore.edit { it.clear() }
    }
}
