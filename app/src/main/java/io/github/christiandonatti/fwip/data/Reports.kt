package io.github.christiandonatti.fwip.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import io.github.christiandonatti.fwip.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Error reports: a crash is written to a file before the app closes, and the user can save a
 * plain text report (device, version, settings, the last crash and the app's recent log) to send
 * to whoever is fixing the problem. Nothing leaves the device unless the user shares the file.
 */
object Reports {
    private fun dir(context: Context) = File(context.filesDir, "reports").apply { mkdirs() }
    private fun crashFile(context: Context) = File(dir(context), "last-crash.txt")
    private fun seenFile(context: Context) = File(dir(context), "last-crash.seen")

    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    /** Writes any crash to a file, then lets Android close the app as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                crashFile(app).writeText("${now()} on thread ${thread.name}\n\n$sw")
                seenFile(app).delete()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The app closed by itself last time and the user has not been told yet. */
    fun unseenCrash(context: Context): Boolean = crashFile(context).exists() && !seenFile(context).exists()

    fun markSeen(context: Context) {
        runCatching { seenFile(context).createNewFile() }
    }

    /** The whole report as text. [extra] adds app state (settings, library size). */
    fun build(context: Context, extra: String): String = buildString {
        appendLine("Fwip error report")
        appendLine("Created: ${now()}")
        appendLine()
        appendLine("== App ==")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, ${if (BuildConfig.OFFICIAL) "official" else "test build"})")
        appendLine()
        appendLine("== Device ==")
        appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val dm = context.resources.displayMetrics
        val conf = context.resources.configuration
        appendLine("Screen: ${dm.widthPixels}x${dm.heightPixels} px, density ${dm.density}, ${conf.screenWidthDp}x${conf.screenHeightDp} dp")
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { am ->
            val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            appendLine("Memory: ${mi.totalMem / (1024 * 1024)} MB total, ${mi.availMem / (1024 * 1024)} MB free, heap ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
        }
        appendLine()
        appendLine("== State ==")
        appendLine(extra.trim())
        appendLine()
        appendLine("== Last crash ==")
        val crash = crashFile(context)
        appendLine(if (crash.exists()) crash.readText().trim() else "None recorded")
        appendLine()
        appendLine("== Recent log ==")
        appendLine(ownLog())
    }

    /** The app's own lines from the system log (an app can always read its own). */
    private fun ownLog(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "3000", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.trim().ifEmpty { "(empty)" }
    }.getOrElse { "(log not available: ${it.message})" }

    fun save(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
            ?: throw java.io.IOException("Could not write the report")
    }

    fun fileName(): String = "fwip-report-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".txt"
}
