@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package io.github.christiandonatti.fwip.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.Shelf
import io.github.christiandonatti.fwip.data.SmartKind
import io.github.christiandonatti.fwip.data.SortOrder
import io.github.christiandonatti.fwip.data.matches
import io.github.christiandonatti.fwip.data.seriesKey
import io.github.christiandonatti.fwip.data.sortedFor
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** Everything the library screens can ask the app to do. */
class LibraryActions(
    val open: (LibraryItem) -> Unit,
    val addFolder: () -> Unit,
    val removeFolder: (Uri) -> Unit,
    val refresh: () -> Unit,
    val setRead: (List<String>, Boolean) -> Unit,
    val createCollection: (String, List<String>) -> String,
    val renameCollection: (String, String) -> Unit,
    val deleteCollection: (String) -> Unit,
    val setInCollection: (String, List<String>, Boolean) -> Unit,
)

private enum class Tab(val label: String) { Home("Início"), Library("Biblioteca"), Collections("Coleções") }

private enum class Filter(val label: String) { All("Tudo"), Series("Séries"), Folders("Pastas"), Unread("Não lidas"), Finished("Lidas") }

/** Saveable pointer to a shelf (a sealed type cannot go into a Bundle directly). */
private fun Shelf.toToken(): String = when (this) {
    is Shelf.Series -> "s:$key\u0000$title"
    is Shelf.Folder -> "f:$name"
    is Shelf.Custom -> "c:$id\u0000$title"
    is Shelf.Smart -> "m:${kind.name}"
}

private fun shelfFromToken(t: String?): Shelf? {
    if (t == null || t.length < 2) return null
    val body = t.substring(2)
    return when (t[0]) {
        's' -> body.split('\u0000', limit = 2).let { Shelf.Series(it[0], it.getOrElse(1) { it[0] }) }
        'f' -> Shelf.Folder(body)
        'c' -> body.split('\u0000', limit = 2).let { Shelf.Custom(it[0], it.getOrElse(1) { "" }) }
        'm' -> runCatching { Shelf.Smart(SmartKind.valueOf(body)) }.getOrNull()
        else -> null
    }
}

