package tf.monochrome.android.debug

/**
 * One line from the process logcat stream. `level` uses the single-char codes
 * logcat emits: V / D / I / W / E / F / S. Wire the raw line too so the export
 * can round-trip exactly what logcat printed even if our parser misread something.
 */
data class DebugLogEntry(
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: Char,
    val tag: String,
    val message: String,
    val raw: String,
)
