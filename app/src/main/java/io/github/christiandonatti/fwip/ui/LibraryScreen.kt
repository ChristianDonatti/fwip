@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.focusRequester
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

private enum class Tab(@androidx.annotation.StringRes val labelRes: Int) {
    Home(R.string.tab_home), Library(R.string.tab_library), Collections(R.string.tab_collections), Servers(R.string.tab_servers);

    val label: String get() = str(labelRes)
}

private enum class Filter(@androidx.annotation.StringRes val labelRes: Int) {
    All(R.string.filter_all), Series(R.string.filter_series), Folders(R.string.filter_folders), Unread(R.string.smart_unread), Finished(R.string.smart_read);

    val label: String get() = str(labelRes)
}

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
        's' -> body.split('\u0000', limit = 2).let { parts -> Shelf.Series(parts[0], parts.getOrElse(1) { parts[0] }) }
        'f' -> Shelf.Folder(body)
        'c' -> body.split('\u0000', limit = 2).let { parts -> Shelf.Custom(parts[0], parts.getOrElse(1) { "" }) }
        'm' -> runCatching { Shelf.Smart(SmartKind.valueOf(body)) }.getOrNull()
        else -> null
    }
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    actions: LibraryActions,
    serverActions: ServerActions,
    syncActions: SyncActions,
    metaActions: MetaActions,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var shelfToken by rememberSaveable { mutableStateOf<String?>(null) }
    val shelf = shelfFromToken(shelfToken)
    var showFolders by remember { mutableStateOf(false) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    // Comic Vine: which comics to identify, which one to edit by hand, and the key dialog.
    var identifyFor by remember { mutableStateOf<List<String>?>(null) }
    var editFor by remember { mutableStateOf<String?>(null) }
    var askKey by remember { mutableStateOf(false) }
    var afterKey by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUnresolved by remember { mutableStateOf(false) }
    val identify: (List<String>) -> Unit = { ids ->
        if (state.cvKey.isBlank()) {
            askKey = true
            afterKey = { identifyFor = ids }
        } else {
            identifyFor = ids
        }
    }
    val identifyLibrary: () -> Unit = {
        if (state.cvKey.isBlank()) {
            askKey = true
            afterKey = metaActions.identifyLibrary
        } else {
            metaActions.identifyLibrary()
        }
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<List<String>?>(null) }
    val selection = remember { Selection() }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val browsingServer = state.servers.firstOrNull { it.id == browsing }

    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = shelf != null && !selection.active) { shelfToken = null }
    BackHandler(enabled = shelf == null && searching && !selection.active) { searching = false; query = "" }

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

            if (showAbout) {
                AboutScreen(gutter, onBack = { showAbout = false })
            } else if (browsingServer != null && browsingServer.kind.isFolder) {
                NetBrowser(browsingServer, state.folders, serverActions, wide, gutter, onClose = { browsing = null })
            } else if (browsingServer != null) {
                ServerBrowser(browsingServer, serverActions, wide, gutter, onClose = { browsing = null })
            } else AnimatedContent(
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
                        onIdentify = { list -> identify(list.map { it.comic.id }) },
                        shelf = current,
                        state = state,
                        actions = actions,
                        wide = wide,
                        gutter = gutter,
                        onBack = { shelfToken = null },
                        onMenu = showMenu,
                        selection = selection,
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
                            onSearch = { tab = Tab.Library; searching = true },
                            onAbout = { showAbout = true },
                            modifier = Modifier.padding(horizontal = gutter, vertical = if (wide) 24.dp else 12.dp),
                        )
                        when {
                            state.items.isEmpty() && tab != Tab.Servers -> EmptyState(state.folders.isEmpty(), state.scanning, actions.addFolder, onServers = { tab = Tab.Servers })
                            tab == Tab.Home && wide -> WideHome(state, selected, { selected = it }, actions, openShelf, showMenu, gutter, boxHeight)
                            tab == Tab.Home -> PhoneHome(state, actions, openShelf, showMenu, gutter)
                            tab == Tab.Servers -> ServersTab(state.servers, serverActions, wide, gutter, onBrowse = { browsing = it.id })
                            tab == Tab.Library -> LibraryTab(
                                state, actions, openShelf, showMenu, wide, gutter, selection,
                                searching = searching,
                                query = query,
                                onQuery = { query = it },
                                onCloseSearch = { searching = false; query = "" },
                            )
                            else -> CollectionsTab(state, actions, openShelf, wide, gutter)
                        }
                    }
                }
            }
            if (selection.active) {
                SelectionBar(
                    count = selection.ids.size,
                    onRead = { actions.setRead(selection.ids.toList(), true); selection.clear() },
                    onUnread = { actions.setRead(selection.ids.toList(), false); selection.clear() },
                    onCollection = { pickerFor = selection.ids.toList() },
                    onClose = { selection.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (!wide && shelf == null && browsingServer == null && !showAbout && (state.items.isNotEmpty() || tab == Tab.Servers)) {
                PhoneTabBar(tab, onTab = { tab = it }, Modifier.align(Alignment.BottomCenter))
            }
            if (!selection.active) {
                MetaStatus(
                    work = state.metaWork,
                    message = state.metaMessage,
                    unresolved = state.unresolved.size,
                    onReview = { showUnresolved = true },
                    onDismiss = metaActions.dismissMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (!wide && shelf == null) 84.dp else 0.dp),
                )
            }
        }
    }

    if (showFolders) {
        FoldersDialog(
            onAbout = { showFolders = false; showAbout = true },
            folders = state.folders,
            servers = state.servers,
            onAdd = { showFolders = false; actions.addFolder() },
            onAddNet = { showFolders = false; tab = Tab.Servers },
            onSync = { showFolders = false; showSync = true },
            onIdentifyLibrary = { showFolders = false; identifyLibrary() },
            onCvKey = { showFolders = false; askKey = true; afterKey = null },
            onRemove = actions.removeFolder,
            onDismiss = { showFolders = false },
        )
    }

    if (showSync) {
        SyncDialog(state.sync, state.folders, state.servers, syncActions, onDismiss = { showSync = false })
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
                onCollections = { menuFor = null; pickerFor = listOf(id) },
                onSelect = {
                    menuFor = null
                    if (shelf == null && tab != Tab.Library) tab = Tab.Library
                    selection.toggle(id)
                },
                onSeries = {
                    menuFor = null
                    shelfToken = Shelf.Series(seriesKeyOf, item.series.name).toToken()
                },
                onShown = { metaActions.loadCredits(id) },
                onIdentify = {
                    menuFor = null
                    identify(state.items.filter { seriesKey(it.series.name) == seriesKeyOf }.map { it.comic.id }.ifEmpty { listOf(id) })
                },
                onEdit = { menuFor = null; editFor = id },
            )
        }
    }

    if (askKey) {
        ComicVineKeyDialog(
            current = state.cvKey,
            onSave = { key ->
                metaActions.setKey(key)
                askKey = false
                afterKey?.invoke()
                afterKey = null
            },
            onDismiss = { askKey = false; afterKey = null },
        )
    }

    identifyFor?.let { ids ->
        val list = ids.mapNotNull(state::item)
        if (list.isEmpty() || state.cvKey.isBlank()) {
            identifyFor = null
        } else {
            IdentifyDialog(list, state.cvKey, metaActions, onDismiss = { identifyFor = null })
        }
    }

    editFor?.let { id ->
        val item = state.item(id)
        if (item == null) editFor = null else EditInfoDialog(item, metaActions, onDismiss = { editFor = null })
    }

    if (showUnresolved) {
        val groups = state.unresolved.map { g -> g.mapNotNull(state::item) }.filter { it.isNotEmpty() }
        UnresolvedDialog(
            groups,
            onPick = { group -> showUnresolved = false; identify(group.map { it.comic.id }) },
            onDismiss = { showUnresolved = false },
        )
    }

    pickerFor?.let { ids ->
        CollectionPickerDialog(
            title = if (ids.size == 1) str(R.string.tab_collections) else str(R.string.collections_for_n, ids.size),
            collections = state.collections,
            comicIds = ids,
            onToggle = { c, inside -> actions.setInCollection(c.id, ids, inside) },
            onCreate = { name -> actions.createCollection(name, ids) },
            onDismiss = {
                pickerFor = null
                if (ids.size > 1) selection.clear()
            },
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
    onSearch: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val button = if (wide) 46.dp else 44.dp
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        FwipLogo(
            size = if (wide) 22 else 19,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = str(R.string.about), onClick = onAbout)
                .padding(4.dp),
        )
        Spacer(Modifier.weight(1f))
        if (wide) {
            Segmented(Tab.entries, tab, { it.label }, onTab)
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 10.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Filled.Search, str(R.string.search), onSearch, size = button)
            if (scanning) {
                Box(Modifier.size(button), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                }
            } else {
                CircleIconButton(Icons.Filled.Refresh, str(R.string.refresh_library), onRefresh, size = button)
            }
            CircleIconButton(Icons.Filled.Add, str(R.string.add_folder), onAdd, size = button)
            CircleButton(str(R.string.library_folders), onFolders, size = button) { FolderIcon(FwipColors.Text) }
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
    item.progress?.finished == true -> str(R.string.kicker_again)
    item.progress?.started == true -> str(R.string.kicker_continue)
    else -> str(R.string.kicker_start)
}