@Composable
fun LibraryScreen(state: LibraryState, actions: LibraryActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var shelfToken by rememberSaveable { mutableStateOf<String?>(null) }
    val shelf = shelfFromToken(shelfToken)
    var showFolders by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = shelf != null) { shelfToken = null }

    val row = state.homeRow
    val sel = row.getOrNull(selected.coerceAtMost(row.lastIndex.coerceAtLeast(0)))
    val glow = when {
        shelf != null -> state.itemsOf(shelf).firstOrNull()?.glowColor()
        tab == Tab.Home -> sel?.glowColor()
        else -> null
    } ?: sel?.glowColor() ?: FwipColors.Glow

    val openShelf: (Shelf) -> Unit = { shelfToken = it.toToken() }
    val showMenu: (LibraryItem) -> Unit = { menuFor = it.comic.id }

    AmbientBackground(color = glow, intensity = if (tab == Tab.Home && shelf == null) 0.55f else 0.35f) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
        ) {
            val wide = maxWidth >= 840.dp
            val boxHeight = maxHeight
            val gutter = if (wide) 48.dp else 20.dp

            AnimatedContent(
                targetState = shelf,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                    } else {
                        fadeIn(tween(260)) togetherWith (slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(200)))
                    }
                },
                contentKey = { it?.toToken() },
                label = "shelf",
            ) { current ->
                if (current != null) {
                    ShelfScreen(
                        shelf = current,
                        state = state,
                        actions = actions,
                        wide = wide,
                        gutter = gutter,
                        onBack = { shelfToken = null },
                        onMenu = showMenu,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header(
                            wide = wide,
                            tab = tab,
                            onTab = { tab = it },
                            scanning = state.scanning,
                            onRefresh = actions.refresh,
                            onAdd = actions.addFolder,
                            onFolders = { showFolders = true },
                            modifier = Modifier.padding(horizontal = gutter, vertical = if (wide) 24.dp else 12.dp),
                        )
                        when {
                            state.items.isEmpty() -> EmptyState(state.folders.isEmpty(), state.scanning, actions.addFolder)
                            tab == Tab.Home && wide -> WideHome(state, selected, { selected = it }, actions, openShelf, showMenu, gutter, boxHeight)
                            tab == Tab.Home -> PhoneHome(state, actions, openShelf, showMenu, gutter)
                            tab == Tab.Library -> LibraryTab(state, actions, openShelf, showMenu, wide, gutter)
                            else -> CollectionsTab(state, actions, openShelf, wide, gutter)
                        }
                    }
                }
            }
            if (!wide && shelf == null && state.items.isNotEmpty()) {
                PhoneTabBar(tab, onTab = { tab = it }, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (showFolders) {
        FoldersDialog(
            folders = state.folders,
            onAdd = { showFolders = false; actions.addFolder() },
            onRemove = actions.removeFolder,
            onDismiss = { showFolders = false },
        )
    }

    menuFor?.let { id ->
        val item = state.item(id)
        if (item == null) {
            menuFor = null
        } else {
            val seriesKeyOf = seriesKey(item.series.name)
            val seriesSize = state.items.count { seriesKey(it.series.name) == seriesKeyOf }
            ComicActionsDialog(
                item = item,
                seriesSize = seriesSize,
                onDismiss = { menuFor = null },
                onOpen = { menuFor = null; actions.open(item) },
                onToggleRead = {
                    actions.setRead(listOf(id), item.progress?.finished != true)
                    menuFor = null
                },
                onCollections = { menuFor = null; pickerFor = id },
                onSeries = {
                    menuFor = null
                    shelfToken = Shelf.Series(seriesKeyOf, item.series.name).toToken()
                },
            )
        }
    }

    pickerFor?.let { id ->
        CollectionPickerDialog(
            title = "Coleções",
            collections = state.collections,
            comicId = id,
            onToggle = { c, inside -> actions.setInCollection(c.id, listOf(id), inside) },
            onCreate = { name -> actions.createCollection(name, listOf(id)) },
            onDismiss = { pickerFor = null },
        )
    }
}

@Composable
private fun Header(
    wide: Boolean,
    tab: Tab,
    onTab: (Tab) -> Unit,
    scanning: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val button = if (wide) 46.dp else 44.dp
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        FwipLogo(size = if (wide) 22 else 19)
        Spacer(Modifier.weight(1f))
        if (wide) {
            Segmented(Tab.entries, tab, { it.label }, onTab)
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                Box(Modifier.size(button), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                }
            } else {
                CircleIconButton(Icons.Filled.Refresh, "Atualizar biblioteca", onRefresh, size = button)
            }
            CircleIconButton(Icons.Filled.Add, "Adicionar pasta", onAdd, size = button)
            CircleButton("Pastas da biblioteca", onFolders, size = button) { FolderIcon(FwipColors.Text) }
        }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { t ->
            val on = t == selected
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (on) FwipColors.Text else Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(t) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(t), style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft)
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (on) FwipColors.Text else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
    }
}

private fun kicker(item: LibraryItem): String = when {
    item.progress?.finished == true -> "LER DE NOVO"
    item.progress?.started == true -> "CONTINUAR LENDO"
    else -> "COMEÇAR"
}

private fun cta(item: LibraryItem): String = when {
    item.progress?.finished == true -> "Reler"
    item.progress?.started == true -> "Continuar"
    else -> "Começar"
}

private fun progressLine(item: LibraryItem): String {
    val p = item.progress ?: return item.comic.format.label
    if (p.pageCount <= 0) return item.comic.format.label
    val remaining = p.pageCount - 1 - p.page
    return when {
        p.finished -> "${p.pageCount} páginas · lido"
        !p.started -> "${p.pageCount} páginas · não iniciado"
        else -> "Página ${p.page + 1} de ${p.pageCount} · $remaining restantes"
    }
}

private fun subtitle(item: LibraryItem): String =
    listOfNotNull(item.comic.folder.takeIf { it.isNotBlank() }, item.comic.format.label).joinToString(" · ")

/** Shelves worth surfacing on the home screen: what you are reading, series, your collections. */
private fun homeShelves(state: LibraryState): List<Pair<Shelf, List<LibraryItem>>> {
    val out = mutableListOf<Pair<Shelf, List<LibraryItem>>>()
    val reading = state.continueReading
    if (reading.isNotEmpty()) out += Shelf.Smart(SmartKind.Reading) to reading
    state.collections.filter { it.comicIds.isNotEmpty() }.forEach { c ->
        out += Shelf.Custom(c.id, c.name) to state.itemsOf(Shelf.Custom(c.id, c.name))
    }
    out += state.series
    if (state.folderShelves.size > 1) out += state.folderShelves
    return out
}

