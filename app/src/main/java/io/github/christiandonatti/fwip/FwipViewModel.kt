package io.github.christiandonatti.fwip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.data.ComicCollection
import io.github.christiandonatti.fwip.data.Shelf
import io.github.christiandonatti.fwip.data.SmartKind
import io.github.christiandonatti.fwip.data.SortOrder
import io.github.christiandonatti.fwip.data.matches
import io.github.christiandonatti.fwip.data.seriesKey
import io.github.christiandonatti.fwip.data.sortedFor
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.data.LibraryRepository
import io.github.christiandonatti.fwip.data.alignTwins
import io.github.christiandonatti.fwip.data.syncKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** What the sync screen shows. */
data class SyncInfo(
    val place: Uri? = null,
    val lastSync: Long = 0L,
    val devices: List<io.github.christiandonatti.fwip.data.SyncDevice> = emptyList(),
    val busy: Boolean = false,
    /** Last result or problem, shown once under the buttons. */
    val message: String? = null,
    val failed: Boolean = false,
)

/** Files being deleted or moved. */
data class FileWork(val label: String, val progress: Float)

data class LibraryState(
    val items: List<LibraryItem> = emptyList(),
    val folders: List<Uri> = emptyList(),
    val scanning: Boolean = false,
    val loaded: Boolean = false,
    val openComicId: String? = null,
    /** The collection a comic was opened from, so "next" follows its reading order. */
    val openContext: String? = null,
    val collections: List<ComicCollection> = emptyList(),
    val servers: List<io.github.christiandonatti.fwip.remote.Server> = emptyList(),
    /** A book opened straight from a server or a fresh download, not (yet) in the library. */
    val outsideComic: Comic? = null,
    val sync: SyncInfo = SyncInfo(),
    val fileWork: FileWork? = null,
    /** Result of the last delete or move, shown once. */
    val fileMessage: String? = null,
    /** Copies not shown (the same comic on the NAS) to the copy shown in their place. */
    val aliases: Map<String, String> = emptyMap(),
) {
    /** Started but not finished, most recent first. */
    val continueReading: List<LibraryItem>
        get() = items.filter { it.progress?.started == true && it.progress.finished.not() }
            .sortedByDescending { it.progress?.lastRead ?: 0L }

    /** What the home carousel shows: in-progress first, then the rest of the library. */
    val homeRow: List<LibraryItem>
        get() {
            val first = continueReading
            val ids = first.map { it.comic.id }.toSet()
            return first + items.filter { it.comic.id !in ids }
        }

    val openComic: Comic? get() = items.firstOrNull { it.comic.id == openComicId }?.comic

    /** What the reader shows: a library comic, or one from outside the library. */
    val readerComic: Comic? get() = outsideComic ?: openComic

    fun item(id: String): LibraryItem? =
        items.firstOrNull { it.comic.id == id } ?: aliases[id]?.let { a -> items.firstOrNull { it.comic.id == a } }

    /** What to read after [item]: the next issue of its series, or else the next file in its folder. */
    fun nextAfter(item: LibraryItem, context: String? = null): LibraryItem? {
        collections.firstOrNull { it.id == context }?.let { c ->
            val at = c.comicIds.indexOfFirst { it == item.comic.id || aliases[it] == item.comic.id }
            if (at >= 0) return c.comicIds.drop(at + 1).firstNotNullOfOrNull(::item)
        }
        val key = seriesKey(item.series.name)
        val series = items.filter { seriesKey(it.series.name) == key }.sortedFor(SortOrder.Title)
        if (series.size >= 2) {
            return series.getOrNull(series.indexOfFirst { it.comic.id == item.comic.id } + 1)
        }
        val folder = items.filter { it.comic.folder == item.comic.folder }
            .sortedWith(compareBy(io.github.christiandonatti.fwip.data.NaturalOrder) { it.comic.fileName })
        return folder.getOrNull(folder.indexOfFirst { it.comic.id == item.comic.id } + 1)
    }

    /** Every series, even with a single issue so far, in alphabetical order. */
    val series: List<Pair<Shelf.Series, List<LibraryItem>>>
        get() = items.groupBy { seriesKey(it.series.name) }
            .map { (key, list) -> Shelf.Series(key, list.first().series.name) to list.sortedFor(SortOrder.Title) }
            .sortedWith(compareBy(io.github.christiandonatti.fwip.data.NaturalOrder) { it.first.title })

    /** Series for the home screen: the ones with several issues or being read, most recent first. */
    val homeSeries: List<Pair<Shelf.Series, List<LibraryItem>>>
        get() = series
            .filter { (_, list) -> list.size >= 2 || list.any { it.progress?.started == true } }
            .sortedByDescending { (_, list) -> list.maxOf { it.progress?.lastRead ?: 0L } }

    val folderShelves: List<Pair<Shelf.Folder, List<LibraryItem>>>
        get() = items.groupBy { it.comic.folder }
            .map { (name, list) -> Shelf.Folder(name) to list.sortedFor(SortOrder.Title) }
            .sortedBy { it.first.title.lowercase() }

    fun itemsOf(shelf: Shelf, order: SortOrder = SortOrder.Title): List<LibraryItem> = when (shelf) {
        is Shelf.Series -> items.filter { seriesKey(it.series.name) == shelf.key }.sortedFor(SortOrder.Title)
        is Shelf.Folder -> items.filter { it.comic.folder == shelf.name }.sortedFor(order)
        is Shelf.Custom -> collections.firstOrNull { it.id == shelf.id }?.comicIds.orEmpty().mapNotNull(::item).distinctBy { it.comic.id }
        is Shelf.Smart -> when (shelf.kind) {
            SmartKind.Recent -> items.sortedFor(SortOrder.Added).take(30)
            SmartKind.Reading -> continueReading
            SmartKind.Want -> items.filter { it.matches(SmartKind.Want) }.sortedByDescending { it.wantedAt ?: 0L }
            else -> items.filter { it.matches(shelf.kind) }.sortedFor(order)
        }
    }
}

