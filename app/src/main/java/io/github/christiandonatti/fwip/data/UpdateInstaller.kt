package io.github.christiandonatti.fwip.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.christiandonatti.fwip.BuildConfig
import io.github.christiandonatti.fwip.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Where an update being fetched and installed inside the app is. */
sealed interface UpdateStep {
    data class Downloading(val version: String, val done: Long, val total: Long) : UpdateStep {
        val percent: Int get() = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
    }
    /** Android must be told once that Fwip may install apps (its own updates). */
    data class NeedsPermission(val file: File) : UpdateStep
    data object Installing : UpdateStep
    /** Android shows its own "Update this app?" prompt; if it could not open by itself, a tap opens it. */
    data class NeedsConfirm(val intent: Intent) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/**
 * Downloads the APK of an update and hands it to Android's package installer, so updating needs
 * no browser and no file manager. After the first update made this way Fwip is the app that
 * installed itself, and on Android 12 and later the next ones go through without a prompt.
 */
object UpdateInstaller {
    var step by mutableStateOf<UpdateStep?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    val busy: Boolean get() = step is UpdateStep.Downloading || step == UpdateStep.Installing

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Fetches [update] and installs it. Safe to call again: a running download is not doubled. */
    fun start(context: Context, update: AppUpdate) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                val file = fetch(app, update)
                check(app, file)
                if (!canInstall(app)) {
                    step = UpdateStep.NeedsPermission(file)
                    return@launch
                }
                install(app, file)
            } catch (e: CancellationException) {
                step = null
                throw e
            } catch (e: Throwable) {
                step = UpdateStep.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        step = null
    }

    /** Opens the system page where Fwip is allowed to install apps. */
    fun openPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Back from that page: go on if the permission is now there. */
    fun resume(context: Context) {
        val s = step
        if (s is UpdateStep.NeedsPermission && canInstall(context)) {
            val app = context.applicationContext
            job = scope.launch {
                try {
                    install(app, s.file)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    step = UpdateStep.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    /** Opens Android's prompt again (it could not open while Fwip was in the background). */
    fun confirm(context: Context) {
        val s = step as? UpdateStep.NeedsConfirm ?: return
        runCatching { context.startActivity(s.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Old downloads, once the new version runs. */
    fun cleanUp(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { f ->
            val code = Regex("-(\\d+)\\.apk$").find(f.name)?.groupValues?.get(1)?.toLongOrNull()
            if (code == null || code <= BuildConfig.VERSION_CODE || f.name.endsWith(".part")) f.delete()
        }
    }

    // ------------------------------------------------------------------ Download

    private suspend fun fetch(context: Context, u: AppUpdate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val key = u.version.filter { it.isLetterOrDigit() || it == '.' }
        // A file already fetched for this version (a cancelled prompt, say) is used again.
        dir.listFiles()?.firstOrNull { it.name.startsWith("fwip-$key-") && it.name.endsWith(".apk") }?.let { done ->
            if (runCatching { check(context, done) }.isSuccess) return@withContext done
            done.delete()
        }
        dir.listFiles()?.forEach { it.delete() }
        step = UpdateStep.Downloading(u.version, 0, 0)
        val part = File(dir, "fwip-$key.part")
        val request = Request.Builder().url(u.apkUrl).header("User-Agent", "Fwip/${BuildConfig.VERSION_NAME}").build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub: HTTP ${r.code}")
            val body = r.body ?: throw IOException("GitHub: empty answer")
            val total = body.contentLength()
            var done = 0L
            var shown = 0L
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - shown > 150) {
                            shown = now
                            step = UpdateStep.Downloading(u.version, done, total)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException(context.getString(R.string.update_cut))
        }
        val code = archiveInfo(context, part)?.let(::versionCode) ?: throw IOException(context.getString(R.string.update_bad_file))
        File(dir, "fwip-$key-$code.apk").also { part.renameTo(it) }
    }

    // ------------------------------------------------------------------ Checks

    @Suppress("DEPRECATION")
    private fun archiveInfo(context: Context, file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return context.packageManager.getPackageArchiveInfo(file.path, flags)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(p: PackageInfo?): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p?.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else p?.signatures
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** The file is a Fwip, newer than this one, and signed with the same key (or Android would refuse it). */
    private fun check(context: Context, file: File) {
        val info = archiveInfo(context, file)
        if (info == null || info.packageName != context.packageName) throw IOException(context.getString(R.string.update_bad_file))
        if (versionCode(info) <= BuildConfig.VERSION_CODE) throw IOException(context.getString(R.string.update_not_newer))
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val mine = signers(context.packageManager.getPackageInfo(context.packageName, flags))
        val theirs = signers(info)
        if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isEmpty()) {
            throw IOException(context.getString(R.string.update_other_key))
        }
    }

    // ------------------------------------------------------------------ Install

    private fun install(context: Context, file: File) {
        step = UpdateStep.Installing
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("fwip.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 256 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (e: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT))
                    ?: return run { step = UpdateStep.Failed(context.getString(R.string.update_bad_file)) }
                step = UpdateStep.NeedsConfirm(confirm)
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // Android closes Fwip to replace it; this is rarely seen.
            PackageInstaller.STATUS_SUCCESS -> step = null
            PackageInstaller.STATUS_FAILURE_ABORTED -> step = null
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                step = UpdateStep.Failed(msg?.takeIf { it.isNotBlank() } ?: context.getString(R.string.update_bad_file))
            }
        }
    }
}

/** Android's answer about an update session: ask the user, done, or why it failed. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UpdateInstaller.onResult(context, intent)
}
