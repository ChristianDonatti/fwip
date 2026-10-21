@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.christiandonatti.fwip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.christiandonatti.fwip.data.ComicCollection
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import java.util.Locale

/** A cover in a grid: tap to read, hold for options. Finished comics get a check badge. */
@Composable
fun CoverTile(
    item: LibraryItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = item.title,
            onLongClickLabel = "Opções",
            onLongClick = onMenu,
            onClick = onOpen,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            CoverImage(
                item,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .shadow(10.dp, RoundedCornerShape(16.dp)),
                corner = 16.dp,
                titleSize = if (wide) 22 else 15,
            )
            if (item.progress?.finished == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(FwipColors.Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, "Lida", tint = FwipColors.AmberInk, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val p = item.progress
        if (p != null && p.started && !p.finished) {
            ProgressBar(p.fraction, item.glowColor(), Modifier.fillMaxWidth())
        }
    }
}

/** A series, folder or collection: a small fan of covers with a name and a count. */
@Composable
fun ShelfCard(
    title: String,
    items: List<LibraryItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = countLabel(items.size),
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverFan(items, Modifier.fillMaxWidth().aspectRatio(1.25f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
        }
    }
}

/** Up to three covers fanned out; an empty outline when there is nothing yet. */
@Composable
fun CoverFan(items: List<LibraryItem>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val h = maxHeight * 0.92f
        val w = h * (2f / 3f)
        val shown = items.take(3)
        if (shown.isEmpty()) {
            Canvas(Modifier.size(w, h)) {
                drawRoundRect(
                    FwipColors.Stroke,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
        }
        val spread = (maxWidth - w) / 2f * 0.8f
        shown.indices.reversed().forEach { i ->
            val pos = if (shown.size == 1) 0f else (i - (shown.size - 1) / 2f) / ((shown.size - 1) / 2f)
            CoverImage(
                shown[i],
                Modifier
                    .size(w, h)
                    .graphicsLayer {
                        translationX = (spread * pos).toPx()
                        rotationZ = pos * 7f
                        scaleX = if (i == 0) 1f else 0.9f
                        scaleY = if (i == 0) 1f else 0.9f
                    }
                    .shadow(8.dp, RoundedCornerShape(10.dp)),
                corner = 10.dp,
                titleSize = 9,
            )
        }
    }
}

fun countLabel(n: Int): String = if (n == 1) "1 item" else "$n itens"

fun fileSizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',')
}

@Composable
fun FwipDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF16171E))
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
fun DialogTitle(text: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        CircleIconButton(Icons.Filled.Close, "Fechar", onClose, size = 40.dp)
    }
}

