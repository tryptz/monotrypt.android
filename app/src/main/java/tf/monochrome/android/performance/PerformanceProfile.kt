package tf.monochrome.android.performance

enum class DeviceTier { LOW, MID, HIGH }

/**
 * Static performance envelope picked once at process start by [DeviceCapabilities.detect].
 * Pool sizes feed Kotlin's coroutine scheduler via `System.setProperty` before any
 * `Dispatchers.Default` work. The rest are read by feature-level consumers
 * (SpectrumAnalyzerTap, the Coil image loader, the liquidGlass modifier) as they come
 * online.
 */
data class PerformanceProfile(
    val tier: DeviceTier,
    val corePoolSize: Int,
    val maxPoolSize: Int,
    val spectrumFps: Int,
    val visualizerFps: Int,
    val coilMemoryPercent: Double,
    val coilDiskBytes: Long,
    val allowHazeBlur: Boolean,
) {
    companion object {
        fun forTier(tier: DeviceTier): PerformanceProfile = when (tier) {
            DeviceTier.LOW -> PerformanceProfile(
                tier = DeviceTier.LOW,
                corePoolSize = 2,
                maxPoolSize = 4,
                spectrumFps = 15,
                visualizerFps = 30,
                coilMemoryPercent = 0.12,
                coilDiskBytes = 128L * 1024 * 1024,
                allowHazeBlur = false,
            )
            DeviceTier.MID -> PerformanceProfile(
                tier = DeviceTier.MID,
                corePoolSize = 2,
                maxPoolSize = 6,
                spectrumFps = 30,
                visualizerFps = 60,
                coilMemoryPercent = 0.20,
                coilDiskBytes = 256L * 1024 * 1024,
                allowHazeBlur = true,
            )
            DeviceTier.HIGH -> PerformanceProfile(
                tier = DeviceTier.HIGH,
                corePoolSize = 4,
                maxPoolSize = 12,
                spectrumFps = 60,
                visualizerFps = 120,
                coilMemoryPercent = 0.30,
                coilDiskBytes = 384L * 1024 * 1024,
                allowHazeBlur = true,
            )
        }
    }
}