private fun cta(item: LibraryItem): String = when {
    item.progress?.finished == true -> str(R.string.reread)
    item.progress?.started == true -> str(R.string.continue_)
    else -> str(R.string.start)
}

private fun progressLine(item: LibraryItem): String {
    val p = item.progress ?: return item.comic.format.label
    if (p.pageCount <= 0) return item.comic.format.label
    val remaining = p.pageCount - 1 - p.page
    return when {
        p.finished -> str(R.string.progress_read, p.pageCount)
        !p.started -> str(R.string.progress_unstarted, p.pageCount)
        else -> str(R.string.progress_reading, p.page + 1, p.pageCount, remaining)
    }
}

private fun subtitle(item: LibraryItem): String {
    val m = item.meta
    val credits = listOfNotNull(m?.writer, m?.artists).flatMap { it.split(',') }.map { it.trim() }.distinct().take(2)
    return listOfNotNull(
        credits.joinToString(", ").ifBlank { null },
        m?.publisher,
        m?.year?.toString(),
    ).ifEmpty {
        listOfNotNull(item.comic.folder.takeIf { it.isNotBlank() }, item.comic.format.label)
    }.joinToString(" · ")
}

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
                    Text(hero.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(hero), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
                    hero.meta?.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressBar(hero.progress?.fraction ?: 0f, hero.glowColor(), Modifier.width(320.dp))
                        Text(progressLine(hero), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(cta(hero), onClick = { actions.open(hero) }, leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)) })
                        PillButton(str(R.string.options), onClick = { onMenu(hero) }, style = PillStyle.Ghost)
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
                    CircleIconButton(Icons.Filled.KeyboardArrowLeft, str(R.string.previous), { onSelect((index - 1).coerceAtLeast(0)) }, size = 44.dp)
                    CircleIconButton(Icons.Filled.KeyboardArrowRight, str(R.string.next_item), { onSelect((index + 1).coerceAtMost(row.lastIndex)) }, size = 44.dp)
                    Text(str(R.string.page_x_of_n, index + 1, row.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
            }
        }
        if (shelves.isNotEmpty()) {
            Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(str(R.string.shelf), style = MaterialTheme.typography.titleLarge)
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
            .combinedClickable(role = Role.Button, onClickLabel = item.title, onLongClick = onLongClick, onClick = onClick)
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
        if (reading.isNotEmpty()) {
            item { CoverRow(str(R.string.continue_reading), reading, actions, onMenu, gutter) }
        }
        if (series.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = gutter)) { SectionTitle(str(R.string.filter_series)) }
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
                    SectionTitle(str(R.string.shelf))
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
            item { CoverRow(str(R.string.smart_recent), recent, actions, onMenu, gutter) }
        }
    }
}

