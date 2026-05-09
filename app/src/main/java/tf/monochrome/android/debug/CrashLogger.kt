package tf.monochrome.android.debug

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import tf.monochrome.android.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catches uncaught exceptions, dumps the in-memory `DebugLogBuffer` plus the
 * throwable's stack trace to a timestamped file under the device's public
 * Downloads directory, then chains to the previous default handler so the
 * system crash dialog still appears and the process is killed.
 *
 * The dump goes to `Downloads/monotrypt-crash-YYYYMMDD-HHMMSS.log` so it's
 * easy to find and share without leaving the app.
 *
 * Storage path varies by API level:
 *   - API 29+ (Q and above): MediaStore.Downloads — no permission needed.
 *   - API 26-28 (O / O_MR1 / P): Direct file write to
 *     Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).
 *     Requires WRITE_EXTERNAL_STORAGE which is already declared in the
 *     manifest with android:maxSdkVersion="28".
 *
 * If the dump itself throws (storage full, no permission, MediaStore
 * insertion rejected, …) we swallow the secondary exception and still
 * forward the original throwable to the prior handler — losing the crash
 * dialog because of a logging side effect would be worse than losing the
 * log.
 */
@Singleton
class CrashLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugLogBuffer: DebugLogBuffer,
) {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashDump(thread, throwable)
            } catch (t: Throwable) {
                Log.w(TAG, "Crash dump failed", t)
            }
            // Always chain to the prior handler so the system shows its crash
            // dialog and kills the process. Without this, an uncaught
            // exception would leave the app in an undefined state.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashDump(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "monotrypt-crash-$timestamp.log"
        val content = formatDump(thread, throwable, timestamp)

        val bytes = content.toByteArray(Charsets.UTF_8)
        val wrote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(fileName, bytes)
        } else {
            writeViaPublicDirectory(fileName, bytes)
        }
        if (!wrote) Log.w(TAG, "Crash dump could not be written to Downloads")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(fileName: String, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private fun writeViaPublicDirectory(fileName: String, bytes: ByteArray): Boolean {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: return false
        if (!downloads.exists() && !downloads.mkdirs()) return false
        val target = File(downloads, fileName)
        return runCatching {
            target.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

    private fun formatDump(thread: Thread, throwable: Throwable, timestamp: String): String {
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()
        val header = buildString {
            appendLine("MonoTrypT crash dump")
            appendLine("====================")
            appendLine("timestamp: $timestamp")
            appendLine("app:       ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android:   ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device:    ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("thread:    ${thread.name} (id=${thread.id})")
            appendLine()
        }
        val recentLog = runCatching { debugLogBuffer.dumpAsText() }.getOrDefault("(log buffer unavailable)")
        return buildString {
            append(header)
            appendLine("--- stack trace ---")
            appendLine(stack)
            appendLine("--- recent log ---")
            appendLine(recentLog)
        }
    }

    companion object {
        private const val TAG = "CrashLogger"
    }
}
