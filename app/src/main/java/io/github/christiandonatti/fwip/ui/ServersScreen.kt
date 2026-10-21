@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.christiandonatti.fwip.remote.HttpException
import io.github.christiandonatti.fwip.remote.Opds
import io.github.christiandonatti.fwip.remote.OpdsEntry
import io.github.christiandonatti.fwip.remote.OpdsFeed
import io.github.christiandonatti.fwip.remote.RemoteBook
import io.github.christiandonatti.fwip.remote.RemoteComics
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.remote.ServerKind
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.launch
import java.io.File

/** What the server screens can ask the app to do. */
class ServerActions(
    val save: (Server) -> Unit,
    val delete: (String) -> Unit,
    val openRemote: (RemoteBook, String) -> Unit,
    val openDownloaded: (File) -> Unit,
    /** Adds a folder of an SMB/WebDAV server (path relative to the server's folder) to the library. */
    val addNetFolder: (Server, String) -> Unit,
    /** Reads a comic from a network folder without adding it to the library. */
    val openNet: (io.github.christiandonatti.fwip.data.Comic) -> Unit,
)

internal fun errorText(t: Throwable): String = when (t) {
    is com.hierynomus.mssmb2.SMBApiException -> when (t.status) {
        com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE, com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED -> str(R.string.error_login)
        com.hierynomus.mserref.NtStatus.STATUS_BAD_NETWORK_NAME -> str(R.string.error_smb_share)
        com.hierynomus.mserref.NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, com.hierynomus.mserref.NtStatus.STATUS_OBJECT_PATH_NOT_FOUND -> str(R.string.error_net_missing)
        else -> t.status.name
    }
    is HttpException -> t.message ?: str(R.string.error_http, t.code)
    is java.net.UnknownHostException -> str(R.string.error_host)
    is java.net.ConnectException, is java.net.SocketTimeoutException ->
        str(R.string.error_connect)
    is org.xmlpull.v1.XmlPullParserException -> str(R.string.error_not_opds)
    else -> t.message ?: t.javaClass.simpleName
}

/** The Servers tab: saved servers, and a button to add one. */
@Composable
fun ServersTab(
    servers: List<Server>,
    actions: ServerActions,
    wide: Boolean,
    gutter: Dp,
    onBrowse: (Server) -> Unit,
) {
    var editing by remember { mutableStateOf<Server?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 320.dp else 260.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(str(R.string.tab_servers), style = MaterialTheme.typography.headlineSmall)
                Text(
                    str(R.string.servers_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FwipColors.TextMuted,
                )
            }
        }
        items(servers, key = { it.id }) { server ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(FwipColors.Surface)
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClickLabel = server.name) { onBrowse(server) }
                    .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(server.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (server.kind.isFolder) str(R.string.network_folder) + " " else "") + server.kind.label + " · " + server.url.substringAfter("://").substringBefore("/api/opds"),
                        style = MaterialTheme.typography.bodySmall,
                        color = FwipColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleIconButton(Icons.Filled.Edit, str(R.string.edit_server), { editing = server }, size = 40.dp)
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = FwipColors.TextMuted)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PillButton(
                str(R.string.add_server),
                { adding = true },
                style = if (servers.isEmpty()) PillStyle.Solid else PillStyle.Ghost,
                leading = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) },
            )
        }
    }

    if (adding || editing != null) {
        ServerDialog(
            initial = editing,
            onSave = { actions.save(it); adding = false; editing = null },
            onDelete = editing?.let { s -> { actions.delete(s.id); editing = null } },
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FwipColors.TextSoft)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                cursorBrush = SolidColor(FwipColors.Amber),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Add or edit a server; it is tested before being saved. */
@Composable
private fun ServerDialog(initial: Server?, onSave: (Server) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(initial?.kind ?: ServerKind.Komga) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    FwipDialog(onDismiss) {
        DialogTitle(if (initial == null) str(R.string.add_server) else str(R.string.edit_server), onDismiss)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServerKind.entries.forEach { k ->
                val on = k == kind
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (on) FwipColors.Text else FwipColors.Surface)
                        .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
                        .clickable(role = Role.RadioButton) { kind = k; error = null }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(k.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft)
                }
            }
        }
        Text(
            when (kind) {
                ServerKind.Komga -> str(R.string.server_hint_komga)
                ServerKind.Kavita -> str(R.string.server_hint_kavita)
                ServerKind.Opds -> str(R.string.server_hint_opds)
                ServerKind.Smb -> str(R.string.server_hint_smb)
                ServerKind.WebDav -> str(R.string.server_hint_webdav)
            },
            style = MaterialTheme.typography.bodySmall,
            color = FwipColors.TextMuted,
        )
        Field(str(R.string.name), name, { name = it }, str(R.string.server_name_hint))
        Field(
            when (kind) {
                ServerKind.Komga -> str(R.string.address)
                ServerKind.Smb -> str(R.string.smb_address)
                ServerKind.WebDav -> str(R.string.webdav_address)
                else -> "OPDS URL"
            },
            url,
            { url = it.trim(); error = null },
            when (kind) {
                ServerKind.Komga -> "http://192.168.0.10:25600"
                ServerKind.Smb -> "192.168.0.10/Comics"
                ServerKind.WebDav -> "https://nas.local:5006/comics"
                else -> "http://..."
            },
            keyboard = KeyboardType.Uri,
        )
        if (kind != ServerKind.Kavita) {
            Field(if (kind == ServerKind.Komga) "E-mail" else str(R.string.user), user, { user = it.trim(); error = null }, if (kind == ServerKind.Komga) str(R.string.email_hint) else str(R.string.optional), keyboard = KeyboardType.Email)
            Field(str(R.string.password), password, { password = it; error = null }, if (kind == ServerKind.Komga) "" else str(R.string.optional), password = true, keyboard = KeyboardType.Password)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FwipColors.Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                CircleIconButton(Icons.Filled.Delete, str(R.string.remove_server), onDelete, size = 48.dp)
            }
            Spacer(Modifier.weight(1f))
            if (testing) CircularProgressIndicator(Modifier.size(22.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
            PillButton(
                str(R.string.test_and_save),
                {
                    var address = url.trim()
                    if (kind == ServerKind.Smb) {
                        address = "smb://" + address.removePrefix("smb://").removePrefix("SMB://").replace('\\', '/').trim('/')
                    } else if (address.isNotBlank() && !address.contains("://")) {
                        address = "http://$address"
                    }
                    val server = Server(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { kind.label },
                        kind = kind,
                        url = address,
                        user = if (kind == ServerKind.Kavita) "" else user,
                        password = if (kind == ServerKind.Kavita) "" else password,
                    )
                    testing = true
                    error = null
                    scope.launch {
                        runCatching {
                            if (server.kind.isFolder) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    io.github.christiandonatti.fwip.remote.NetClients.forget(server.id)
                                    io.github.christiandonatti.fwip.remote.NetClients.forServer(server).list("")
                                }
                            } else {
                                Opds.feed(server, server.catalogUrl)
                            }
                        }
                            .onSuccess { testing = false; onSave(server) }
                            .onFailure { testing = false; error = errorText(it) }
                    }
                },
                height = 48.dp,
                style = PillStyle.Amber,
                enabled = url.isNotBlank() && !testing,
            )
        }
    }
}

