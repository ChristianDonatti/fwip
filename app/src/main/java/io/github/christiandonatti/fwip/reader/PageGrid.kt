package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.CircleIconButton
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** Ribbon bookmark icon, outlined or filled. */
@Composable
internal fun BookmarkIcon(color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val u = size.width / 24f
        val path = Path().apply {
            moveTo(6 * u, 3 * u)
            lineTo(18 * u, 3 * u)
            lineTo(18 * u, 21 * u)
            lineTo(12 * u, 16.5f * u)
            lineTo(6 * u, 21 * u)
            close()
        }
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(width = 2f * u))
    }
}

/** Four small pages: the page overview. */
@Composable
internal fun GridIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val u = size.width / 24f
        val s = Stroke(width = 2f * u)
        val r = androidx.compose.ui.geometry.CornerRadius(1.5f * u)
        for ((x, y) in listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)) {
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Size(8 * u, 8 * u), r, style = s)
        }
    }
}

/** Round button with one of the drawn icons above, matching [CircleIconButton]. */
@Composable
internal fun DrawnIconButton(
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    on: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) FwipColors.Amber else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (on) FwipColors.AmberInk else FwipColors.Text)
    }
}

/**
 * Every page of the comic as thumbnails: tap one to jump there. Can show only bookmarked pages.
 * In manga mode the grid runs right to left, like the book.
 */
@Composable
internal fun PageGrid(
    pageCount: Int,
    cache: PageCache,
    current: Int,
    bookmarks: Set<Int>,
    rtl: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var onlyMarked by remember { mutableStateOf(false) }
    val pages = if (onlyMarked) bookmarks.sorted() else (0 until pageCount).toList()
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = if (onlyMarked) 0 else (current - 4).coerceAtLeast(0))

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF70B0C10))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 44.dp)
            Text(str(R.string.pages_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilterChip(str(R.string.all_pages), !onlyMarked) { onlyMarked = false }
            FilterChip(str(R.string.bookmarks_n, bookmarks.size), onlyMarked) { onlyMarked = true }
        }
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    str(R.string.no_bookmarks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FwipColors.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@Column
        }
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                state = state,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
            ) {
                items(pages, key = { it }) { page ->
                    Thumb(page, cache, page == current, page in bookmarks) { onPick(page) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (on) FwipColors.Text else FwipColors.Surface)
            .border(1.dp, if (on) Color.Transparent else FwipColors.Stroke, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
    }
}

@Composable
private fun Thumb(page: Int, cache: PageCache, current: Boolean, marked: Boolean, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(cache.peek(page, THUMB_W, THUMB_H), page) {
        if (value == null) value = cache.get(page, THUMB_W, THUMB_H)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(FwipColors.Surface)
                .border(if (current) 3.dp else 1.dp, if (current) FwipColors.Amber else FwipColors.Stroke, RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = str(R.string.page_n, page + 1), onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(if (current) 3.dp else 1.dp), contentScale = ContentScale.Fit)
            } else {
                CircularProgressIndicator(Modifier.size(18.dp), color = FwipColors.TextMuted, strokeWidth = 2.dp)
            }
            if (marked) {
                BookmarkIcon(FwipColors.Amber, filled = true, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Text(
            "${page + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (current) FwipColors.Amber else FwipColors.TextSoft,
        )
    }
}

private const val THUMB_W = 240
private const val THUMB_H = 360
