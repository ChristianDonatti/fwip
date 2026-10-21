package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.Shelf
import io.github.christiandonatti.fwip.data.SmartKind
import io.github.christiandonatti.fwip.data.SortOrder
import io.github.christiandonatti.fwip.data.sortedFor
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import java.time.LocalDate

/** Which shelves the home strip shows. */
internal enum class ShelfKind(@androidx.annotation.StringRes private val labelRes: Int) {
    All(R.string.filter_all), Series(R.string.filter_series), Collections(R.string.tab_collections), Folders(R.string.filter_folders);

    val label: String get() = str(labelRes)
}

private fun shelvesOf(state: LibraryState, kind: ShelfKind): List<Pair<Shelf, List<LibraryItem>>> = when (kind) {
    ShelfKind.All -> homeShelves(state)
    // Series being read first, then the rest by name.
    ShelfKind.Series -> {
        val recent = state.homeSeries
        val keys = recent.map { it.first.key }.toSet()
        recent + state.series.filter { it.first.key !in keys }
    }
    ShelfKind.Collections -> {
        val want = state.itemsOf(Shelf.Smart(SmartKind.Want))
        (if (want.isNotEmpty()) listOf(Shelf.Smart(SmartKind.Want) to want) else emptyList()) +
            state.collections.map { c -> Shelf.Custom(c.id, c.name).let { it to state.itemsOf(it) } }
    }
    ShelfKind.Folders -> state.folderShelves
}

/**
 * The shelf along the bottom of the home screen: every series, collection and folder in a row
 * you can swipe through, with chips to narrow it down and a way to the full list.
 */
