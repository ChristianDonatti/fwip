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
import io.github.christiandonatti.fwip.ui.LibraryActions
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

        setContent {
            FwipTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) viewModel.addFolder(uri)
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

                AnimatedContent(
                    targetState = state.openComic,
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
                        )
                    } else {
                        val progress = viewModel.repository.progressFor(comic.id)
                        val start = if (progress == null || progress.finished) 0 else progress.page
                        val item = state.item(comic.id)
                        ReaderScreen(
                            comic = comic,
                            title = item?.title ?: comic.title,
                            meta = item?.meta,
                            startPage = start,
                            onClose = viewModel::closeReader,
                            onProgress = { page, count -> viewModel.saveProgress(comic, page, count) },
                        )
                    }
                }
            }
        }
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