/** Browsing a server's catalog: folders of the catalog, then books to read or download. */
@Composable
fun ServerBrowser(
    server: Server,
    actions: ServerActions,
    wide: Boolean,
    gutter: Dp,
    onClose: () -> Unit,
) {
    var stack by remember(server.id) { mutableStateOf(listOf(server.catalogUrl)) }
    var feed by remember { mutableStateOf<OpdsFeed?>(null) }
    var extra by remember { mutableStateOf<List<OpdsEntry>>(emptyList()) }
    var nextUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var bookMenu by remember { mutableStateOf<OpdsEntry?>(null) }
    var download by remember { mutableStateOf<Pair<OpdsEntry, Float>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = stack.last()

    LaunchedEffect(current, reload) {
        loading = true
        error = null
        extra = emptyList()
        runCatching { Opds.feed(server, current) }
            .onSuccess { feed = it; nextUrl = it.next }
            .onFailure { error = errorText(it) }
        loading = false
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1) else onClose()
    }
    BackHandler(enabled = bookMenu == null && download == null) { back() }

    fun read(entry: OpdsEntry) {
        val stream = entry.stream
        val count = stream?.pseCount
        if (stream != null && count != null && count > 0) {
            val start = ((stream.pseLastRead ?: 0) - 1).coerceIn(0, count - 1)
            actions.openRemote(RemoteBook(server, entry.id, stream.href, count, start), entry.title)
        } else {
            bookMenu = entry
        }
    }

    fun startDownload(entry: OpdsEntry) {
        val link = entry.acquisition ?: return
        bookMenu = null
        download = entry to 0f
        scope.launch {
            runCatching {
                RemoteComics.download(context, server, link.href, RemoteComics.fileNameFor(entry.title, link.href, link.type)) { p ->
                    download = entry to p
                }
            }.onSuccess { file ->
                download = null
                actions.openDownloaded(file)
            }.onFailure {
                download = null
                error = str(R.string.download_failed, errorText(it))
            }
        }
    }

    val entries = feed?.entries.orEmpty() + extra
    val nav = entries.filter { !it.isBook && it.navigation != null }
    val books = entries.filter { it.isBook }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 160.dp else 104.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = if (wide) 24.dp else 12.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), { back() }, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(server.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft, maxLines = 1)
                        Text(
                            feed?.title?.takeIf { stack.size > 1 } ?: server.name,
                            style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (feed?.search != null) {
                        CircleIconButton(if (searching) Icons.Filled.Close else Icons.Filled.Search, str(R.string.search_server), { searching = !searching; query = "" }, size = 44.dp)
                    }
                    if (stack.size > 1) {
                        CircleButton(str(R.string.catalog_start), { stack = listOf(server.catalogUrl) }, size = 44.dp) { FolderIcon(FwipColors.Text) }
                    }
                }
                if (searching) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(FwipColors.Surface)
                            .border(1.dp, FwipColors.Stroke, CircleShape)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(str(R.string.search_server_hint), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                                cursorBrush = SolidColor(FwipColors.Amber),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    val link = feed?.search ?: return@KeyboardActions
                                    if (query.isBlank()) return@KeyboardActions
                                    scope.launch {
                                        val url = Opds.searchUrl(server, link, query.trim())
                                        if (url != null) {
                                            searching = false
                                            stack = stack + url
                                        } else {
                                            error = str(R.string.error_no_search)
                                        }
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        when {
            loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FwipColors.Text)
                }
            }
            error != null && entries.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 24.dp)) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
                    PillButton(str(R.string.try_again), { reload++ }, style = PillStyle.Ghost, height = 46.dp)
                }
            }
            entries.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(str(R.string.nothing_here), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
        }

        if (!loading) {
            error?.takeIf { entries.isNotEmpty() }?.let { msg ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = FwipColors.Danger)
                }
            }
            items(nav, key = { "n:" + it.id + it.navigation?.href }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(FwipColors.Surface)
                        .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { entry.navigation?.let { stack = stack + it.href } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    entry.thumbnail?.let { RemoteCover(server, it, Modifier.size(40.dp, 60.dp), corner = 6.dp) }
                        ?: FolderIcon(FwipColors.TextSoft)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        entry.summary?.takeIf { it.length < 140 }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = FwipColors.TextMuted)
                }
            }
            items(books, key = { "b:" + it.id }) { entry ->
                Column(
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = entry.title,
                        onLongClick = { bookMenu = entry },
                        onClick = { read(entry) },
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemoteCover(
                        server,
                        entry.thumbnail ?: entry.cover,
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(16.dp)),
                        corner = 16.dp,
                    )
                    Text(entry.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    entry.stream?.let { s ->
                        val count = s.pseCount ?: 0
                        val read = s.pseLastRead ?: 0
                        if (count > 0 && read > 0) ProgressBar(read / count.toFloat(), FwipColors.Amber, Modifier.fillMaxWidth())
                    }
                }
            }
            nextUrl?.let { more ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                        } else {
                            PillButton(str(R.string.load_more), {
                                loadingMore = true
                                scope.launch {
                                    runCatching { Opds.feed(server, more) }
                                        .onSuccess { extra = extra + it.entries; nextUrl = it.next }
                                        .onFailure { error = errorText(it) }
                                    loadingMore = false
                                }
                            }, style = PillStyle.Ghost, height = 46.dp)
                        }
                    }
                }
            }
        }
    }

    bookMenu?.let { entry ->
        FwipDialog({ bookMenu = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteCover(server, entry.thumbnail ?: entry.cover, Modifier.size(72.dp, 108.dp), corner = 10.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (entry.authors.isNotEmpty()) {
                        Text(entry.authors.distinct().joinToString(", "), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                CircleIconButton(Icons.Filled.Close, str(R.string.close), { bookMenu = null }, size = 40.dp)
            }
            entry.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            if (entry.stream?.pseCount != null) {
                SheetButton(str(R.string.read_online), { bookMenu = null; read(entry) }, leading = { Icon(Icons.Filled.PlayArrow, null) })
            }
            if (entry.acquisition != null) {
                SheetButton(str(R.string.download_offline), { startDownload(entry) }, leading = { Icon(Icons.Filled.Add, null) })
            }
            if (entry.stream == null && entry.acquisition == null) {
                Text(str(R.string.error_unreadable), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            }
        }
    }

    download?.let { (entry, progress) ->
        FwipDialog({}) {
            Text(str(R.string.downloading), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressBar(progress, FwipColors.Amber, Modifier.fillMaxWidth())
            Text(
                if (progress > 0f) "${(progress * 100).toInt()}%" else str(R.string.starting),
                style = MaterialTheme.typography.bodySmall,
                color = FwipColors.TextMuted,
            )
        }
    }
}

/** A cover image from the server, sent with the server's credentials. */
@Composable
fun RemoteCover(server: Server, url: String?, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val context = LocalContext.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(FwipColors.PanelHigh)
    ) {
        if (url != null) {
            AsyncImage(
                model = remember(url, server.authHeader) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .apply { server.authHeader?.let { addHeader("Authorization", it) } }
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