@Composable
internal fun ShelfStrip(
    state: LibraryState,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    onSeeAll: (ShelfKind) -> Unit,
    gutter: Dp,
    cardWidth: Dp = 300.dp,
) {
    var kind by rememberSaveable { mutableStateOf(ShelfKind.All) }
    var creating by remember { mutableStateOf(false) }
    val kinds = ShelfKind.entries.filter { it == ShelfKind.All || it == ShelfKind.Collections || shelvesOf(state, it).isNotEmpty() }
    val shelves = shelvesOf(state, kind)
    val listState = rememberLazyListState()
    LaunchedEffect(kind) { listState.scrollToItem(0) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.padding(horizontal = gutter).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(str(R.string.shelf), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 8.dp))
            Row(
                Modifier.weight(1f).horizontalScrollSafe(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                kinds.forEach { k -> SmallChip(k.label, k == kind) { kind = k } }
            }
            Text(
                str(R.string.see_all),
                style = MaterialTheme.typography.labelMedium,
                color = FwipColors.TextSoft,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onSeeAll(kind) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelves, key = { shelfKey(it.first) }) { (shelf, list) ->
                ShelfStripCard(shelf, list, Modifier.width(cardWidth)) { openShelf(shelf) }
            }
            if (kind == ShelfKind.All || kind == ShelfKind.Collections) {
                item(key = "new") {
                    Row(
                        Modifier
                            .width(cardWidth * 0.62f)
                            .height(STRIP_CARD_H)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button) { creating = true }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(FwipColors.Surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = FwipColors.Text, modifier = Modifier.size(20.dp))
                        }
                        Text(str(R.string.new_collection), style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = str(R.string.new_collection),
            initial = "",
            action = str(R.string.create),
            onDone = { name ->
                creating = false
                val id = actions.createCollection(name, emptyList())
                openShelf(Shelf.Custom(id, name))
            },
            onDismiss = { creating = false },
        )
    }
}

private val STRIP_CARD_H = 112.dp

private fun shelfKey(s: Shelf): String = when (s) {
    is Shelf.Series -> "s:" + s.key
    is Shelf.Folder -> "f:" + s.name
    is Shelf.Custom -> "c:" + s.id
    is Shelf.Smart -> "m:" + s.kind.name
}

/** One shelf: a little stack of covers, its name, how much of it is read. */
@Composable
private fun ShelfStripCard(shelf: Shelf, list: List<LibraryItem>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val glow = list.firstOrNull()?.glowColor() ?: FwipColors.Glow
    val read = list.count { it.progress?.finished == true }
    Row(
        modifier
            .height(STRIP_CARD_H)
            .clip(RoundedCornerShape(24.dp))
            .background(FwipColors.Surface)
            .background(Brush.horizontalGradient(listOf(glow.copy(alpha = if (FwipColors.isLight) 0.14f else 0.22f), Color.Transparent)))
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = shelf.title, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(84.dp).fillMaxHeight()) {
            if (list.isEmpty()) {
                Box(
                    Modifier.size(56.dp, 84.dp).align(Alignment.Center).clip(RoundedCornerShape(8.dp)).border(1.dp, FwipColors.Stroke, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { StackIcon(FwipColors.TextMuted) }
            }
            list.take(3).reversed().forEachIndexed { i, it ->
                val depth = list.take(3).size - 1 - i
                CoverImage(
                    it,
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (depth * 13).dp)
                        .size(58.dp, 87.dp)
                        .graphicsLayer {
                            val s = 1f - depth * 0.08f
                            scaleX = s; scaleY = s
                        }
                        .shadow(6.dp, RoundedCornerShape(8.dp)),
                    corner = 8.dp,
                    titleSize = 7,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                when (shelf) {
                    is Shelf.Series -> str(R.string.kind_series)
                    is Shelf.Folder -> str(R.string.kind_folder)
                    is Shelf.Custom -> str(R.string.kind_collection)
                    is Shelf.Smart -> str(R.string.kind_smart)
                },
                style = MaterialTheme.typography.labelSmall,
                color = FwipColors.TextMuted,
                maxLines = 1,
            )
            Text(shelf.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (read > 0) str(R.string.count_read, countLabel(list.size), read) else countLabel(list.size),
                style = MaterialTheme.typography.bodySmall,
                color = FwipColors.TextMuted,
                maxLines = 1,
            )
            if (list.isNotEmpty()) ProgressBar(read / list.size.toFloat(), glow, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SmallChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (on) FwipColors.Text else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
    }
}

@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

/**
 * Home for a tablet held upright: big covers to flick through at the top, the chosen one told
 * in full below, then this week's reading, what is in progress, the shelf and what is new.
 */
@Composable
internal fun TallHome(
    state: LibraryState,
    selected: Int,
    onSelect: (Int) -> Unit,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    onSeeAll: (ShelfKind) -> Unit,
    onStats: () -> Unit,
    gutter: Dp,
    maxWidth: Dp,
    maxHeight: Dp,
    wide: Boolean,
) {
    val row = state.homeRow
    val index = selected.coerceIn(0, row.lastIndex)
    val item = row[index]
    val cardH = (maxHeight * 0.3f).coerceIn(250.dp, 440.dp)
    val cardW = cardH * (2f / 3f)
    val carousel = rememberLazyListState()
    // Keep the chosen cover in the middle of the screen.
    val side = ((maxWidth - cardW) / 2f).coerceAtLeast(gutter)
    LaunchedEffect(index) { carousel.animateScrollToItem(index) }
    val reading = state.continueReading.filter { it.comic.id != item.comic.id }
    val want = state.itemsOf(Shelf.Smart(SmartKind.Want)).filter { it.progress?.finished != true }
    val recent = state.items.sortedFor(SortOrder.Added).take(14)
    val week = remember(state.items) { weekSummary(actions.stats()) }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = if (wide) 40.dp else 110.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item(key = "carousel") {
            LazyRow(
                state = carousel,
                contentPadding = PaddingValues(start = side, end = side),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(cardH + 40.dp),
            ) {
                itemsIndexed(row, key = { _, it -> it.comic.id }) { i, it ->
                    CarouselCard(
                        item = it,
                        selected = i == index,
                        width = cardW,
                        height = cardH,
                        onClick = { if (i == index) actions.open(it) else onSelect(i) },
                        onLongClick = { onMenu(it) },
                    )
                }
            }
        }
        item(key = "hero") {
            AnimatedContent(
                targetState = item,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
                contentKey = { it.comic.id },
                label = "tallHero",
                modifier = Modifier.padding(horizontal = gutter),
            ) { hero ->
                Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft, modifier = Modifier.weight(1f))
                        CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.previous), { onSelect((index - 1).coerceAtLeast(0)) }, size = 40.dp)
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.next_item), { onSelect((index + 1).coerceAtMost(row.lastIndex)) }, size = 40.dp)
                    }
                    Text(hero.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(hero), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
                    hero.meta?.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProgressBar(hero.progress?.fraction ?: 0f, hero.glowColor(), Modifier.weight(1f))
                        Text(progressLine(hero), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        PillButton(cta(hero), onClick = { actions.open(hero) }, leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)) })
                        PillButton(str(R.string.options), onClick = { onMenu(hero) }, style = PillStyle.Ghost)
                    }
                }
            }
        }
        item(key = "week") {
            WeekCard(week, onStats, Modifier.padding(horizontal = gutter))
        }
        if (reading.isNotEmpty()) {
            item(key = "reading") { CoverRow(str(R.string.continue_reading), reading, actions, onMenu, gutter, coverW = 136.dp) }
        }
        item(key = "shelf") { ShelfStrip(state, actions, openShelf, onSeeAll, gutter, cardWidth = 320.dp) }
        if (want.isNotEmpty()) {
            item(key = "want") { CoverRow(str(R.string.smart_want), want, actions, onMenu, gutter, coverW = 136.dp) }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") { CoverRow(str(R.string.smart_recent), recent, actions, onMenu, gutter, coverW = 136.dp) }
        }
    }
}

