package io.github.christiandonatti.fwip.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.christiandonatti.fwip.MainActivity
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- shared pieces

private fun c(color: Color) = ColorProvider(color)

private fun openApp(context: Context) = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun Frame(colors: WidgetColors, onClick: Action? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = GlanceModifier.fillMaxSize().background(c(colors.background)).cornerRadius(24.dp).padding(12.dp)
    Column(if (onClick != null) base.clickable(onClick) else base, content = content)
}

@Composable
private fun Header(colors: WidgetColors, title: String, trailing: String? = null, onClick: Action? = null) {
    val base = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)
    Row(if (onClick != null) base.clickable(onClick) else base, verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.fwip_mark), null, GlanceModifier.width(18.dp).height(16.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(title, style = TextStyle(color = c(colors.text), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        trailing?.let { Text(it, style = TextStyle(color = c(colors.muted), fontSize = 11.sp), maxLines = 1) }
    }
}

@Composable
private fun Label(text: String, color: Color, size: TextUnit = 12.sp, bold: Boolean = false, lines: Int = 1, modifier: GlanceModifier = GlanceModifier) {
    Text(text, style = TextStyle(color = c(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = lines, modifier = modifier)
}

@Composable
private fun Cover(bitmap: Bitmap?, title: String, colors: WidgetColors, modifier: GlanceModifier) {
    if (bitmap != null) {
        Image(ImageProvider(bitmap), title, modifier.cornerRadius(10.dp), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(c(colors.card)).cornerRadius(10.dp).padding(6.dp)) {
            Label(title, colors.soft, 10.sp, lines = 4)
        }
    }
}

@Composable
private fun Bar(fraction: Float, colors: WidgetColors, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    LinearProgressIndicator(progress = fraction.coerceIn(0f, 1f), modifier = modifier.height(4.dp), color = c(colors.accent), backgroundColor = c(colors.track))
}

@Composable
private fun Pill(text: String, colors: WidgetColors, action: Action, filled: Boolean = true) {
    Box(
        GlanceModifier
            .background(c(if (filled) colors.accent else colors.card))
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, if (filled) colors.accentInk else colors.text, 12.sp, bold = true)
    }
}

@Composable
private fun Empty(colors: WidgetColors, text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Label(text, colors.muted, 12.sp, lines = 4)
    }
}

private fun percent(item: LibraryItem) = ((item.progress?.fraction ?: 0f) * 100).toInt()

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val BIG = DpSize(250.dp, 250.dp)

/** All Fwip widgets, refreshed whenever reading progress or the library changes. */
object FwipWidgets {
    suspend fun refreshAll(context: Context) {
        runCatching { ContinueReadingWidget().updateAll(context) }
        runCatching { UpNextWidget().updateAll(context) }
        runCatching { CollectionWidget().updateAll(context) }
        runCatching { StatsWidget().updateAll(context) }
        runCatching { NewArrivalsWidget().updateAll(context) }
        runCatching { ShortcutsWidget().updateAll(context) }
    }
}

// ---------------------------------------------------------------- Continue reading

/**
 * Continue reading. Small: the current cover. Wide: the current comic with its page, a button to
 * continue and one for the next issue. Big: also the other comics in progress.
 */
class ContinueReadingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val reading = lib.reading.take(4)
        val covers = withContext(Dispatchers.IO) { reading.associate { it.comic.id to lib.coverOf(it) } }
        val next = reading.firstOrNull()?.let { lib.nextIssue(it) }
        provideContent { Content(context, colors, reading, covers, next) }
    }

    @Composable
    private fun Content(context: Context, colors: WidgetColors, reading: List<LibraryItem>, covers: Map<String, Bitmap?>, next: LibraryItem?) {
        val size = LocalSize.current
        val current = reading.firstOrNull()
        if (current == null) {
            Frame(colors, actionStartActivity(openApp(context))) {
                Header(colors, str(R.string.widget_title))
                Empty(colors, str(R.string.widget_empty))
            }
            return
        }
        val open = actionStartActivity(openComicIntent(context, current.comic.id))
        if (size.width < 200.dp) {
            // Small: just the cover, with how far along.
            Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(c(colors.background)).clickable(open)) {
                Cover(covers[current.comic.id], current.title, colors, GlanceModifier.fillMaxSize())
                Column(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                    Box(GlanceModifier.fillMaxWidth().background(c(Color(0xAA000000))).cornerRadius(8.dp).padding(6.dp)) {
                        Column {
                            Label("${percent(current)}%", Color.White, 11.sp, bold = true)
                            Bar(current.progress?.fraction ?: 0f, colors)
                        }
                    }
                }
            }
            return
        }
        Frame(colors) {
            Row(GlanceModifier.fillMaxWidth().height(if (size.height < 200.dp) 86.dp else 120.dp)) {
                Cover(covers[current.comic.id], current.title, colors, GlanceModifier.width(if (size.height < 200.dp) 58.dp else 80.dp).fillMaxHeight().clickable(open))
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Label(str(R.string.widget_continue_kicker), colors.accent, 10.sp, bold = true)
                    Label(current.title, colors.text, 14.sp, bold = true, lines = 2)
                    val p = current.progress
                    Label(
                        if (p != null && p.pageCount > 0) str(R.string.widget_page_of, p.page + 1, p.pageCount, percent(current)) else "${percent(current)}%",
                        colors.muted, 11.sp,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Bar(p?.fraction ?: 0f, colors)
                    Spacer(GlanceModifier.defaultWeight())
                    Row {
                        Pill(str(R.string.widget_continue), colors, open)
                        if (next != null) {
                            Spacer(GlanceModifier.width(6.dp))
                            Pill(
                                next.series.number?.let { str(R.string.widget_next_n, "#" + it.toString().removeSuffix(".0")) } ?: str(R.string.widget_next),
                                colors,
                                actionStartActivity(openComicIntent(context, next.comic.id)),
                                filled = false,
                            )
                        }
                    }
                }
            }
            if (size.height >= 200.dp && reading.size > 1) {
                Spacer(GlanceModifier.height(10.dp))
                Label(str(R.string.widget_also_reading), colors.soft, 11.sp, bold = true)
                Spacer(GlanceModifier.height(4.dp))
                reading.drop(1).take(3).forEach { r ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(openComicIntent(context, r.comic.id))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(covers[r.comic.id], r.title, colors, GlanceModifier.size(26.dp, 39.dp))
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Label(r.title, colors.text, 12.sp, lines = 1)
                            Bar(r.progress?.fraction ?: 0f, colors)
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        Label("${percent(r)}%", colors.muted, 11.sp)
                    }
                }
            }
        }
    }

    companion object {
        /** Refreshes every placed widget and the icon shortcuts. */
        suspend fun refresh(context: Context) {
            withContext(Dispatchers.IO) { ReadingShortcuts.update(context) }
            FwipWidgets.refreshAll(context)
        }
    }
}

class ContinueReadingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}

// ---------------------------------------------------------------- Up next

/** The next issue of every series being read, so the next read is one tap away. */
class UpNextWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val list = lib.upNext.take(6)
        val covers = withContext(Dispatchers.IO) { list.associate { it.comic.id to lib.coverOf(it) } }
        provideContent {
            Frame(colors) {
                Header(colors, str(R.string.widget_up_next))
                if (list.isEmpty()) {
                    Empty(colors, str(R.string.widget_up_next_empty))
                    return@Frame
                }
                val size = LocalSize.current
                val count = ((size.width.value - 24f) / 76f).toInt().coerceIn(1, list.size)
                Row(GlanceModifier.fillMaxSize()) {
                    list.take(count).forEachIndexed { i, item ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(openComicIntent(context, item.comic.id)))) {
                            Cover(covers[item.comic.id], item.title, colors, GlanceModifier.fillMaxWidth().defaultWeight())
                            Spacer(GlanceModifier.height(4.dp))
                            Label(item.series.name, colors.text, 11.sp, bold = true)
                            Label(
                                item.series.number?.let { "#" + it.toString().removeSuffix(".0") } ?: item.title,
                                if (item.progress?.started == true) colors.accent else colors.muted, 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

class UpNextReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

// ---------------------------------------------------------------- Collection / event

/** One collection followed from the home screen: how much of it is read and its next issue. */
class CollectionWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val collectionId = CollectionWidgetConfig.chosen(context, widgetId)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val progress = collectionId?.let { lib.progressOf(it) }
        val after = progress?.next?.let { n ->
            val c = lib.collection(collectionId)!!
            val all = lib.state.itemsOf(io.github.christiandonatti.fwip.data.Shelf.Custom(c.id, c.name))
            all.drop(all.indexOfFirst { it.comic.id == n.comic.id } + 1).filter { it.progress?.finished != true }.take(4)
        }.orEmpty()
        val covers = withContext(Dispatchers.IO) { (listOfNotNull(progress?.next) + after).associate { it.comic.id to lib.coverOf(it) } }
        val configure = actionStartActivity(CollectionWidgetConfig.intent(context, widgetId))
        provideContent {
            if (progress == null) {
                Frame(colors, configure) {
                    Header(colors, str(R.string.widget_collection))
                    Empty(colors, str(R.string.widget_collection_pick))
                }
                return@provideContent
            }
            Frame(colors) {
                Header(colors, progress.name, str(R.string.widget_x_of_y, progress.read, progress.total), onClick = configure)
                Bar(if (progress.total == 0) 0f else progress.read / progress.total.toFloat(), colors)
                Spacer(GlanceModifier.height(10.dp))
                val next = progress.next
                if (next == null) {
                    Empty(colors, str(R.string.widget_collection_done))
                    return@Frame
                }
                val open = actionStartActivity(openComicIntent(context, next.comic.id, collectionId))
                val size = LocalSize.current
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Cover(covers[next.comic.id], next.title, colors, GlanceModifier.width(70.dp).fillMaxHeight().clickable(open))
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        Label(str(R.string.widget_next_in_order), colors.accent, 10.sp, bold = true)
                        Label(next.title, colors.text, 14.sp, bold = true, lines = 2)
                        if (next.progress?.started == true) Label("${percent(next)}%", colors.muted, 11.sp)
                        Spacer(GlanceModifier.defaultWeight())
                        Pill(str(if (next.progress?.started == true) R.string.widget_continue else R.string.widget_start), colors, open)
                    }
                    if (size.width >= 300.dp && after.isNotEmpty()) {
                        Spacer(GlanceModifier.width(10.dp))
                        after.take(((size.width.value - 220f) / 52f).toInt().coerceIn(0, after.size)).forEach { a ->
                            Cover(
                                covers[a.comic.id], a.title, colors,
                                GlanceModifier.width(44.dp).fillMaxHeight().padding(end = 6.dp)
                                    .clickable(actionStartActivity(openComicIntent(context, a.comic.id, collectionId))),
                            )
                        }
                    }
                }
            }
        }
    }
}

class CollectionReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CollectionWidget()
}

// ---------------------------------------------------------------- Stats

/** The reading streak, today's pages and the last seven days. */
class StatsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val (week, streak) = withContext(Dispatchers.IO) { lib.week(context) }
        val finished = lib.state.items.count { it.progress?.finished == true }
        val days = (6 downTo 0).map { java.time.LocalDate.now().minusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) }
        provideContent {
            Frame(colors, actionStartActivity(showScreenIntent(context, "stats"))) {
                Header(colors, str(R.string.widget_stats))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(GlanceModifier.defaultWeight()) {
                        Label(if (streak == 1) str(R.string.stats_one_day) else str(R.string.stats_n_days, streak), colors.accent, 22.sp, bold = true)
                        Label(str(R.string.widget_streak), colors.muted, 11.sp)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Label(week.last().toString(), colors.text, 22.sp, bold = true)
                        Label(str(R.string.widget_pages_today), colors.muted, 11.sp)
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Label(finished.toString(), colors.text, 22.sp, bold = true)
                        Label(str(R.string.widget_finished), colors.muted, 11.sp)
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                val top = (week.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, pages ->
                        Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.Bottom) {
                            val h = (4 + 44 * pages / top).dp
                            Box(GlanceModifier.fillMaxWidth().height(h).background(c(if (pages > 0) colors.accent else colors.track)).cornerRadius(4.dp)) {}
                            Spacer(GlanceModifier.height(3.dp))
                            Label(days[i], if (i == 6) colors.text else colors.muted, 10.sp)
                        }
                    }
                }
            }
        }
    }
}

class StatsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}

// ---------------------------------------------------------------- New arrivals

