package io.github.christiandonatti.fwip.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.christiandonatti.fwip.MainActivity
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.widget.openComicIntent
import java.util.concurrent.TimeUnit

/**
 * Every few hours the library folders (the NAS included) are looked through in the background;
 * a new issue of a series being read gets a notification that opens it.
 */
object NewIssues {
    private const val WORK = "new-issues"
    private const val CHANNEL = "new_issues"

    private fun prefs(context: Context) = context.getSharedPreferences("fwip_alerts", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun schedule(context: Context) {
        if (!enabled(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NewIssuesWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Which of [fresh] continue a series already being read in [all]. */
    fun worthTelling(repo: LibraryRepository, all: List<Comic>, fresh: List<Comic>): List<LibraryItem> {
        val visible = Twins.split(all).visible
        val items = visible.map { LibraryItem(it, null, null, repo.progressFor(it.id), repo.metaFor(it)) }
        val reading = items.filter { it.progress?.started == true || it.progress?.finished == true }
            .map { seriesKey(it.series.name) }.toSet()
        val freshKeys = fresh.map { it.syncKey() }.toSet()
        return items.filter { it.comic.syncKey() in freshKeys && seriesKey(it.series.name) in reading }
            .sortedWith(compareBy({ it.series.name.lowercase() }, { it.series.number ?: 0f }))
    }

    fun notify(context: Context, items: List<LibraryItem>) {
        if (items.isEmpty()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val first = items.first()
        val intent = if (items.size == 1) openComicIntent(context, first.comic.id)
        else android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_balloon)
            .setContentTitle(if (items.size == 1) str(R.string.alert_one_title, first.series.name) else str(R.string.alert_n_title, items.size))
            .setContentText(items.joinToString(", ") { it.title })
            .setStyle(NotificationCompat.BigTextStyle().bigText(items.take(8).joinToString("\n") { it.title }))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(42, n) }
            .onFailure { Log.w("Fwip", "new issues: cannot notify", it) }
    }
}

class NewIssuesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!NewIssues.enabled(context)) return Result.success()
        return runCatching {
            val repo = LibraryRepository(context)
            val all = repo.scan()
            val fresh = repo.markSeen(all)
            val tell = NewIssues.worthTelling(repo, all, fresh)
            Log.i("Fwip", "new issues: ${fresh.size} new, ${tell.size} in series being read")
            NewIssues.notify(context, tell)
            Result.success()
        }.getOrElse {
            Log.w("Fwip", "new issues check failed", it)
            Result.retry()
        }
    }
}
