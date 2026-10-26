@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.key
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
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
    val glow = ShelfLooks.color(shelf) ?: list.firstOrNull()?.glowColor() ?: FwipColors.Glow
    val art = ShelfLooks.cover(shelf)
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
            if (art != null) {
                coil.compose.AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                )
            } else if (list.isEmpty()) {
                Box(
                    Modifier.size(56.dp, 84.dp).align(Alignment.Center).clip(RoundedCornerShape(8.dp)).border(1.dp, FwipColors.Stroke, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { StackIcon(FwipColors.TextMuted) }
            }
            if (art == null) list.take(3).reversed().forEachIndexed { i, it ->
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
                        .coverShadow(6.dp, 8.dp),
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

/** How the home screen is arranged on this screen. */
internal enum class HomeLayout { Landscape, Upright, Phone }

/**
 * The home screen, built from the parts chosen in Appearance › Home, in their order: greeting,
 * cover carousel (or the phone's featured card), continue reading, the shelf, the last 7 days,
 * want to read, recently added, and any pinned collections and series.
 */
@Composable
internal fun Home(
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
    layout: HomeLayout,
) {
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    val row = state.homeRow
    val index = selected.coerceIn(0, row.lastIndex)
    val item = row[index]
    val parts = ui.sections.filter { it.on && (it.part != HomePart.Carousel || ui.showCarousel) }
    val hasHero = parts.any { it.part == HomePart.Carousel }
    val week = remember(state.items) { weekSummary(actions.stats()) }
    val rowCover = when (layout) {
        HomeLayout.Phone -> 112.dp
        else -> 136.dp
    }

    LazyColumn(
        contentPadding = PaddingValues(top = if (layout == HomeLayout.Landscape) 0.dp else 8.dp, bottom = if (wide) 40.dp else 110.dp),
        verticalArrangement = Arrangement.spacedBy(if (layout == HomeLayout.Phone) 24.dp else 30.dp),
    ) {
        parts.forEach { part ->
            when (part.part) {
                HomePart.Greeting -> item(key = part.key) {
                    Text(
                        greetingText(ui.name),
                        style = if (layout == HomeLayout.Phone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = gutter),
                    )
                }
                HomePart.Carousel -> when (layout) {
                    HomeLayout.Landscape -> item(key = part.key) {
                        LandscapeHero(row, index, item, onSelect, actions, onMenu, gutter, maxHeight, ui.carousel)
                    }
                    HomeLayout.Upright -> {
                        item(key = part.key) {
                            val cardH = (maxHeight * 0.3f).coerceIn(250.dp, 440.dp)
                            CoverCarousel(row, index, onSelect, actions.open, onMenu, cardH * (2f / 3f), cardH, ui.carousel, centered = true, gutter = gutter, maxWidth = maxWidth)
                        }
                        item(key = part.key + ":story") {
                            HeroStory(item, index, row.lastIndex, onSelect, actions, onMenu, Modifier.padding(horizontal = gutter), arrows = true)
                        }
                    }
                    HomeLayout.Phone -> item(key = part.key) { PhoneHero(item, actions, onMenu, gutter) }
                }
                HomePart.Continue -> {
                    val reading = state.continueReading.filter { !hasHero || it.comic.id != item.comic.id }
                    if (reading.isNotEmpty()) item(key = part.key) { CoverRow(str(R.string.continue_reading), reading, actions, onMenu, gutter, coverW = rowCover) }
                }
                HomePart.Shelf -> item(key = part.key) {
                    ShelfStrip(state, actions, openShelf, onSeeAll, gutter, cardWidth = if (layout == HomeLayout.Phone) 270.dp else 310.dp)
                }
                HomePart.Week -> item(key = part.key) { WeekCard(week, onStats, compact = layout == HomeLayout.Phone, modifier = Modifier.padding(horizontal = gutter)) }
                HomePart.Want -> {
                    val want = state.itemsOf(Shelf.Smart(SmartKind.Want)).filter { it.progress?.finished != true }
                    if (want.isNotEmpty()) item(key = part.key) {
                        CoverRow(str(R.string.smart_want), want, actions, onMenu, gutter, coverW = rowCover, onSeeAll = { openShelf(Shelf.Smart(SmartKind.Want)) })
                    }
                }
                HomePart.Recent -> {
                    val recent = state.items.sortedFor(SortOrder.Added).take(14)
                    if (recent.isNotEmpty()) item(key = part.key) {
                        CoverRow(str(R.string.smart_recent), recent, actions, onMenu, gutter, coverW = rowCover, onSeeAll = { openShelf(Shelf.Smart(SmartKind.Recent)) })
                    }
                }
                HomePart.Collection -> {
                    val c = state.collections.firstOrNull { it.id == part.ref }
                    if (c != null) {
                        val shelf = Shelf.Custom(c.id, c.name)
                        val list = state.itemsOf(shelf)
                        if (list.isNotEmpty()) item(key = part.key) {
                            CoverRow(c.name, list, actions, onMenu, gutter, coverW = rowCover, kicker = str(R.string.kind_collection), onSeeAll = { openShelf(shelf) }, openIn = c.id)
                        }
                    }
                }
                HomePart.Series -> {
                    val shelf = Shelf.Series(part.ref, part.title)
                    val list = state.itemsOf(shelf)
                    if (list.isNotEmpty()) item(key = part.key) {
                        CoverRow(list.first().series.name, list, actions, onMenu, gutter, coverW = rowCover, kicker = str(R.string.kind_series), onSeeAll = { openShelf(shelf) })
                    }
                }
            }
        }
    }
}

/** Tablet on its side: the story on the left, covers to flick through on the right. */
@Composable
private fun LandscapeHero(
    row: List<LibraryItem>,
    index: Int,
    item: LibraryItem,
    onSelect: (Int) -> Unit,
    actions: LibraryActions,
    onMenu: (LibraryItem) -> Unit,
    gutter: Dp,
    maxHeight: Dp,
    style: CarouselStyle,
) {
    // Leaves room for the shelf to show under it, as before.
    val heroH = (maxHeight - 214.dp).coerceAtLeast(300.dp)
    val cardH = (maxHeight * 0.4f).coerceIn(200.dp, 400.dp)
    val cardW = cardH * (2f / 3f)
    Row(
        Modifier
            .height(heroH)
            .fillMaxWidth()
            .padding(start = gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroStory(item, index, row.lastIndex, onSelect, actions, onMenu, Modifier.width(380.dp), arrows = false)
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverCarousel(row, index, onSelect, actions.open, onMenu, cardW, cardH, style, centered = false, gutter = gutter, maxWidth = 0.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.previous), { onSelect((index - 1).coerceAtLeast(0)) }, size = 44.dp)
                CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.next_item), { onSelect((index + 1).coerceAtMost(row.lastIndex)) }, size = 44.dp)
                Text(str(R.string.page_x_of_n, index + 1, row.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }
        }
    }
}

/** The selected comic told in full: title, credits, synopsis, progress and buttons. */
@Composable
private fun HeroStory(
    item: LibraryItem,
    index: Int,
    last: Int,
    onSelect: (Int) -> Unit,
    actions: LibraryActions,
    onMenu: (LibraryItem) -> Unit,
    modifier: Modifier,
    arrows: Boolean,
) {
    AnimatedContent(
        targetState = item,
        transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
        contentKey = { it.comic.id },
        label = "hero",
        modifier = modifier,
    ) { hero ->
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft, modifier = Modifier.weight(1f))
                if (arrows) {
                    CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.previous), { onSelect((index - 1).coerceAtLeast(0)) }, size = 40.dp)
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.next_item), { onSelect((index + 1).coerceAtMost(last)) }, size = 40.dp)
                }
            }
            Text(hero.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(subtitle(hero), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            hero.meta?.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** The phone's featured card: the comic you are on, with a big button to carry on. */
@Composable
private fun PhoneHero(hero: LibraryItem, actions: LibraryActions, onMenu: (LibraryItem) -> Unit, gutter: Dp) {
    Row(
        Modifier
            .padding(horizontal = gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp))
            .combinedClickable(onLongClick = { onMenu(hero) }, onClick = { actions.open(hero) })
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverImage(hero, Modifier.size(120.dp, 180.dp).coverShadow(12.dp, 14.dp), corner = 14.dp, titleSize = 16, track = true)
        Column(Modifier.weight(1f).height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
            Text(hero.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(progressLine(hero), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressBar(hero.progress?.fraction ?: 0f, hero.glowColor(), Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            PillButton(
                cta(hero),
                onClick = { actions.open(hero) },
                height = 46.dp,
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)) },
            )
        }
    }
}

/**
 * Covers to flick through. Flat is a row with the chosen cover larger; Tilt turns the side covers
 * away in perspective; Fan opens them like cards in a hand. Tap a side cover to choose it, the
 * chosen one to read, hold for options, or swipe.
 */
@Composable
private fun CoverCarousel(
    row: List<LibraryItem>,
    index: Int,
    onSelect: (Int) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    cardW: Dp,
    cardH: Dp,
    style: CarouselStyle,
    centered: Boolean,
    gutter: Dp,
    maxWidth: Dp,
) {
    if (style == CarouselStyle.Flat) {
        val listState = rememberLazyListState()
        LaunchedEffect(index) { listState.animateScrollToItem(index) }
        val side = if (centered) ((maxWidth - cardW) / 2f).coerceAtLeast(gutter) else 14.dp
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = side, end = if (centered) side else gutter + cardW * 2),
            horizontalArrangement = Arrangement.spacedBy(if (centered) 22.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(cardH + if (centered) 40.dp else 60.dp),
        ) {
            itemsIndexed(row, key = { _, it -> it.comic.id }) { i, it ->
                CarouselCard(it, i == index, cardW, cardH, onClick = { if (i == index) onOpen(it) else onSelect(i) }, onLongClick = { onMenu(it) })
            }
        }
        return
    }
    // Tilt and Fan: the covers overlap, so they are laid out by hand around the chosen one.
    val pos by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.82f, stiffness = 260f), label = "carousel")
    val currentIndex by androidx.compose.runtime.rememberUpdatedState(index)
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stepPx = with(density) { cardW.toPx() } * 0.45f
    Box(
        Modifier
            .fillMaxWidth()
            .height(cardH + 60.dp)
            .pointerInput(row.size) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    if (kotlin.math.abs(acc) > stepPx) {
                        val next = (currentIndex + if (acc < 0) 1 else -1).coerceIn(0, row.lastIndex)
                        if (next != currentIndex) select(next)
                        acc = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val window = (index - 4).coerceAtLeast(0)..(index + 4).coerceAtMost(row.lastIndex)
        for (i in window) {
            val it = row[i]
            val d = i - pos
            val ad = kotlin.math.abs(d)
            key(it.comic.id) {
                Box(
                    Modifier
                        .zIndex(-ad)
                        .graphicsLayer {
                            cameraDistance = 14f * density.density
                            when (style) {
                                CarouselStyle.Tilt -> {
                                    translationX = d.coerceIn(-3.5f, 3.5f) * cardW.toPx() * 0.55f + kotlin.math.sign(d) * cardW.toPx() * 0.18f * ad.coerceAtMost(1f)
                                    rotationY = -d.coerceIn(-1f, 1f) * 52f
                                    val s = 1f - 0.1f * ad.coerceAtMost(3f)
                                    scaleX = s; scaleY = s
                                }
                                else -> {
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 2.4f)
                                    rotationZ = d.coerceIn(-4f, 4f) * 9f
                                    val s = 1f - 0.05f * ad.coerceAtMost(3f)
                                    scaleX = s; scaleY = s
                                }
                            }
                            alpha = (1f - 0.16f * ad).coerceIn(0f, 1f)
                        }
                ) {
                    CarouselCard(it, i == index, cardW, cardH, onClick = { if (i == index) onOpen(it) else onSelect(i) }, onLongClick = { onMenu(it) }, flat = false)
                }
            }
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
private fun WeekCard(week: Week, onClick: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp)) {
                WeekFigure(week.pages.toString(), str(R.string.stats_pages))
                WeekFigure(if (week.minutes < 60) "${week.minutes} min" else "${week.minutes / 60} h", str(R.string.stats_time))
                if (!compact) WeekFigure(week.finished.toString(), str(R.string.stats_finished))
                WeekFigure(week.streak.toString(), str(R.string.week_streak))
            }
        }
        if (!compact) {
            // Seven little bars, today in the accent colour.
            val max = week.perDay.maxOrNull()?.coerceAtLeast(1) ?: 1
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.perDay.forEachIndexed { i, p ->
                    Box(
                        Modifier
                            .width(10.dp)
                            .height((64f * p / max).coerceAtLeast(4f).dp)
                            .clip(CircleShape)
                            .background(if (i == week.perDay.lastIndex) FwipColors.Amber else FwipColors.Amber.copy(alpha = 0.35f))
                    )
                }
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
