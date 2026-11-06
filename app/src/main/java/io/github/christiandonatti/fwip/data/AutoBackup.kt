package io.github.christiandonatti.fwip.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetUris
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.remote.ServerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The backup file: reading state, settings, servers (without passwords) and network folders. */
object Backups {
    fun build(context: Context, comics: List<Comic>, servers: List<Server>, folders: List<Uri>, sync: SyncManager): JSONObject {
        val list = JSONArray()
        // Passwords stay out of backup files.
        servers.forEach { s ->
            list.put(JSONObject().put("id", s.id).put("name", s.name).put("kind", s.kind.name).put("url", s.url).put("user", s.user))
        }
        val net = JSONArray()
        folders.filter(NetUris::isNet).forEach { net.put(it.toString()) }
        return JSONObject()
            .put("app", "fwip").put("kind", "backup").put("v", 1)
            .put("created", System.currentTimeMillis())
            .put("device", sync.deviceName)
            .put("state", sync.localState(comics).toJson())
            .put("settings", io.github.christiandonatti.fwip.reader.ReaderSettings.get(context).toJson())
            .put("light", io.github.christiandonatti.fwip.reader.LightSettings.get(context).toJson())
            .put("books", io.github.christiandonatti.fwip.book.BookSettings.get(context).toJson())
            .put("servers", list)
            .put("netFolders", net)
    }
}

/**
 * A backup written to the NAS every week by itself (into a hidden ".fwip/backups" folder of the
 * folder comics are sent to, or of the sync folder), keeping the last few.
 */
object AutoBackup {
    private const val WORK = "fwip-autobackup"
    private const val NOW = "fwip-autobackup-now"
    const val KEEP = 4

    data class Status(val enabled: Boolean = false, val last: Long = 0L, val error: String? = null, val running: Boolean = false)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private fun prefs(context: Context) = context.getSharedPreferences("fwip_autobackup", Context.MODE_PRIVATE)

    fun refresh(context: Context) {
        val p = prefs(context)
        _status.value = _status.value.copy(enabled = p.getBoolean("on", false), last = p.getLong("last", 0L), error = p.getString("error", null))
    }

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** The NAS folder backups go into: where comics are sent, else the sync folder if it is on the NAS. */
    fun target(context: Context): Uri? =
        NasUpload.target(context) ?: SyncManager(context, LibraryRepository(context)).place?.uri?.takeIf(NetUris::isNet)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        val wm = WorkManager.getInstance(context)
        if (on) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).setConstraints(c).build())
            // The first one right away, so the setting shows it works.
            wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().setConstraints(c).build())
        } else {
            wm.cancelUniqueWork(WORK)
        }
        refresh(context)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    /** Writes one backup now. Returns the file name. */
    fun run(context: Context): String {
        val target = target(context) ?: error(io.github.christiandonatti.fwip.str(io.github.christiandonatti.fwip.R.string.autobackup_no_target))
        _status.value = _status.value.copy(running = true)
        try {
            val repo = LibraryRepository(context)
            val sync = SyncManager(context, repo)
            val o = Backups.build(context, repo.cachedComics(), ServerStore(context).load(), repo.folders(), sync)
            val client = NetClients.forUri(context, target)
            val dir = (NetUris.path(target).let { if (it.isEmpty()) "" else "$it/" }) + ".fwip/backups"
            client.mkdirs(dir)
            val device = sync.deviceName.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "device" }
            val name = "fwip-backup-$device-" + java.time.LocalDate.now() + ".json"
            client.write("$dir/$name", o.toString(2).toByteArray())
            // Keep the newest few of this device's backups.
            runCatching {
                client.list(dir)
                    .filter { !it.isDir && it.name.startsWith("fwip-backup-$device-") && it.name.endsWith(".json") }
                    .sortedByDescending { it.name }
                    .drop(KEEP)
                    .forEach { client.delete(it.path) }
            }
            prefs(context).edit().putLong("last", System.currentTimeMillis()).remove("error").apply()
            return name
        } catch (e: Exception) {
            prefs(context).edit().putString("error", io.github.christiandonatti.fwip.ui.errorText(e)).apply()
            throw e
        } finally {
            _status.value = _status.value.copy(running = false)
            refresh(context)
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (AutoBackup.target(applicationContext) == null) Result.success()
        else {
            AutoBackup.run(applicationContext)
            Result.success()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Fwip", "auto backup failed", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
