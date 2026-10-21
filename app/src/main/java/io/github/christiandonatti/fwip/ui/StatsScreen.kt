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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val days = remember { load() }
    val today = LocalDate.now()
    val totalPages = days.values.sumOf { it.pages }
    val totalMillis = days.values.sumOf { it.millis }
    val finishedComics = state.items.count { it.progress?.finished == true }
    val streak = remember(days) {
        var d = if ((days[today]?.pages ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((days[d]?.pages ?: 0) > 0) { n++; d = d.minusDays(1) }
        n
    }
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
            CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Text(str(R.string.stats_title), style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)

            val cards = listOf(
                str(R.string.stats_finished) to finishedComics.toString(),
                str(R.string.stats_pages) to totalPages.toString(),
                str(R.string.stats_time) to durationLabel(totalMillis),
                str(R.string.stats_streak) to if (streak == 1) str(R.string.stats_one_day) else str(R.string.stats_n_days, streak),
            )
            (if (wide) cards.chunked(4) else cards.chunked(2)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                }
            }

            Panel {
                Text(str(R.string.stats_two_weeks), style = MaterialTheme.typography.titleMedium)
                Text(
                    str(R.string.stats_month, month.sumOf { it.pages }, month.sumOf { it.finished }),
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
                val max = last14.maxOf { days[it]?.pages ?: 0 }.coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val slot = size.width / last14.size
                    val barW = slot * 0.62f
                    last14.forEachIndexed { i, d ->
                        val pages = days[d]?.pages ?: 0
                        val h = (size.height - 4f) * pages / max
                        val left = i * slot + (slot - barW) / 2f
                        drawRoundRect(
                            color = if (d == today) FwipColors.Amber else FwipColors.Amber.copy(alpha = 0.45f),
                            topLeft = Offset(left, size.height - h.coerceAtLeast(3f)),
                            size = Size(barW, h.coerceAtLeast(3f)),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
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
