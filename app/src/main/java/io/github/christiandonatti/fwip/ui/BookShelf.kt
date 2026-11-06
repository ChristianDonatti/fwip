package io.github.christiandonatti.fwip.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** Text laid on its side, read from top to bottom, as on a book's spine. */
private fun Modifier.sideways(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
}.rotate(90f)

/** How thick a book looks: its length (positions, about a page each). */
private fun thickness(item: LibraryItem): Dp {
    val pages = item.progress?.pageCount ?: 200
    return (30 + (pages.coerceIn(20, 1200) - 20) * 30 / 1180).dp
}

private fun spineHeight(item: LibraryItem, tallest: Dp): Dp = tallest - ((item.comic.id.hashCode() and 0x7fffffff) % 5 * 8).dp

/**
 * Books as spines on shelves, like a real bookcase: thickness by length, colour from the cover,
 * a band at the foot for the one you are reading.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookShelf(items: List<LibraryItem>, onOpen: (LibraryItem) -> Unit, onMenu: (LibraryItem) -> Unit, wide: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 3.dp
        val tallest = if (wide) 250.dp else 190.dp
        // Fill each shelf from left to right, then start the next.
        val rows = mutableListOf<MutableList<LibraryItem>>()
        var used = 0.dp
        items.forEach { item ->
            val w = thickness(item) + gap
            if (rows.isEmpty() || used + w > maxWidth) { rows += mutableListOf<LibraryItem>(); used = 0.dp }
            rows.last() += item
            used += w
        }
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            rows.forEach { row ->
                Column {
                    Row(
                        Modifier.height(tallest).padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        row.forEach { item -> Spine(item, spineHeight(item, tallest), onOpen, onMenu) }
                    }
                    // The board.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .shadow(6.dp, RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.verticalGradient(listOf(FwipColors.PanelHigh, FwipColors.Panel))),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Spine(item: LibraryItem, height: Dp, onOpen: (LibraryItem) -> Unit, onMenu: (LibraryItem) -> Unit) {
    val base = item.color?.let { Color(it) } ?: placeholderColor(item.comic.id)
    val ink = if (base.luminance() > 0.45f) Color(0xFF1C1B1A) else Color(0xFFF4F1EA)
    val p = item.progress
    Box(
        Modifier
            .width(thickness(item))
            .height(height)
            .shadow(3.dp, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(Brush.horizontalGradient(listOf(base.copy(alpha = 0.82f).compositeOverBlack(), base, base.copy(alpha = 0.9f).compositeOverBlack())))
            .combinedClickable(role = Role.Button, onClickLabel = item.title, onLongClick = { onMenu(item) }) { onOpen(item) },
        contentAlignment = Alignment.Center,
    ) {
        // Two thin bands near the top and bottom, as printed on many spines.
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).fillMaxWidth().height(2.dp).background(ink.copy(alpha = 0.35f)))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth().height(2.dp).background(ink.copy(alpha = 0.35f)))
        Text(
            item.title,
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 18.dp).sideways(),
        )
        if (p != null && p.started && !p.finished) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(FwipColors.Amber))
        }
    }
}

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)