private class Week(val pages: Int, val minutes: Long, val finished: Int, val streak: Int, val perDay: List<Int>)

private fun weekSummary(days: Map<LocalDate, io.github.christiandonatti.fwip.data.DayStats>): Week {
    val today = LocalDate.now()
    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
    var d = if ((days[today]?.pages ?: 0) > 0) today else today.minusDays(1)
    var streak = 0
    while ((days[d]?.pages ?: 0) > 0) { streak++; d = d.minusDays(1) }
    return Week(
        pages = last7.sumOf { days[it]?.pages ?: 0 },
        minutes = last7.sumOf { days[it]?.millis ?: 0L } / 60_000,
        finished = last7.sumOf { days[it]?.finished ?: 0 },
        streak = streak,
        perDay = last7.map { days[it]?.pages ?: 0 },
    )
}

/** This week at a glance, with a small bar per day; opens the full statistics. */
@Composable
private fun WeekCard(week: Week, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClickLabel = str(R.string.stats_title), onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(str(R.string.week_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                WeekFigure(week.pages.toString(), str(R.string.stats_pages))
                WeekFigure(if (week.minutes < 60) "${week.minutes} min" else "${week.minutes / 60} h", str(R.string.stats_time))
                WeekFigure(week.finished.toString(), str(R.string.stats_finished))
                WeekFigure(week.streak.toString(), str(R.string.week_streak))
            }
        }
        // Seven little bars, today in the accent colour.
        val max = week.perDay.maxOrNull()?.coerceAtLeast(1) ?: 1
        val density = LocalDensity.current
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.perDay.forEachIndexed { i, p ->
                val h = with(density) { (64.dp.toPx() * p / max).coerceAtLeast(4.dp.toPx()).toDp() }
                Box(
                    Modifier
                        .width(10.dp)
                        .height(h)
                        .clip(CircleShape)
                        .background(if (i == week.perDay.lastIndex) FwipColors.Amber else FwipColors.Amber.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun WeekFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = FwipColors.Amber, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
    }
}