class FwipViewModel(app: Application) : AndroidViewModel(app) {

    val repository = LibraryRepository(app)
    private val serverStore = io.github.christiandonatti.fwip.remote.ServerStore(app)
    private val sync = io.github.christiandonatti.fwip.data.SyncManager(app, repository)
    val stats = io.github.christiandonatti.fwip.data.ReadingStats(app.filesDir)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Which copy of each comic shows, when it is in more than one place. */
    private var twins = io.github.christiandonatti.fwip.data.Twins.split(emptyList())

    init {
        twins = io.github.christiandonatti.fwip.data.Twins.split(repository.cachedComics())
        _state.value = LibraryState(
            items = twins.visible.map(::itemFor),
            aliases = twins.aliases,
            folders = repository.folders(),
            loaded = true,
            collections = repository.collections(),
            servers = serverStore.load(),
            sync = SyncInfo(sync.place?.uri, sync.lastSync, sync.devices),
        )
        refresh()
    }

    private fun itemFor(comic: Comic) = LibraryItem(
        comic = comic,
        coverPath = repository.coverPathIfReady(comic),
        color = repository.colorFor(comic),
        progress = repository.progressFor(comic.id),
        meta = repository.metaFor(comic),
        wantedAt = repository.wantedAt(comic.id),
        alsoOnNas = comic.id in twins.onNas,
    )

    fun addFolder(uri: Uri) {
        repository.addFolder(uri)
        _state.update { it.copy(folders = repository.folders()) }
        refresh()
        // The upload walks the folders itself, so it can start even if the app is closed before the scan ends.
        io.github.christiandonatti.fwip.data.NasUpload.schedule(getApplication())
    }

