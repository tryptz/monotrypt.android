// SPDX-License-Identifier: GPL-3.0-or-later
// Kotlin JNI wrappers + state for Oxford Inflator / Compressor.
//
// Integration points:
//   - MixBusProcessor calls prepare() on format change and
//     processArrays(L, R, frames) after the native engine runs.
//   - UI observes `state` StateFlow; updates go through public setters which
//     mutate the flow and push params to native (native holds the
//     authoritative atomic copy used on the audio thread).

package tf.monochrome.android.audio.dsp.oxford

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// ---- Native entry points -------------------------------------------------

internal object InflatorNative {
    init { System.loadLibrary("monochrome_dsp") }
    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativePrepare(handle: Long, sampleRate: Double, channels: Int)
    external fun nativeSetParams(
        handle: Long,
        inputDb: Float, outputDb: Float, effect: Float, curve: Float,
        clipZeroDb: Boolean, bandSplit: Boolean, bypass: Boolean,
    )
    external fun nativeProcess(handle: Long, buffer: ByteBuffer, frames: Int, channels: Int)
    external fun nativeProcessArrays(handle: Long, l: FloatArray, r: FloatArray, frames: Int)
    external fun nativeReadMeters(handle: Long): Long
}

internal object CompressorNative {
    init { System.loadLibrary("monochrome_dsp") }
    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativePrepare(handle: Long, sampleRate: Double, channels: Int)
    external fun nativeSetParams(
        handle: Long,
        thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float,
        kneeDb: Float, makeupDb: Float, bypass: Boolean,
    )
    external fun nativeProcess(handle: Long, buffer: ByteBuffer, frames: Int, channels: Int)
    external fun nativeProcessArrays(handle: Long, l: FloatArray, r: FloatArray, frames: Int)
    external fun nativeGainReductionDb(handle: Long): Float
    external fun nativeReadMeters(handle: Long): Long
}

// ---- State data classes --------------------------------------------------

data class InflatorState(
    val inputDb:    Float   =  0.0f,   // [-6, +12]
    val outputDb:   Float   =  0.0f,   // [-12,  0]
    val effectPct:  Float   = 100.0f,  // [0, 100]  — UI-native unit
    val curve:      Float   =  0.0f,   // [-50, +50]
    val clipZeroDb: Boolean = true,
    val bandSplit:  Boolean = false,
    val effectIn:   Boolean = false,   // UI "Effect In" — false == bypass, off by default
)

data class CompressorState(
    val thresholdDb: Float = -20.0f,
    val ratio:       Float =   4.0f,
    val attackMs:    Float =  10.0f,
    val releaseMs:   Float = 100.0f,
    val kneeDb:      Float =   6.0f,
    val makeupDb:    Float =   0.0f,
    val bypass:      Boolean = true,   // off by default
)

data class StereoPeak(val left: Float, val right: Float) {
    companion object { val Zero = StereoPeak(0f, 0f) }
}

// ---- Effect classes ------------------------------------------------------

@Singleton
class InflatorEffect @Inject constructor() {
    private val handle = AtomicLong(InflatorNative.nativeCreate())

    private val _state = MutableStateFlow(InflatorState())
    val state: StateFlow<InflatorState> = _state.asStateFlow()

    private val _peak = MutableStateFlow(StereoPeak.Zero)
    val peak: StateFlow<StereoPeak> = _peak.asStateFlow()

    init { push(_state.value) }

    fun prepare(sampleRate: Double, channels: Int) {
        val h = handle.get()
        if (h != 0L) InflatorNative.nativePrepare(h, sampleRate, channels)
    }

    fun process(planarFloats: ByteBuffer, frames: Int, channels: Int) {
        require(planarFloats.isDirect) { "Buffer must be direct" }
        require(planarFloats.order() == ByteOrder.nativeOrder()) { "Buffer must be native-order" }
        val h = handle.get()
        if (h != 0L) InflatorNative.nativeProcess(h, planarFloats, frames, channels)
    }

    fun processArrays(l: FloatArray, r: FloatArray, frames: Int) {
        val h = handle.get()
        if (h != 0L) InflatorNative.nativeProcessArrays(h, l, r, frames)
    }

    fun pollMeters() {
        val h = handle.get()
        if (h == 0L) return
        val packed = InflatorNative.nativeReadMeters(h)
        val l = java.lang.Float.intBitsToFloat((packed ushr 32).toInt())
        val r = java.lang.Float.intBitsToFloat((packed and 0xFFFFFFFFL).toInt())
        _peak.value = StereoPeak(l, r)
    }

