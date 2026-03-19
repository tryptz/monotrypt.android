package tf.monochrome.android.ui.eq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.audio.eq.AutoEqEngine
import tf.monochrome.android.audio.eq.EqDataParser
import tf.monochrome.android.audio.eq.FrequencyTargets
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.data.repository.EqRepository
import tf.monochrome.android.data.repository.HeadphoneRepository
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.EqPreset
import tf.monochrome.android.domain.model.EqTarget
import tf.monochrome.android.domain.model.FrequencyPoint
import tf.monochrome.android.domain.model.Headphone
import javax.inject.Inject

@HiltViewModel
class EqViewModel @Inject constructor(
    private val eqRepository: EqRepository,
    private val headphoneRepository: HeadphoneRepository,
    private val preferences: PreferencesManager
) : ViewModel() {

    // ===== UI State =====

    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _allPresets = MutableStateFlow<List<EqPreset>>(emptyList())
    val allPresets: StateFlow<List<EqPreset>> = _allPresets.asStateFlow()

    private val _activePreset = MutableStateFlow<EqPreset?>(null)
    val activePreset: StateFlow<EqPreset?> = _activePreset.asStateFlow()

    private val _currentBands = MutableStateFlow<List<EqBand>>(emptyList())
    val currentBands: StateFlow<List<EqBand>> = _currentBands.asStateFlow()

    private val _currentPreamp = MutableStateFlow(0f)
    val currentPreamp: StateFlow<Float> = _currentPreamp.asStateFlow()

    private val _availableTargets = MutableStateFlow<List<EqTarget>>(FrequencyTargets.getAllTargets())
    val availableTargets: StateFlow<List<EqTarget>> = _availableTargets.asStateFlow()

    private val _selectedTarget = MutableStateFlow<EqTarget>(FrequencyTargets.getHarmanOverEar2018())
    val selectedTarget: StateFlow<EqTarget> = _selectedTarget.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _customPresets = MutableStateFlow<List<EqPreset>>(emptyList())
    val customPresets: StateFlow<List<EqPreset>> = _customPresets.asStateFlow()

    private val _presetCount = MutableStateFlow(0)
    val presetCount: StateFlow<Int> = _presetCount.asStateFlow()

    // ===== Headphone Selection State =====

    private val _availableHeadphones = MutableStateFlow<List<Headphone>>(emptyList())
    val availableHeadphones: StateFlow<List<Headphone>> = _availableHeadphones.asStateFlow()

    private val _selectedHeadphone = MutableStateFlow<Headphone?>(null)
    val selectedHeadphone: StateFlow<Headphone?> = _selectedHeadphone.asStateFlow()

    private val _headphonesLoading = MutableStateFlow(false)
    val headphonesLoading: StateFlow<Boolean> = _headphonesLoading.asStateFlow()

    private val _headphoneSearchQuery = MutableStateFlow("")
    val headphoneSearchQuery: StateFlow<String> = _headphoneSearchQuery.asStateFlow()

    // ===== AutoEQ Parameters =====

    private val _bandCount = MutableStateFlow(10)
    val bandCount: StateFlow<Int> = _bandCount.asStateFlow()

    private val _maxFrequency = MutableStateFlow(16000f)
    val maxFrequency: StateFlow<Float> = _maxFrequency.asStateFlow()

    private val _sampleRate = MutableStateFlow(48000f)
    val sampleRate: StateFlow<Float> = _sampleRate.asStateFlow()

    private val _headphoneTypeFilter = MutableStateFlow<String?>(null)
    val headphoneTypeFilter: StateFlow<String?> = _headphoneTypeFilter.asStateFlow()

    private val _originalMeasurement = MutableStateFlow<List<FrequencyPoint>>(emptyList())
    val originalMeasurement: StateFlow<List<FrequencyPoint>> = _originalMeasurement.asStateFlow()

    // ===== Initialization =====

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            // Load enabled state
            preferences.eqEnabled.collect { enabled ->
                _eqEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            // Load all presets
            eqRepository.getAllPresets().collect { presets ->
                _allPresets.value = presets
            }
        }

        viewModelScope.launch {
            // Load custom presets
            eqRepository.getCustomPresets().collect { presets ->
                _customPresets.value = presets
            }
        }

        viewModelScope.launch {
            // Load custom preset count
            eqRepository.getCustomPresetCount().collect { count ->
                _presetCount.value = count
            }
        }

        viewModelScope.launch {
            // Load active preset from preferences
            val presetId = preferences.eqActivePresetId.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            ).value

            if (presetId != null) {
                val preset = eqRepository.getPresetById(presetId)
                _activePreset.value = preset
                if (preset != null) {
                    _currentBands.value = preset.bands
                    _currentPreamp.value = preset.preamp
                    _selectedTarget.value = FrequencyTargets.getTargetById(preset.targetId)
                        ?: FrequencyTargets.getHarmanOverEar2018()
                }
            }
        }

        viewModelScope.launch {
            // Load preamp
            preferences.eqPreamp.collect { preamp ->
                _currentPreamp.value = preamp.toFloat()
            }
        }

        viewModelScope.launch {
            // Load target
            preferences.eqTargetId.collect { targetId ->
                val target = FrequencyTargets.getTargetById(targetId)
                if (target != null) {
                    _selectedTarget.value = target
                }
            }
        }
    }

    // ===== User Actions =====

    /**
     * Toggle EQ on/off
     */
    fun toggleEq() {
        val newState = !_eqEnabled.value
        viewModelScope.launch {
            preferences.setEqEnabled(newState)
            _eqEnabled.value = newState
        }
    }

    /**
     * Enable EQ
     */
    fun enableEq() {
        viewModelScope.launch {
            preferences.setEqEnabled(true)
            _eqEnabled.value = true
        }
    }

    /**
     * Disable EQ
     */
    fun disableEq() {
        viewModelScope.launch {
            preferences.setEqEnabled(false)
            _eqEnabled.value = false
        }
    }

    /**
     * Load a preset
     */
    fun loadPreset(presetId: String) {
        viewModelScope.launch {
            val preset = eqRepository.getPresetById(presetId) ?: return@launch
            _activePreset.value = preset
            _currentBands.value = preset.bands
            _currentPreamp.value = preset.preamp

            // Update preferences
            preferences.setEqActivePreset(presetId)
            preferences.setEqPreamp(preset.preamp.toDouble())
            preferences.setEqTarget(preset.targetId)

            // Update UI
            val target = FrequencyTargets.getTargetById(preset.targetId)
            if (target != null) {
                _selectedTarget.value = target
            }

            // Serialize and save bands
            saveBandsToPreferences(preset.bands)
        }
    }

    /**
     * Update an individual band
     */
    fun updateBand(bandId: Int, newBand: EqBand) {
        val updatedBands = _currentBands.value.toMutableList()
        val index = updatedBands.indexOfFirst { it.id == bandId }
        if (index >= 0) {
            updatedBands[index] = newBand
            _currentBands.value = updatedBands
            saveBandsToPreferences(updatedBands)
        }
    }

    /**
     * Update preamp gain
     */
    fun setPreamp(preamp: Float) {
        _currentPreamp.value = preamp
        viewModelScope.launch {
            preferences.setEqPreamp(preamp.toDouble())
        }
    }

    /**
     * Select target curve
     */
    fun selectTarget(targetId: String) {
        val target = FrequencyTargets.getTargetById(targetId) ?: return
        _selectedTarget.value = target
        viewModelScope.launch {
            preferences.setEqTarget(targetId)
        }
    }

    /**
     * Reset all bands to flat
     */
    fun resetToFlat() {
        val flatBands = _currentBands.value.map { band ->
            band.copy(gain = 0f)
        }
        _currentBands.value = flatBands
        _currentPreamp.value = 0f
        saveBandsToPreferences(flatBands)
        viewModelScope.launch {
            preferences.setEqPreamp(0.0)
        }
    }

    /**
     * Save current bands as a custom preset
     */
    fun saveAsPreset(presetName: String, description: String = "") {
        viewModelScope.launch {
            try {
                val preset = EqPreset(
                    id = "custom_preset_${System.currentTimeMillis()}",
                    name = presetName,
                    description = description,
                    bands = _currentBands.value,
                    preamp = _currentPreamp.value,
                    targetId = _selectedTarget.value.id,
                    targetName = _selectedTarget.value.label,
                    isCustom = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                eqRepository.savePreset(preset)
                _activePreset.value = preset
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to save preset: ${e.message}"
            }
        }
    }

    /**
     * Delete a custom preset
     */
    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            try {
                eqRepository.deletePreset(presetId)
                if (_activePreset.value?.id == presetId) {
                    _activePreset.value = null
                    _currentBands.value = emptyList()
                    _currentPreamp.value = 0f
                    preferences.setEqActivePreset(null)
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete preset: ${e.message}"
            }
        }
    }

    /**
     * Search presets by name
     */
    fun searchPresets(query: String) {
        viewModelScope.launch {
            eqRepository.searchPresets(query).collect { results ->
                _allPresets.value = results
            }
        }
    }

    /**
     * Calculate optimal EQ from headphone measurement
     *
     * @param measurementCsv Raw frequency response measurement (CSV format)
     * @param bandCount Number of EQ bands to generate (typically 10)
     */
    fun calculateAutoEq(measurementCsv: String) {
        viewModelScope.launch {
            try {
                _isCalculating.value = true
                _error.value = null

                val measurement = EqDataParser.parseRawData(measurementCsv)
                if (measurement.isEmpty()) {
                    _error.value = "Failed to parse measurement data"
                    _isCalculating.value = false
                    return@launch
                }

                _originalMeasurement.value = measurement

                val target = _selectedTarget.value.data
                if (target.isEmpty()) {
                    _error.value = "Target curve not available"
                    _isCalculating.value = false
                    return@launch
                }

                val bands = AutoEqEngine.runAutoEqAlgorithm(
                    measurement = measurement,
                    target = target,
                    bandCount = _bandCount.value,
                    maxFrequency = _maxFrequency.value,
                    sampleRate = _sampleRate.value
                )

                _currentBands.value = bands
                saveBandsToPreferences(bands)
                _error.value = null

            } catch (e: Exception) {
                _error.value = "AutoEQ calculation failed: ${e.message}"
            } finally {
                _isCalculating.value = false
            }
        }
    }

    /**
     * Load available headphones from GitHub AutoEq repository
     */
    fun loadAvailableHeadphones() {
        viewModelScope.launch {
            try {
                _headphonesLoading.value = true
                _error.value = null

                headphoneRepository.getAllHeadphones().collect { headphones ->
                    _availableHeadphones.value = headphones
                    _headphonesLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to load headphones: ${e.message}"
                _headphonesLoading.value = false
            }
        }
    }

    /**
     * Search headphones from GitHub AutoEq repository
     */
    fun searchAvailableHeadphones(query: String) {
        _headphoneSearchQuery.value = query
        viewModelScope.launch {
            try {
                _headphonesLoading.value = true
                _error.value = null

                headphoneRepository.searchHeadphones(query).collect { headphones ->
                    _availableHeadphones.value = headphones
                    _headphonesLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
                _headphonesLoading.value = false
            }
        }
    }

    /**
     * Select a headphone and load its measurement
     */
    fun selectHeadphone(headphone: Headphone) {
        _selectedHeadphone.value = headphone
        loadHeadphonePreset(headphone.name)
    }

    /**
     * Load preset and apply AutoEQ for a specific headphone
     *
     * Fetches measurement from GitHub AutoEq, parses it, and calculates optimal bands
     */
    fun loadHeadphonePreset(headphoneName: String) {
        viewModelScope.launch {
            try {
                _isCalculating.value = true
                _error.value = null

                val headphoneId = headphoneName.replace(" ", "_").lowercase()
                val measurementResult = headphoneRepository.loadHeadphoneMeasurement(headphoneId)

                measurementResult.collect { result ->
                    result.onSuccess { csvData ->
                        val measurement = EqDataParser.parseRawData(csvData)
                        if (measurement.isEmpty()) {
                            _error.value = "Failed to parse headphone measurement"
                            _isCalculating.value = false
                            return@collect
                        }

                        _originalMeasurement.value = measurement

                        val target = _selectedTarget.value.data
                        if (target.isEmpty()) {
                            _error.value = "Target curve not available"
                            _isCalculating.value = false
                            return@collect
                        }

                        val bands = AutoEqEngine.runAutoEqAlgorithm(
                            measurement = measurement,
                            target = target,
                            bandCount = _bandCount.value,
                            maxFrequency = _maxFrequency.value,
                            sampleRate = _sampleRate.value
                        )

                        _currentBands.value = bands
                        saveBandsToPreferences(bands)
                        _error.value = null
                        _isCalculating.value = false

                    }.onFailure { error ->
                        _error.value = "Failed to load measurement: ${error.message}"
                        _isCalculating.value = false
                    }
                }

            } catch (e: Exception) {
                _error.value = "Failed to load headphone preset: ${e.message}"
                _isCalculating.value = false
            }
        }
    }

    /**
     * Refresh headphone list from GitHub
     */
    fun refreshHeadphones() {
        viewModelScope.launch {
            headphoneRepository.refreshCache()
            loadAvailableHeadphones()
        }
    }

    /**
     * Clear any error messages
     */
    fun clearError() {
        _error.value = null
    }

    // ===== AutoEQ Parameter Actions =====

    fun setBandCount(count: Int) {
        _bandCount.value = count
    }

    fun setMaxFrequency(freq: Float) {
        _maxFrequency.value = freq
    }

    fun setSampleRate(rate: Float) {
        _sampleRate.value = rate
    }

    fun setHeadphoneTypeFilter(type: String?) {
        _headphoneTypeFilter.value = type
    }

    fun updateBandByDrag(bandId: Int, newFreq: Float, newGain: Float) {
        val updatedBands = _currentBands.value.toMutableList()
        val index = updatedBands.indexOfFirst { it.id == bandId }
        if (index >= 0) {
            updatedBands[index] = updatedBands[index].copy(
                freq = newFreq.coerceIn(20f, 20000f),
                gain = newGain.coerceIn(-12f, 12f)
            )
            _currentBands.value = updatedBands
            saveBandsToPreferences(updatedBands)
        }
    }

    fun runAutoEq() {
        val measurement = _originalMeasurement.value
        if (measurement.isEmpty()) {
            _error.value = "No headphone measurement loaded"
            return
        }
        val target = _selectedTarget.value.data
        if (target.isEmpty()) {
            _error.value = "Target curve not available"
            return
        }
        viewModelScope.launch {
            try {
                _isCalculating.value = true
                _error.value = null
                val bands = AutoEqEngine.runAutoEqAlgorithm(
                    measurement = measurement,
                    target = target,
                    bandCount = _bandCount.value,
                    maxFrequency = _maxFrequency.value,
                    sampleRate = _sampleRate.value
                )
                _currentBands.value = bands
                saveBandsToPreferences(bands)
            } catch (e: Exception) {
                _error.value = "AutoEQ calculation failed: ${e.message}"
            } finally {
                _isCalculating.value = false
            }
        }
    }

    // ===== Private Helpers =====

    private fun saveBandsToPreferences(bands: List<EqBand>) {
        viewModelScope.launch {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val bandsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        EqBand.serializer()
                    ),
                    bands
                )
                preferences.setEqBands(bandsJson)
            } catch (e: Exception) {
                _error.value = "Failed to save bands: ${e.message}"
            }
        }
    }
}