    fun removeFolder(uri: Uri) {
        repository.removeFolder(uri)
        _state.update { it.copy(folders = repository.folders()) }
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val comics = repository.scan()
            // Moved comics: carry their reading state over to the file's new place.
            synchronized(pendingMoves) {
                for (m in pendingMoves) {
                    comics.firstOrNull { it.fileName == m.newName && it.size == m.size && it.id != m.oldId && it.id.startsWith(m.targetPrefix) }
                        ?.let { repository.migrate(m.oldId, it.id) }
                }
                pendingMoves.clear()
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                sync.afterScan(comics)
                repository.alignTwins(comics, io.github.christiandonatti.fwip.data.BookmarkStore.get(getApplication()))
            }
            twins = io.github.christiandonatti.fwip.data.Twins.split(comics)
            _state.update { it.copy(items = twins.visible.map(::itemFor), aliases = twins.aliases, collections = repository.collections()) }
            scheduleSync(0)
            // New comics on this device go to the NAS, when that is turned on.
            io.github.christiandonatti.fwip.data.NasUpload.schedule(getApplication())
            // Covers and metadata are read one comic at a time so the shelves fill in while you watch.
            for (comic in twins.visible) {
                if (repository.isAnalyzed(comic)) continue
                repository.analyze(comic)
                val fresh = itemFor(comic)
                _state.update { s -> s.copy(items = s.items.map { if (it.comic.id == comic.id) fresh else it }) }
            }
            _state.update { it.copy(scanning = false) }
            refreshHomeScreen()
        }
    }

    fun open(comic: Comic, context: String? = null) {
        _state.update { it.copy(openComicId = comic.id, openContext = context) }
    }

    /** A collection's own reading order (an event, a crossover). */
    fun reorderCollection(id: String, comicIds: List<String>) =
        updateCollections { list -> list.map { if (it.id == id) it.copy(comicIds = comicIds) else it } }

    /** From a shortcut or the widget. */
    fun openById(id: String) {
        val comic = _state.value.item(id)?.comic ?: repository.cachedComics().firstOrNull { it.id == id } ?: return
        _state.update { it.copy(openComicId = comic.id, outsideComic = if (it.item(id) == null) comic else null) }
    }

    /** Home screen widget and icon shortcuts follow what is being read. */
    private fun refreshHomeScreen() {
        viewModelScope.launch { io.github.christiandonatti.fwip.widget.ContinueReadingWidget.refresh(getApplication()) }
    }

    /** Goes straight from one comic to another (the "next issue" button). */
    fun switchTo(comic: Comic) {
        _state.update { s ->
            s.copy(
                openComicId = comic.id,
                items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) },
            )
        }
    }

    fun closeReader() {
        _state.update { s ->
            s.copy(
                openComicId = null,
                openContext = null,
                outsideComic = null,
                items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) },
            )
        }
        scheduleSync(0)
        refreshHomeScreen()
        stats.stop()
    }

    /** The app went to the background: reading time stops counting. */
    fun onPause() = stats.stop()

    // ---------- Sync & backup ----------

    private var syncTimer: Job? = null
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    /** Syncs after [delayMs]; a newer call replaces a waiting one, so bursts of changes sync once. */
    fun scheduleSync(delayMs: Long = 6000) {
        if (sync.place == null) return
        syncTimer?.cancel()
        syncTimer = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            syncNow()
        }
    }

    /** Coming back to the app: pick up what the other device read meanwhile. */
    fun onResume() {
        if (System.currentTimeMillis() - sync.lastSync > 20_000) scheduleSync(0)
    }

    fun syncNow(manual: Boolean = false) {
        if (sync.place == null) return
        viewModelScope.launch {
            syncLock.withLock {
                _state.update { it.copy(sync = it.sync.copy(busy = true, message = null, failed = false)) }
                val comics = _state.value.items.map { it.comic }
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { sync.syncNow(comics) } }
                result.onSuccess { changed -> if (changed) { reloadReadingState(); refreshHomeScreen() } }
                _state.update {
                    it.copy(
                        sync = SyncInfo(
                            place = sync.place?.uri,
                            lastSync = sync.lastSync,
                            devices = sync.devices,
                            busy = false,
                            message = result.exceptionOrNull()?.let { e -> str(R.string.sync_failed, io.github.christiandonatti.fwip.ui.errorText(e)) }
                                ?: if (manual) str(R.string.sync_done) else null,
                            failed = result.isFailure,
                        ),
                    )
                }
            }
        }
    }

    /** Progress, collections, bookmarks and metadata may have changed underneath (sync, restore). */
    private fun reloadReadingState() {
        _state.update { s ->
            s.copy(
                items = s.items.map { itemFor(it.comic) },
                collections = repository.collections(),
            )
        }
    }

    // ---------- Names, series and covers fixed by hand ----------

    private fun refreshItems(ids: Collection<String>) {
        val set = ids.toSet()
        _state.update { s -> s.copy(items = s.items.map { if (it.comic.id in set) itemFor(it.comic) else it }) }
        refreshHomeScreen()
    }

    private fun saveEdited(id: String, change: (io.github.christiandonatti.fwip.data.ComicMeta) -> io.github.christiandonatti.fwip.data.ComicMeta) {
        val base = repository.onlineFor(id)
        val meta = change(base?.meta ?: io.github.christiandonatti.fwip.data.ComicMeta())
        repository.setOnline(id, io.github.christiandonatti.fwip.data.OnlineMeta(meta, "manual", updated = System.currentTimeMillis()))
    }

    /** Series, number, title and year typed in by the user. */
    fun saveManual(id: String, series: String, number: String, title: String, year: String) {
        saveEdited(id) {
            it.copy(
                series = series.trim().ifBlank { null },
                number = number.trim().ifBlank { null },
                title = title.trim().ifBlank { null },
                year = year.trim().toIntOrNull(),
            )
        }
        refreshItems(listOf(id))
        scheduleSync()
    }

    /**
     * Puts these comics in one series (renaming it, or joining comics the file names split up).
     * Each keeps its own issue number; [startYear] tells relaunches apart ("Batman (2016)").
     */
    fun renameSeries(ids: List<String>, name: String, startYear: String) {
        val year = startYear.trim().toIntOrNull()?.takeIf { it > 1900 }
        ids.forEach { id ->
            val item = _state.value.item(id) ?: return@forEach
            saveEdited(id) {
                it.copy(
                    series = name.trim().ifBlank { null },
                    volume = year,
                    // Keep the number the file name gave, so the issues stay in order.
                    number = it.issue ?: item.issueNumber,
                )
            }
        }
        refreshItems(ids)
        scheduleSync()
    }

    /** Back to what the file itself says. */
    fun clearEdited(ids: List<String>) {
        ids.forEach { repository.setOnline(it, null) }
        refreshItems(ids)
        scheduleSync()
    }

    /** One of the comic's own pages as its cover. */
    fun coverFromPage(id: String, page: Int) {
        val comic = _state.value.item(id)?.comic ?: return
        viewModelScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    io.github.christiandonatti.fwip.data.ComicSources.open(getApplication(), comic).use { source ->
                        source.decodePage(page, LibraryRepository.COVER_W * 2, LibraryRepository.COVER_H)
                    }
                }?.let { repository.setCustomCover(comic, it) }
            }
            refreshItems(listOf(id))
        }
    }

    /** Any picture from the device (a scan, a download) as the cover. */
    fun coverFromImage(id: String, uri: Uri) {
        val comic = _state.value.item(id)?.comic ?: return
        viewModelScope.launch {
            runCatching {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    io.github.christiandonatti.fwip.data.ImageDecoding.decodeBytes(bytes, LibraryRepository.COVER_W * 2, LibraryRepository.COVER_H)
                }
                if (bitmap != null) repository.setCustomCover(comic, bitmap)
            }
            refreshItems(listOf(id))
        }
    }

    /** First page again, also retrying comics whose cover could not be read before. */
    fun resetCover(id: String) {
        val comic = _state.value.item(id)?.comic ?: return
        viewModelScope.launch {
            runCatching { repository.resetCover(comic) }
            refreshItems(listOf(id))
        }
    }

    fun setSyncPlace(uri: Uri?) {
        if (uri != null && !io.github.christiandonatti.fwip.remote.NetUris.isNet(uri)) {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val old = sync.place?.uri
        if (old != null && old != uri && !io.github.christiandonatti.fwip.remote.NetUris.isNet(old) && old !in repository.folders()) {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    old,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        sync.place = uri?.let(io.github.christiandonatti.fwip.data.SyncPlace::of)
        _state.update { it.copy(sync = it.sync.copy(place = uri, message = null, failed = false)) }
        if (uri != null) syncNow(manual = true)
    }

    fun exportBackup(target: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val comics = _state.value.items.map { it.comic }
                    val servers = org.json.JSONArray()
                    // Passwords stay out of backup files.
                    _state.value.servers.forEach { s ->
                        servers.put(org.json.JSONObject().put("id", s.id).put("name", s.name).put("kind", s.kind.name).put("url", s.url).put("user", s.user))
                    }
                    val net = org.json.JSONArray()
                    repository.folders().filter(io.github.christiandonatti.fwip.remote.NetUris::isNet).forEach { net.put(it.toString()) }
                    val o = org.json.JSONObject()
                        .put("app", "fwip").put("kind", "backup").put("v", 1)
                        .put("created", System.currentTimeMillis())
                        .put("device", sync.deviceName)
                        .put("state", sync.localState(comics).toJson())
                        .put("settings", io.github.christiandonatti.fwip.reader.ReaderSettings(app).toJson())
                        .put("servers", servers)
                        .put("netFolders", net)
                    app.contentResolver.openOutputStream(target, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
                }
            }
            _state.update {
                it.copy(sync = it.sync.copy(message = result.fold({ str(R.string.backup_saved) }, { e -> str(R.string.backup_failed, e.message ?: e.javaClass.simpleName) }), failed = result.isFailure))
            }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val o = org.json.JSONObject(app.contentResolver.openInputStream(source)!!.use { it.readBytes().decodeToString() })
                    require(o.optString("app") == "fwip") { str(R.string.backup_not_fwip) }
                    o.optJSONObject("settings")?.let { io.github.christiandonatti.fwip.reader.ReaderSettings(app).fromJson(it) }
                    val known = _state.value.servers.map { it.id }.toSet()
                    val added = mutableListOf<io.github.christiandonatti.fwip.remote.Server>()
                    o.optJSONArray("servers")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val s = arr.getJSONObject(i)
                            if (s.getString("id") in known) continue
                            added += io.github.christiandonatti.fwip.remote.Server(
                                id = s.getString("id"),
                                name = s.optString("name"),
                                kind = runCatching { io.github.christiandonatti.fwip.remote.ServerKind.valueOf(s.getString("kind")) }
                                    .getOrDefault(io.github.christiandonatti.fwip.remote.ServerKind.Opds),
                                url = s.getString("url"),
                                user = s.optString("user"),
                            )
                        }
                    }
                    val servers = _state.value.servers + added
                    if (added.isNotEmpty()) serverStore.save(servers)
                    val ids = servers.map { it.id }.toSet()
                    o.optJSONArray("netFolders")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val uri = Uri.parse(arr.getString(i))
                            if (io.github.christiandonatti.fwip.remote.NetUris.serverId(uri) in ids) repository.addFolder(uri)
                        }
                    }
                    val state = io.github.christiandonatti.fwip.data.SyncState.fromJson(o.getJSONObject("state"))
                    sync.merge(state, _state.value.items.map { it.comic })
                    servers to added.any { it.password.isEmpty() && it.user.isNotEmpty() }
                }
            }
            result.onSuccess { (servers, needPasswords) ->
                _state.update {
                    it.copy(
                        servers = servers,
                        folders = repository.folders(),
                        sync = it.sync.copy(message = str(if (needPasswords) R.string.backup_restored_passwords else R.string.backup_restored), failed = false),
                    )
                }
                reloadReadingState()
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(sync = it.sync.copy(message = str(R.string.backup_failed, e.message ?: e.javaClass.simpleName), failed = true)) }
            }
        }
    }

    fun saveProgress(comic: Comic, page: Int, pageCount: Int) {
        val before = repository.progressFor(comic.id)
        val justFinished = pageCount > 0 && page >= pageCount - 1 && before?.finished != true
        stats.turned(comic.id, page, justFinished)
        repository.setProgress(comic.id, page, pageCount)
        scheduleSync(8000)
        comic.remote?.let { book ->
            syncJob?.cancel()
            // Wait a moment so flipping quickly through pages sends a single update.
            syncJob = viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                io.github.christiandonatti.fwip.remote.RemoteComics.syncProgress(book, page, pageCount)
            }
        }
    }

    private var syncJob: Job? = null

    // ---------- Servers ----------

    fun saveServer(server: io.github.christiandonatti.fwip.remote.Server) {
        io.github.christiandonatti.fwip.remote.NetClients.forget(server.id)
        val list = _state.value.servers.filterNot { it.id == server.id } + server
        serverStore.save(list)
        _state.update { it.copy(servers = list) }
    }

    fun deleteServer(id: String) {
        val list = _state.value.servers.filterNot { it.id == id }
        serverStore.save(list)
        io.github.christiandonatti.fwip.remote.NetClients.forget(id)
        // Its network folders go too, or the library would keep looking for them.
        val gone = repository.folders().filter {
            io.github.christiandonatti.fwip.remote.NetUris.isNet(it) && io.github.christiandonatti.fwip.remote.NetUris.serverId(it) == id
        }
        gone.forEach(repository::removeFolder)
        _state.update { it.copy(servers = list, folders = repository.folders()) }
        if (gone.isNotEmpty()) refresh()
    }

    fun addNetFolder(server: io.github.christiandonatti.fwip.remote.Server, path: String) {
        addFolder(io.github.christiandonatti.fwip.remote.NetUris.of(server.id, path))
    }

    /** Opens a comic that is not part of the library (streamed, or just downloaded). */
    fun openOutside(comic: Comic) {
        _state.update { it.copy(outsideComic = comic, openComicId = null) }
    }

    /** After a download: show it in the library and open it. */
    fun openDownloaded(file: java.io.File) {
        val comic = repository.localComic(file) ?: return
        openOutside(comic)
        refresh()
    }

    private fun refreshProgress() {
        _state.update { s -> s.copy(items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) }) }
    }

    /** Puts comics on the "Want to read" list, or takes them off. */
    fun setWant(ids: List<String>, on: Boolean) {
        ids.forEach { repository.setWant(it, on) }
        refreshItems(ids)
        scheduleSync()
    }

    // ---------- Deleting and moving files ----------

    private data class PendingMove(val oldId: String, val newName: String, val size: Long, val targetPrefix: String)
    private val pendingMoves = mutableListOf<PendingMove>()

    fun dismissFileMessage() = _state.update { it.copy(fileMessage = null) }

    /** Saves an error report (device, version, settings, last crash, recent log) to [target]. */
    fun saveReport(target: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val s = _state.value
                    val ui = io.github.christiandonatti.fwip.ui.UiSettings.get(app)
                    val extra = buildString {
                        appendLine("Comics: ${s.items.size} " + s.items.groupingBy { it.comic.format.label }.eachCount())
                        appendLine("Folders: ${s.folders.size} (network ${s.folders.count(io.github.christiandonatti.fwip.remote.NetUris::isNet)})")
                        appendLine("Servers: " + s.servers.map { it.kind.name })
                        appendLine("Collections: ${s.collections.size}")
                        appendLine("Sync folder: " + if (s.sync.place != null) "yes" else "no")
                        appendLine("Look: ${ui.preset} accent ${ui.accentMode} #${ui.accentIndex}, font ${ui.titleFont}, backdrop ${ui.backdrop}, carousel ${ui.carousel}, icon ${ui.appIcon}, covers x${ui.coverScale}")
                        appendLine("Reader settings: " + io.github.christiandonatti.fwip.reader.ReaderSettings(app).toJson())
                    }
                    io.github.christiandonatti.fwip.data.Reports.save(app, target, io.github.christiandonatti.fwip.data.Reports.build(app, extra))
                    io.github.christiandonatti.fwip.data.Reports.markSeen(app)
                }
            }
            _state.update {
                it.copy(fileMessage = result.fold({ str(R.string.report_saved) }, { e -> str(R.string.report_failed, e.message ?: e.javaClass.simpleName) }))
            }
        }
    }

    /** The app's own downloads folder, a place comics can be moved to as well. */
    val downloadsUri: Uri get() = Uri.fromFile(io.github.christiandonatti.fwip.remote.RemoteComics.downloadsDir(getApplication()))

    /** Copies comics from the NAS into this device's downloads, to read offline. The NAS copy stays. */
    fun downloadComics(ids: List<String>) {
        val comics = ids.mapNotNull { _state.value.item(it)?.comic }
            .filter { io.github.christiandonatti.fwip.remote.NetUris.isNet(it.uri) }
            .distinctBy { it.id }
        if (comics.isEmpty() || _state.value.fileWork != null) return
        viewModelScope.launch {
            var got = 0
            var error: String? = null
            for ((i, comic) in comics.withIndex()) {
                val label = str(R.string.downloading_named, comic.fileName)
                _state.update { it.copy(fileWork = FileWork(label, i / comics.size.toFloat())) }
                runCatching {
                    io.github.christiandonatti.fwip.remote.NetComics.download(getApplication(), comic) { p ->
                        _state.update { it.copy(fileWork = FileWork(label, (i + p) / comics.size)) }
                    }
                }.onSuccess { got++ }.onFailure { error = io.github.christiandonatti.fwip.ui.errorText(it) }
            }
            _state.update {
                it.copy(
                    fileWork = null,
                    fileMessage = error?.let { e -> str(R.string.download_some_failed, got, comics.size, e) }
                        ?: if (got == 1) str(R.string.downloaded_one) else str(R.string.downloaded_n, got),
                )
            }
            // The downloaded copies take the NAS ones' place, with the same progress.
            refresh()
        }
    }

    /** Deletes the files themselves (from the device or the NAS), not just from the library. */
    fun deleteComics(ids: List<String>) {
        val comics = ids.mapNotNull { _state.value.item(it)?.comic }
        if (comics.isEmpty() || _state.value.fileWork != null) return
        viewModelScope.launch {
            val gone = mutableSetOf<String>()
            var error: String? = null
            comics.forEachIndexed { i, comic ->
                _state.update { it.copy(fileWork = FileWork(str(R.string.deleting, comic.fileName), i / comics.size.toFloat())) }
                runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.christiandonatti.fwip.data.FileOps.delete(getApplication(), comic) } }
                    .onSuccess { gone += comic.id }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }
            }
            repository.forgetComics(gone)
            // A comic deleted here but still on the NAS: the NAS copy takes its place, collections included.
            val deleted = comics.filter { it.id in gone }
            val rest = (twins.visible + twins.hidden).filterNot { it.id in gone }
            for (d in deleted) {
                rest.firstOrNull { it.syncKey() == d.syncKey() }?.let { repository.migrate(d.id, it.id) }
            }
            twins = io.github.christiandonatti.fwip.data.Twins.split(rest)
            _state.update { s ->
                s.copy(
                    items = twins.visible.map(::itemFor),
                    aliases = twins.aliases,
                    collections = repository.collections(),
                    fileWork = null,
                    fileMessage = error?.let { str(R.string.delete_failed, gone.size, comics.size, it) }
                        ?: if (gone.size == 1) str(R.string.deleted_one) else str(R.string.deleted_n, gone.size),
                )
            }
            refreshHomeScreen()
        }
    }

    /** Moves comics into another library folder (or the app's downloads), keeping their progress. */
    fun moveComics(ids: List<String>, target: Uri) {
        val comics = ids.mapNotNull { _state.value.item(it)?.comic }
        if (comics.isEmpty() || _state.value.fileWork != null) return
        viewModelScope.launch {
            var moved = 0
            var error: String? = null
            for ((i, comic) in comics.withIndex()) {
                val label = str(R.string.moving, comic.fileName)
                _state.update { it.copy(fileWork = FileWork(label, i / comics.size.toFloat())) }
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        io.github.christiandonatti.fwip.data.FileOps.move(getApplication(), comic, target) { p ->
                            _state.update { it.copy(fileWork = FileWork(label, (i + p) / comics.size)) }
                        }
                    }
                }.onSuccess { name ->
                    moved++
                    synchronized(pendingMoves) {
                        pendingMoves += PendingMove(comic.id, name, comic.size, if (target.scheme == "file") "file:" else target.toString().trimEnd('/'))
                    }
                }.onFailure { error = it.message ?: it.javaClass.simpleName }
            }
            _state.update {
                it.copy(
                    fileWork = null,
                    fileMessage = error?.let { e -> str(R.string.move_failed, moved, comics.size, e) }
                        ?: if (moved == 1) str(R.string.moved_one) else str(R.string.moved_n, moved),
                )
            }
            refresh()
        }
    }

    fun setRead(ids: List<String>, read: Boolean) {
        ids.forEach { repository.setRead(it, read) }
        refreshProgress()
        scheduleSync()
    }

    private fun updateCollections(change: (List<ComicCollection>) -> List<ComicCollection>) {
        val before = _state.value.collections
        val now = System.currentTimeMillis()
        val old = before.associateBy { it.id }
        val next = change(before).map { c -> if (old[c.id] == c) c else c.copy(updated = now) }
        val kept = next.map { it.id }.toSet()
        repository.markCollectionsDeleted(before.filter { it.id !in kept }.associate { it.id to now })
        repository.saveCollections(next)
        _state.update { it.copy(collections = next) }
        scheduleSync()
    }

    fun createCollection(name: String, comicIds: List<String> = emptyList()): String {
        val id = java.util.UUID.randomUUID().toString()
        updateCollections { it + ComicCollection(id, name.trim().ifBlank { str(R.string.new_collection) }, comicIds.distinct()) }
        return id
    }

    fun renameCollection(id: String, name: String) =
        updateCollections { list -> list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it } }

    fun deleteCollection(id: String) = updateCollections { list -> list.filterNot { it.id == id } }

    fun setInCollection(collectionId: String, comicIds: List<String>, inside: Boolean) =
        updateCollections { list ->
            list.map { c ->
                if (c.id != collectionId) c
                else if (inside) c.copy(comicIds = (c.comicIds + comicIds).distinct())
                else {
                    // The collection may hold the NAS copy of a comic shown from the tablet.
                    val out = comicIds.toSet()
                    val aliases = _state.value.aliases
                    c.copy(comicIds = c.comicIds.filterNot { it in out || aliases[it] in out })
                }
            }
        }
}
