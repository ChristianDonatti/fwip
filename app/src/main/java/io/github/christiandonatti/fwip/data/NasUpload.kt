package io.github.christiandonatti.fwip.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.remote.NetClient
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetUris
import io.github.christiandonatti.fwip.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/** A comic file in one of this device's library folders, by its path inside that folder. */
data class LocalFile(val rel: String, val size: Long, val source: String) {
    val name: String get() = rel.substringAfterLast('/')
    val dir: String get() = rel.substringBeforeLast('/', "")
    /** Remembered once sent, so a copy deleted on the NAS is not sent again. */
    val key: String get() = rel.lowercase() + "|" + size
    val syncKey: String get() = name.trim().lowercase() + "|" + size
}

/** One file to send: into [dir] (under the NAS folder) as [name]. */
data class UploadStep(val file: LocalFile, val dir: String, val name: String)

/** What to send, and files found to be on the NAS already (remembered as sent without copying). */
data class UploadPlan(val steps: List<UploadStep>, val alreadyThere: List<LocalFile>)

data class UploadStatus(
    val running: Boolean = false,
    /** Waiting for its turn (Wi-Fi, or the previous round). */
    val queued: Boolean = false,
    val current: String? = null,
    val done: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    /** Bumped when the saved settings or the last result change, so screens read them again. */
    val rev: Int = 0,
)

/**
 * Comics put on this device go to a folder on the NAS by themselves, keeping their subfolders.
 * It only ever adds files: deleting on one side leaves the other alone.
 */
object NasUpload {
    private const val TAG = "Fwip"
    private const val WORK = "nas-upload"
    private const val PERIODIC = "nas-upload-periodic"
    private const val PART = ".fwip-part"
    private const val CHANNEL = "uploads"

    private val _status = MutableStateFlow(UploadStatus())
    val status: StateFlow<UploadStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("fwip_upload", Context.MODE_PRIVATE)
    private fun sentFile(context: Context) = File(context.filesDir, "upload_sent.json")

    fun target(context: Context): Uri? = prefs(context).getString("target", null)?.let(Uri::parse)
    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean("wifi", true)
    fun lastRun(context: Context): Long = prefs(context).getLong("last", 0L)
    fun lastSent(context: Context): Int = prefs(context).getInt("lastCount", 0)
    fun lastError(context: Context): String? = prefs(context).getString("error", null)

