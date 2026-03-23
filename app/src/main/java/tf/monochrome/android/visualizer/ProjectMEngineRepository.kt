package tf.monochrome.android.visualizer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.VisualizerEnginePhase
import tf.monochrome.android.domain.model.VisualizerEngineStatus
import tf.monochrome.android.domain.model.VisualizerPreset

@Singleton
class ProjectMEngineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesManager,
    val audioBus: ProjectMAudioBus,
    private val assetInstaller: ProjectMAssetInstaller,
    private val presetCatalog: ProjectMPresetCatalog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineLock = Any()
    private val nativeBridge = ProjectMNativeBridge()

    private val _engineStatus = MutableStateFlow(
        VisualizerEngineStatus(
            phase = if (ProjectMNativeBridge.isLibraryLoaded) VisualizerEnginePhase.READY else VisualizerEnginePhase.FALLBACK,
            nativeLibraryLoaded = ProjectMNativeBridge.isLibraryLoaded,
            message = if (ProjectMNativeBridge.isLibraryLoaded) {
                "projectM native bridge loaded."
            } else {
                "Native projectM bridge unavailable. Using fallback visualizer."
            }
        )
    )
    val engineStatus: StateFlow<VisualizerEngineStatus> = _engineStatus.asStateFlow()

    private val _presets = MutableStateFlow<List<VisualizerPreset>>(emptyList())
    val presets: StateFlow<List<VisualizerPreset>> = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow<VisualizerPreset?>(null)
    val currentPreset: StateFlow<VisualizerPreset?> = _currentPreset.asStateFlow()

    private val _autoShuffle = MutableStateFlow(true)
    val autoShuffle: StateFlow<Boolean> = _autoShuffle.asStateFlow()

    private val _engineEnabled = MutableStateFlow(true)
    val engineEnabled: StateFlow<Boolean> = _engineEnabled.asStateFlow()

    private val _currentFps = MutableStateFlow(0)
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

    private var installedAssets: InstalledProjectMAssets? = null
    private var textureSize: Int = 1024
    private var meshX: Int = 32
    private var meshY: Int = 24
    private var targetFps: Int = 60
    private var preferredPresetId: String? = null
    private var beatSensitivity: Int = 50
    private var brightness: Int = 80
    private var rotationSeconds: Int = 20
    private var playbackPaused: Boolean = false
    private var attachedSurfaceCount: Int = 0
    private var lastPcmTimestampMs: Long = 0L

    private var fpsFrameCount = 0
    private var fpsStartTimeMs = 0L

    init {
        observePreferences()
        scope.launch(Dispatchers.IO) {
            prepareEngine()
        }
    }

    private fun observePreferences() {
        scope.launch {
            preferences.visualizerEngineEnabled.collectLatest { enabled ->
                _engineEnabled.value = enabled
                synchronized(engineLock) {
                    if (!enabled) {
                        nativeBridge.release()
                        updateStatus(
                            phase = VisualizerEnginePhase.FALLBACK,
                            message = "projectM disabled in settings. Showing fallback visualizer."
                        )
                    } else if (ProjectMNativeBridge.isLibraryLoaded) {
                        updateStatus(
                            phase = if (attachedSurfaceCount > 0) VisualizerEnginePhase.READY else _engineStatus.value.phase,
                            message = "projectM enabled and ready."
                        )
                    }
                }
            }
        }
        scope.launch {
            preferences.visualizerAutoShuffle.collectLatest { enabled ->
                _autoShuffle.value = enabled
                synchronized(engineLock) {
                    nativeBridge.setPresetShuffleEnabled(enabled)
                }
            }
        }
        scope.launch {
            preferences.visualizerPresetId.collectLatest { presetId ->
                preferredPresetId = presetId
                val selected = _presets.value.firstOrNull { it.id == presetId }
                if (selected != null) {
                    _currentPreset.value = selected
                    synchronized(engineLock) {
                        if (engineStatus.value.isNativeReady) {
                            nativeBridge.setPreset(resolveAbsolutePresetPath(selected))
                        }
                    }
                }
            }
        }
        scope.launch {
            preferences.visualizerTextureSize.collectLatest { size ->
                textureSize = size
            }
        }
        scope.launch {
            preferences.visualizerMeshX.collectLatest { x ->
                meshX = x
                synchronized(engineLock) {
                    nativeBridge.configureQuality(meshX, meshY)
                }
            }
        }
        scope.launch {
            preferences.visualizerMeshY.collectLatest { y ->
                meshY = y
                synchronized(engineLock) {
                    nativeBridge.configureQuality(meshX, meshY)
                }
            }
        }
        scope.launch {
            preferences.visualizerTargetFps.collectLatest { fps ->
                targetFps = fps
                synchronized(engineLock) {
                    nativeBridge.configureTargetFps(targetFps)
                }
            }
        }
        scope.launch {
            preferences.visualizerSensitivity.collectLatest { value ->
                beatSensitivity = value
                synchronized(engineLock) {
                    nativeBridge.setBeatSensitivity(value)
                }
            }
        }
        scope.launch {
            preferences.visualizerBrightness.collectLatest { value ->
                brightness = value
                synchronized(engineLock) {
                    nativeBridge.setBrightness(value)
                }
            }
        }
        scope.launch {
            preferences.visualizerRotationSeconds.collectLatest { seconds ->
                rotationSeconds = seconds
                synchronized(engineLock) {
                    applyRotationLocked()
                }
            }
        }
    }

    fun prepareEngine() {
        synchronized(engineLock) {
            if (!_engineEnabled.value || !ProjectMNativeBridge.isLibraryLoaded) return
            ensureAssetsLocked()
        }
    }

    fun onSurfaceAttached(width: Int, height: Int) {
        synchronized(engineLock) {
            attachedSurfaceCount += 1
            if (!_engineEnabled.value || !ProjectMNativeBridge.isLibraryLoaded) {
                updateStatus(
                    phase = VisualizerEnginePhase.FALLBACK,
                    message = "Fallback visualizer active."
                )
                return
            }

            ensureAssetsLocked()
            val assets = installedAssets ?: return
            nativeBridge.configureQuality(meshX, meshY)
            nativeBridge.configureTargetFps(targetFps)
            val maxDim = max(width, height)
            val scale = if (maxDim > textureSize) textureSize.toFloat() / maxDim else 1f
            val scaledWidth = max(1, (width * scale).toInt())
            val scaledHeight = max(1, (height * scale).toInt())
            val initialized = nativeBridge.initialize(assets.rootDir.absolutePath, scaledWidth, scaledHeight, meshX, meshY)
            if (!initialized) {
                updateStatus(
                    phase = VisualizerEnginePhase.ERROR,
                    message = "projectM failed to initialize. Showing fallback visualizer."
                )
                return
            }

            nativeBridge.setPresetShuffleEnabled(_autoShuffle.value)
            nativeBridge.setBeatSensitivity(beatSensitivity)
            nativeBridge.setBrightness(brightness)
            applyRotationLocked()
            applyPreferredPresetLocked()
            updateStatus(
                phase = VisualizerEnginePhase.READY,
                message = "projectM surface ready."
            )
        }
    }

    fun onSurfaceResized(width: Int, height: Int) {
        synchronized(engineLock) {
            val maxDim = max(width, height)
            val scale = if (maxDim > textureSize) textureSize.toFloat() / maxDim else 1f
            val scaledWidth = max(1, (width * scale).toInt())
            val scaledHeight = max(1, (height * scale).toInt())
            nativeBridge.resize(scaledWidth, scaledHeight)
        }
    }

    fun onSurfaceDetached() {
        synchronized(engineLock) {
            attachedSurfaceCount = (attachedSurfaceCount - 1).coerceAtLeast(0)
            if (attachedSurfaceCount == 0) {
                nativeBridge.release()
                updateStatus(
                    phase = if (ProjectMNativeBridge.isLibraryLoaded && _engineEnabled.value) {
                        VisualizerEnginePhase.READY
                    } else {
                        VisualizerEnginePhase.FALLBACK
                    },
                    message = if (_engineEnabled.value) {
                        "projectM ready for the next visualizer session."
                    } else {
                        "Fallback visualizer active."
                    }
                )
            }
        }
    }

    fun renderFrame(frameTimeNanos: Long) {
        synchronized(engineLock) {
            if (!_engineEnabled.value || !engineStatus.value.isNativeReady) return
            audioBus.consumeLatest()?.let { frame ->
                lastPcmTimestampMs = frame.timestampMs
                nativeBridge.pushPcm(frame.samples, frame.channelCount, frame.sampleRate)
            }
            val freezeFrame = playbackPaused && (System.currentTimeMillis() - lastPcmTimestampMs) > 2_000L
            if (!freezeFrame) {
                nativeBridge.renderFrame(frameTimeNanos)
                updateStatus(
                    phase = VisualizerEnginePhase.ACTIVE,
                    message = "projectM rendering bundled presets."
                )
                
                fpsFrameCount++
                val now = System.currentTimeMillis()
                if (now - fpsStartTimeMs >= 1000) {
                    _currentFps.value = (fpsFrameCount * 1000L / (now - fpsStartTimeMs)).toInt()
                    fpsFrameCount = 0
                    fpsStartTimeMs = now
                }
            }
        }
    }

    fun setPlaybackPaused(paused: Boolean) {
        playbackPaused = paused
        synchronized(engineLock) {
            nativeBridge.setPaused(paused)
        }
    }

    fun nextPreset() {
        synchronized(engineLock) {
            val currentPath = nativeBridge.nextPreset() ?: return
            updateCurrentPresetFromPathLocked(currentPath)
            scope.launch {
                preferences.setVisualizerPresetId(_currentPreset.value?.id)
            }
        }
    }

    fun selectPreset(preset: VisualizerPreset) {
        preferredPresetId = preset.id
        _currentPreset.value = preset
        scope.launch {
            preferences.setVisualizerPresetId(preset.id)
        }
        synchronized(engineLock) {
            if (engineStatus.value.isNativeReady) {
                nativeBridge.setPreset(resolveAbsolutePresetPath(preset))
            }
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        scope.launch {
            preferences.setVisualizerAutoShuffle(enabled)
        }
    }

    fun setRotationSeconds(seconds: Int) {
        scope.launch {
            preferences.setVisualizerRotationSeconds(seconds)
        }
        synchronized(engineLock) {
            rotationSeconds = seconds
            applyRotationLocked()
        }
    }

    fun reportAudioTapFailure(message: String) {
        updateStatus(
            phase = VisualizerEnginePhase.FALLBACK,
            message = message
        )
    }

    private fun ensureAssetsLocked() {
        if (installedAssets != null) return
        updateStatus(
            phase = VisualizerEnginePhase.INSTALLING,
            message = "Installing bundled projectM presets."
        )
        runCatching {
            val assets = assetInstaller.ensureInstalled()
            val presets = presetCatalog.load(assets.catalogFile)
            installedAssets = assets
            _presets.value = presets
            _currentPreset.value = preferredPresetId?.let { id ->
                presets.firstOrNull { it.id == id }
            } ?: presets.firstOrNull()
            updateStatus(
                phase = VisualizerEnginePhase.READY,
                message = "Bundled projectM presets ready.",
                assetRoot = assets.rootDir.absolutePath,
                assetVersion = assets.version
            )
        }.onFailure { error ->
            updateStatus(
                phase = VisualizerEnginePhase.ERROR,
                message = "projectM assets failed to install: ${error.message ?: "unknown error"}"
            )
        }
    }

    private fun applyPreferredPresetLocked() {
        val presets = _presets.value
        if (presets.isEmpty()) return
        val selected = preferredPresetId?.let { id ->
            presets.firstOrNull { it.id == id }
        } ?: presets.first()
        if (nativeBridge.setPreset(resolveAbsolutePresetPath(selected))) {
            _currentPreset.value = selected
        } else {
            val currentPath = nativeBridge.nextPreset()
            updateCurrentPresetFromPathLocked(currentPath)
        }
    }

    private fun applyRotationLocked() {
        val seconds = rotationSeconds.coerceIn(5, 120)
        nativeBridge.setPresetShuffleEnabled(_autoShuffle.value)
        nativeBridge.configurePresetDuration(seconds)
    }

    private fun updateCurrentPresetFromPathLocked(path: String?) {
        val normalized = path ?: return
        _currentPreset.value = _presets.value.firstOrNull { preset ->
            resolveAbsolutePresetPath(preset) == normalized
        }
    }

    private fun resolveAbsolutePresetPath(preset: VisualizerPreset): String {
        val assets = installedAssets ?: assetInstaller.ensureInstalled().also { installedAssets = it }
        return File(assets.rootDir, preset.filePath).absolutePath
    }

    private fun updateStatus(
        phase: VisualizerEnginePhase,
        message: String,
        assetRoot: String? = _engineStatus.value.assetRoot,
        assetVersion: String = _engineStatus.value.assetVersion
    ) {
        _engineStatus.value = VisualizerEngineStatus(
            phase = phase,
            nativeLibraryLoaded = ProjectMNativeBridge.isLibraryLoaded,
            assetVersion = assetVersion,
            message = message,
            assetRoot = assetRoot
        )
    }
}
