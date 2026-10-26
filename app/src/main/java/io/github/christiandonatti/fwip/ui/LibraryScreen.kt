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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Brush
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
    val setWant: (List<String>, Boolean) -> Unit,
    /** Opens a comic from a collection, whose order then decides what comes next. */
    val openIn: (LibraryItem, String?) -> Unit,
    val reorderCollection: (String, List<String>) -> Unit,
    val stats: () -> Map<java.time.LocalDate, io.github.christiandonatti.fwip.data.DayStats>,
    val deleteComics: (List<String>) -> Unit,
    val moveComics: (List<String>, Uri) -> Unit,
    /** Copies comics from the NAS to this device (the NAS copy stays). */
    val downloadComics: (List<String>) -> Unit = {},
    /** Picks a picture for a shelf: its cover, or (logo = true) its logo. */
    val pickShelfArt: (key: String, logo: Boolean) -> Unit = { _, _ -> },
    /** Notices about new issues of series being read (asks for permission when turned on). */
    val setAlerts: (Boolean) -> Unit = {},
    /** Picks a ComicRack .cbl reading list; the match comes back in the state. */
    val importReadingList: () -> Unit = {},
    val createFromReadingList: () -> String? = { null },
    val dismissReadingList: () -> Unit = {},
    val downloadsUri: () -> Uri,
    val dismissFileMessage: () -> Unit,
    val saveReport: () -> Unit = {},
    /** Picks a picture for the library background. */
    val pickBackdrop: () -> Unit = {},
)

private enum class Tab(@androidx.annotation.StringRes val labelRes: Int) {
    Home(R.string.tab_home), Library(R.string.tab_library), Collections(R.string.tab_collections), Servers(R.string.tab_servers);

    val label: String get() = str(labelRes)
}

