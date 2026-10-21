package io.github.christiandonatti.fwip

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.christiandonatti.fwip.reader.ReaderKeys
import io.github.christiandonatti.fwip.reader.ReaderScreen
import androidx.compose.runtime.remember
import io.github.christiandonatti.fwip.remote.RemoteComics
import io.github.christiandonatti.fwip.ui.LibraryActions
import io.github.christiandonatti.fwip.ui.ServerActions
import io.github.christiandonatti.fwip.ui.LibraryScreen
import io.github.christiandonatti.fwip.ui.theme.FwipTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FwipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenIntent(intent)

        setContent {
            FwipTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) viewModel.addFolder(uri)
                }
                val pickSyncFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) viewModel.setSyncPlace(uri)
                }
                val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) viewModel.exportBackup(uri)
                }
                val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.importBackup(uri)
                }
                val syncActions = remember(viewModel) {
                    io.github.christiandonatti.fwip.ui.SyncActions(
                        pickLocal = { pickSyncFolder.launch(null) },
                        useNetwork = { viewModel.setSyncPlace(it) },
                        stop = { viewModel.setSyncPlace(null) },
                        syncNow = { viewModel.syncNow(manual = true) },
                        saveBackup = {
                            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                            saveBackup.launch("fwip-backup-$day.json")
                        },
                        restoreBackup = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    )
                }

                val actions = remember(viewModel) {
                    LibraryActions(
                        open = { viewModel.open(it.comic) },
                        addFolder = { pickFolder.launch(null) },
                        removeFolder = viewModel::removeFolder,
                        refresh = viewModel::refresh,
                        setRead = viewModel::setRead,
                        createCollection = viewModel::createCollection,
                        renameCollection = viewModel::renameCollection,
                        deleteCollection = viewModel::deleteCollection,
                        setInCollection = viewModel::setInCollection,
                    )
                }

                val serverActions = remember(viewModel) {
                    ServerActions(
                        save = viewModel::saveServer,
                        delete = viewModel::deleteServer,
                        openRemote = { book, title -> viewModel.openOutside(RemoteComics.comic(book, title)) },
                        openDownloaded = viewModel::openDownloaded,
                        addNetFolder = viewModel::addNetFolder,
                        openNet = viewModel::openOutside,
                    )
                }

                AnimatedContent(
                    targetState = state.readerComic,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.02f))
                    },
                    contentKey = { it?.id },
                    label = "screen",
                ) { comic ->
                    if (comic == null) {
                        LibraryScreen(
                            state = state,
                            actions = actions,
                            serverActions = serverActions,
                            syncActions = syncActions,
                        )
                    } else {
                        val progress = viewModel.repository.progressFor(comic.id)
                        val start = when {
                            progress != null && !progress.finished -> progress.page
                            progress == null && comic.remote != null -> comic.remote.startPage
                            else -> 0
                        }
                        val item = state.item(comic.id)
                        val next = item?.let(state::nextAfter)
                        ReaderScreen(
                            comic = comic,
                            title = item?.title ?: comic.title,
                            meta = item?.meta,
                            next = next,
                            onOpenNext = { next?.let { viewModel.switchTo(it.comic) } },
                            startPage = start,
                            onClose = viewModel::closeReader,
                            onProgress = { page, count -> viewModel.saveProgress(comic, page, count) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /** A home screen shortcut or the widget asked for a specific comic. */
    private fun handleOpenIntent(intent: android.content.Intent?) {
        if (intent?.action != io.github.christiandonatti.fwip.widget.ACTION_OPEN_COMIC) return
        intent.getStringExtra(io.github.christiandonatti.fwip.widget.EXTRA_COMIC)?.let(viewModel::openById)
        intent.action = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderKeys.active && (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                ReaderKeys.events.tryEmit(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
