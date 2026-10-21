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

data class LibraryState(
    val items: List<LibraryItem> = emptyList(),
    val folders: List<Uri> = emptyList(),
    val scanning: Boolean = false,
    val loaded: Boolean = false,
    val openComicId: String? = null,
    val collections: List<ComicCollection> = emptyList(),
    val servers: List<io.github.christiandonatti.fwip.remote.Server> = emptyList(),
    /** A book opened straight from a server or a fresh download, not (yet) in the library. */
    val outsideComic: Comic? = null,
    val sync: SyncInfo = SyncInfo(),
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

    fun item(id: String): LibraryItem? = items.firstOrNull { it.comic.id == id }

    /** What to read after [item]: the next issue of its series, or else the next file in its folder. */
    fun nextAfter(item: LibraryItem): LibraryItem? {
        val key = seriesKey(item.series.name)
        val series = items.filter { seriesKey(it.series.name) == key }.sortedFor(SortOrder.Title)
        if (series.size >= 2) {
            return series.getOrNull(series.indexOfFirst { it.comic.id == item.comic.id } + 1)
        }
        val folder = items.filter { it.comic.folder == item.comic.folder }
            .sortedWith(compareBy(io.github.christiandonatti.fwip.data.NaturalOrder) { it.comic.fileName })
        return folder.getOrNull(folder.indexOfFirst { it.comic.id == item.comic.id } + 1)
    }

    /** Series with at least two issues, biggest first. */
    val series: List<Pair<Shelf.Series, List<LibraryItem>>>
        get() = items.groupBy { seriesKey(it.series.name) }
            .filter { it.value.size >= 2 }
            .map { (key, list) -> Shelf.Series(key, list.first().series.name) to list.sortedFor(SortOrder.Title) }
            .sortedByDescending { (_, list) -> list.maxOf { it.progress?.lastRead ?: 0L } }

    val folderShelves: List<Pair<Shelf.Folder, List<LibraryItem>>>
        get() = items.groupBy { it.comic.folder }
            .map { (name, list) -> Shelf.Folder(name) to list.sortedFor(SortOrder.Title) }
            .sortedBy { it.first.title.lowercase() }

    fun itemsOf(shelf: Shelf, order: SortOrder = SortOrder.Title): List<LibraryItem> = when (shelf) {
        is Shelf.Series -> items.filter { seriesKey(it.series.name) == shelf.key }.sortedFor(SortOrder.Title)
        is Shelf.Folder -> items.filter { it.comic.folder == shelf.name }.sortedFor(order)
        is Shelf.Custom -> collections.firstOrNull { it.id == shelf.id }?.comicIds.orEmpty().mapNotNull(::item)
        is Shelf.Smart -> when (shelf.kind) {
            SmartKind.Recent -> items.sortedFor(SortOrder.Added).take(30)
            SmartKind.Reading -> continueReading
            else -> items.filter { it.matches(shelf.kind) }.sortedFor(order)
        }
    }
}

class FwipViewModel(app: Application) : AndroidViewModel(app) {

    val repository = LibraryRepository(app)
    private val serverStore = io.github.christiandonatti.fwip.remote.ServerStore(app)
    private val sync = io.github.christiandonatti.fwip.data.SyncManager(app, repository)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        val cached = repository.cachedComics()
        _state.value = LibraryState(
            items = cached.map(::itemFor),
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
    )

    fun addFolder(uri: Uri) {
        repository.addFolder(uri)
        _state.update { it.copy(folders = repository.folders()) }
        refresh()
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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sync.afterScan(comics) }
            _state.update { it.copy(items = comics.map(::itemFor), collections = repository.collections()) }
            scheduleSync(0)
            // Covers and metadata are read one comic at a time so the shelves fill in while you watch.
            for (comic in comics) {
                if (repository.isAnalyzed(comic)) continue
                repository.analyze(comic)
                val fresh = itemFor(comic)
                _state.update { s -> s.copy(items = s.items.map { if (it.comic.id == comic.id) fresh else it }) }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun open(comic: Comic) {
        _state.update { it.copy(openComicId = comic.id) }
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
                outsideComic = null,
                items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) },
            )
        }
        scheduleSync(0)
    }

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
                result.onSuccess { changed -> if (changed) reloadReadingState() }
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

    /** Progress, collections and bookmarks may have changed underneath (sync, restore). */
    private fun reloadReadingState() {
        _state.update { s ->
            s.copy(
                items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) },
                collections = repository.collections(),
            )
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
                else c.copy(comicIds = c.comicIds - comicIds.toSet())
            }
        }
}