@Composable
private fun WideHome(
    state: LibraryState,
    selected: Int,
    onSelect: (Int) -> Unit,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    gutter: Dp,
    maxHeight: Dp,
) {
    val row = state.homeRow
    val index = selected.coerceIn(0, row.lastIndex)
    val item = row[index]
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.animateScrollToItem(index) }

    val cardH = (maxHeight * 0.42f).coerceIn(220.dp, 420.dp)
    val cardW = cardH * (2f / 3f)
    val shelves = homeShelves(state).take(4)

    Column(Modifier.fillMaxSize().padding(bottom = 28.dp)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = item,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
                contentKey = { it.comic.id },
                label = "hero",
                modifier = Modifier.width(380.dp),
            ) { hero ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
                    Text(hero.comic.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(hero), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressBar(hero.progress?.fraction ?: 0f, hero.glowColor(), Modifier.width(320.dp))
                        Text(progressLine(hero), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(cta(hero), onClick = { actions.open(hero) }, leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)) })
                        PillButton("Opções", onClick = { onMenu(hero) }, style = PillStyle.Ghost)
                    }
                }
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(start = 14.dp, end = gutter + cardW * 2),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(cardH + 60.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(Icons.Filled.KeyboardArrowLeft, "Anterior", { onSelect((index - 1).coerceAtLeast(0)) }, size = 44.dp)
                    CircleIconButton(Icons.Filled.KeyboardArrowRight, "Próxima", { onSelect((index + 1).coerceAtMost(row.lastIndex)) }, size = 44.dp)
                    Text("${index + 1} de ${row.size}", style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
            }
        }
        if (shelves.isNotEmpty()) {
            Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Estante", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    shelves.forEach { (shelf, list) ->
                        ShelfRowCard(shelf.title, list, Modifier.weight(1f)) { openShelf(shelf) }
                    }
                    repeat(4 - shelves.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CarouselCard(item: LibraryItem, selected: Boolean, width: Dp, height: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "scale")
    val alpha by animateFloatAsState(if (selected) 1f else 0.6f, label = "alpha")
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .shadow(if (selected) 24.dp else 10.dp, RoundedCornerShape(22.dp))
            .then(if (selected) Modifier.border(3.dp, FwipColors.Text, RoundedCornerShape(22.dp)) else Modifier)
            .combinedClickable(role = Role.Button, onClickLabel = item.comic.title, onLongClick = onLongClick, onClick = onClick)
    ) {
        CoverImage(item, Modifier.fillMaxSize(), corner = 22.dp)
    }
}