internal enum class Filter(@androidx.annotation.StringRes val labelRes: Int) {
    All(R.string.filter_all), Series(R.string.filter_series), Folders(R.string.filter_folders), Want(R.string.smart_want), Unread(R.string.smart_unread), Finished(R.string.smart_read);

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
    // Fixing names, series and covers by hand.
    var editFor by remember { mutableStateOf<String?>(null) }
    var seriesEditFor by remember { mutableStateOf<List<String>?>(null) }
    var coverFor by remember { mutableStateOf<String?>(null) }
    // Deleting and moving files.
    var deleteFor by remember { mutableStateOf<List<String>?>(null) }
    var moveFor by remember { mutableStateOf<List<String>?>(null) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<List<String>?>(null) }
    val selection = remember { Selection() }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showStorage by rememberSaveable { mutableStateOf(false) }
    var showWifi by rememberSaveable { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var alertsOn by remember { mutableStateOf(io.github.christiandonatti.fwip.data.NewIssues.enabled(appContext)) }
    var showUpdateNotes by remember { mutableStateOf(false) }
    var libraryFilter by rememberSaveable { mutableStateOf(Filter.All) }
    // "See all" under the home shelf jumps to the matching list.
    val seeAll: (ShelfKind) -> Unit = { kind ->
        when (kind) {
            ShelfKind.Collections -> tab = Tab.Collections
            ShelfKind.Folders -> { libraryFilter = Filter.Folders; tab = Tab.Library }
            else -> { libraryFilter = Filter.Series; tab = Tab.Library }
        }
    }
    val browsingServer = state.servers.firstOrNull { it.id == browsing }
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    // A brand new install gets the welcome; anyone who already has comics never sees it.
    val fresh = state.items.isEmpty() && state.folders.isEmpty() && state.servers.isEmpty()
    LaunchedEffect(fresh) { if (!fresh && !ui.welcomed) ui.setWelcomed() }

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

    LaunchedEffect(glow) { ui.coverTint = glow }
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
            val boxWidth = maxWidth
            // A tablet held upright: tall and roomy, too big for the phone layout.
            val tall = maxHeight > maxWidth && maxWidth >= 600.dp
            val gutter = if (wide) 48.dp else 20.dp

            if (fresh && !ui.welcomed) {
                WelcomeScreen(
                    onPickFolder = { ui.setWelcomed(); actions.addFolder() },
                    onServers = { ui.setWelcomed(); tab = Tab.Servers },
                    onSkip = { ui.setWelcomed() },
                )
            } else if (showAppearance) {
                AppearanceScreen(state, gutter, wide, onPickImage = actions.pickBackdrop, onBack = { showAppearance = false })
            } else if (showStorage) {
                StorageScreen(state, gutter, onDelete = actions.deleteComics, onBack = { showStorage = false })
            } else if (showWifi) {
                WifiScreen(state.folders, state.servers, gutter, onReceived = actions.refresh, onBack = { showWifi = false })
            } else if (showStats) {
                StatsScreen(state, actions.stats, gutter, wide, onBack = { showStats = false })
            } else if (showAbout) {
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
                        onEditSeries = { list -> seriesEditFor = list.map { it.comic.id } },
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
                            tab == Tab.Home -> Home(
                                state, selected, { selected = it }, actions, openShelf, showMenu, seeAll,
                                onStats = { showStats = true },
                                gutter = gutter, maxWidth = boxWidth, maxHeight = boxHeight, wide = wide,
                                layout = when {
                                    tall -> HomeLayout.Upright
                                    wide -> HomeLayout.Landscape
                                    else -> HomeLayout.Phone
                                },
                            )
                            tab == Tab.Servers -> ServersTab(state.servers, serverActions, wide, gutter, onBrowse = { browsing = it.id })
                            tab == Tab.Library -> LibraryTab(
                                state, actions, openShelf, showMenu, wide, gutter, selection,
                                searching = searching,
                                query = query,
                                onQuery = { query = it },
                                onCloseSearch = { searching = false; query = "" },
                                filter = libraryFilter,
                                onFilter = { libraryFilter = it },
                            )
                            else -> CollectionsTab(state, actions, openShelf, wide, gutter)
                        }
                    }
                }
            }
            val update = io.github.christiandonatti.fwip.data.Updates.available
            if (update != null && !selection.active && !(fresh && !ui.welcomed) && shelf == null && !showStats && !showAbout && !showAppearance && browsingServer == null) {
                UpdateBanner(
                    update,
                    onNotes = { showUpdateNotes = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 0.dp else 78.dp),
                )
            }
            if (selection.active) {
                SelectionBar(
                    count = selection.ids.size,
                    onRead = { actions.setRead(selection.ids.toList(), true); selection.clear() },
                    onUnread = { actions.setRead(selection.ids.toList(), false); selection.clear() },
                    onCollection = { pickerFor = selection.ids.toList() },
                    onWant = { actions.setWant(selection.ids.toList(), true); selection.clear() },
                    onMove = { moveFor = selection.ids.toList() },
                    onDelete = { deleteFor = selection.ids.toList() },
                    onDownload = selection.ids.filter { id -> state.item(id)?.comic?.uri?.let(io.github.christiandonatti.fwip.remote.NetUris::isNet) == true }
                        .takeIf { it.isNotEmpty() }
                        ?.let { ids -> { actions.downloadComics(ids); selection.clear() } },
                    onClose = { selection.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (!wide && shelf == null && browsingServer == null && !showAbout && !showStats && !showAppearance && !(fresh && !ui.welcomed) && (state.items.isNotEmpty() || tab == Tab.Servers)) {
                PhoneTabBar(tab, onTab = { tab = it }, Modifier.align(Alignment.BottomCenter))
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
            onStats = { showFolders = false; showStats = true },
            onAppearance = { showFolders = false; showAppearance = true },
            onUpdates = { showFolders = false; showUpdates = true },
            onReport = { showFolders = false; showReport = true },
            alertsOn = alertsOn,
            onAlerts = { on -> alertsOn = on; actions.setAlerts(on) },
            onStorage = { showFolders = false; showStorage = true },
            onWifi = { showFolders = false; showWifi = true },
            onRemove = actions.removeFolder,
            onDismiss = { showFolders = false },
        )
    }

    if (showUpdates) {
        UpdatesDialog(onDismiss = { showUpdates = false })
    }
    if (showReport) {
        ReportDialog(onSave = actions.saveReport, onDismiss = { showReport = false })
    }
    io.github.christiandonatti.fwip.data.Updates.available?.let { u ->
        if (showUpdateNotes) UpdateNotesDialog(u, onDismiss = { showUpdateNotes = false })
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
            // Issues of this series that are only on the NAS, for "download the series".
            val seriesOnNas = state.items
                .filter { seriesKey(it.series.name) == seriesKeyOf && io.github.christiandonatti.fwip.remote.NetUris.isNet(it.comic.uri) }
                .sortedFor(io.github.christiandonatti.fwip.data.SortOrder.Title)
                .map { it.comic.id }
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
                onCover = { menuFor = null; coverFor = id },
                onWant = { actions.setWant(listOf(id), !item.wanted) },
                onMove = { menuFor = null; moveFor = listOf(id) },
                onDelete = { menuFor = null; deleteFor = listOf(id) },
                onEdit = { menuFor = null; editFor = id },
                onResetProgress = {
                    actions.setRead(listOf(id), false)
                    menuFor = null
                },
                onDownload = if (io.github.christiandonatti.fwip.remote.NetUris.isNet(item.comic.uri)) {
                    { menuFor = null; actions.downloadComics(listOf(id)) }
                } else null,
                onDownloadSeries = if (seriesOnNas.any { it != id }) {
                    { menuFor = null; actions.downloadComics(seriesOnNas) }
                } else null,
                seriesToDownload = seriesOnNas.size,
                onEditSeries = {
                    menuFor = null
                    seriesEditFor = state.items.filter { seriesKey(it.series.name) == seriesKeyOf }.map { it.comic.id }.ifEmpty { listOf(id) }
                },
            )
        }
    }

    editFor?.let { id ->
        val item = state.item(id)
        if (item == null) editFor = null else EditInfoDialog(item, metaActions, onDismiss = { editFor = null })
    }

    seriesEditFor?.let { ids ->
        val list = ids.mapNotNull(state::item)
        if (list.isEmpty()) seriesEditFor = null else SeriesEditDialog(list, metaActions, onDismiss = { seriesEditFor = null })
    }

    deleteFor?.let { ids ->
        val list = ids.mapNotNull(state::item)
        FwipDialog({ deleteFor = null }) {
            DialogTitle(if (list.size == 1) str(R.string.delete_one_title) else str(R.string.delete_n_title, list.size), { deleteFor = null })
            if (list.size == 1) Text(list[0].comic.fileName, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            Text(str(R.string.delete_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            if (list.isNotEmpty() && list.all { it.alsoOnNas }) {
                Text(str(R.string.delete_keeps_nas), style = MaterialTheme.typography.bodyMedium, color = FwipColors.Amber)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(str(R.string.cancel), { deleteFor = null }, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.weight(1f))
                PillButton(str(R.string.delete), {
                    actions.deleteComics(ids)
                    deleteFor = null
                    selection.clear()
                }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }

    moveFor?.let { ids ->
        FwipDialog({ moveFor = null }) {
            DialogTitle(if (ids.size == 1) str(R.string.move_one_title) else str(R.string.move_n_title, ids.size), { moveFor = null })
            Text(str(R.string.move_explain), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            val targets = state.folders.map { it to folderLabel(it, state.servers) } + (actions.downloadsUri() to str(R.string.downloads_folder))
            targets.forEach { (uri, label) ->
                SheetButton(label, {
                    actions.moveComics(ids, uri)
                    moveFor = null
                    selection.clear()
                }, leading = { FolderIcon(FwipColors.Text) })
            }
        }
    }

    state.fileWork?.let { work ->
        FwipDialog({}) {
            Text(work.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressBar(work.progress, FwipColors.Amber, Modifier.fillMaxWidth())
        }
    }

    state.fileMessage?.let { message ->
        FwipDialog(actions.dismissFileMessage) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            PillButton(str(R.string.ok), actions.dismissFileMessage, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.fillMaxWidth())
        }
    }

    coverFor?.let { id ->
        val item = state.item(id)
        if (item == null) coverFor = null else CoverDialog(item, metaActions, onDismiss = { coverFor = null })
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
            CircleIconButton(Icons.Filled.Settings, str(R.string.app_settings), onFolders, size = button)
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

internal fun kicker(item: LibraryItem): String = when {
    item.progress?.finished == true -> str(R.string.kicker_again)
    item.progress?.started == true -> str(R.string.kicker_continue)
    else -> str(R.string.kicker_start)
}

internal fun cta(item: LibraryItem): String = when {
    item.progress?.finished == true -> str(R.string.reread)
    item.progress?.started == true -> str(R.string.continue_)
    else -> str(R.string.start)
}

internal fun progressLine(item: LibraryItem): String {
    val p = item.progress ?: return item.comic.format.label
    if (p.pageCount <= 0) return item.comic.format.label
    val remaining = p.pageCount - 1 - p.page
    return when {
        p.finished -> str(R.string.progress_read, p.pageCount)
        !p.started -> str(R.string.progress_unstarted, p.pageCount)
        else -> str(R.string.progress_reading, p.page + 1, p.pageCount, remaining)
    }
}

internal fun subtitle(item: LibraryItem): String {
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
internal fun homeShelves(state: LibraryState): List<Pair<Shelf, List<LibraryItem>>> {
    val out = mutableListOf<Pair<Shelf, List<LibraryItem>>>()
    val reading = state.continueReading
    if (reading.isNotEmpty()) out += Shelf.Smart(SmartKind.Reading) to reading
    val want = state.itemsOf(Shelf.Smart(SmartKind.Want))
    if (want.isNotEmpty()) out += Shelf.Smart(SmartKind.Want) to want
    state.collections.filter { it.comicIds.isNotEmpty() }.forEach { c ->
        out += Shelf.Custom(c.id, c.name) to state.itemsOf(Shelf.Custom(c.id, c.name))
    }
    out += state.homeSeries
    if (state.folderShelves.size > 1) out += state.folderShelves
    return out
}

@Composable
internal fun CarouselCard(item: LibraryItem, selected: Boolean, width: Dp, height: Dp, onClick: () -> Unit, onLongClick: () -> Unit, flat: Boolean = true) {
    // In the flat row the side covers shrink and fade; the other carousels do that themselves.
    val scale by animateFloatAsState(if (selected || !flat) 1f else 0.86f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "scale")
    val alpha by animateFloatAsState(if (selected || !flat) 1f else 0.6f, label = "alpha")
    val corner = coverCorner(22.dp)
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .coverShadow(if (selected) 24.dp else 10.dp, 22.dp)
            .then(if (selected) Modifier.border(3.dp, FwipColors.Text, RoundedCornerShape(corner)) else Modifier)
            .combinedClickable(role = Role.Button, onClickLabel = item.title, onLongClick = onLongClick, onClick = onClick)
    ) {
        CoverImage(item, Modifier.fillMaxSize(), corner = 22.dp, track = selected)
    }
}

@Composable
internal fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
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

internal fun seriesSubtitle(list: List<LibraryItem>): String {
    val read = list.count { it.progress?.finished == true }
    return if (read > 0) str(R.string.n_issues_read, list.size, read) else str(R.string.n_issues, list.size)
}

@Composable
internal fun CoverRow(
    title: String,
    list: List<LibraryItem>,
    actions: LibraryActions,
    onMenu: (LibraryItem) -> Unit,
    gutter: Dp,
    coverW: Dp = 112.dp,
    kicker: String? = null,
    onSeeAll: (() -> Unit)? = null,
    /** A collection whose reading order "next issue" should follow. */
    openIn: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = gutter)) {
            if (kicker != null) Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextMuted)
            SectionTitle(title, if (onSeeAll != null) str(R.string.see_all) else null, onSeeAll)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.comic.id }) { it ->
                Column(Modifier.width(coverW), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoverImage(
                        it,
                        Modifier
                            .size(coverW, coverW * 1.5f)
                            .coverShadow(10.dp, 14.dp)
                            .combinedClickable(role = Role.Button, onClickLabel = it.title, onLongClick = { onMenu(it) }) { if (openIn != null) actions.openIn(it, openIn) else actions.open(it) },
                        corner = 14.dp,
                        titleSize = 16,
                        track = true,
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
    filter: Filter,
    onFilter: (Filter) -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(SortOrder.Title) }
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    val minCell = (if (wide) 160.dp else 104.dp) * ui.coverScale
    val shelfCell = (if (wide) 220.dp else 150.dp) * ui.coverScale.coerceIn(0.8f, 1.6f)
    val bigTitles = wide || ui.coverScale > 1.35f

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (filter == Filter.Series || filter == Filter.Folders) shelfCell else minCell),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        modifier = Modifier.pinchToResize(ui::scaleCovers, ui::saveCoverScale),
    ) {
        if (searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(query, onQuery, onCloseSearch)
            }
        }
        val results = if (query.isBlank()) null else search(state.items, query)
        if (results != null) {
            // Series whose name matches come first, single-issue ones too.
            val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
            val matchingSeries = state.series.filter { (shelf, _) -> words.all { it in normalize(shelf.title) } }
            if (matchingSeries.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(str(R.string.filter_series), style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            matchingSeries.take(20).forEach { (shelf, list) ->
                                ShelfCard(shelf.title, list, onClick = { openShelf(shelf) }, modifier = Modifier.width(if (wide) 280.dp else 220.dp), subtitle = seriesSubtitle(list), shelf = shelf)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when (results.size) { 0 -> str(R.string.search_none, query); 1 -> str(R.string.search_one); else -> str(R.string.search_n, results.size) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FwipColors.TextMuted,
                )
            }
            items(results, key = { it.comic.id }) { it ->
                CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = bigTitles, selection = selection)
            }
            return@LazyVerticalGrid
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Filter.entries.forEach { f -> Chip(f.label, f == filter) { onFilter(f) } }
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
                    ShelfCard(shelf.title, list, onClick = { openShelf(shelf) }, subtitle = seriesSubtitle(list), shelf = shelf)
                }
            }
            Filter.Folders -> items(state.folderShelves, key = { "f:" + it.first.name }) { (shelf, list) ->
                ShelfCard(shelf.title, list, onClick = { openShelf(shelf) }, shelf = shelf)
            }
            else -> {
                val list = when (filter) {
                    Filter.Unread -> state.items.filter { it.matches(SmartKind.Unread) }
                    Filter.Want -> state.items.filter { it.matches(SmartKind.Want) }
                    Filter.Finished -> state.items.filter { it.matches(SmartKind.Finished) }
                    else -> state.items
                }.sortedFor(sort)
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Hint(if (filter == Filter.Finished) str(R.string.none_read) else str(R.string.all_read))
                    }
                }
                items(list, key = { it.comic.id }) { it ->
                    CoverTile(it, onOpen = { actions.open(it) }, onMenu = { onMenu(it) }, wide = bigTitles, selection = selection)
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
    onWant: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
) {
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(FwipColors.Panel.copy(alpha = 0.95f))
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
            PillButton(str(R.string.smart_want), onWant, height = 44.dp, style = PillStyle.Ghost)
            onDownload?.let { PillButton(str(R.string.download), it, height = 44.dp, style = PillStyle.Ghost) }
            PillButton(str(R.string.move), onMove, height = 44.dp, style = PillStyle.Ghost)
            PillButton(str(R.string.delete), onDelete, height = 44.dp, style = PillStyle.Ghost)
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
    val smart = listOf(SmartKind.Reading, SmartKind.Want, SmartKind.Unread, SmartKind.Finished, SmartKind.Recent).map { Shelf.Smart(it) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 220.dp else 150.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(str(R.string.your_collections)) }
        items(state.collections, key = { it.id }) { c ->
            val shelf = Shelf.Custom(c.id, c.name)
            ShelfCard(c.name, state.itemsOf(shelf), onClick = { openShelf(shelf) }, shelf = shelf)
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
        item(key = "cbl") {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button) { actions.importReadingList() }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(FwipColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) { ListGlyph(FwipColors.Text) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(str(R.string.cbl_import), style = MaterialTheme.typography.labelLarge)
                    Text(str(R.string.cbl_import_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 3)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(top = 12.dp)) { SectionTitle(str(R.string.smart_collections)) } }
        items(smart, key = { "m:" + it.kind.name }) { shelf ->
            ShelfCard(shelf.title, state.itemsOf(shelf), onClick = { openShelf(shelf) })
        }
    }

    state.readingList?.let { m ->
        ReadingListDialog(
            m,
            onCreate = {
                val name = m.name
                actions.createFromReadingList()?.let { id -> openShelf(Shelf.Custom(id, name)) }
            },
            onDismiss = actions.dismissReadingList,
        )
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
    onEditSeries: (List<LibraryItem>) -> Unit,
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
    var reordering by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf(false) }
    val tint = ShelfLooks.color(shelf)
    val art = ShelfLooks.cover(shelf)
    val logo = ShelfLooks.logo(shelf)
    val openHere: (LibraryItem) -> Unit = { if (collection != null) actions.openIn(it, collection.id) else actions.open(it) }
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val backdropFade by remember {
        androidx.compose.runtime.derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 0f else (1f - gridState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize()) {
    // The series' first cover, blurred, colours the top of the screen.
    CoverBackdrop(
        list.firstOrNull { it.coverPath != null },
        Modifier.fillMaxWidth().height(if (wide) 460.dp else 380.dp),
        alpha = backdropFade,
    )
    if (tint != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (wide) 460.dp else 380.dp)
                .graphicsLayer { alpha = backdropFade }
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.38f), Color.Transparent)))
        )
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive((if (wide) 160.dp else 104.dp) * ui.coverScale),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = if (wide) 24.dp else 12.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        modifier = Modifier.fillMaxSize().pinchToResize(ui::scaleCovers, ui::saveCoverScale),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
                    Spacer(Modifier.weight(1f))
                    if (ShelfLooks.keyOf(shelf) != null) {
                        CircleIconButton(Icons.Filled.Star, str(R.string.shelf_look), { looking = true }, size = 44.dp)
                    }
                    if (collection != null) {
                        CircleIconButton(Icons.Filled.Edit, str(R.string.rename_collection), { renaming = true }, size = 44.dp)
                        CircleIconButton(Icons.Filled.Delete, str(R.string.delete_collection), { confirmDelete = true }, size = 44.dp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (wide && art != null) {
                        coil.compose.AsyncImage(
                            model = art,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(200.dp, 150.dp).clip(RoundedCornerShape(18.dp)),
                        )
                    } else if (wide && list.isNotEmpty()) CoverFan(list, Modifier.size(200.dp, 150.dp))
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
                        if (logo != null) {
                            coil.compose.AsyncImage(
                                model = logo,
                                contentDescription = title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.height(if (wide) 120.dp else 88.dp).widthIn(max = 460.dp),
                            )
                        } else {
                            Text(title, style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
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
                                onClick = { openHere(next) },
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
                        if (collection != null && list.size >= 2) {
                            PillButton(
                                str(R.string.reading_order),
                                onClick = { reordering = true },
                                height = 48.dp,
                                style = PillStyle.Ghost,
                            )
                        }
                        if (shelf is Shelf.Series || shelf is Shelf.Folder) {
                            PillButton(
                                str(R.string.series_edit),
                                onClick = { onEditSeries(list) },
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
            CoverTile(it, onOpen = { openHere(it) }, onMenu = { onMenu(it) }, wide = wide || ui.coverScale > 1.35f, selection = selection)
        }
    }
    }

    if (reordering && collection != null) {
        ReorderDialog(
            list,
            onSave = { ids -> actions.reorderCollection(collection.id, ids); reordering = false },
            onDismiss = { reordering = false },
        )
    }

    if (looking) {
        ShelfLookDialog(shelf, title, onPick = { logoPick -> ShelfLooks.keyOf(shelf)?.let { actions.pickShelfArt(it, logoPick) } }, onDismiss = { looking = false })
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
            .background(FwipColors.Panel.copy(alpha = 0.92f))
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
    onStats: () -> Unit,
    onAppearance: () -> Unit,
    onUpdates: () -> Unit,
    onReport: () -> Unit,
    onRemove: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onAbout: () -> Unit,
    alertsOn: Boolean = false,
    onAlerts: (Boolean) -> Unit = {},
    onStorage: () -> Unit = {},
    onWifi: () -> Unit = {},
) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.app_settings), onDismiss)
        Text(str(R.string.library_folders), style = MaterialTheme.typography.labelLarge, color = FwipColors.TextSoft)
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
        SheetButton(str(R.string.appearance), onAppearance)
        SheetButton(str(R.string.stats_title), onStats)
        SheetButton(str(R.string.sync_title), onSync)
        SheetButton(str(R.string.wifi_title), onWifi)
        SheetButton(str(R.string.storage_title), onStorage)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FwipColors.Surface)
                .clickable(role = Role.Switch) { onAlerts(!alertsOn) }
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(str(R.string.alerts_title), style = MaterialTheme.typography.labelLarge)
                Text(str(R.string.alerts_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }
            androidx.compose.material3.Switch(
                checked = alertsOn,
                onCheckedChange = onAlerts,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
            )
        }
        if (io.github.christiandonatti.fwip.data.Updates.available != null) {
            SheetButton(str(R.string.updates), onUpdates, trailing = { Box(Modifier.size(10.dp).clip(CircleShape).background(FwipColors.Amber)) })
        } else {
            SheetButton(str(R.string.updates), onUpdates)
        }
        SheetButton(str(R.string.report_title), onReport)
        SheetButton(str(R.string.about), onAbout)
    }
}



