package tf.monochrome.android.audio.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase D regression coverage for [LibusbAudioSink]'s gain functions.
 *
 * These tests do NOT touch the USB driver, the renderer, or any
 * Android framework class — they exercise the gain math in isolation
 * by reflectively invoking the private apply* methods on a minimally-
 * constructed sink instance. The functions are pure: they take a
 * source ByteBuffer and a gain Float, and return a scratch
 * ByteBuffer with the gain applied. That isolation is what makes
 * them testable from a host-side JVM without needing a connected
 * device or even an Android emulator.
 *
 * The coverage is intentionally narrow — it targets the specific
 * properties Phase B claimed:
 *
 *   1. Unity gain produces the input byte-for-byte (the "bit-perfect
 *      at unity" promise of the bypass).
 *   2. Half gain produces samples that are mathematically half the
 *      input, with sign preservation, within the rounding error
 *      inherent to the integer multiply path.
 *   3. Maximum-positive samples saturate cleanly to the format's
 *      positive maximum (no integer overflow).
 *   4. Maximum-negative samples saturate cleanly to the format's
 *      negative minimum (no asymmetric overflow into garbage).
 *
 * These four properties together are what "the volume slider works
 * at all bit depths the renderer produces" decomposes into. If a
 * future change to [LibusbAudioSink] breaks any of them — for
 * example a 24-bit sign-extension regression — these tests fail.
 *
 * Why unit tests for this and not for the EOS contract or watchdog
 * recovery: those depend on Android framework state (Format, audio
 * thread, executor lifecycle) that requires an instrumented test on
 * a device. The manual reproduction protocol in
 * docs/usb-dac-phase-d-regression-protocol.md covers them. The gain
 * functions are the only Phase B fix that is pure data and can be
 * exercised cleanly here.
 */
class LibusbAudioSinkGainTest {

    /**
     * Helper: build a direct ByteBuffer of [count] samples of
     * little-endian 16-bit PCM with values [valueProducer]. Direct
     * because the production code reads via ByteBuffer.getShort,
     * which behaves the same for direct and heap buffers in modern
     * JDKs but matches the iso pump's actual buffer type.
     */
    private fun pcm16(count: Int, valueProducer: (Int) -> Short): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until count) buf.putShort(valueProducer(i))
        buf.flip()
        return buf
    }

    /** Same idea for packed 24-bit LE. */
    private fun pcm24(count: Int, valueProducer: (Int) -> Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(count * 3).order(ByteOrder.nativeOrder())
        for (i in 0 until count) {
            val s = valueProducer(i)
            buf.put((s and 0xFF).toByte())
            buf.put(((s ushr 8) and 0xFF).toByte())
            buf.put(((s ushr 16) and 0xFF).toByte())
        }
        buf.flip()
        return buf
    }

    /** And 32-bit signed. */
    private fun pcm32(count: Int, valueProducer: (Int) -> Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until count) buf.putInt(valueProducer(i))
        buf.flip()
        return buf
    }

    /**
     * Property: at unity gain the bypass must be bit-perfect.
     *
     * The production dispatch in handleBuffer skips the gain path
     * entirely when gain >= 0.9999f, so technically applyGainPcm*
     * is never called at unity. But if a future refactor changes
     * the threshold, we want a test that catches a unity-gain path
     * that introduces ANY change to the input. We assert this for
     * each bit depth.
     *
     * Note: we test at gain=1.0f exactly. The 0.9999 threshold in
     * production is a pragmatic equality check; this test asserts
     * the strongest property — gain=1.0 reproduces the input — so
     * the production threshold can move without invalidating the
     * test.
     */
    @Test
    fun `gain 1_0 preserves all bytes for 16-bit input`() {
        val input = pcm16(8) { i -> ((i + 1) * 1000).toShort() }
        val expected = ByteArray(input.remaining())
        input.duplicate().get(expected)
        // We invoke through reflection because applyGainPcm16 is
        // private — adding @VisibleForTesting and bumping it to
        // internal would also work but introduces a production-
        // surface change just for tests.
        val got = invokeApplyGainPcm16(input, 1.0f)
        val gotBytes = ByteArray(got.remaining())
        got.duplicate().get(gotBytes)
        assertArrayEquals(expected, gotBytes)
    }

    @Test
    fun `gain 1_0 preserves all bytes for 24-bit input`() {
        // Cover the sign-extend path: include negative samples so a
        // bug that drops the sign bit would show up here. 0x800000
        // is the most negative 24-bit sample.
        val samples = intArrayOf(0, 1, -1, 0x7FFFFF, -0x800000, 12345, -54321, 0x123456)
        val input = pcm24(samples.size) { i -> samples[i] }
        val expected = ByteArray(input.remaining())
        input.duplicate().get(expected)
        val got = invokeApplyGainPcm24(input, 1.0f)
        val gotBytes = ByteArray(got.remaining())
        got.duplicate().get(gotBytes)
        assertArrayEquals(expected, gotBytes)
    }

    @Test
    fun `gain 1_0 preserves all bytes for 32-bit input`() {
        val samples = intArrayOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 1_000_000, -1_000_000)
        val input = pcm32(samples.size) { i -> samples[i] }
        val expected = ByteArray(input.remaining())
        input.duplicate().get(expected)
        val got = invokeApplyGainPcm32(input, 1.0f)
        val gotBytes = ByteArray(got.remaining())
        got.duplicate().get(gotBytes)
        assertArrayEquals(expected, gotBytes)
    }

    /**
     * Property: half gain halves the samples within the format's
     * inherent rounding tolerance.
     *
     * Integer multiply then truncate-toward-zero introduces a tiny
     * asymmetric bias — multiplying 1 by 0.5 truncates to 0, but
     * multiplying -1 by 0.5 also truncates to 0 (because 0.5*-1 =
     * -0.5 truncates toward zero to 0, not -1). The tolerance of 1
     * LSB absorbs that edge case while still catching a real bug
     * like an unintended bit shift or a sign-flip.
     */
    @Test
    fun `gain 0_5 halves 16-bit samples within 1 LSB`() {
        val samples = shortArrayOf(0, 100, -100, 30000, -30000, 16384, -16384)
        val input = pcm16(samples.size) { i -> samples[i] }
        val got = invokeApplyGainPcm16(input, 0.5f)
        val gotShorts = ShortArray(samples.size) { got.getShort(it * 2) }
        for (i in samples.indices) {
            val expected = (samples[i] * 0.5f).toInt().toShort()
            val diff = (gotShorts[i] - expected).let { if (it < 0) -it else it }
            assertTrue("sample $i: got ${gotShorts[i]} expected ~$expected (diff=$diff)",
                diff <= 1)
        }
    }

    /**
     * Property: positive saturation. Multiplying near-max values
     * by gain >= 1 must not overflow into negative territory. This
     * is the integer-overflow trap: short * gain returns Int, and
     * if the gain pushed the value over Int range there'd be wrap.
     * Our coerceIn on the production code prevents that; the test
     * confirms it stays prevented.
     */
    @Test
    fun `gain greater than 1_0 saturates 16-bit positive max`() {
        val input = pcm16(1) { Short.MAX_VALUE }
        val got = invokeApplyGainPcm16(input, 2.0f)
        assertEquals(Short.MAX_VALUE.toInt(), got.getShort(0).toInt())
    }

    @Test
    fun `gain greater than 1_0 saturates 16-bit negative min`() {
        val input = pcm16(1) { Short.MIN_VALUE }
        val got = invokeApplyGainPcm16(input, 2.0f)
        assertEquals(Short.MIN_VALUE.toInt(), got.getShort(0).toInt())
    }

    @Test
    fun `gain greater than 1_0 saturates 24-bit positive max`() {
        val input = pcm24(1) { 0x7FFFFF }
        val got = invokeApplyGainPcm24(input, 2.0f)
        // Rebuild the 24-bit sample from the 3 bytes.
        val b0 = got.get(0).toInt() and 0xFF
        val b1 = got.get(1).toInt() and 0xFF
        val b2 = got.get(2).toInt()        // signed for sign-extend
        val sample = (b2 shl 16) or (b1 shl 8) or b0
        assertEquals(0x7FFFFF, sample)
    }

    @Test
    fun `gain greater than 1_0 saturates 24-bit negative min`() {
        val input = pcm24(1) { -0x800000 }
        val got = invokeApplyGainPcm24(input, 2.0f)
        val b0 = got.get(0).toInt() and 0xFF
        val b1 = got.get(1).toInt() and 0xFF
        val b2 = got.get(2).toInt()
        val sample = (b2 shl 16) or (b1 shl 8) or b0
        assertEquals(-0x800000, sample)
    }

    @Test
    fun `gain greater than 1_0 saturates 32-bit boundaries`() {
        // 32-bit uses double-precision multiply internally, so the
        // saturation happens in the toLong().coerceIn step. A bug
        // that did the multiply in float instead of double would
        // first manifest as quantization at large negative values
        // because float can't represent Int.MIN_VALUE exactly.
        val maxIn = pcm32(1) { Int.MAX_VALUE }
        val maxGot = invokeApplyGainPcm32(maxIn, 2.0f)
        assertEquals(Int.MAX_VALUE, maxGot.getInt(0))

        val minIn = pcm32(1) { Int.MIN_VALUE }
        val minGot = invokeApplyGainPcm32(minIn, 2.0f)
        assertEquals(Int.MIN_VALUE, minGot.getInt(0))
    }

    // ---- Reflection helpers --------------------------------------
    //
    // The applyGain* methods are private. We use reflection to invoke
    // them so we don't need to widen the production surface for tests.
    // The instance we create is intentionally minimal: we do NOT pass
    // a real driver or volume controller because the gain methods
    // don't touch them. They DO touch gainScratch, which is allocated
    // lazily on first call, so a fresh instance per test isolates
    // scratch state between tests.

    private fun newSink(): LibusbAudioSink {
        // The constructor requires a delegate AudioSink, a driver,
        // and a volume controller. None are touched by the gain
        // methods, so we can pass null-ish stand-ins via mocks. To
        // avoid dragging in a mocking framework we use reflection
        // to construct the sink with bypass-irrelevant nulls; if
        // the production constructor adds null guards in the
        // future, this test will need a small mock library.
        val ctor = LibusbAudioSink::class.java.declaredConstructors[0]
        ctor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return ctor.newInstance(
            null, null, null, emptyList<Any>()
        ) as LibusbAudioSink
    }

    private fun invokeApplyGainPcm16(src: ByteBuffer, gain: Float): ByteBuffer {
        val sink = newSink()
        val m = LibusbAudioSink::class.java.getDeclaredMethod(
            "applyGainPcm16", ByteBuffer::class.java, Float::class.java)
        m.isAccessible = true
        return m.invoke(sink, src, gain) as ByteBuffer
    }

    private fun invokeApplyGainPcm24(src: ByteBuffer, gain: Float): ByteBuffer {
        val sink = newSink()
        val m = LibusbAudioSink::class.java.getDeclaredMethod(
            "applyGainPcm24", ByteBuffer::class.java, Float::class.java)
        m.isAccessible = true
        return m.invoke(sink, src, gain) as ByteBuffer
    }

    private fun invokeApplyGainPcm32(src: ByteBuffer, gain: Float): ByteBuffer {
        val sink = newSink()
        val m = LibusbAudioSink::class.java.getDeclaredMethod(
            "applyGainPcm32", ByteBuffer::class.java, Float::class.java)
        m.isAccessible = true
        return m.invoke(sink, src, gain) as ByteBuffer
    }
}
