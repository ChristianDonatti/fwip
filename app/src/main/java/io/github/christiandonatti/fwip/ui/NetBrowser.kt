@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.data.ComicFormat
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetComics
import io.github.christiandonatti.fwip.remote.NetEntry
import io.github.christiandonatti.fwip.remote.NetUris
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browsing an SMB share or WebDAV folder: open subfolders, read a comic straight away,
 * copy it for offline reading, or add the folder to the library.
 */
@Composable
fun NetBrowser(
    server: Server,
    folders: List<Uri>,
    actions: ServerActions,
    wide: Boolean,
    gutter: Dp,
    onClose: () -> Unit,
) {
    var path by remember(server.id) { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NetEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<NetEntry?>(null) }
    var download by remember { mutableStateOf<Pair<NetEntry, Float>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(server, path, reload) {
        entries = null
        error = null
        runCatching { withContext(Dispatchers.IO) { NetClients.forServer(server).list(path) } }
            .onSuccess { list ->
                entries = list.filter { it.isDir || ComicFormat.fromFileName(it.name) != null }
                    .sortedWith(compareBy<NetEntry> { !it.isDir }.thenBy(io.github.christiandonatti.fwip.data.NaturalOrder) { it.name })
            }
            .onFailure { error = errorText(it) }
    }

    fun back() {
        if (path.isNotEmpty()) path = path.substringBeforeLast('/', "") else onClose()
    }
    BackHandler(enabled = menu == null && download == null) { back() }

    // This folder is in the library when it, or a folder above it, was added.
    val here = NetUris.of(server.id, path).toString().trimEnd('/')
    val inLibrary = folders.any { f ->
        val root = f.toString().trimEnd('/')
        NetUris.isNet(f) && (here == root || here.startsWith("$root/"))
    }
    val folderName = path.substringAfterLast('/').ifEmpty { server.name }

    fun comicFor(e: NetEntry) = NetComics.comic(server.id, e, e.path.substringBeforeLast('/', "").substringAfterLast('/'))

    fun startDownload(e: NetEntry) {
        val comic = comicFor(e) ?: return
        menu = null
        download = e to 0f
        scope.launch {
            runCatching { NetComics.download(context, comic) { p -> download = e to p } }
                .onSuccess { file -> download = null; actions.openDownloaded(file) }
                .onFailure { download = null; error = str(R.string.download_failed, errorText(it)) }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = gutter, end = gutter, top = if (wide) 24.dp else 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), { back() }, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(server.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft, maxLines = 1)
                        Text(
                            folderName,
                            style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (inLibrary) {
                    PillButton(
                        str(R.string.folder_in_library),
                        {},
                        style = PillStyle.Ghost,
                        height = 46.dp,
                        enabled = false,
                        leading = { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
                    )
                } else {
                    PillButton(
                        str(R.string.add_folder_to_library),
                        { actions.addNetFolder(server, path) },
                        style = PillStyle.Amber,
                        height = 46.dp,
                        leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
                Text(str(R.string.net_browser_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }
        }

        val list = entries
        when {
            error != null && list == null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 24.dp)) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
                    PillButton(str(R.string.try_again), { reload++ }, style = PillStyle.Ghost, height = 46.dp)
                }
            }
            list == null -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FwipColors.Text)
                }
            }
            list.isEmpty() -> item {
                Text(str(R.string.nothing_here), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
            else -> {
                error?.let { msg -> item { Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80)) } }
                items(list, key = { it.path }) { e ->
                    NetRow(
                        e,
                        onClick = {
                            if (e.isDir) path = e.path
                            else comicFor(e)?.let(actions.openNet)
                        },
                        onLongClick = { if (!e.isDir) menu = e },
                    )
                }
            }
        }
    }

    menu?.let { e ->
        FwipDialog({ menu = null }) {
            DialogTitle(io.github.christiandonatti.fwip.data.prettyTitle(e.name), { menu = null })
            Text(fileSizeLabel(e.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            SheetButton(str(R.string.read_now), { menu = null; comicFor(e)?.let(actions.openNet) }, leading = { Icon(Icons.Filled.PlayArrow, null) })
            SheetButton(str(R.string.download_offline), { startDownload(e) }, leading = { Icon(Icons.Filled.Add, null) })
        }
    }

    download?.let { (e, progress) ->
        FwipDialog({}) {
            Text(str(R.string.downloading), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
            Text(io.github.christiandonatti.fwip.data.prettyTitle(e.name), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressBar(progress, FwipColors.Amber, Modifier.fillMaxWidth())
            Text(
                if (progress > 0f) "${(progress * 100).toInt()}%" else str(R.string.starting),
                style = MaterialTheme.typography.bodySmall,
                color = FwipColors.TextMuted,
            )
        }
    }
}

@Composable
private fun NetRow(e: NetEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
            .combinedClickable(role = Role.Button, onClickLabel = e.name, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (e.isDir) {
            FolderIcon(FwipColors.TextSoft)
        } else {
            Box(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FwipColors.Amber.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(ComicFormat.fromFileName(e.name)?.label.orEmpty(), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (e.isDir) e.name else io.github.christiandonatti.fwip.data.prettyTitle(e.name),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!e.isDir) Text(fileSizeLabel(e.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
        if (e.isDir) Icon(Icons.Filled.KeyboardArrowRight, null, tint = FwipColors.TextMuted)
    }
}
