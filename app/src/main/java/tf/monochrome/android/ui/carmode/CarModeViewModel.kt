package tf.monochrome.android.ui.carmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.FilterType
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import javax.inject.Inject

@HiltViewModel
class CarModeViewModel @Inject constructor(
    private val preferences: PreferencesManager
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _bandCount = MutableStateFlow(10)
    val bandCount: StateFlow<Int> = _bandCount.asStateFlow()

    private val _eqBands = MutableStateFlow<List<EqBand>>(emptyList())
    val eqBands: StateFlow<List<EqBand>> = _eqBands.asStateFlow()

    init {
        loadCarModeState()
    }

    private fun loadCarModeState() {
        viewModelScope.launch {
            combine(preferences.carModeBandCount, preferences.eqBandsJson) { count, bandsJson ->
                count.coerceIn(3, 32) to decodeBands(bandsJson)
            }.collect { (count, savedBands) ->
                _bandCount.value = count
                generateEqBands(count, savedBands)
            }
        }
    }

    fun setBandCount(count: Int) {
        val coerced = count.coerceIn(3, 32)
        _bandCount.value = coerced
        val generated = buildGraphicBands(coerced, _eqBands.value)
        _eqBands.value = generated
        viewModelScope.launch {
            preferences.setCarModeBandCount(coerced)
            saveEqBands(generated)
        }
    }

    fun updateBand(index: Int, gain: Float) {
        val current = _eqBands.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(gain = gain.coerceIn(-12f, 12f))
            val updated = current.toList()
            _eqBands.value = updated
            viewModelScope.launch { saveEqBands(updated) }
        }
    }

    private fun generateEqBands(count: Int, seedBands: List<EqBand> = _eqBands.value) {
        _eqBands.value = buildGraphicBands(count, seedBands)
    }

    private fun buildGraphicBands(count: Int, seedBands: List<EqBand>): List<EqBand> {
        val minFreq = 20f
        val maxFreq = 20000f
        return (0 until count).map { i ->
            val ratio = i.toFloat() / (count - 1)
            val freq = minFreq * (maxFreq / minFreq).pow(ratio)
            val gain = seedBands.nearestTo(freq)?.gain ?: 0f
            EqBand(
                id = i,
                type = FilterType.PEAKING,
                freq = freq,
                gain = gain.coerceIn(-12f, 12f),
                q = 1f
            )
        }
    }

    private fun List<EqBand>.nearestTo(freq: Float): EqBand? =
        minByOrNull { abs(log10(it.freq.coerceAtLeast(1f)) - log10(freq.coerceAtLeast(1f))) }

    private fun decodeBands(bandsJson: String?): List<EqBand> {
        if (bandsJson.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<EqBand>>(bandsJson) }
            .getOrDefault(emptyList())
    }

    private suspend fun saveEqBands(bands: List<EqBand>) {
        preferences.setEqBands(json.encodeToString(bands))
        preferences.setEqPreamp(0.0)
        preferences.setEqActivePreset(null)
        preferences.setEqEnabled(true)
    }
}