private fun seriesSubtitle(list: List<LibraryItem>): String {
    val read = list.count { it.progress?.finished == true }
    return if (read > 0) str(R.string.n_issues_read, list.size, read) else str(R.string.n_issues, list.size)
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
                            .combinedClickable(role = Role.Button, onClickLabel = it.title, onLongClick = { onMenu(it) }) { actions.open(it) },
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
    selection: Selection,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onCloseSearch: () -> Unit,
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
        if (searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(query, onQuery, onCloseSearch)
            }
        }
        val results = if (query.isBlank()) null else search(state.items, query)
        if (results != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when (results.size) { 0 -> str(R.string.search_none, query); 1 -> str(R.string.search_one); else -> str(R.string.search_n, results.size) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FwipColors.TextMuted,
                )
            }
            items(results, key = { it.comic.id }) { it ->
                CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = wide, selection = selection)
            }
            return@LazyVerticalGrid
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Filter.entries.forEach { f -> Chip(f.label, f == filter) { filter = f } }
                if (filter != Filter.Series && filter != Filter.Folders) {
                    Spacer(Modifier.width(8.dp))
                    Chip(str(R.string.sort_by, sort.label), false) { sort = SortOrder.entries[(sort.ordinal + 1) % SortOrder.entries.size] }
                }
            }
        }
        when (filter) {
            Filter.Series -> {
                val series = state.series
                if (series.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Hint(str(R.string.series_hint))
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
                        Hint(if (filter == Filter.Finished) str(R.string.none_read) else str(R.string.all_read))
                    }
                }
                items(list, key = { it.comic.id }) { it ->
                    CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = wide, selection = selection)
                }
            }
        }
    }
}