/** What a reading list found in the library, before it becomes a collection. */
@Composable
private fun ReadingListDialog(m: io.github.christiandonatti.fwip.data.CblMatch, onCreate: () -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(m.name, onDismiss)
        Text(
            str(R.string.cbl_found, m.ids.size, m.total),
            style = MaterialTheme.typography.bodyLarge,
            color = if (m.ids.isEmpty()) FwipColors.Danger else FwipColors.Text,
        )
        if (m.missing.isNotEmpty()) {
            Text(str(R.string.cbl_missing), style = MaterialTheme.typography.labelLarge, color = FwipColors.TextSoft)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                m.missing.take(40).forEach { b ->
                    Text(b.label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (m.missing.size > 40) {
                    Text(str(R.string.cbl_more_missing, m.missing.size - 40), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
            }
            Text(str(R.string.cbl_missing_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(str(R.string.cancel), onDismiss, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.weight(1f))
            PillButton(str(R.string.cbl_create), onCreate, style = PillStyle.Amber, height = 46.dp, enabled = m.ids.isNotEmpty(), modifier = Modifier.weight(1f))
        }
    }
}

/** Three lines with dots: a reading list. */
@Composable
internal fun ListGlyph(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.09f
        for (i in 0..2) {
            val y = w * (0.25f + i * 0.25f)
            drawCircle(color, radius = stroke * 0.75f, center = androidx.compose.ui.geometry.Offset(w * 0.16f, y))
            drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.34f, y), androidx.compose.ui.geometry.Offset(w * 0.88f, y), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}


/** A shelf's own colour, picture and logo. */
@Composable
private fun ShelfLookDialog(shelf: Shelf, title: String, onPick: (logo: Boolean) -> Unit, onDismiss: () -> Unit) {
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    val theme = ui.resolved(androidx.compose.foundation.isSystemInDarkTheme())
    val current = ShelfLooks.color(shelf)
    val art = ShelfLooks.cover(shelf)
    val logo = ShelfLooks.logo(shelf)
    FwipDialog(onDismiss, tint = current) {
        DialogTitle(title, onDismiss)
        Text(str(R.string.shelf_color), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // "Automatic": the colour of the first cover, as before.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(if (current == null) 3.dp else 1.dp, if (current == null) FwipColors.Text else FwipColors.Stroke, CircleShape)
                    .clickable(role = Role.RadioButton) { ShelfLooks.setColor(shelf, null) },
                contentAlignment = Alignment.Center,
            ) { Text("A", style = MaterialTheme.typography.labelLarge, color = FwipColors.TextSoft) }
            (theme.accents.map { it.color } + listOf(Color(0xFF8E8E99), Color(0xFF2F5C8A), Color(0xFF86304A))).distinct().forEach { c ->
                val on = current == c
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) FwipColors.Text else FwipColors.Stroke, CircleShape)
                        .padding(if (on) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable(role = Role.RadioButton) { ShelfLooks.setColor(shelf, c) },
                )
            }
        }
        Text(str(R.string.shelf_picture), style = MaterialTheme.typography.labelLarge)
        Text(str(R.string.shelf_picture_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(str(R.string.shelf_choose), { onPick(false) }, style = PillStyle.Ghost, height = 44.dp)
            if (art != null) PillButton(str(R.string.shelf_remove), { ShelfLooks.clearCover(shelf) }, style = PillStyle.Ghost, height = 44.dp)
        }
        if (shelf !is Shelf.Folder) {
            Text(str(R.string.shelf_logo), style = MaterialTheme.typography.labelLarge)
            Text(str(R.string.shelf_logo_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(str(R.string.shelf_choose), { onPick(true) }, style = PillStyle.Ghost, height = 44.dp)
                if (logo != null) PillButton(str(R.string.shelf_remove), { ShelfLooks.clearLogo(shelf) }, style = PillStyle.Ghost, height = 44.dp)
            }
        }
    }
}