    fun update(transform: (InflatorState) -> InflatorState) {
        val next = transform(_state.value)
        _state.value = next
        push(next)
    }

    fun setInputDb(db: Float)       = update { it.copy(inputDb    = db.coerceIn(-6f, 12f)) }
    fun setOutputDb(db: Float)      = update { it.copy(outputDb   = db.coerceIn(-12f, 0f)) }
    fun setEffectPct(pct: Float)    = update { it.copy(effectPct  = pct.coerceIn(0f, 100f)) }
    fun setCurve(c: Float)          = update { it.copy(curve      = c.coerceIn(-50f, 50f)) }
    fun setClipZeroDb(on: Boolean)  = update { it.copy(clipZeroDb = on) }
    fun setBandSplit(on: Boolean)   = update { it.copy(bandSplit  = on) }
    fun setEffectIn(on: Boolean)    = update { it.copy(effectIn   = on) }

    fun release() {
        val h = handle.getAndSet(0L)
        if (h != 0L) InflatorNative.nativeDestroy(h)
    }

    private fun push(s: InflatorState) {
        val h = handle.get()
        if (h == 0L) return
        InflatorNative.nativeSetParams(
            h,
            s.inputDb, s.outputDb, s.effectPct / 100f, s.curve,
            s.clipZeroDb, s.bandSplit, bypass = !s.effectIn,
        )
    }
}

@Singleton
class CompressorEffect @Inject constructor() {
    private val handle = AtomicLong(CompressorNative.nativeCreate())

    private val _state = MutableStateFlow(CompressorState())
    val state: StateFlow<CompressorState> = _state.asStateFlow()

    private val _gainReductionDb = MutableStateFlow(0f)
    val gainReductionDb: StateFlow<Float> = _gainReductionDb.asStateFlow()

    private val _peak = MutableStateFlow(StereoPeak.Zero)
    val peak: StateFlow<StereoPeak> = _peak.asStateFlow()

    init { push(_state.value) }

    fun prepare(sampleRate: Double, channels: Int) {
        val h = handle.get()
        if (h != 0L) CompressorNative.nativePrepare(h, sampleRate, channels)
    }

    fun process(planarFloats: ByteBuffer, frames: Int, channels: Int) {
        val h = handle.get()
        if (h != 0L) CompressorNative.nativeProcess(h, planarFloats, frames, channels)
    }

    fun processArrays(l: FloatArray, r: FloatArray, frames: Int) {
        val h = handle.get()
        if (h != 0L) CompressorNative.nativeProcessArrays(h, l, r, frames)
    }

    fun pollMeters() {
        val h = handle.get()
        if (h == 0L) return
        _gainReductionDb.value = CompressorNative.nativeGainReductionDb(h)
        val packed = CompressorNative.nativeReadMeters(h)
        val l = java.lang.Float.intBitsToFloat((packed ushr 32).toInt())
        val r = java.lang.Float.intBitsToFloat((packed and 0xFFFFFFFFL).toInt())
        _peak.value = StereoPeak(l, r)
    }

    fun update(transform: (CompressorState) -> CompressorState) {
        val next = transform(_state.value)
        _state.value = next
        push(next)
    }

    fun setThresholdDb(v: Float) = update { it.copy(thresholdDb = v.coerceIn(-60f, 0f)) }
    fun setRatio(v: Float)       = update { it.copy(ratio       = v.coerceIn(1f, 20f)) }
    fun setAttackMs(v: Float)    = update { it.copy(attackMs    = v.coerceIn(0.1f, 200f)) }
    fun setReleaseMs(v: Float)   = update { it.copy(releaseMs   = v.coerceIn(5f, 2000f)) }
    fun setKneeDb(v: Float)      = update { it.copy(kneeDb      = v.coerceIn(0f, 24f)) }
    fun setMakeupDb(v: Float)    = update { it.copy(makeupDb    = v.coerceIn(-12f, 24f)) }
    fun setBypass(b: Boolean)    = update { it.copy(bypass      = b) }

    fun release() {
        val h = handle.getAndSet(0L)
        if (h != 0L) CompressorNative.nativeDestroy(h)
    }

    private fun push(s: CompressorState) {
        val h = handle.get()
        if (h == 0L) return
        CompressorNative.nativeSetParams(
            h,
            s.thresholdDb, s.ratio, s.attackMs, s.releaseMs,
            s.kneeDb, s.makeupDb, s.bypass,
        )
    }
}