/** Compact horizontal card for the home strip: a few covers and a name. */
@Composable
private fun ShelfRowCard(title: String, list: List<LibraryItem>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(62.dp).height(50.dp)) {
            list.take(3).forEachIndexed { i, it ->
                CoverImage(
                    it,
                    Modifier
                        .padding(start = (i * 14).dp)
                        .size(34.dp, 50.dp)
                        .border(2.dp, Color(0xFF15161C), RoundedCornerShape(6.dp)),
                    corner = 6.dp,
                    titleSize = 6,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countLabel(list.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = FwipColors.TextSoft,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PhoneHome(
    state: LibraryState,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    gutter: Dp,
) {
    val hero = state.homeRow.first()
    val reading = state.continueReading.filter { it.comic.id != hero.comic.id }
    val series = state.series
    val shelves = homeShelves(state).filter { it.first !is Shelf.Series && it.first !is Shelf.Smart }
    val recent = state.items.sortedFor(SortOrder.Added).filter { it.comic.id != hero.comic.id }.take(12)

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
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
                CoverImage(hero, Modifier.size(120.dp, 180.dp).shadow(12.dp, RoundedCornerShape(14.dp)), corner = 14.dp, titleSize = 16)
                Column(Modifier.weight(1f).height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
                    Text(hero.comic.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
        if (reading.isNotEmpty()) {
            item { CoverRow("Continuar lendo", reading, actions, onMenu, gutter) }
        }
        if (series.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = gutter)) { SectionTitle("Séries") }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(series, key = { it.first.key }) { (shelf, list) ->
                            ShelfCard(
                                shelf.title,
                                list,
                                onClick = { openShelf(shelf) },
                                subtitle = seriesSubtitle(list),
                                modifier = Modifier.width(168.dp),
                            )
                        }
                    }
                }
            }
        }
        if (shelves.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Estante")
                    shelves.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (shelf, list) ->
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(FwipColors.Surface)
                                        .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
                                        .clickable(role = Role.Button) { openShelf(shelf) }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (shelf is Shelf.Folder) FolderIcon(FwipColors.TextSoft) else StackIcon(FwipColors.TextSoft)
                                    Column {
                                        Text(shelf.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(countLabel(list.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { CoverRow("Adicionadas recentemente", recent, actions, onMenu, gutter) }
        }
    }
}

private fun seriesSubtitle(list: List<LibraryItem>): String {
    val read = list.count { it.progress?.finished == true }
    return if (read > 0) "${list.size} edições · $read lidas" else "${list.size} edições"
}

@Composable
private fun CoverRow(title: String, list: List<LibraryItem>, actions: LibraryActions, onMenu: (LibraryItem) -> Unit, gutter: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = gutter)) { SectionTitle(title) }
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.comic.id }) { it ->
                Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoverImage(
                        it,
                        Modifier
                            .size(112.dp, 168.dp)
                            .shadow(10.dp, RoundedCornerShape(14.dp))
                            .combinedClickable(role = Role.Button, onClickLabel = it.comic.title, onLongClick = { onMenu(it) }) { actions.open(it) },
                        corner = 14.dp,
                        titleSize = 16,
                    )
                    val p = it.progress
                    if (p != null && p.started && !p.finished) ProgressBar(p.fraction, it.glowColor(), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    state: LibraryState,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    wide: Boolean,
    gutter: Dp,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.Title) }
    val minCell = if (wide) 160.dp else 104.dp
    val shelfCell = if (wide) 220.dp else 150.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (filter == Filter.Series || filter == Filter.Folders) shelfCell else minCell),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Filter.entries.forEach { f -> Chip(f.label, f == filter) { filter = f } }
                if (filter != Filter.Series && filter != Filter.Folders) {
                    Spacer(Modifier.width(8.dp))
                    Chip("Ordem: ${sort.label}", false) { sort = SortOrder.entries[(sort.ordinal + 1) % SortOrder.entries.size] }
                }
            }
        }
        when (filter) {
            Filter.Series -> {
                val series = state.series
                if (series.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Hint("Quando houver duas ou mais edições com o mesmo nome (como \"Saga #01\" e \"Saga #02\"), elas aparecem agrupadas aqui.")
                    }
                }
                items(series, key = { it.first.key }) { (shelf, list) ->
                    ShelfCard(shelf.title, list, onClick = { openShelf(shelf) }, subtitle = seriesSubtitle(list))
                }
            }
            Filter.Folders -> items(state.folderShelves, key = { "f:" + it.first.name }) { (shelf, list) ->
                ShelfCard(shelf.title, list, onClick = { openShelf(shelf) })
            }
            else -> {
                val list = when (filter) {
                    Filter.Unread -> state.items.filter { it.matches(SmartKind.Unread) }
                    Filter.Finished -> state.items.filter { it.matches(SmartKind.Finished) }
                    else -> state.items
                }.sortedFor(sort)
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Hint(if (filter == Filter.Finished) "Nenhuma HQ lida ainda." else "Tudo lido por aqui.")
                    }
                }
                items(list, key = { it.comic.id }) { it ->
                    CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = wide)
                }
            }
        }
    }
}