/** Comics that just reached the library (the NEW badge), one tap from reading. */
class NewArrivalsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val list = lib.fresh.take(6)
        val covers = withContext(Dispatchers.IO) { list.associate { it.comic.id to lib.coverOf(it) } }
        provideContent {
            Frame(colors) {
                Header(colors, str(R.string.widget_new), if (lib.fresh.isNotEmpty()) lib.fresh.size.toString() else null, onClick = actionStartActivity(showScreenIntent(context, "new")))
                if (list.isEmpty()) {
                    Empty(colors, str(R.string.widget_new_empty))
                    return@Frame
                }
                val size = LocalSize.current
                val count = ((size.width.value - 24f) / 76f).toInt().coerceIn(1, list.size)
                Row(GlanceModifier.fillMaxSize()) {
                    list.take(count).forEachIndexed { i, item ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(openComicIntent(context, item.comic.id)))) {
                            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                                Cover(covers[item.comic.id], item.title, colors, GlanceModifier.fillMaxSize())
                                Box(GlanceModifier.padding(5.dp)) {
                                    Box(GlanceModifier.background(c(colors.accent)).cornerRadius(6.dp).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                        Label(str(R.string.new_badge), colors.accentInk, 9.sp, bold = true)
                                    }
                                }
                            }
                            Spacer(GlanceModifier.height(4.dp))
                            Label(item.title, colors.text, 11.sp, lines = 1)
                        }
                    }
                }
            }
        }
    }
}

class NewArrivalsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewArrivalsWidget()
}

// ---------------------------------------------------------------- Shortcuts

/** Four big buttons: continue, a surprise pick, receive over Wi-Fi, sync now. */
class ShortcutsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetColors.of(context)
        val lib = withContext(Dispatchers.IO) { WidgetLibrary.load(context) }
        val current = lib.reading.firstOrNull()
        val lastSync = context.getSharedPreferences("fwip_sync", Context.MODE_PRIVATE).getLong("last", 0L)
        val syncOn = context.getSharedPreferences("fwip_sync", Context.MODE_PRIVATE).getString("place", null) != null
        provideContent {
            Frame(colors) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Tile(
                        colors, str(R.string.widget_continue), current?.title ?: str(R.string.widget_nothing_open), true,
                        actionStartActivity(current?.let { openComicIntent(context, it.comic.id) } ?: openApp(context)),
                        GlanceModifier.defaultWeight().fillMaxHeight(),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Tile(colors, str(R.string.widget_surprise), str(R.string.widget_surprise_hint), false, actionStartActivity(randomIntent(context)), GlanceModifier.defaultWeight().fillMaxHeight())
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Tile(colors, str(R.string.widget_wifi), str(R.string.widget_wifi_hint), false, actionStartActivity(showScreenIntent(context, "wifi")), GlanceModifier.defaultWeight().fillMaxHeight())
                    Spacer(GlanceModifier.width(8.dp))
                    Tile(
                        colors, str(R.string.widget_sync),
                        when {
                            !syncOn -> str(R.string.widget_sync_off)
                            lastSync > 0 -> str(R.string.widget_sync_last, android.text.format.DateUtils.formatDateTime(context, lastSync, android.text.format.DateUtils.FORMAT_SHOW_TIME))
                            else -> str(R.string.sync_never)
                        },
                        false,
                        if (syncOn) actionRunCallback<SyncNowAction>() else actionStartActivity(openApp(context)),
                        GlanceModifier.defaultWeight().fillMaxHeight(),
                    )
                }
            }
        }
    }

    @Composable
    private fun Tile(colors: WidgetColors, title: String, hint: String, strong: Boolean, action: Action, modifier: GlanceModifier) {
        Column(
            modifier.background(c(if (strong) colors.accent else colors.card)).cornerRadius(16.dp).padding(10.dp).clickable(action),
            verticalAlignment = Alignment.Bottom,
        ) {
            Label(title, if (strong) colors.accentInk else colors.text, 13.sp, bold = true)
            Label(hint, if (strong) colors.accentInk else colors.muted, 10.sp, lines = 2)
        }
    }
}

class ShortcutsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutsWidget()
}

/** "Sync now" from the home screen: reads the other devices and writes this one, without opening the app. */
class SyncNowAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = io.github.christiandonatti.fwip.data.LibraryRepository(context)
                val comics = io.github.christiandonatti.fwip.data.Twins.split(repo.cachedComics()).visible
                io.github.christiandonatti.fwip.data.SyncManager(context, repo).syncNow(comics)
            }.onFailure { android.util.Log.w("Fwip", "widget sync", it) }
        }
        FwipWidgets.refreshAll(context)
    }
}
