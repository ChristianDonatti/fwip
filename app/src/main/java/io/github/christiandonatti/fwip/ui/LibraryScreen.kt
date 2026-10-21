package io.github.christiandonatti.fwip.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.ui.theme.FwipColors

private enum class Tab(val label: String) { Home("Início"), Library("Biblioteca") }

@Composable
fun LibraryScreen(
    state: LibraryState,
    onOpen: (LibraryItem) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFolders by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }

    val row = state.homeRow
    val sel = row.getOrNull(selected.coerceAtMost(row.lastIndex.coerceAtLeast(0)))
    val glow = sel?.glowColor() ?: FwipColors.Glow

    AmbientBackground(color = glow) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val wide = maxWidth >= 840.dp
            val boxHeight = maxHeight
            val gutter = if (wide) 48.dp else 20.dp
            Column(Modifier.fillMaxSize()) {
                Header(
                    wide = wide,
                    tab = tab,
                    onTab = { tab = it; if (it == Tab.Home) folderFilter = null },
                    scanning = state.scanning,
                    onRefresh = onRefresh,
                    onAdd = onAddFolder,
                    onFolders = { showFolders = true },
                    modifier = Modifier.padding(horizontal = gutter, vertical = if (wide) 24.dp else 12.dp),
                )
                when {
                    state.items.isEmpty() -> EmptyState(state.folders.isEmpty(), state.scanning, onAddFolder)
                    tab == Tab.Home && wide -> WideHome(
                        state = state,
                        selected = selected,
                        onSelect = { selected = it },
                        onOpen = onOpen,
                        onFolder = { folderFilter = it; tab = Tab.Library },
                        gutter = gutter,
                        maxHeight = boxHeight,
                    )
                    tab == Tab.Home -> PhoneHome(
                        state = state,
                        onOpen = onOpen,
                        onFolder = { folderFilter = it; tab = Tab.Library },
                        gutter = gutter,
                    )
                    else -> LibraryGrid(
                        items = state.items.filter { folderFilter == null || it.comic.folder == folderFilter },
                        folderFilter = folderFilter,
                        onClearFilter = { folderFilter = null },
                        onOpen = onOpen,
                        wide = wide,
                        gutter = gutter,
                    )
                }
            }
            if (!wide && state.items.isNotEmpty()) {
                PhoneTabBar(tab, onTab = { tab = it; if (it == Tab.Home) folderFilter = null }, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (showFolders) {
        FoldersDialog(
            folders = state.folders,
            onAdd = { showFolders = false; onAddFolder() },
            onRemove = onRemoveFolder,
            onDismiss = { showFolders = false },
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
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        FwipLogo(size = if (wide) 22 else 19)
        Spacer(Modifier.weight(1f))
        if (wide) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(FwipColors.Surface)
                    .border(1.dp, FwipColors.Stroke, CircleShape)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (on) FwipColors.Text else Color.Transparent)
                            .clickable(role = Role.Tab) { onTab(t) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                Box(Modifier.size(if (wide) 46.dp else 44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                }
            } else {
                CircleIconButton(Icons.Filled.Refresh, "Atualizar biblioteca", onRefresh, size = if (wide) 46.dp else 44.dp)
            }
            CircleIconButton(Icons.Filled.Add, "Adicionar pasta", onAdd, size = if (wide) 46.dp else 44.dp)
            CircleIconButton(Icons.Filled.Settings, "Pastas da biblioteca", onFolders, size = if (wide) 46.dp else 44.dp)
        }
    }
}

private fun kicker(item: LibraryItem): String = when {
    item.progress?.finished == true -> "LER DE NOVO"
    item.progress?.started == true -> "CONTINUAR LENDO"
    else -> "COMEÇAR"
}

private fun cta(item: LibraryItem): String = when {
    item.progress?.finished == true -> "Reler"
    item.progress?.started == true -> "Continuar"
    else -> "Começar"
}

private fun progressLine(item: LibraryItem): String {
    val p = item.progress ?: return item.comic.format.label
    if (p.pageCount <= 0) return item.comic.format.label
    val remaining = p.pageCount - 1 - p.page
    return when {
        !p.started -> "${p.pageCount} páginas · não iniciado"
        p.finished -> "${p.pageCount} páginas · lido"
        else -> "Página ${p.page + 1} de ${p.pageCount} · $remaining restantes"
    }
}

private fun subtitle(item: LibraryItem): String =
    listOf(item.comic.folder.takeIf { it.isNotBlank() }, item.comic.format.label).filterNotNull().joinToString(" · ")

@Composable
private fun WideHome(
    state: LibraryState,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onFolder: (String) -> Unit,
    gutter: Dp,
    maxHeight: Dp,
) {
    val row = state.homeRow
    val index = selected.coerceIn(0, row.lastIndex)
    val item = row[index]
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.animateScrollToItem(index) }

    val cardH = (maxHeight * 0.42f).coerceIn(220.dp, 420.dp)
    val cardW = cardH * (2f / 3f)

    Column(Modifier.fillMaxSize().padding(bottom = 28.dp)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = item,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
                contentKey = { it.comic.id },
                label = "hero",
                modifier = Modifier.width(380.dp),
            ) { it ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(kicker(it), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
                    Text(it.comic.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(it), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressBar(it.progress?.fraction ?: 0f, it.glowColor(), Modifier.width(320.dp))
                        Text(progressLine(it), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        cta(it),
                        onClick = { onOpen(it) },
                        leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)) },
                    )
                }
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(start = 14.dp, end = gutter + cardW * 2),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(cardH + 60.dp),
                ) {
                    itemsIndexed(row, key = { _, it -> it.comic.id }) { i, it ->
                        CarouselCard(
                            item = it,
                            selected = i == index,
                            width = cardW,
                            height = cardH,
                            onClick = { if (i == index) onOpen(it) else onSelect(i) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(Icons.Filled.KeyboardArrowLeft, "Anterior", { onSelect((index - 1).coerceAtLeast(0)) }, size = 44.dp)
                    CircleIconButton(Icons.Filled.KeyboardArrowRight, "Próxima", { onSelect((index + 1).coerceAtMost(row.lastIndex)) }, size = 44.dp)
                    Text("${index + 1} de ${row.size}", style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                }
            }
        }
        FolderStrip(state, onFolder, gutter, columns = 4)
    }
}

