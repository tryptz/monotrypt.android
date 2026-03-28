package tf.monochrome.android.ui.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tf.monochrome.android.audio.dsp.DspEngineManager
import tf.monochrome.android.audio.dsp.SnapinType
import tf.monochrome.android.audio.dsp.model.BusConfig
import tf.monochrome.android.audio.dsp.model.BusLevels
import tf.monochrome.android.audio.dsp.model.MixPreset
import tf.monochrome.android.data.repository.MixPresetRepository
import javax.inject.Inject

@HiltViewModel
class MixerViewModel @Inject constructor(
    private val dspManager: DspEngineManager,
    private val presetRepository: MixPresetRepository
) : ViewModel() {

    val enabled: StateFlow<Boolean> = dspManager.enabled
    val buses: StateFlow<List<BusConfig>> = dspManager.buses
    val busLevels: StateFlow<List<BusLevels>> = dspManager.busLevels

    val presets: StateFlow<List<MixPreset>> = presetRepository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Poll meter levels at ~50ms (20 fps) while the ViewModel is alive
        viewModelScope.launch {
            while (isActive) {
                dspManager.pollLevels()
                delay(50L)
            }
        }
    }

    private val _selectedBusIndex = MutableStateFlow(0)
    val selectedBusIndex: StateFlow<Int> = _selectedBusIndex.asStateFlow()

    private val _showPluginPicker = MutableStateFlow(false)
    val showPluginPicker: StateFlow<Boolean> = _showPluginPicker.asStateFlow()

    private val _editingPlugin = MutableStateFlow<Pair<Int, Int>?>(null) // busIndex, slotIndex
    val editingPlugin: StateFlow<Pair<Int, Int>?> = _editingPlugin.asStateFlow()

    fun setEnabled(enabled: Boolean) = dspManager.setEnabled(enabled)

    fun selectBus(index: Int) { _selectedBusIndex.value = index }

    // ── Bus controls ────────────────────────────────────────────────────

    fun setBusGain(busIndex: Int, gainDb: Float) = dspManager.setBusGain(busIndex, gainDb)
    fun setBusPan(busIndex: Int, pan: Float) = dspManager.setBusPan(busIndex, pan)
    fun toggleMute(busIndex: Int) {
        val bus = buses.value.getOrNull(busIndex) ?: return
        dspManager.setBusMute(busIndex, !bus.muted)
    }
    fun toggleSolo(busIndex: Int) {
        val bus = buses.value.getOrNull(busIndex) ?: return
        dspManager.setBusSolo(busIndex, !bus.soloed)
    }

    // ── Plugin chain ────────────────────────────────────────────────────

    fun showAddPlugin() { _showPluginPicker.value = true }
    fun dismissPluginPicker() { _showPluginPicker.value = false }

    fun addPlugin(type: SnapinType) {
        val busIndex = _selectedBusIndex.value
        val bus = buses.value.getOrNull(busIndex) ?: return
        dspManager.addPlugin(busIndex, bus.plugins.size, type)
        _showPluginPicker.value = false
    }

    fun removePlugin(busIndex: Int, slotIndex: Int) {
        dspManager.removePlugin(busIndex, slotIndex)
        if (_editingPlugin.value == Pair(busIndex, slotIndex)) {
            _editingPlugin.value = null
        }
    }

    fun togglePluginBypass(busIndex: Int, slotIndex: Int) {
        val bus = buses.value.getOrNull(busIndex) ?: return
        val plugin = bus.plugins.getOrNull(slotIndex) ?: return
        dspManager.setPluginBypassed(busIndex, slotIndex, !plugin.bypassed)
    }

    fun editPlugin(busIndex: Int, slotIndex: Int) {
        _editingPlugin.value = Pair(busIndex, slotIndex)
    }

    fun dismissPluginEditor() { _editingPlugin.value = null }

    fun setParameter(busIndex: Int, slotIndex: Int, paramIndex: Int, value: Float) {
        dspManager.setParameter(busIndex, slotIndex, paramIndex, value)
    }

    // ── Presets ──────────────────────────────────────────────────────────

    fun savePreset(name: String) {
        viewModelScope.launch {
            val json = dspManager.getStateJson()
            presetRepository.savePreset(MixPreset(name = name, stateJson = json))
        }
    }

    fun loadPreset(preset: MixPreset) {
        dspManager.loadStateJson(preset.stateJson)
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            presetRepository.deletePreset(id)
        }
    }
}
