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
            _state.update { it.copy(items = comics.map(::itemFor)) }
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
    }

    fun saveProgress(comic: Comic, page: Int, pageCount: Int) {
        repository.setProgress(comic.id, page, pageCount)
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
        val list = _state.value.servers.filterNot { it.id == server.id } + server
        serverStore.save(list)
        _state.update { it.copy(servers = list) }
    }

    fun deleteServer(id: String) {
        val list = _state.value.servers.filterNot { it.id == id }
        serverStore.save(list)
        _state.update { it.copy(servers = list) }
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
    }

    private fun updateCollections(change: (List<ComicCollection>) -> List<ComicCollection>) {
        val next = change(_state.value.collections)
        repository.saveCollections(next)
        _state.update { it.copy(collections = next) }
    }

    fun createCollection(name: String, comicIds: List<String> = emptyList()): String {
        val id = java.util.UUID.randomUUID().toString()
        updateCollections { it + ComicCollection(id, name.trim().ifBlank { "Nova coleção" }, comicIds.distinct()) }
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
