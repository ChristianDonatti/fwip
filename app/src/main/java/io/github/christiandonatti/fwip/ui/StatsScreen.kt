package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.data.DayStats
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** How much has been read: totals, the last two weeks, the reading streak and finished series. */
@Composable
fun StatsScreen(
    state: LibraryState,
    load: () -> Map<LocalDate, DayStats>,
    gutter: Dp,
    wide: Boolean,
    onBack: (() -> Unit)?,
    handleBack: Boolean = true,
) {
    if (onBack != null && handleBack) BackHandler(onBack = onBack)
    val days = remember { load() }
    val today = LocalDate.now()
    val totalPages = days.values.sumOf { it.allPages }
    val totalMillis = days.values.sumOf { it.allMillis }
    val hasBooks = state.items.any { it.isBook }
    val finishedComics = state.items.count { it.progress?.finished == true && !it.isBook }
    val finishedBooks = state.items.count { it.progress?.finished == true && it.isBook }
    val streak = remember(days) {
        var d = if ((days[today]?.allPages ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((days[d]?.allPages ?: 0) > 0) { n++; d = d.minusDays(1) }
        n
    }
    // Finished this year: from the library (synced devices included), by the day they were last read.
    val year = today.year
    fun thisYear(item: io.github.christiandonatti.fwip.data.LibraryItem): Boolean {
        val p = item.progress ?: return false
        if (!p.finished || p.lastRead <= 0) return false
        return java.time.Instant.ofEpochMilli(p.lastRead).atZone(java.time.ZoneId.systemDefault()).year == year
    }
    val comicsThisYear = state.items.count { !it.isBook && thisYear(it) }
    val booksThisYear = state.items.count { it.isBook && thisYear(it) }
    val month = days.filterKeys { it.year == today.year && it.month == today.month }.values
    val series = state.series
    val completeSeries = series.filter { (_, list) -> list.size >= 2 && list.all { it.progress?.finished == true } }
    val topSeries = series
        .map { (shelf, list) -> shelf.title to list.count { it.progress?.finished == true } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)
    val last14 = (13 downTo 0).map { today.minusDays(it.toLong()) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (onBack != null) CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Text(str(R.string.stats_title), style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)

            YearGoal(year, comicsThisYear, booksThisYear, hasBooks, today)

            val cards = listOfNotNull(
                str(R.string.stats_finished) to finishedComics.toString(),
                (str(R.string.stats_books_finished) to finishedBooks.toString()).takeIf { hasBooks },
                str(R.string.stats_pages) to totalPages.toString(),
                str(R.string.stats_time) to durationLabel(totalMillis),
                str(R.string.stats_streak) to if (streak == 1) str(R.string.stats_one_day) else str(R.string.stats_n_days, streak),
            )
            (if (wide) cards.chunked(cards.size) else cards.chunked(2)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                }
            }

            Panel {
                Text(str(R.string.stats_two_weeks), style = MaterialTheme.typography.titleMedium)
                Text(
                    str(R.string.stats_month, month.sumOf { it.pages }, month.sumOf { it.finished }) +
                        (if (hasBooks) " · " + str(R.string.stats_month_books, month.sumOf { it.bookFinished }) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
                // Minutes a day: comics in the accent colour, books stacked above in a softer tone.
                val max = last14.maxOf { days[it]?.allMillis ?: 0L }.coerceAtLeast(60_000L)
                val bookColor = FwipColors.TextSoft
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val slot = size.width / last14.size
                    val barW = slot * 0.62f
                    last14.forEachIndexed { i, d ->
                        val day = days[d]
                        val left = i * slot + (slot - barW) / 2f
                        val hc = (size.height - 4f) * (day?.millis ?: 0L) / max
                        val hb = (size.height - 4f) * (day?.bookMillis ?: 0L) / max
                        if (hc + hb < 3f) {
                            drawRoundRect(FwipColors.Amber.copy(alpha = 0.25f), Offset(left, size.height - 3f), Size(barW, 3f), CornerRadius(6f, 6f))
                        }
                        if (hc > 0f) drawRoundRect(
                            color = if (d == today) FwipColors.Amber else FwipColors.Amber.copy(alpha = 0.55f),
                            topLeft = Offset(left, size.height - hc),
                            size = Size(barW, hc),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                        if (hb > 0f) drawRoundRect(
                            color = bookColor.copy(alpha = if (d == today) 0.9f else 0.55f),
                            topLeft = Offset(left, size.height - hc - hb),
                            size = Size(barW, hb),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }
                if (hasBooks) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(FwipColors.Amber, str(R.string.mode_comics))
                        Legend(bookColor, str(R.string.mode_books))
                        Text(str(R.string.stats_minutes_a_day), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextMuted)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    last14.forEach { d ->
                        Text(
                            d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (d == today) FwipColors.Amber else FwipColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (topSeries.isNotEmpty()) {
                Panel {
                    Text(str(R.string.stats_top_series), style = MaterialTheme.typography.titleMedium)
                    topSeries.forEach { (name, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(str(R.string.stats_n_read, count), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                        }
                    }
                }
            }

            Panel {
                Text(str(R.string.stats_complete_series, completeSeries.size), style = MaterialTheme.typography.titleMedium)
                if (completeSeries.isEmpty()) {
                    Text(str(R.string.stats_no_complete), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
                completeSeries.take(12).forEach { (shelf, list) ->
                    Text("${shelf.title} · ${countLabel(list.size)}", style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
                }
            }

            Text(str(R.string.stats_note), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
    }
}

/** The year's goal: comics and books to finish, as rings, with the pace needed to make it. */
@Composable
private fun YearGoal(year: Int, comics: Int, books: Int, hasBooks: Boolean, today: LocalDate) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui = UiSettings.get(context)
    var editing by remember { mutableStateOf(false) }
    val comicGoal = ui.goalComics
    val bookGoal = ui.goalBooks
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.goal_title, year), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            PillButton(if (comicGoal + bookGoal == 0) str(R.string.goal_set) else str(R.string.goal_change), { editing = true }, style = PillStyle.Ghost, height = 40.dp)
        }
        if (comicGoal + bookGoal == 0) {
            Text(str(R.string.goal_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        } else {
            val daysLeft = (today.lengthOfYear() - today.dayOfYear + 1).coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (comicGoal > 0) GoalRing(comics, comicGoal, str(R.string.mode_comics), FwipColors.Amber, daysLeft, Modifier.weight(1f))
                if (bookGoal > 0) GoalRing(books, bookGoal, str(R.string.mode_books), FwipColors.TextSoft, daysLeft, Modifier.weight(1f))
            }
        }
    }
    if (editing) {
        var c by remember { mutableStateOf(comicGoal) }
        var b by remember { mutableStateOf(bookGoal) }
        FwipDialog({ editing = false }) {
            DialogTitle(str(R.string.goal_title, year), { editing = false })
            Stepper(str(R.string.goal_comics), c, step = 5) { c = it }
            if (hasBooks || b > 0) Stepper(str(R.string.goal_books), b, step = 1) { b = it }
            Text(str(R.string.goal_zero_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            PillButton(str(R.string.save), { ui.updateGoals(c, b); editing = false }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        PillButton("−", { onChange((value - step).coerceAtLeast(0)) }, style = PillStyle.Ghost, height = 40.dp)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        PillButton("+", { onChange((value + step).coerceAtMost(9999)) }, style = PillStyle.Ghost, height = 40.dp)
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int, label: String, color: androidx.compose.ui.graphics.Color, daysLeft: Int, modifier: Modifier = Modifier) {
    val fraction = (done.toFloat() / goal).coerceIn(0f, 1f)
    val track = FwipColors.Stroke
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(track, -90f, 360f, false, style = stroke)
                if (fraction > 0f) drawArc(color, -90f, 360f * fraction, false, style = stroke)
            }
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(str(R.string.goal_progress, done, goal), style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            val left = goal - done
            Text(
                when {
                    left <= 0 -> str(R.string.goal_done)
                    else -> str(R.string.goal_pace, left, daysLeft)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (left <= 0) color else FwipColors.TextSoft,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = FwipColors.Amber, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

private fun durationLabel(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) str(R.string.stats_minutes, minutes.toInt()) else str(R.string.stats_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
}
