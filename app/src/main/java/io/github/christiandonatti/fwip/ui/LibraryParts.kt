@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.net.Uri
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import io.github.christiandonatti.fwip.data.sortedFor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
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
    selection: Selection? = null,
) {
    val selecting = selection?.active == true
    val selected = selection?.contains(item.comic.id) == true
    val ui = UiSettings.get(androidx.compose.ui.platform.LocalContext.current)
    val finished = item.progress?.finished == true
    Column(
        modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = if (selecting) str(R.string.select) else item.title,
            onLongClickLabel = if (selecting) str(R.string.select) else str(R.string.options),
            onLongClick = { if (selecting) selection?.toggle(item.comic.id) else onMenu() },
            onClick = { if (selecting) selection?.toggle(item.comic.id) else onOpen() },
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.graphicsLayer { val s = if (selected) 0.93f else 1f; scaleX = s; scaleY = s }) {
            CoverImage(
                item,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .coverShadow(10.dp, 16.dp)
                    .graphicsLayer { alpha = if (finished && ui.readMark == ReadMark.Faded) 0.4f else 1f },
                corner = 16.dp,
                titleSize = if (wide) 22 else 15,
                track = true,
            )
            if (finished && ui.readMark == ReadMark.Ribbon) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(coverCorner(16.dp)))) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 30.dp, y = 16.dp)
                            .rotate(45f)
                            .width(120.dp)
                            .background(FwipColors.Amber)
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(str(R.string.read_ribbon), style = MaterialTheme.typography.labelSmall, color = FwipColors.AmberInk, maxLines = 1)
                    }
                }
            }
            if (finished && ui.readMark == ReadMark.Badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(FwipColors.Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, str(R.string.read_badge), tint = FwipColors.AmberInk, modifier = Modifier.size(16.dp))
                }
            }
            if (selecting) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) FwipColors.Amber.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.25f))
                        .border(if (selected) 3.dp else 0.dp, if (selected) FwipColors.Amber else Color.Transparent, RoundedCornerShape(16.dp))
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (selected) FwipColors.Amber else Color.Black.copy(alpha = 0.45f))
                        .border(2.dp, if (selected) FwipColors.Amber else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, str(R.string.selected), tint = FwipColors.AmberInk, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (ui.coverTitles) Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val p = item.progress
        if (p != null && p.started && !p.finished) {
            ProgressBar(p.fraction, item.glowColor(), Modifier.fillMaxWidth())
        }
    }
}

/** Comics picked in selection mode. Empty means selection mode is off. */
@androidx.compose.runtime.Stable
class Selection {
    var ids by mutableStateOf(emptySet<String>())
        private set
    val active: Boolean get() = ids.isNotEmpty()
    fun contains(id: String) = id in ids
    fun toggle(id: String) { ids = if (id in ids) ids - id else ids + id }
    fun selectAll(all: Collection<String>) { ids = ids + all }
    fun clear() { ids = emptySet() }
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
                    .coverShadow(8.dp, 10.dp),
                corner = 10.dp,
                titleSize = 9,
            )
        }
    }
}

fun countLabel(n: Int): String = if (n == 1) str(R.string.one_item) else str(R.string.n_items, n)

fun fileSizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',')
}