/** Accent- and case-insensitive text for matching ("Ação" finds "acao"). */
private fun normalize(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/** Every word typed must appear in the title, file name, series, credits, publisher or tags. */
private fun search(items: List<LibraryItem>, query: String): List<LibraryItem> {
    val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return items.filter { item ->
        val m = item.meta
        val haystack = normalize(
            listOfNotNull(
                item.title, item.comic.title, item.comic.fileName, item.comic.folder, item.series.name,
                m?.title, m?.writer, m?.penciller, m?.inker, m?.colorist, m?.coverArtist, m?.publisher,
                m?.imprint, m?.storyArc, m?.genre, m?.tags, m?.characters, m?.year?.toString(),
            ).joinToString(" ")
        )
        words.all { it in haystack }
    }.sortedFor(SortOrder.Title)
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = FwipColors.TextMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(str(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(FwipColors.Amber),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        CircleIconButton(Icons.Filled.Close, str(R.string.close_search), onClose, size = 42.dp)
    }
}

/** Actions for the selected comics, floating at the bottom. */
@Composable
private fun SelectionBar(
    count: Int,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onCollection: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color(0xF216171E))
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(Icons.Filled.Close, str(R.string.cancel_selection), onClose, size = 46.dp)
        Text(
            if (count == 1) str(R.string.one_selected) else str(R.string.n_selected, count),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            PillButton(str(R.string.smart_read), onRead, height = 44.dp, style = PillStyle.Ghost)
            PillButton(str(R.string.smart_unread), onUnread, height = 44.dp, style = PillStyle.Ghost)
            PillButton(str(R.string.collection), onCollection, height = 44.dp, style = PillStyle.Amber)
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
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(str(R.string.your_collections)) }
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
                    Text(str(R.string.new_collection), style = MaterialTheme.typography.labelLarge)
                    Text(str(R.string.hold_cover_to_add), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(top = 12.dp)) { SectionTitle(str(R.string.smart_collections)) } }
        items(smart, key = { "m:" + it.kind.name }) { shelf ->
            ShelfCard(shelf.title, state.itemsOf(shelf), onClick = { openShelf(shelf) })
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
    onIdentify: (List<LibraryItem>) -> Unit,
    shelf: Shelf,
    state: LibraryState,
    actions: LibraryActions,
    wide: Boolean,
    gutter: Dp,
    onBack: () -> Unit,
    onMenu: (LibraryItem) -> Unit,
    selection: Selection,
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
                    CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
                    Spacer(Modifier.weight(1f))
                    if (collection != null) {
                        CircleIconButton(Icons.Filled.Edit, str(R.string.rename_collection), { renaming = true }, size = 44.dp)
                        CircleIconButton(Icons.Filled.Delete, str(R.string.delete_collection), { confirmDelete = true }, size = 44.dp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (wide && list.isNotEmpty()) CoverFan(list, Modifier.size(200.dp, 150.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when (shelf) {
                                is Shelf.Series -> str(R.string.kind_series)
                                is Shelf.Folder -> str(R.string.kind_folder)
                                is Shelf.Custom -> str(R.string.kind_collection)
                                is Shelf.Smart -> str(R.string.kind_smart)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = FwipColors.TextSoft,
                        )
                        Text(title, style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val years = list.mapNotNull { it.meta?.year }
                        val publisher = list.firstNotNullOfOrNull { it.meta?.publisher }
                        Text(
                            listOfNotNull(
                                if (read > 0) str(R.string.count_read, countLabel(list.size), read) else countLabel(list.size),
                                publisher,
                                years.takeIf { it.isNotEmpty() }?.let { y -> if (y.min() == y.max()) "${y.min()}" else "${y.min()}-${y.max()}" },
                            ).joinToString(" · "),
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
                                if (next.progress?.started == true) (str(R.string.continue_) + " " + (next.series.number?.let { "#" + it.toInt() } ?: "")).trim() else str(R.string.start),
                                onClick = { actions.open(next) },
                                height = 48.dp,
                                leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)) },
                            )
                        }
                        val allRead = read == list.size
                        PillButton(
                            if (allRead) str(R.string.mark_all_unread) else str(R.string.mark_all_read),
                            onClick = { actions.setRead(list.map { it.comic.id }, !allRead) },
                            height = 48.dp,
                            style = PillStyle.Ghost,
                        )
                        PillButton(
                            str(R.string.select),
                            onClick = { selection.selectAll(list.map { it.comic.id }) },
                            height = 48.dp,
                            style = PillStyle.Ghost,
                        )
                        if (shelf is Shelf.Series || shelf is Shelf.Folder) {
                            PillButton(
                                str(R.string.cv_find_series),
                                onClick = { onIdentify(list) },
                                height = 48.dp,
                                style = PillStyle.Ghost,
                            )
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Hint(
                    if (collection != null) str(R.string.empty_collection)
                    else str(R.string.nothing_yet)
                )
            }
        }
        items(list, key = { it.comic.id }) { it ->
            CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = wide, selection = selection)
        }
    }

    if (renaming && collection != null) {
        NameDialog(
            title = str(R.string.rename_collection),
            initial = collection.name,
            action = str(R.string.save),
            onDone = { actions.renameCollection(collection.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete && collection != null) {
        FwipDialog({ confirmDelete = false }) {
            DialogTitle(str(R.string.delete_collection_q, collection.name), { confirmDelete = false })
            Text(str(R.string.delete_collection_hint), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(str(R.string.cancel), { confirmDelete = false }, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.weight(1f))
                PillButton(
                    str(R.string.delete),
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
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(noFolders: Boolean, scanning: Boolean, onAdd: () -> Unit, onServers: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(color = FwipColors.Text)
                Text(str(R.string.scanning), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
            } else {
                Text(
                    if (noFolders) str(R.string.empty_title) else str(R.string.empty_no_comics),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (noFolders) str(R.string.empty_hint)
                    else str(R.string.empty_no_comics_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FwipColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                PillButton(str(R.string.choose_folder), onAdd, leading = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) })
                PillButton(str(R.string.connect_server), onServers, style = PillStyle.Ghost)
            }
        }
    }
}

@Composable
private fun FoldersDialog(
    folders: List<Uri>,
    servers: List<io.github.christiandonatti.fwip.remote.Server>,
    onAdd: () -> Unit,
    onAddNet: () -> Unit,
    onSync: () -> Unit,
    onIdentifyLibrary: () -> Unit,
    onCvKey: () -> Unit,
    onRemove: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onAbout: () -> Unit,
) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.library_folders), onDismiss)
        if (folders.isEmpty()) {
            Text(str(R.string.no_folders), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
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
                Text(folderLabel(uri, servers), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CircleIconButton(Icons.Filled.Delete, str(R.string.remove_folder), { onRemove(uri) }, size = 40.dp)
            }
        }
        PillButton(str(R.string.add_folder), onAdd, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth(), leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        PillButton(str(R.string.add_network_folder), onAddNet, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth(), leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        SheetButton(str(R.string.sync_title), onSync)
        SheetButton(str(R.string.cv_identify_library), onIdentifyLibrary)
        SheetButton(str(R.string.cv_title), onCvKey)
        SheetButton(str(R.string.about), onAbout)
    }
}