/** A full-width row button used in option sheets. */
@Composable
fun SheetButton(
    text: String,
    onClick: () -> Unit,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FwipColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tint = if (danger) Color(0xFFFF8A80) else FwipColors.Text
        if (leading != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides tint) { leading() }
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** Options for one comic, opened by holding its cover. */
@Composable
fun ComicActionsDialog(
    item: LibraryItem,
    seriesSize: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onCollections: () -> Unit,
    onSeries: () -> Unit,
) {
    FwipDialog(onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(item, Modifier.size(72.dp, 108.dp), corner = 10.dp, titleSize = 10)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val p = item.progress
                val pages = p?.pageCount?.takeIf { it > 0 }?.let { "$it páginas" }
                Text(
                    listOfNotNull(item.comic.format.label, pages, fileSizeLabel(item.comic.size).ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
                if (item.comic.folder.isNotBlank()) {
                    Text(item.comic.folder, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CircleIconButton(Icons.Filled.Close, "Fechar", onDismiss, size = 40.dp)
        }
        item.meta?.let { MetaDetails(it) }
        val finished = item.progress?.finished == true
        val started = item.progress?.started == true
        SheetButton(
            when { finished -> "Ler de novo"; started -> "Continuar lendo"; else -> "Ler" },
            onOpen,
            leading = { Icon(Icons.Filled.PlayArrow, null) },
        )
        SheetButton(
            if (finished) "Marcar como não lida" else "Marcar como lida",
            onToggleRead,
            leading = { Icon(Icons.Filled.Check, null) },
        )
        SheetButton("Adicionar a uma coleção", onCollections, leading = { Icon(Icons.Filled.Add, null) })
        if (seriesSize >= 2) {
            SheetButton(
                "Ver série ${item.series.name}",
                onSeries,
                leading = { StackIcon(LocalContentColor.current) },
                trailing = { Text(countLabel(seriesSize), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted) },
            )
        }
    }
}

/** Credits, publication and synopsis from ComicInfo.xml. */
@Composable
fun MetaDetails(meta: io.github.christiandonatti.fwip.data.ComicMeta) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val facts = listOfNotNull(
            meta.writer?.let { "Roteiro" to it },
            meta.artists?.let { "Arte" to it },
            meta.publisher?.let { p -> "Editora" to listOfNotNull(p, meta.imprint).distinct().joinToString(" / ") },
            meta.date?.let { "Publicação" to it },
            meta.storyArc?.let { "Arco" to it },
            meta.genre?.let { "Gênero" to it },
        )
        facts.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, modifier = Modifier.width(84.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextSoft, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        meta.summary?.let { summary ->
            var expanded by remember { mutableStateOf(false) }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = FwipColors.TextSoft,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = if (expanded) "Mostrar menos" else "Mostrar mais") { expanded = !expanded },
            )
        }
    }
}

/** Pick which collections a comic belongs to, or start a new one. */
@Composable
fun CollectionPickerDialog(
    title: String,
    collections: List<ComicCollection>,
    comicId: String,
    onToggle: (ComicCollection, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(collections.isEmpty()) }
    FwipDialog(onDismiss) {
        DialogTitle(title, onDismiss)
        collections.forEach { c ->
            val inside = comicId in c.comicIds
            SheetButton(
                c.name,
                onClick = { onToggle(c, !inside) },
                trailing = {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (inside) FwipColors.Amber else Color.Transparent)
                            .border(2.dp, if (inside) FwipColors.Amber else FwipColors.Stroke, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inside) Icon(Icons.Filled.Check, null, tint = FwipColors.AmberInk, modifier = Modifier.size(16.dp))
                    }
                },
            )
        }
        if (creating) {
            NameField(
                initial = "",
                placeholder = "Nome da nova coleção",
                action = "Criar",
                onDone = { onCreate(it); creating = false },
            )
        } else {
            SheetButton("Nova coleção", { creating = true }, leading = { Icon(Icons.Filled.Add, null) })
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(title, onDismiss)
        NameField(initial, "Nome", action, onDone)
    }
}

@Composable
private fun NameField(initial: String, placeholder: String, action: String, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FwipColors.Text),
                cursorBrush = SolidColor(FwipColors.Amber),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        PillButton(action, { if (text.isNotBlank()) onDone(text) }, height = 54.dp, style = PillStyle.Amber, enabled = text.isNotBlank())
    }
}

/** Folder outline icon (the core icon set has none). */
@Composable
fun FolderIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(3 * u, 7 * u)
            quadraticTo(3 * u, 5 * u, 5 * u, 5 * u)
            lineTo(9.5f * u, 5 * u)
            lineTo(11.5f * u, 7 * u)
            lineTo(19 * u, 7 * u)
            quadraticTo(21 * u, 7 * u, 21 * u, 9 * u)
            lineTo(21 * u, 17 * u)
            quadraticTo(21 * u, 19 * u, 19 * u, 19 * u)
            lineTo(5 * u, 19 * u)
            quadraticTo(3 * u, 19 * u, 3 * u, 17 * u)
            close()
        }
        drawPath(path, color, style = Stroke(width = 2f * u))
    }
}

/** Two stacked cards, for series. */
@Composable
fun StackIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(width = 2f * u)
        drawRoundRect(color, Offset(7 * u, 3 * u), Size(12 * u, 16 * u), CornerRadius(2 * u), style = s)
        drawRoundRect(color, Offset(4 * u, 6 * u), Size(12 * u, 16 * u), CornerRadius(2 * u), style = s)
    }
}

/** A round button holding any small drawing, like [CircleIconButton]. */
@Composable
fun CircleButton(contentDescription: String, onClick: () -> Unit, size: Dp = 46.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