@Composable
fun FwipDialog(onDismiss: () -> Unit, tint: Color? = null, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(FwipColors.Panel)
                // A comic's own colour washing down from the top of its sheet.
                .then(
                    if (tint == null) Modifier
                    else Modifier.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to tint.copy(alpha = if (FwipColors.isLight) 0.16f else 0.26f),
                            0.45f to tint.copy(alpha = 0f),
                        )
                    )
                )
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
        CircleIconButton(Icons.Filled.Close, str(R.string.close), onClose, size = 40.dp)
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
        val tint = if (danger) FwipColors.Danger else FwipColors.Text
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
    onSelect: () -> Unit,
    onCover: () -> Unit = {},
    onWant: () -> Unit = {},
    onMove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
    onEditSeries: () -> Unit = {},
    onResetProgress: () -> Unit = {},
) {
    FwipDialog(onDismiss, tint = item.glowColor()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(item, Modifier.size(72.dp, 108.dp), corner = 10.dp, titleSize = 10)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val p = item.progress
                val pages = p?.pageCount?.takeIf { it > 0 }?.let { str(R.string.n_pages, it) }
                Text(
                    listOfNotNull(item.comic.format.label, pages, fileSizeLabel(item.comic.size).ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
                if (item.comic.folder.isNotBlank()) {
                    Text(item.comic.folder, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 40.dp)
        }
        item.meta?.let { MetaDetails(it) }
        val finished = item.progress?.finished == true
        val started = item.progress?.started == true
        SheetButton(
            when { finished -> str(R.string.read_again); started -> str(R.string.continue_reading); else -> str(R.string.read) },
            onOpen,
            leading = { Icon(Icons.Filled.PlayArrow, null) },
        )
        SheetButton(
            if (finished) str(R.string.mark_unread) else str(R.string.mark_read),
            onToggleRead,
            leading = { Icon(Icons.Filled.Check, null) },
        )
        if (started && !finished) {
            SheetButton(str(R.string.reset_progress), onResetProgress, leading = { Icon(Icons.Filled.Refresh, null) })
        }
        SheetButton(str(R.string.add_to_collection), onCollections, leading = { Icon(Icons.Filled.Add, null) })
        if (!finished) {
            SheetButton(
                str(if (item.wanted) R.string.want_remove else R.string.want_add),
                onWant,
                leading = { BookmarkGlyph(LocalContentColor.current, filled = item.wanted) },
            )
        }
        if (item.comic.remote == null) {
            SheetButton(str(R.string.edit_info), onEdit, leading = { Icon(Icons.Filled.Edit, null) })
            SheetButton(str(R.string.series_edit), onEditSeries, leading = { StackIcon(LocalContentColor.current) })
            SheetButton(str(R.string.cover_change), onCover, leading = { CoverIcon(LocalContentColor.current) })
            SheetButton(str(R.string.move_to), onMove, leading = { FolderIcon(LocalContentColor.current) })
            SheetButton(str(R.string.delete_file), onDelete, leading = { Icon(Icons.Filled.Delete, null) }, danger = true)
        }
        if (item.coverPath != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            SheetButton(str(R.string.use_as_backdrop), {
                if (Backdrops.set(context, java.io.File(item.coverPath))) {
                    val ui = UiSettings.get(context)
                    ui.updateBackdrop(Backdrop.Image)
                    ui.backdropChanged()
                }
                onDismiss()
            }, leading = { CoverIcon(LocalContentColor.current) })
        }
        SheetButton(str(R.string.select_several), onSelect, leading = { SelectIcon(LocalContentColor.current) })
        if (seriesSize >= 1) {
            SheetButton(
                str(R.string.see_series, item.series.name),
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
            meta.writer?.let { str(R.string.meta_writer) to it },
            meta.artists?.let { str(R.string.meta_art) to it },
            meta.publisher?.let { p -> str(R.string.meta_publisher) to listOfNotNull(p, meta.imprint).distinct().joinToString(" / ") },
            meta.date?.let { str(R.string.meta_date) to it },
            meta.storyArc?.let { str(R.string.meta_arc) to it },
            meta.genre?.let { str(R.string.meta_genre) to it },
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
                    .clickable(onClickLabel = if (expanded) str(R.string.show_less) else str(R.string.show_more)) { expanded = !expanded },
            )
        }
    }
}

/** Pick which collections a comic belongs to, or start a new one. */
@Composable
fun CollectionPickerDialog(
    title: String,
    collections: List<ComicCollection>,
    comicIds: List<String>,
    onToggle: (ComicCollection, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(collections.isEmpty()) }
    FwipDialog(onDismiss) {
        DialogTitle(title, onDismiss)
        collections.forEach { c ->
            val inside = comicIds.isNotEmpty() && c.comicIds.containsAll(comicIds)
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
                placeholder = str(R.string.new_collection_name),
                action = str(R.string.create),
                onDone = { onCreate(it); creating = false },
            )
        } else {
            SheetButton(str(R.string.new_collection), { creating = true }, leading = { Icon(Icons.Filled.Add, null) })
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(title, onDismiss)
        NameField(initial, str(R.string.name), action, onDone)
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

/** Rounded square with a check, for "select several". */
@Composable
fun SelectIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        drawRoundRect(color, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), CornerRadius(4 * u), style = Stroke(width = 2f * u))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(7.5f * u, 12.5f * u)
            lineTo(10.5f * u, 15.5f * u)
            lineTo(16.5f * u, 8.5f * u)
        }
        drawPath(path, color, style = Stroke(width = 2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
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

/** Human name of a library folder: "Internal storage / Comics" or "NAS / Comics". */
internal fun folderLabel(uri: Uri, servers: List<io.github.christiandonatti.fwip.remote.Server>): String {
    if (io.github.christiandonatti.fwip.remote.NetUris.isNet(uri)) {
        val name = servers.firstOrNull { it.id == io.github.christiandonatti.fwip.remote.NetUris.serverId(uri) }?.name ?: str(R.string.network_folder)
        val path = io.github.christiandonatti.fwip.remote.NetUris.path(uri)
        return if (path.isEmpty()) name else "$name / ${path.replace("/", " / ")}"
    }
    val raw = Uri.decode(uri.lastPathSegment ?: uri.toString())
    val (volume, path) = raw.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val vol = if (volume == "primary") str(R.string.internal_storage) else str(R.string.sd_card, volume)
    return if (path.isBlank()) vol else "$vol / $path"
}

/** A small picture frame: the cover. */
@Composable
fun CoverIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * u)
        drawRoundRect(color, Offset(5 * u, 3 * u), androidx.compose.ui.geometry.Size(14 * u, 18 * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
        val hill = androidx.compose.ui.graphics.Path().apply {
            moveTo(7 * u, 18 * u); lineTo(11 * u, 12.5f * u); lineTo(13.5f * u, 15.5f * u); lineTo(15 * u, 14 * u); lineTo(17 * u, 18 * u); close()
        }
        drawPath(hill, color)
        drawCircle(color, radius = 1.6f * u, center = Offset(14.5f * u, 8 * u))
    }
}

/** Ribbon: the "Want to read" mark. */
@Composable
fun BookmarkGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(6 * u, 3 * u); lineTo(18 * u, 3 * u); lineTo(18 * u, 21 * u); lineTo(12 * u, 16.5f * u); lineTo(6 * u, 21 * u); close()
        }
        if (filled) drawPath(path, color) else drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * u))
    }
}