@Composable
private fun CarouselCard(item: LibraryItem, selected: Boolean, width: Dp, height: Dp, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "scale")
    val alpha by animateFloatAsState(if (selected) 1f else 0.6f, label = "alpha")
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .shadow(if (selected) 24.dp else 10.dp, RoundedCornerShape(22.dp))
            .then(if (selected) Modifier.border(3.dp, FwipColors.Text, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = item.comic.title, onClick = onClick)
    ) {
        CoverImage(item, Modifier.fillMaxSize(), corner = 22.dp)
    }
}

@Composable
private fun FolderStrip(state: LibraryState, onFolder: (String) -> Unit, gutter: Dp, columns: Int) {
    val groups = state.items.groupBy { it.comic.folder.ifBlank { "Pasta principal" } }
        .entries.sortedByDescending { it.value.size }.take(columns)
    if (groups.size < 2) return
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Pastas", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            groups.forEach { (name, list) ->
                FolderCard(name, list, Modifier.weight(1f)) { onFolder(if (name == "Pasta principal") "" else name) }
            }
            repeat(columns - groups.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FolderCard(name: String, list: List<LibraryItem>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(62.dp).height(50.dp)) {
            list.take(3).forEachIndexed { i, it ->
                CoverImage(
                    it,
                    Modifier
                        .padding(start = (i * 14).dp)
                        .size(34.dp, 50.dp)
                        .border(2.dp, Color(0xFF15161C), RoundedCornerShape(6.dp)),
                    corner = 6.dp,
                    titleSize = 6,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (list.size == 1) "1 item" else "${list.size} itens", style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
    }
}

@Composable
private fun PhoneHome(state: LibraryState, onOpen: (LibraryItem) -> Unit, onFolder: (String) -> Unit, gutter: Dp) {
    val hero = state.homeRow.first()
    val others = state.homeRow.drop(1)
    val groups = state.items.groupBy { it.comic.folder.ifBlank { "Pasta principal" } }.entries.sortedByDescending { it.value.size }
    LazyColumn(
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(FwipColors.Surface)
                    .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoverImage(hero, Modifier.size(104.dp, 156.dp).shadow(12.dp, RoundedCornerShape(14.dp)), corner = 14.dp, titleSize = 16)
                Column(Modifier.weight(1f).height(156.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kicker(hero), style = MaterialTheme.typography.labelSmall, color = FwipColors.TextSoft)
                    Text(hero.comic.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(progressLine(hero), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressBar(hero.progress?.fraction ?: 0f, hero.glowColor(), Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    PillButton(
                        cta(hero),
                        onClick = { onOpen(hero) },
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth(),
                        leading = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        if (others.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recentes", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(others, key = { it.comic.id }) { it ->
                            CoverImage(
                                it,
                                Modifier
                                    .size(108.dp, 162.dp)
                                    .shadow(10.dp, RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Button, onClickLabel = it.comic.title) { onOpen(it) },
                                corner = 14.dp,
                                titleSize = 16,
                            )
                        }
                    }
                }
            }
        }
        if (groups.size >= 2) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pastas", style = MaterialTheme.typography.titleMedium)
                    groups.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (name, list) ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(FwipColors.Surface)
                                        .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
                                        .clickable(role = Role.Button) { onFolder(if (name == "Pasta principal") "" else name) }
                                        .padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${list.size} itens", style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItem>,
    folderFilter: String?,
    onClearFilter: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    wide: Boolean,
    gutter: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 160.dp else 104.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = if (wide) 32.dp else 110.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
    ) {
        if (folderFilter != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(folderFilter.ifBlank { "Pasta principal" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                    CircleIconButton(Icons.Filled.Close, "Mostrar tudo", onClearFilter, size = 40.dp)
                }
            }
        }
        items(items, key = { it.comic.id }) { it ->
            Column(
                Modifier.clickable(role = Role.Button, onClickLabel = it.comic.title) { onOpen(it) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverImage(it, Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(16.dp)), corner = 16.dp, titleSize = if (wide) 22 else 15)
                Text(it.comic.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val p = it.progress
                if (p != null && p.started) {
                    ProgressBar(p.fraction, it.glowColor(), Modifier.fillMaxWidth())
                }
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
            .background(Color(0xE616171E))
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
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(noFolders: Boolean, scanning: Boolean, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(color = FwipColors.Text)
                Text("Procurando HQs nas suas pastas...", style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
            } else {
                Text(
                    if (noFolders) "Sua estante está vazia" else "Nenhuma HQ encontrada",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (noFolders) "Escolha a pasta onde ficam suas HQs. O Fwip lê arquivos CBZ, CBR, CB7 e PDF, inclusive em subpastas."
                    else "As pastas escolhidas não têm arquivos CBZ, CBR, CB7 ou PDF. Tente adicionar outra pasta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FwipColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                PillButton("Escolher pasta", onAdd, leading = { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)) })
            }
        }
    }
}

@Composable
private fun FoldersDialog(folders: List<Uri>, onAdd: () -> Unit, onRemove: (Uri) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF16171E))
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pastas da biblioteca", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Close, "Fechar", onDismiss, size = 40.dp)
            }
            if (folders.isEmpty()) {
                Text("Nenhuma pasta adicionada ainda.", style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            }
            folders.forEach { uri ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(FwipColors.Surface)
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(readableFolder(uri), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    CircleIconButton(Icons.Filled.Delete, "Remover pasta", { onRemove(uri) }, size = 40.dp)
                }
            }
            PillButton("Adicionar pasta", onAdd, style = PillStyle.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth(), leading = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        }
    }
}

private fun readableFolder(uri: Uri): String {
    val raw = Uri.decode(uri.lastPathSegment ?: uri.toString())
    val (volume, path) = raw.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val vol = if (volume == "primary") "Armazenamento interno" else "Cartão / $volume"
    return if (path.isBlank()) vol else "$vol / $path"
}
