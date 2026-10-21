package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a page sits inside a spread. In double mode the cover goes on the right, like a real book. */
enum class Slot { Left, Right, Full }

data class PlacedPage(val page: Int, val slot: Slot, val rect: Rect)

/**
 * Groups page indices into what is shown at once: single pages, or the cover alone and then
 * pairs. A [wide] page (a double-page spread scanned as one image) always stands alone.
 */
fun buildSpreads(pageCount: Int, double: Boolean, wide: Set<Int> = emptySet()): List<List<Int>> {
    if (!double) return (0 until pageCount).map { listOf(it) }
    val out = mutableListOf(listOf(0))
    var i = 1
    while (i < pageCount) {
        if (i in wide || i + 1 >= pageCount || (i + 1) in wide) {
            out += listOf(i)
            i += 1
        } else {
            out += listOf(i, i + 1)
            i += 2
        }
    }
    return out
}

object SpreadLayout {
    private val DEFAULT_SIZE = IntSize(1000, 1530)

    fun slots(pages: List<Int>, double: Boolean, isWide: (Int) -> Boolean = { false }): List<Pair<Int, Slot>> = when {
        !double -> pages.map { it to Slot.Full }
        pages.size >= 2 -> listOf(pages[0] to Slot.Left, pages[1] to Slot.Right)
        pages.firstOrNull()?.let(isWide) == true -> pages.map { it to Slot.Full }
        pages.firstOrNull() == 0 -> listOf(0 to Slot.Right)
        else -> pages.map { it to Slot.Left }
    }

    /**
     * Rectangles of each page inside a [w] x [h] reader. With [rtl] the result is mirrored so the
     * first page of a pair sits on the right, as in manga.
     */
    fun place(
        pages: List<Int>,
        double: Boolean,
        rtl: Boolean,
        w: Float,
        h: Float,
        sizeOf: (Int) -> IntSize?,
        isWide: (Int) -> Boolean = { false },
    ): List<PlacedPage> = slots(pages, double, isWide).map { (page, slot) ->
        val size = sizeOf(page) ?: DEFAULT_SIZE
        val logical = fit(size, slot, w, h)
        PlacedPage(page, slot, if (rtl) mirror(logical, w) else logical)
    }

    fun fit(size: IntSize, slot: Slot, w: Float, h: Float): Rect {
        val slotRect = when (slot) {
            Slot.Left -> Rect(0f, 0f, w / 2f, h)
            Slot.Right -> Rect(w / 2f, 0f, w, h)
            Slot.Full -> Rect(0f, 0f, w, h)
        }
        val s = min(slotRect.width / size.width, slotRect.height / size.height)
        val rw = size.width * s
        val rh = size.height * s
        val top = slotRect.top + (slotRect.height - rh) / 2f
        val left = when (slot) {
            Slot.Left -> slotRect.right - rw // against the spine
            Slot.Right -> slotRect.left
            Slot.Full -> slotRect.left + (slotRect.width - rw) / 2f
        }
        return Rect(left, top, left + rw, top + rh)
    }

    fun mirror(r: Rect, w: Float) = Rect(w - r.right, r.top, w - r.left, r.bottom)
}

sealed interface PageState {
    data object Loading : PageState
    data class Ready(val bitmap: Bitmap) : PageState
    data class Failed(val message: String?) : PageState
}

/** Loads the pages of a spread and exposes them as observable state. */
@Stable
class SpreadBitmaps {
    val states = mutableStateMapOf<Int, PageState>()
    fun sizeOf(page: Int): IntSize? = (states[page] as? PageState.Ready)?.bitmap?.let { IntSize(it.width, it.height) }
}

@Composable
fun rememberSpreadBitmaps(pages: List<Int>, cache: PageCache, decodeW: Int, decodeH: Int): SpreadBitmaps {
    val holder = remember(pages, decodeW, decodeH) {
        SpreadBitmaps().apply {
            pages.forEach { p -> states[p] = cache.peek(p, decodeW, decodeH)?.let { PageState.Ready(it) } ?: PageState.Loading }
        }
    }
    LaunchedEffect(holder) {
        for (p in pages) {
            if (holder.states[p] is PageState.Ready) continue
            val b = cache.get(p, decodeW, decodeH)
            holder.states[p] = if (b != null) PageState.Ready(b) else PageState.Failed(cache.errorFor(p))
        }
    }
    return holder
}

/** A spread drawn statically: one page, or two pages meeting at the spine. */
@Composable
fun SpreadView(
    pages: List<Int>,
    double: Boolean,
    rtl: Boolean,
    cache: PageCache,
    decodeW: Int,
    decodeH: Int,
    modifier: Modifier = Modifier,
    isWide: (Int) -> Boolean = { false },
) {
    // A wide page in double mode gets the whole screen width to decode at.
    val wideAlone = double && pages.size == 1 && isWide(pages[0])
    val bitmaps = rememberSpreadBitmaps(pages, cache, if (wideAlone) decodeW * 2 else decodeW, decodeH)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val placed = SpreadLayout.place(pages, double, rtl, w, h, bitmaps::sizeOf, isWide)
        placed.forEach { p ->
            val dpW = with(density) { p.rect.width.toDp() }
            val dpH = with(density) { p.rect.height.toDp() }
            Box(
                Modifier
                    .offset { IntOffset(p.rect.left.roundToInt(), p.rect.top.roundToInt()) }
                    .size(dpW, dpH),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = bitmaps.states[p.page]) {
                    is PageState.Ready -> {
                        val image = remember(s.bitmap) { s.bitmap.asImageBitmap() }
                        Image(
                            image,
                            contentDescription = "Página ${p.page + 1}",
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is PageState.Failed -> Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Não foi possível carregar a página ${p.page + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FwipColors.TextSoft,
                            textAlign = TextAlign.Center,
                        )
                        s.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, textAlign = TextAlign.Center)
                        }
                    }
                    else -> CircularProgressIndicator(Modifier.size(28.dp), color = FwipColors.TextMuted, strokeWidth = 2.dp)
                }
            }
        }
        if (placed.size == 2) {
            // Soft shadow where the two pages meet.
            Canvas(Modifier.fillMaxSize()) {
                val spine = size.width / 2f
                val sw = 28.dp.toPx()
                val top = placed.minOf { it.rect.top }
                val bottom = placed.maxOf { it.rect.bottom }
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Transparent),
                        startX = spine - sw,
                        endX = spine + sw,
                    ),
                    topLeft = Offset(spine - sw, top),
                    size = Size(sw * 2, bottom - top),
                )
            }
        }
    }
}
