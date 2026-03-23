package tf.monochrome.android.visualizer

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class ProjectMAudioFrame(
    val samples: FloatArray,
    val channelCount: Int,
    val sampleRate: Int,
    val timestampMs: Long
)

@Singleton
class ProjectMAudioBus @Inject constructor() {
    private val latestFrame = AtomicReference<ProjectMAudioFrame?>(null)
    private val latestTimestampMs = AtomicLong(0L)

    fun publish(samples: FloatArray, channelCount: Int, sampleRate: Int) {
        val now = System.currentTimeMillis()
        latestTimestampMs.set(now)
        latestFrame.set(
            ProjectMAudioFrame(
                samples = samples,
                channelCount = channelCount,
                sampleRate = sampleRate,
                timestampMs = now
            )
        )
    }

    fun consumeLatest(): ProjectMAudioFrame? = latestFrame.getAndSet(null)

    fun latestTimestampMs(): Long = latestTimestampMs.get()

    fun clear() {
        latestFrame.set(null)
        latestTimestampMs.set(0L)
    }
}
