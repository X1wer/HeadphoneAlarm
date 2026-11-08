package hu.ulpifex.fulesebreszto.data

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Small local-only diagnostic log (never uploaded). Entries describe engine decisions and
 * device *types*, not device names, file names or other personal data.
 */
class DiagnosticLog(storageContext: Context) {

    private val file = File(storageContext.filesDir, "diagnostics.log")
    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    @Synchronized
    fun log(message: String) {
        Log.i(TAG, message)
        runCatching {
            file.appendText("${LocalDateTime.now().format(formatter)}  $message\n")
            if (file.length() > MAX_BYTES) {
                val keep = file.readLines().takeLast(KEEP_LINES)
                file.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
    }

    @Synchronized
    fun read(): List<String> = runCatching { file.readLines().takeLast(KEEP_LINES) }.getOrDefault(emptyList())

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val TAG = "FulesEbreszto"
        const val MAX_BYTES = 64 * 1024L
        const val KEEP_LINES = 300
    }
}
