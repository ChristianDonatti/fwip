package io.github.christiandonatti.fwip.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.MainActivity
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.LibraryRepository
import io.github.christiandonatti.fwip.data.ReadingStats
import io.github.christiandonatti.fwip.data.Twins
import io.github.christiandonatti.fwip.data.seriesKey
import io.github.christiandonatti.fwip.data.sortedFor
import io.github.christiandonatti.fwip.data.SortOrder
import io.github.christiandonatti.fwip.ui.UiSettings
import io.github.christiandonatti.fwip.ui.theme.Palette
import java.time.LocalDate

const val ACTION_RANDOM = "io.github.christiandonatti.fwip.RANDOM"
const val ACTION_SHOW = "io.github.christiandonatti.fwip.SHOW"
const val EXTRA_CONTEXT = "context"
const val EXTRA_SCREEN = "screen"

/** Opens the app on one of its screens ("wifi", "stats", "new"). */
fun showScreenIntent(context: Context, screen: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_SHOW)
        .putExtra(EXTRA_SCREEN, screen)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Opens an unread comic picked at random when tapped. */
fun randomIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_RANDOM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** The app's colours, read from its settings, for drawing outside it. */
class WidgetColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val accent: Color,
    val accentInk: Color,
    val track: Color,
) {
    companion object {
        fun of(context: Context): WidgetColors {
            val ui = UiSettings.get(context)
            val theme = ui.resolved()
            val p = Palette.of(theme, ui.currentAccent())
            return WidgetColors(
                background = p.panel,
                card = p.panelHigh,
                text = p.text,
                soft = p.textSoft,
                muted = p.textMuted,
                accent = p.accent,
                accentInk = p.accentInk,
                track = p.text.copy(alpha = 0.16f),
            )
        }
    }
}

/** A snapshot of the library for the widgets, read from what the app saved. */
class WidgetLibrary(val state: LibraryState, private val repo: LibraryRepository) {
    /** Comics started and not finished, most recent first. */
    val reading: List<LibraryItem> get() = state.continueReading

    /** For each series being read, its first unread issue (series read most recently first). */
    val upNext: List<LibraryItem>
        get() {
            val out = mutableListOf<Pair<Long, LibraryItem>>()
            for ((_, list) in state.series) {
                if (list.size < 2) continue
                val last = list.maxOf { it.progress?.lastRead ?: 0L }
                if (last == 0L) continue
                val next = list.firstOrNull { it.progress?.started == true && it.progress.finished.not() }
                    ?: list.firstOrNull { it.progress?.finished != true }
                    ?: continue
                out += last to next
            }
            return out.sortedByDescending { it.first }.map { it.second }
        }

    /** Comics that arrived lately and were not opened. */
    val fresh: List<LibraryItem> get() = state.items.filter { it.isNew }.sortedByDescending { it.arrived ?: 0L }

    fun collection(id: String) = state.collections.firstOrNull { it.id == id }

    fun coverOf(item: LibraryItem, height: Int = 260): Bitmap? = item.coverPath?.let { path ->
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= height) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Pages per day for the last [n] days, oldest first, plus the reading streak. */
    fun week(context: Context, n: Int = 7): Pair<List<Int>, Int> {
        val days = ReadingStats(context.filesDir).all()
        val today = LocalDate.now()
        val pages = (n - 1 downTo 0).map { days[today.minusDays(it.toLong())]?.pages ?: 0 }
        var d = if ((days[today]?.pages ?: 0) > 0) today else today.minusDays(1)
        var streak = 0
        while ((days[d]?.pages ?: 0) > 0) { streak++; d = d.minusDays(1) }
        return pages to streak
    }

    companion object {
        fun load(context: Context): WidgetLibrary {
            val repo = LibraryRepository(context)
            val split = Twins.split(repo.cachedComics())
            val now = System.currentTimeMillis()
            val items = split.visible.map { c ->
                LibraryItem(
                    comic = c,
                    coverPath = repo.coverPathIfReady(c),
                    color = repo.colorFor(c),
                    progress = repo.progressFor(c.id),
                    meta = repo.metaFor(c),
                    wantedAt = repo.wantedAt(c.id),
                    arrived = repo.firstSeen(c)?.takeIf { it > 0 && now - it < LibraryRepository.NEW_FOR_MS },
                )
            }
            return WidgetLibrary(LibraryState(items = items, collections = repo.collections(), aliases = split.aliases, loaded = true), repo)
        }
    }
}

/** How far through a collection: read issues, total, and the next one in its reading order. */
data class CollectionProgress(val name: String, val read: Int, val total: Int, val next: LibraryItem?)

fun WidgetLibrary.progressOf(collectionId: String): CollectionProgress? {
    val c = collection(collectionId) ?: return null
    val list = state.itemsOf(io.github.christiandonatti.fwip.data.Shelf.Custom(c.id, c.name))
    val next = list.firstOrNull { it.progress?.started == true && it.progress.finished.not() }
        ?: list.firstOrNull { it.progress?.finished != true }
    return CollectionProgress(c.name, list.count { it.progress?.finished == true }, list.size, next)
}

/** The issue after [item] in its series, if there is one in the library. */
fun WidgetLibrary.nextIssue(item: LibraryItem): LibraryItem? {
    val key = seriesKey(item.series.name)
    val list = state.items.filter { seriesKey(it.series.name) == key }.sortedFor(SortOrder.Title)
    if (list.size < 2) return null
    return list.getOrNull(list.indexOfFirst { it.comic.id == item.comic.id } + 1)
}
