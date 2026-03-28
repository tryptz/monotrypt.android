package tf.monochrome.android.audio.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tf.monochrome.android.audio.dsp.model.BusConfig
import tf.monochrome.android.audio.dsp.model.BusLevels
import tf.monochrome.android.audio.dsp.model.PluginInstance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DspEngineManager @Inject constructor(
    private val processor: MixBusProcessor
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _buses = MutableStateFlow(BusConfig.defaultBuses())
    val buses: StateFlow<List<BusConfig>> = _buses.asStateFlow()

    // Meter levels — polled from UI at ~50ms intervals
    private val levelsBuffer = FloatArray(TOTAL_BUSES * 2)  // [peakL, peakR] per bus
    private val _busLevels = MutableStateFlow(List(TOTAL_BUSES) { BusLevels() })
    val busLevels: StateFlow<List<BusLevels>> = _busLevels.asStateFlow()

    fun pollLevels() {
        val ptr = processor.getEnginePtr()
        if (ptr == 0L) return
        processor.nativeGetBusLevels(ptr, levelsBuffer)
        _busLevels.value = List(TOTAL_BUSES) { b ->
            BusLevels(
                peakDbL = levelsBuffer[b * 2],
                peakDbR = levelsBuffer[b * 2 + 1]
            )
        }
    }

    companion object {
        private const val TOTAL_BUSES = 5
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        processor.setEnabled(enabled)
    }

    // ── Bus controls ────────────────────────────────────────────────────

    fun setBusGain(busIndex: Int, gainDb: Float) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetBusGain(ptr, busIndex, gainDb)
        updateBus(busIndex) { it.copy(gainDb = gainDb) }
    }

    fun setBusPan(busIndex: Int, pan: Float) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetBusPan(ptr, busIndex, pan)
        updateBus(busIndex) { it.copy(pan = pan) }
    }

    fun setBusMute(busIndex: Int, muted: Boolean) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetBusMute(ptr, busIndex, muted)
        updateBus(busIndex) { it.copy(muted = muted) }
    }

    fun setBusSolo(busIndex: Int, soloed: Boolean) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetBusSolo(ptr, busIndex, soloed)
        updateBus(busIndex) { it.copy(soloed = soloed) }
    }

    // ── Plugin chain ────────────────────────────────────────────────────

    fun addPlugin(busIndex: Int, slotIndex: Int, type: SnapinType): Int {
        val ptr = processor.getEnginePtr()
        if (ptr == 0L) return -1
        val resultSlot = processor.nativeAddPlugin(ptr, busIndex, slotIndex, type.ordinal)
        if (resultSlot >= 0) {
            updateBus(busIndex) { bus ->
                val plugins = bus.plugins.toMutableList()
                plugins.add(resultSlot, PluginInstance(
                    slotIndex = resultSlot,
                    typeOrdinal = type.ordinal
                ))
                // Re-index
                bus.copy(plugins = plugins.mapIndexed { i, p -> p.copy(slotIndex = i) })
            }
        }
        return resultSlot
    }

    fun removePlugin(busIndex: Int, slotIndex: Int) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeRemovePlugin(ptr, busIndex, slotIndex)
        updateBus(busIndex) { bus ->
            val plugins = bus.plugins.toMutableList()
            if (slotIndex in plugins.indices) {
                plugins.removeAt(slotIndex)
            }
            bus.copy(plugins = plugins.mapIndexed { i, p -> p.copy(slotIndex = i) })
        }
    }

    fun movePlugin(busIndex: Int, fromSlot: Int, toSlot: Int) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeMovePlugin(ptr, busIndex, fromSlot, toSlot)
        updateBus(busIndex) { bus ->
            val plugins = bus.plugins.toMutableList()
            if (fromSlot in plugins.indices && toSlot in plugins.indices) {
                val plugin = plugins.removeAt(fromSlot)
                plugins.add(toSlot, plugin)
            }
            bus.copy(plugins = plugins.mapIndexed { i, p -> p.copy(slotIndex = i) })
        }
    }

    fun setParameter(busIndex: Int, slotIndex: Int, paramIndex: Int, value: Float) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetParameter(ptr, busIndex, slotIndex, paramIndex, value)
        updateBus(busIndex) { bus ->
            val plugins = bus.plugins.toMutableList()
            if (slotIndex in plugins.indices) {
                val plugin = plugins[slotIndex]
                plugins[slotIndex] = plugin.copy(
                    parameters = plugin.parameters + (paramIndex to value)
                )
            }
            bus.copy(plugins = plugins)
        }
    }

    fun setPluginBypassed(busIndex: Int, slotIndex: Int, bypassed: Boolean) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeSetPluginBypassed(ptr, busIndex, slotIndex, bypassed)
        updateBus(busIndex) { bus ->
            val plugins = bus.plugins.toMutableList()
            if (slotIndex in plugins.indices) {
                plugins[slotIndex] = plugins[slotIndex].copy(bypassed = bypassed)
            }
            bus.copy(plugins = plugins)
        }
    }

    // ── State serialization ─────────────────────────────────────────────

    fun getStateJson(): String {
        val ptr = processor.getEnginePtr()
        if (ptr == 0L) return "{}"
        return processor.nativeGetStateJson(ptr)
    }

    fun loadStateJson(json: String) {
        val ptr = processor.getEnginePtr()
        if (ptr != 0L) processor.nativeLoadStateJson(ptr, json)
        // Reset Kotlin state to defaults — the native engine is the source of truth
        _buses.value = BusConfig.defaultBuses()
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun updateBus(busIndex: Int, transform: (BusConfig) -> BusConfig) {
        _buses.value = _buses.value.map { bus ->
            if (bus.index == busIndex) transform(bus) else bus
        }
    }
}
