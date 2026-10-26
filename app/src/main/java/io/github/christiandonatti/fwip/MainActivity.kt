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
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.christiandonatti.fwip.reader.ReaderKeys
import io.github.christiandonatti.fwip.reader.ReaderScreen
import androidx.compose.runtime.remember
import io.github.christiandonatti.fwip.remote.RemoteComics
import io.github.christiandonatti.fwip.ui.LibraryActions
import io.github.christiandonatti.fwip.ui.ServerActions
import io.github.christiandonatti.fwip.ui.LibraryScreen
import io.github.christiandonatti.fwip.ui.theme.FwipTheme
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.UiSettings
import io.github.christiandonatti.fwip.ui.CoverZoom
import io.github.christiandonatti.fwip.ui.CoverZoomOverlay
import io.github.christiandonatti.fwip.ui.CoverPositions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private val viewModel: FwipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenIntent(intent)
        val ui = UiSettings.get(this)
        ui.apply()

        setContent {
            // Every setting that changes colours or fonts re-applies the look.
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val look = listOf(ui.preset, ui.accentMode, ui.accentIndex, ui.titleFont, ui.coverTint, systemDark)
            LaunchedEffect(look) { ui.apply(systemDark) }
            FwipTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val readerOpen = state.readerComic != null
                // Light library: the lights go down while the reader opens, and back up after.
                LaunchedEffect(readerOpen) {
                    androidx.compose.animation.core.animate(FwipColors.dim, if (readerOpen) 1f else 0f, animationSpec = tween(360)) { v, _ ->
                        FwipColors.dim = v
                    }
                }
                val lightBars = FwipColors.chosen.light && !readerOpen
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    bars.isAppearanceLightStatusBars = lightBars
                    bars.isAppearanceLightNavigationBars = lightBars
                }
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
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted) android.widget.Toast.makeText(this, getString(R.string.alerts_denied), android.widget.Toast.LENGTH_LONG).show()
                }
                val openReadingList = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) viewModel.importReadingList(uri)
                }
                val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri != null) viewModel.saveReport(uri)
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val lookScope = androidx.compose.runtime.rememberCoroutineScope()
                var shelfArtFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, Boolean>?>(null) }
                val pickShelfArt = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    val target = shelfArtFor
                    shelfArtFor = null
                    if (uri != null && target != null) lookScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            io.github.christiandonatti.fwip.ui.ShelfLooks.setImage(context, target.first, uri, target.second)
                        }
                    }
                }
                val pickBackdrop = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) lookScope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.christiandonatti.fwip.ui.Backdrops.set(context, uri) }
                        if (ok) {
                            ui.updateBackdrop(io.github.christiandonatti.fwip.ui.Backdrop.Image)
                            ui.backdropChanged()
                        }
                    }
                }
                // Quietly look for a newer Fwip (at most twice a day).
                LaunchedEffect(Unit) { io.github.christiandonatti.fwip.data.Updates.checkInBackground(context) }
                // The app closed by itself last time: offer to save a report.
                var crashed by remember { mutableStateOf(io.github.christiandonatti.fwip.data.Reports.unseenCrash(context)) }
                if (crashed) {
                    io.github.christiandonatti.fwip.ui.CrashDialog(
                        onSave = { saveReport.launch(io.github.christiandonatti.fwip.data.Reports.fileName()) },
                        onDismiss = {
                            io.github.christiandonatti.fwip.data.Reports.markSeen(context)
                            crashed = false
                        },
                    )
                }
                val metaActions = remember(viewModel) {
                    io.github.christiandonatti.fwip.ui.MetaActions(
                        saveManual = viewModel::saveManual,
                        renameSeries = viewModel::renameSeries,
                        clear = viewModel::clearEdited,
                        coverFromPage = viewModel::coverFromPage,
                        coverFromImage = viewModel::coverFromImage,
                        resetCover = viewModel::resetCover,
                    )
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
                        uploadTo = { io.github.christiandonatti.fwip.data.NasUpload.setTarget(this, it) },
                        uploadNow = { io.github.christiandonatti.fwip.data.NasUpload.schedule(this) },
                        uploadWifi = { io.github.christiandonatti.fwip.data.NasUpload.setWifiOnly(this, it) },
                    )
                }

                val actions = remember(viewModel) {
                    LibraryActions(
                        open = { viewModel.open(it.comic) },
                        openIn = { item, context -> viewModel.open(item.comic, context) },
                        reorderCollection = viewModel::reorderCollection,
                        stats = { viewModel.stats.all() },
                        deleteComics = viewModel::deleteComics,
                        moveComics = viewModel::moveComics,
                        downloadComics = viewModel::downloadComics,
                        downloadsUri = { viewModel.downloadsUri },
                        dismissFileMessage = viewModel::dismissFileMessage,
                        saveReport = { saveReport.launch(io.github.christiandonatti.fwip.data.Reports.fileName()) },
                        pickBackdrop = { pickBackdrop.launch("image/*") },
                        importReadingList = { openReadingList.launch(arrayOf("*/*")) },
                        pickShelfArt = { key, logo -> shelfArtFor = key to logo; pickShelfArt.launch("image/*") },
                        screenShown = viewModel::screenShown,
                        setAlerts = { on ->
                            io.github.christiandonatti.fwip.data.NewIssues.setEnabled(this, on)
                            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                        createFromReadingList = viewModel::createFromReadingList,
                        dismissReadingList = viewModel::dismissReadingList,
                        addFolder = { pickFolder.launch(null) },
                        removeFolder = viewModel::removeFolder,
                        refresh = viewModel::refresh,
                        setRead = viewModel::setRead,
                        createCollection = viewModel::createCollection,
                        renameCollection = viewModel::renameCollection,
                        deleteCollection = viewModel::deleteCollection,
                        setInCollection = viewModel::setInCollection,
                        setWant = viewModel::setWant,
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

                // The cover that grows into the reader (and shrinks back on closing).
                var zoom by remember { mutableStateOf<CoverZoom?>(null) }
                val opened = remember { arrayOfNulls<String>(1) }
                val shelfRect = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
                val readerId = state.readerComic?.id
                if (opened[0] != readerId) {
                    val previous = opened[0]
                    opened[0] = readerId
                    zoom = when {
                        !ui.coverAnimation -> null
                        previous == null && readerId != null -> {
                            val rect = CoverPositions.of(readerId)
                            shelfRect[0] = rect
                            val cover = state.item(readerId)?.coverPath
                            if (rect != null && cover != null) CoverZoom(readerId, cover, rect, opening = true) else null
                        }
                        previous != null && readerId == null -> {
                            val rect = shelfRect[0]
                            val cover = state.item(previous)?.coverPath
                            if (rect != null && cover != null) CoverZoom(previous, cover, rect, opening = false) else null
                        }
                        else -> {
                            // Moved on to the next issue: it was never on screen.
                            shelfRect[0] = null
                            null
                        }
                    }
                }
                val growing = zoom != null
                val libraryState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

                AnimatedContent(
                    targetState = state.readerComic,
                    transitionSpec = {
                        if (growing) {
                            // The flying cover carries the eye; the screens just cross-fade behind it.
                            if (targetState != null) {
                                fadeIn(tween(260, delayMillis = 200)) togetherWith fadeOut(tween(300))
                            } else {
                                fadeIn(tween(260)) togetherWith fadeOut(tween(220, delayMillis = 60))
                            }
                        } else {
                            (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.02f))
                        }
                    },
                    contentKey = { it?.id },
                    label = "screen",
                ) { comic ->
                    if (comic == null) {
                        // Tab, open series and scroll position are still there after reading.
                        libraryState.SaveableStateProvider("library") {
                            LibraryScreen(
                                state = state,
                                actions = actions,
                                serverActions = serverActions,
                                syncActions = syncActions,
                                metaActions = metaActions,
                            )
                        }
                    } else {
                        val progress = viewModel.repository.progressFor(comic.id)
                        val start = when {
                            progress != null && !progress.finished -> progress.page
                            progress == null && comic.remote != null -> comic.remote.startPage
                            else -> 0
                        }
                        val item = state.item(comic.id)
                        val next = item?.let { state.nextAfter(it, state.openContext) }
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
                zoom?.let { z -> CoverZoomOverlay(z, onDone = { if (zoom === z) zoom = null }) }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /** A home screen shortcut or the widget asked for a specific comic. */
    private fun handleOpenIntent(intent: android.content.Intent?) {
        when (intent?.action) {
            io.github.christiandonatti.fwip.widget.ACTION_OPEN_COMIC -> intent.getStringExtra(io.github.christiandonatti.fwip.widget.EXTRA_COMIC)?.let {
                viewModel.openById(it, intent.getStringExtra(io.github.christiandonatti.fwip.widget.EXTRA_CONTEXT))
            }
            io.github.christiandonatti.fwip.widget.ACTION_RANDOM -> viewModel.openRandom()
            io.github.christiandonatti.fwip.widget.ACTION_SHOW -> viewModel.requestScreen(intent.getStringExtra(io.github.christiandonatti.fwip.widget.EXTRA_SCREEN))
            else -> return
        }
        intent.action = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
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
