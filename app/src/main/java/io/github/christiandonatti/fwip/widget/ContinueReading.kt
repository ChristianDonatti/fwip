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
fun openComicIntent(context: Context, comicId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_COMIC)
        .putExtra(EXTRA_COMIC, comicId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

const val ACTION_OPEN_COMIC = "io.github.christiandonatti.fwip.OPEN_COMIC"
const val EXTRA_COMIC = "comic"

/** The comics in progress, most recent first, as the widget and the shortcuts show them. */
private class Reading(val id: String, val title: String, val fraction: Float, val cover: Bitmap?)

private fun loadReading(context: Context, max: Int): List<Reading> {
    val repo = LibraryRepository(context)
    return repo.cachedComics()
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

/** Home screen widget: the comics in progress, tap one to jump back in. */
class ContinueReadingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = withContext(Dispatchers.IO) { loadReading(context, 5) }
        provideContent { Content(context, items) }
    }

    @Composable
    private fun Content(context: Context, items: List<Reading>) {
        val size = LocalSize.current
        val ink = ColorProvider(Color(0xFF0B0C10))
        val text = ColorProvider(Color(0xFFF2F2F5))
        val muted = ColorProvider(Color(0xFFA3A6B4))
        val amber = ColorProvider(Color(0xFFF2B544))
        Column(
            GlanceModifier.fillMaxSize().background(ink).cornerRadius(24.dp).padding(14.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.fwip_mark), null, GlanceModifier.width(22.dp).height(20.dp))
                Spacer(GlanceModifier.width(8.dp))
                Text(str(R.string.widget_title), style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(10.dp))
            if (items.isEmpty()) {
                Box(
                    GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(str(R.string.widget_empty), style = TextStyle(color = muted, fontSize = 13.sp))
                }
                return@Column
            }
            // As many covers as fit side by side, about 84dp each.
            val count = ((size.width.value - 28f) / 92f).toInt().coerceIn(1, items.size)
            Row(GlanceModifier.fillMaxSize()) {
                items.take(count).forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxSize().clickable(actionStartActivity(openComicIntent(context, r.id)))) {
                        if (r.cover != null) {
                            Image(
                                ImageProvider(r.cover),
                                r.title,
                                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(10.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(GlanceModifier.fillMaxWidth().defaultWeight().background(ColorProvider(Color(0xFF1E1F27))).cornerRadius(10.dp).padding(6.dp)) {
                                Text(r.title, style = TextStyle(color = text, fontSize = 11.sp), maxLines = 4)
                            }
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = r.fraction,
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                            color = amber,
                            backgroundColor = ColorProvider(Color(0x33FFFFFF)),
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Refreshes every placed widget and the icon shortcuts. */
        suspend fun refresh(context: Context) {
            withContext(Dispatchers.IO) { ReadingShortcuts.update(context) }
            runCatching { ContinueReadingWidget().updateAll(context) }
        }
    }
}

class ContinueReadingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
