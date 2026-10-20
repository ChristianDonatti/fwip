package io.github.christiandonatti.fwip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.christiandonatti.fwip.data.Comic
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
}

class FwipViewModel(app: Application) : AndroidViewModel(app) {

    val repository = LibraryRepository(app)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        val cached = repository.cachedComics()
        _state.value = LibraryState(
            items = cached.map(::itemFor),
            folders = repository.folders(),
            loaded = true,
        )
        refresh()
    }

    private fun itemFor(comic: Comic) = LibraryItem(
        comic = comic,
        coverPath = repository.coverPathIfReady(comic),
        color = repository.colorFor(comic),
        progress = repository.progressFor(comic.id),
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
            // Covers are generated one by one so the carousel fills in while you watch.
            for (comic in comics) {
                if (repository.coverPathIfReady(comic) != null && repository.colorFor(comic) != null) continue
                val (path, color) = repository.ensureCover(comic)
                _state.update { s ->
                    s.copy(items = s.items.map {
                        if (it.comic.id == comic.id) it.copy(coverPath = path, color = color, progress = repository.progressFor(comic.id)) else it
                    })
                }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun open(comic: Comic) {
        _state.update { it.copy(openComicId = comic.id) }
    }

    fun closeReader() {
        _state.update { s ->
            s.copy(
                openComicId = null,
                items = s.items.map { it.copy(progress = repository.progressFor(it.comic.id)) },
            )
        }
    }

    fun saveProgress(comic: Comic, page: Int, pageCount: Int) {
        repository.setProgress(comic.id, page, pageCount)
    }
}
