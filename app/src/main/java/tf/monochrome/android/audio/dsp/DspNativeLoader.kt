package tf.monochrome.android.audio.dsp

/**
 * Single entry point for loading libmonochrome_dsp.so. Multiple call sites
 * (MixBusProcessor, InflatorNative, CompressorNative) used to invoke
 * `System.loadLibrary("monochrome_dsp")` from their own companion-object init
 * blocks. Every one of those is reachable from a Hilt @Singleton constructor,
 * so the first injection chain on app startup did the dlopen on the main
 * thread — contributing to "Choreographer: Skipped 39 frames" at launch.
 *
 * `ensureLoaded` collapses the load to a single class-init guarded by the JVM
 * (idempotent across threads). MonochromeApp.onCreate calls it on
 * Dispatchers.IO so the linker work happens off the UI thread; the JNI symbols
 * are resolved before any audio buffer reaches the engine because audio
 * processing runs on its own thread that necessarily blocks here as well.
 */
internal object DspNativeLoader {
    init { System.loadLibrary("monochrome_dsp") }

    @JvmStatic
    fun ensureLoaded() {
        // Touching the object triggers the init block. Method body intentionally empty.
    }
}