@Composable
private fun CollectionsTab(
    state: LibraryState,
    actions: LibraryActions,
    openShelf: (Shelf) -> Unit,
    wide: Boolean,
    gutter: Dp,
) {
    var creating by remember { mutableStateOf(false) }
    val smart = listOf(SmartKind.Reading, SmartKind.Unread, SmartKind.Finished, SmartKind.Recent).map { Shelf.Smart(it) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 220.dp else 150.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Suas coleções") }
        items(state.collections, key = { it.id }) { c ->
            val shelf = Shelf.Custom(c.id, c.name)
            ShelfCard(c.name, state.itemsOf(shelf), onClick = { openShelf(shelf) })
        }
        item(key = "new") {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button) { creating = true }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(FwipColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = FwipColors.Text) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Nova coleção", style = MaterialTheme.typography.labelLarge)
                    Text("Segure uma capa para adicionar", style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(top = 12.dp)) { SectionTitle("Automáticas") } }
        items(smart, key = { "m:" + it.kind.name }) { shelf ->
            ShelfCard(shelf.title, state.itemsOf(shelf), onClick = { openShelf(shelf) })
        }
    }

    if (creating) {
        NameDialog(
            title = "Nova coleção",
            initial = "",
            action = "Criar",
            onDone = { name ->
                creating = false
                val id = actions.createCollection(name, emptyList())
                openShelf(Shelf.Custom(id, name))
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = FwipColors.TextMuted,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

/** A series, folder or collection opened full screen. */
@Composable
private fun ShelfScreen(
    shelf: Shelf,
    state: LibraryState,
    actions: LibraryActions,
    wide: Boolean,
    gutter: Dp,
    onBack: () -> Unit,
    onMenu: (LibraryItem) -> Unit,
) {
    val custom = shelf as? Shelf.Custom
    val collection = custom?.let { c -> state.collections.firstOrNull { it.id == c.id } }
    val title = collection?.name ?: shelf.title
    val list = state.itemsOf(shelf)
    val read = list.count { it.progress?.finished == true }
    val next = list.firstOrNull { it.progress?.started == true && it.progress.finished.not() }
        ?: list.firstOrNull { it.progress?.finished != true }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 160.dp else 104.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = if (wide) 24.dp else 12.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleIconButton(Icons.Filled.ArrowBack, "Voltar", onBack, size = 44.dp)
                    Spacer(Modifier.weight(1f))
                    if (collection != null) {
                        CircleIconButton(Icons.Filled.Edit, "Renomear coleção", { renaming = true }, size = 44.dp)
                        CircleIconButton(Icons.Filled.Delete, "Apagar coleção", { confirmDelete = true }, size = 44.dp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (wide && list.isNotEmpty()) CoverFan(list, Modifier.size(200.dp, 150.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when (shelf) {
                                is Shelf.Series -> "SÉRIE"
                                is Shelf.Folder -> "PASTA"
                                is Shelf.Custom -> "COLEÇÃO"
                                is Shelf.Smart -> "AUTOMÁTICA"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = FwipColors.TextSoft,
                        )
                        Text(title, style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (read > 0) "${countLabel(list.size)} · $read lidas" else countLabel(list.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FwipColors.TextMuted,
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (next != null) {
                            PillButton(
                                if (next.progress?.started == true) "Continuar ${next.series.number?.let { "#" + it.toInt() } ?: ""}".trim() else "Começar",
                                onClick = { actions.open(next) },
                                height = 48.dp,
                                leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)) },
                            )
                        }
                        val allRead = read == list.size
                        PillButton(
                            if (allRead) "Marcar tudo como não lido" else "Marcar tudo como lido",
                            onClick = { actions.setRead(list.map { it.comic.id }, !allRead) },
                            height = 48.dp,
                            style = PillStyle.Ghost,
                        )
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Hint(
                    if (collection != null) "Coleção vazia. Segure a capa de uma HQ e escolha \"Adicionar a uma coleção\"."
                    else "Nada por aqui ainda."
                )
            }
        }
        items(list, key = { it.comic.id }) { it ->
            CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = wide)
        }
    }

    if (renaming && collection != null) {
        NameDialog(
            title = "Renomear coleção",
            initial = collection.name,
            action = "Salvar",
            onDone = { actions.renameCollection(collection.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete && collection != null) {
        FwipDialog({ confirmDelete = false }) {
            DialogTitle("Apagar \"${collection.name}\"?", { confirmDelete = false })
            Text("As HQs continuam na biblioteca, só a coleção some.", style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cancelar", { confirmDelete = false }, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.weight(1f))
                PillButton(
                    "Apagar",
                    {
                        confirmDelete = false
                        onBack()
                        actions.deleteCollection(collection.id)
                    },
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhoneTabBar(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE616171E))
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(26.dp)),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Tab) { onTab(t) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) FwipColors.Text else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(noFolders: Boolean, scanning: Boolean, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(color = FwipColors.Text)
                Text("Procurando HQs nas suas pastas...", style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
            } else {
                Text(
                    if (noFolders) "Sua estante está vazia" else "Nenhuma HQ encontrada",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (noFolders) "Escolha a pasta onde ficam suas HQs. O Fwip lê arquivos CBZ, CBR, CB7 e PDF, inclusive em subpastas."
                    else "As pastas escolhidas não têm arquivos CBZ, CBR, CB7 ou PDF. Tente adicionar outra pasta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FwipColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                PillButton("Escolher pasta", onAdd, leading = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) })
            }
        }
    }
}

@Composable
private fun FoldersDialog(folders: List<Uri>, onAdd: () -> Unit, onRemove: (Uri) -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle("Pastas da biblioteca", onDismiss)
        if (folders.isEmpty()) {
            Text("Nenhuma pasta adicionada ainda.", style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
        }
        folders.forEach { uri ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FwipColors.Surface)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FolderIcon(FwipColors.TextSoft)
                Text(readableFolder(uri), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CircleIconButton(Icons.Filled.Delete, "Remover pasta", { onRemove(uri) }, size = 40.dp)
            }
        }
        PillButton("Adicionar pasta", onAdd, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth(), leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
    }
}

private fun readableFolder(uri: Uri): String {
    val raw = Uri.decode(uri.lastPathSegment ?: uri.toString())
    val (volume, path) = raw.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val vol = if (volume == "primary") "Armazenamento interno" else "Cartão / $volume"
    return if (path.isBlank()) vol else "$vol / $path"
}
