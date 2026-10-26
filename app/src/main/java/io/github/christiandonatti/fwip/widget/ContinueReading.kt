package io.github.christiandonatti.fwip.widget

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.christiandonatti.fwip.MainActivity
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens a comic straight from a shortcut or the widget. */
fun openComicIntent(context: Context, comicId: String, collection: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_COMIC)
        .putExtra(EXTRA_COMIC, comicId)
        .apply { if (collection != null) putExtra(EXTRA_CONTEXT, collection) }
        // Each comic its own intent, so widget buttons for different comics stay apart.
        .setData(android.net.Uri.parse("fwip://comic/" + android.net.Uri.encode(comicId) + "?c=" + (collection ?: "")))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

const val ACTION_OPEN_COMIC = "io.github.christiandonatti.fwip.OPEN_COMIC"
const val EXTRA_COMIC = "comic"

/** The comics in progress, most recent first, as the widget and the shortcuts show them. */
private class Reading(val id: String, val title: String, val fraction: Float, val cover: Bitmap?)

private fun loadReading(context: Context, max: Int): List<Reading> {
    val repo = LibraryRepository(context)
    // One entry per comic, even when it is both on the tablet and on the NAS.
    return io.github.christiandonatti.fwip.data.Twins.split(repo.cachedComics()).visible
        .map { c -> LibraryItem(c, repo.coverPathIfReady(c), repo.colorFor(c), repo.progressFor(c.id), repo.metaFor(c)) }
        .filter { it.progress?.started == true && it.progress.finished.not() }
        .sortedByDescending { it.progress?.lastRead ?: 0L }
        .take(max)
        .map { item ->
            val cover = item.coverPath?.let { path ->
                runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()
            }
            Reading(item.comic.id, item.title, item.progress?.fraction ?: 0f, cover)
        }
}

/** Long-press shortcuts on the app icon: the comics being read. */
object ReadingShortcuts {
    fun update(context: Context) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceIn(1, 4)
            val list = loadReading(context, max).mapIndexed { rank, r ->
                ShortcutInfoCompat.Builder(context, "comic:" + r.id.hashCode())
                    .setShortLabel(r.title.take(24))
                    .setLongLabel(r.title.take(40))
                    .setRank(rank)
                    .setIcon(r.cover?.let { IconCompat.createWithAdaptiveBitmap(adaptiveCover(it)) } ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(openComicIntent(context, r.id))
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, list)
        }
    }

    /** The cover standing in the middle of an adaptive icon (108 units, the safe 66 in the centre). */
    private fun adaptiveCover(cover: Bitmap): Bitmap {
        val size = 216
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF16171E.toInt())
        val h = 128f
        val w = h * cover.width / cover.height
        val dst = RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f)
        canvas.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