/**
 * Puts a collection in reading order: move comics up and down one by one, or sort them by
 * publication date or by series and number in one tap. Reading from the collection then
 * follows this order.
 */
@Composable
fun ReorderDialog(items: List<LibraryItem>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(items) }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.reading_order), onDismiss)
        Text(str(R.string.reading_order_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(str(R.string.order_by_date), {
                order = order.sortedWith(compareBy<LibraryItem> { it.meta?.year ?: it.parsed.year ?: Int.MAX_VALUE }.thenBy { it.meta?.month ?: 0 })
            }, height = 40.dp, style = PillStyle.Ghost)
            PillButton(str(R.string.order_by_series), { order = order.sortedFor(io.github.christiandonatti.fwip.data.SortOrder.Title) }, height = 40.dp, style = PillStyle.Ghost)
        }
        order.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FwipColors.Surface)
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = FwipColors.Amber, modifier = Modifier.width(24.dp))
                CoverImage(item, Modifier.size(32.dp, 48.dp), corner = 5.dp, titleSize = 6)
                Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.KeyboardArrowUp, str(R.string.move_up), {
                    if (i > 0) order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                }, size = 36.dp)
                CircleIconButton(Icons.Filled.KeyboardArrowDown, str(R.string.move_down), {
                    if (i < order.lastIndex) order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                }, size = 36.dp)
            }
        }
        PillButton(str(R.string.save), { onSave(order.map { it.comic.id }) }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.fillMaxWidth())
    }
}