    fun setTarget(context: Context, uri: Uri?) {
        prefs(context).edit().putString("target", uri?.toString()).remove("error").apply()
        // A new folder starts from scratch: what was sent went somewhere else.
        sentFile(context).delete()
        if (uri == null) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            _status.update { UploadStatus(rev = it.rev + 1) }
        } else {
            schedule(context)
        }
    }

    fun setWifiOnly(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("wifi", on).apply()
        _status.update { it.copy(rev = it.rev + 1) }
        if (target(context) != null) schedule(context, replacePeriodic = true)
    }

    private fun constraints(context: Context) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** Sends what is new as soon as the network allows (after a scan, or by hand), plus a check every few hours. */
    fun schedule(context: Context, replacePeriodic: Boolean = false) {
        if (target(context) == null) return
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NasUploadWorker>().setConstraints(constraints(context)).build(),
        )
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (replacePeriodic) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NasUploadWorker>(6, TimeUnit.HOURS).setConstraints(constraints(context)).build(),
        )
        _status.update { if (it.running) it else it.copy(queued = true, rev = it.rev + 1) }
    }

    private fun loadSent(context: Context): MutableSet<String> = runCatching {
        val a = JSONArray(sentFile(context).readText())
        (0 until a.length()).map { a.getString(it) }.toMutableSet()
    }.getOrDefault(mutableSetOf())

    private fun saveSent(context: Context, set: Set<String>) {
        runCatching { sentFile(context).writeText(JSONArray(set.toList()).toString()) }
    }

    /** "Batman 001.cbz", or "Batman 001 (2).cbz" when a different file has that name. */
    internal fun freeName(name: String, taken: Set<String>): String {
        if (name.lowercase() !in taken) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val candidate = "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    /**
     * Decides what to send. [onNas] holds the comics already known on the NAS (name and size);
     * [list] gives the files in a folder under the NAS folder, or null when it does not exist yet.
     */
    fun plan(
        files: List<LocalFile>,
        sent: Set<String>,
        onNas: Set<String>,
        list: (String) -> List<Pair<String, Long>>?,
    ): UploadPlan {
        val listings = HashMap<String, MutableMap<String, Long>>()
        val steps = mutableListOf<UploadStep>()
        val there = mutableListOf<LocalFile>()
        for (f in files) {
            if (f.key in sent) continue
            if (f.syncKey in onNas) { there += f; continue }
            val existing = listings.getOrPut(f.dir) {
                list(f.dir).orEmpty().associate { (n, s) -> n.lowercase() to s }.toMutableMap()
            }
            if (existing[f.name.lowercase()] == f.size) { there += f; continue }
            val name = freeName(f.name, existing.keys)
            existing[name.lowercase()] = f.size
            steps += UploadStep(f, f.dir, name)
        }
        return UploadPlan(steps, there)
    }

    /** Every comic file in this device's library folders (not network folders or downloads). */
    fun localFiles(context: Context, folders: List<Uri>): List<LocalFile> {
        val out = mutableListOf<LocalFile>()
        for (tree in folders) {
            if (NetUris.isNet(tree) || tree.scheme == "file") continue
            runCatching { walk(context, tree, DocumentsContract.getTreeDocumentId(tree), "", 0, out) }
                .onFailure { Log.w(TAG, "upload: cannot read $tree", it) }
        }
        return out
    }

    private fun walk(context: Context, tree: Uri, docId: String, prefix: String, depth: Int, out: MutableList<LocalFile>) {
        if (depth > 8) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val subfolders = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) { subfolders += id to name; continue }
                if (ComicFormat.fromFileName(name) == null) continue
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if (size <= 0) continue
                out += LocalFile(prefix + name, size, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString())
            }
        }
        for ((id, name) in subfolders) walk(context, tree, id, "$prefix$name/", depth + 1, out)
    }

    private fun join(vararg parts: String) = parts.map { it.trim('/') }.filter { it.isNotEmpty() }.joinToString("/")

    /** One round: plans, then copies file by file. Returns how many were sent. */
    suspend fun run(context: Context, foreground: suspend (String) -> Unit): Int {
        val target = target(context) ?: return 0
        val repo = LibraryRepository(context)
        val client: NetClient = NetClients.forUri(context, target)
        val base = NetUris.path(target)
        val sent = loadSent(context)
        val prefix = target.toString().trimEnd('/') + "/"
        val onNas = repo.cachedComics().filter { it.id.startsWith(prefix) || it.id == target.toString() }.map { it.syncKey() }.toSet()
        var count = 0
        _status.update { UploadStatus(running = true, rev = it.rev) }
        try {
            // New files may land while sending; a few rounds catch them.
            repeat(3) {
                val files = localFiles(context, repo.folders())
                val plan = plan(files, sent, onNas) { dir ->
                    runCatching { client.list(join(base, dir)).filter { !it.isDir }.map { it.name to it.size } }.getOrNull()
                }
                if (plan.alreadyThere.isNotEmpty()) {
                    plan.alreadyThere.forEach { sent += it.key }
                    saveSent(context, sent)
                }
                if (plan.steps.isEmpty()) return count
                val made = HashSet<String>()
                for ((i, step) in plan.steps.withIndex()) {
                    _status.update { UploadStatus(running = true, current = step.name, done = i, total = plan.steps.size, rev = it.rev) }
                    foreground(step.name)
                    val dir = join(base, step.dir)
                    if (made.add(dir) && dir.isNotEmpty()) client.mkdirs(dir)
                    val part = join(dir, step.name + PART)
                    val final = join(dir, step.name)
                    try {
                        val input = context.contentResolver.openInputStream(Uri.parse(step.file.source))
                            ?: throw java.io.IOException(str(R.string.error_read_file))
                        input.use { raw ->
                            var done = 0L
                            val counting = object : java.io.FilterInputStream(raw) {
                                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                                    if (n > 0) {
                                        done += n
                                        _status.update { s -> s.copy(progress = (i + done / step.file.size.toFloat().coerceAtLeast(1f)) / plan.steps.size) }
                                    }
                                }
                            }
                            client.upload(part, step.file.size, counting)
                        }
                        client.rename(part, final)
                    } catch (e: Exception) {
                        runCatching { client.delete(part) }
                        throw e
                    }
                    sent += step.file.key
                    saveSent(context, sent)
                    count++
                    Log.i(TAG, "upload: sent ${step.file.rel} as $final")
                }
            }
            return count
        } finally {
            _status.update { UploadStatus(rev = it.rev) }
        }
    }

    internal fun finished(context: Context, count: Int, error: String?) {
        val edit = prefs(context).edit().putLong("last", System.currentTimeMillis())
        if (count > 0 || error == null) edit.putInt("lastCount", count)
        if (error == null) edit.remove("error") else edit.putString("error", error)
        edit.apply()
        _status.update { it.copy(rev = it.rev + 1) }
    }

    internal fun foregroundInfo(context: Context, name: String): ForegroundInfo {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.upload_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(str(R.string.upload_sending))
            .setContentText(name)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(41, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(41, n)
    }
}

class NasUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (NasUpload.target(context) == null) return Result.success()
        return try {
            val count = NasUpload.run(context) { name ->
                // Long copies keep going with the screen off. Newer Android refuses this when the app is
                // in the background; the copy then simply runs as normal background work.
                runCatching { setForeground(NasUpload.foregroundInfo(context, name)) }
            }
            NasUpload.finished(context, count, null)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Fwip", "upload failed", e)
            NasUpload.finished(context, 0, io.github.christiandonatti.fwip.ui.errorText(e))
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}
