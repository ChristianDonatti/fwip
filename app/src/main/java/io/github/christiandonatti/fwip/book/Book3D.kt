package io.github.christiandonatti.fwip.book

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.reader.CurlPages
import io.github.christiandonatti.fwip.reader.CurlRenderer
import io.github.christiandonatti.fwip.reader.PageDraw
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/** Proportions of a printed page (width over height), close to a paperback. */
const val PAGE_ASPECT = 0.68f

/**
 * Where the pages of the 3D book go in a [w] x [h] area: a two-page spread on a wide screen
 * lying sideways, one page otherwise, [scale] of the room left by the bars.
 */
fun bookPageRect(w: Float, h: Float, top: Float, bottom: Float, scale: Float, spread: Boolean): Rect {
    val roomW = w * scale
    val roomH = (h - top - bottom) * scale
    val aspect = if (spread) PAGE_ASPECT * 2f else PAGE_ASPECT
    var pw = roomW
    var ph = pw / aspect
    if (ph > roomH) { ph = roomH; pw = ph * aspect }
    val left = (w - pw) / 2f
    val t = top + (h - top - bottom - ph) / 2f
    return Rect(left, t, left + pw, t + ph)
}

/** How thick the page block on each side is, in px, for a book [fraction] read. */
private fun blockWidths(fraction: Float, max: Float): Pair<Float, Float> {
    val f = fraction.coerceIn(0f, 1f)
    return (max * (0.25f + 0.75f * f)) to (max * (0.25f + 0.75f * (1f - f)))
}

/**
 * Everything behind the pages: the shadow on the table, the boards of the cover, and the
 * stack of page edges on each side (thicker on the left as you read on).
 */
@Composable
fun BookBody(page: Rect, spread: Boolean, cover: Color, paper: Color, fraction: Float) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        if (page.width < 4f) return@Canvas
        val maxBlock = with(density) { (if (spread) 9.dp else 7.dp).toPx() }
        val (leftBlock, rightBlock) = blockWidths(fraction, maxBlock)
        val board = with(density) { 7.dp.toPx() }
        val lb = if (spread) leftBlock else with(density) { 3.dp.toPx() }
        val outer = Rect(page.left - lb - board, page.top - board, page.right + rightBlock + board, page.bottom + board)

        // Soft shadow: several widening, fading rounded rects, offset down as from a lamp above.
        for (i in 6 downTo 1) {
            val g = with(density) { (i * 7).dp.toPx() }
            drawRoundRect(
                Color.Black.copy(alpha = 0.055f),
                topLeft = Offset(outer.left - g * 0.6f, outer.top - g * 0.3f + g * 0.55f),
                size = Size(outer.width + g * 1.2f, outer.height + g * 1.05f),
                cornerRadius = CornerRadius(g + 12f),
            )
        }
        // The boards, with a darker edge and a faint cloth weave.
        drawRoundRect(lerp(cover, Color.Black, 0.35f), outer.topLeft, outer.size, CornerRadius(board * 1.2f))
        drawRoundRect(cover, outer.topLeft + Offset(1.5f, 1.5f), Size(outer.width - 3f, outer.height - 3f), CornerRadius(board))
        clothWeave(outer, cover)
        if (spread) {
            // The spine sinks into the boards at the middle.
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Transparent),
                    startX = page.center.x - board * 2, endX = page.center.x + board * 2,
                ),
                topLeft = Offset(page.center.x - board * 2, outer.top), size = Size(board * 4, outer.height),
            )
        }
        // Page edges: thin lines of paper, shaded towards the boards.
        fun block(x0: Float, x1: Float, leftSide: Boolean) {
            if (x1 - x0 < 0.5f) return
            val top = page.top + 2f
            val h = page.height - 4f
            drawRect(lerp(paper, Color(0xFF9A8F7C), 0.25f), Offset(x0, top), Size(x1 - x0, h))
            var x = x0 + 1.2f
            var k = 0
            while (x < x1) {
                drawLine(Color.Black.copy(alpha = if (k % 3 == 0) 0.18f else 0.08f), Offset(x, top), Offset(x, top + h), strokeWidth = 0.7f)
                x += 1.6f; k++
            }
            drawRect(
                Brush.horizontalGradient(
                    if (leftSide) listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent) else listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    startX = x0, endX = x1,
                ),
                Offset(x0, top), Size(x1 - x0, h),
            )
        }
        block(page.left - lb, page.left, leftSide = true)
        block(page.right, page.right + rightBlock, leftSide = false)
    }
}

private fun DrawScope.clothWeave(r: Rect, cover: Color) {
    val light = lerp(cover, Color.White, 0.12f).copy(alpha = 0.10f)
    val dark = lerp(cover, Color.Black, 0.3f).copy(alpha = 0.10f)
    var y = r.top + 2f
    while (y < r.bottom) { drawLine(light, Offset(r.left, y), Offset(r.right, y), 0.8f); y += 3f }
    var x = r.left + 1f
    while (x < r.right) { drawLine(dark, Offset(x, r.top), Offset(x, r.bottom), 0.8f); x += 3f }
}

/**
 * Over the pages: the curve of the paper. Pages dip into the gutter and rise towards the
 * edges; that light and shade is what makes a flat picture read as a book.
 */
@Composable
fun BookFront(page: Rect, spread: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        if (page.width < 4f) return@Canvas
        if (spread) {
            val c = page.center.x
            val g = page.width * 0.07f
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.10f), 0.92f to Color.Black.copy(alpha = 0.28f), 1f to Color.Black.copy(alpha = 0.42f),
                    startX = c - g, endX = c,
                ),
                Offset(c - g, page.top), Size(g, page.height),
            )
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.42f), 0.08f to Color.Black.copy(alpha = 0.26f), 0.45f to Color.Black.copy(alpha = 0.08f), 1f to Color.Transparent,
                    startX = c, endX = c + g,
                ),
                Offset(c, page.top), Size(g, page.height),
            )
            // A faint highlight where each page bulges up.
            val hl = page.width * 0.12f
            listOf(c - g - hl, c + g).forEach { x ->
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), startX = x, endX = x + hl),
                    Offset(x, page.top), Size(hl, page.height),
                )
            }
        } else {
            val g = page.width * 0.08f
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.30f), 0.25f to Color.Black.copy(alpha = 0.10f), 1f to Color.Transparent,
                    startX = page.left, endX = page.left + g,
                ),
                Offset(page.left, page.top), Size(g, page.height),
            )
        }
        // The outer edges turn a little away from the light.
        val e = page.width * 0.025f
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)), startX = page.right - e, endX = page.right), Offset(page.right - e, page.top), Size(e, page.height))
        if (spread) drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent), startX = page.left, endX = page.left + e), Offset(page.left, page.top), Size(e, page.height))
    }
}

/** The turn in progress, drawn over the page view while it lasts. */
@Composable
fun TurnOverlay(turner: PageTurner, paper: Color) {
    val t = turner.turn ?: return
    val renderer = remember { CurlRenderer() }
    Canvas(Modifier.fillMaxSize()) {
        if (turner.version < 0) return@Canvas
        val r = turner.pageRect
        // Cover the live page: it is already changing underneath.
        drawRect(paper, r.topLeft, r.size)
        val before = turner.before
        val after = turner.after
        val spread = turner.spread
        fun half(b: Bitmap?, right: Boolean, dst: Rect): PageDraw? = b?.let {
            if (!spread) PageDraw(it, dst)
            else PageDraw(it, dst, if (right) android.graphics.Rect(it.width / 2, 0, it.width, it.height) else android.graphics.Rect(0, 0, it.width / 2, it.height))
        }
        val leftRect = Rect(r.left, r.top, r.center.x, r.bottom)
        val pages = CurlPages(
            fromLeft = if (spread) half(before, right = false, leftRect) else null,
            fromLeaf = half(before, right = true, t.leaf),
            back = if (spread) half(after, right = false, t.leaf) else null,
            toRight = half(after, right = true, t.leaf),
            paperBack = !spread,
        )
        drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, t, pages, rtl = false, background = 0, paper = paperArgb(paper)) }
    }
}

private fun paperArgb(c: Color): Int = android.graphics.Color.argb((c.alpha * 255).roundToInt(), (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

/**
 * Opening the book: the cover lies closed over the right-hand page and swings open on the
 * spine to the left, showing the inside of the board, then fades away.
 */
@Composable
fun CoverOpening(
    page: Rect,
    spread: Boolean,
    cover: ImageBitmap?,
    board: Color,
    ready: Boolean,
    place: @Composable () -> Unit = {},
    onDone: () -> Unit,
) {
    val angle = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var done by remember { mutableStateOf(false) }
    // The cover stays shut until the first page is there to be revealed.
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        angle.animateTo(180f, tween(1250, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(260, easing = LinearEasing))
        done = true
        onDone()
    }
    if (done || page.width < 4f) return
    val density = LocalDensity.current
    val leaf = if (spread) Rect(page.center.x, page.top, page.right, page.bottom) else page
    val pad = with(density) { 6.dp.toPx() }
    if (spread) {
        // A shut book is only as wide as its cover: the place shows where the left page will be,
        // until the cover swinging over has laid it open.
        val a = angle.value
        val reach = if (a <= 90f) 0f else -cos(Math.toRadians(a.toDouble())).toFloat() * leaf.width
        val margin = with(density) { 64.dp.toPx() }
        val hide = Rect(page.left - margin, page.top - margin, page.center.x - reach, page.bottom + margin * 1.4f)
        if (hide.width > 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value }
                    .drawWithContent { clipRect(hide.left, hide.top, hide.right, hide.bottom) { this@drawWithContent.drawContent() } },
            ) { place() }
        }
    }
    val w = leaf.width + pad
    val h = leaf.height + pad * 2
    Box(
        Modifier
            .offset { IntOffset((leaf.left).roundToInt(), (leaf.top - pad).roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = -angle.value
                cameraDistance = 14f * density.density * 72f
                alpha = fade.value
            },
    ) {
        val showFront = angle.value < 90f
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(lerp(board, Color.Black, 0.3f), cornerRadius = CornerRadius(pad))
            if (showFront) {
                if (cover != null) {
                    val inset = pad * 0.6f
                    val dw = size.width - inset * 2
                    val dh = size.height - inset * 2
                    // Fill the board with the cover, cropped to its shape.
                    val sw = cover.width.toFloat()
                    val sh = cover.height.toFloat()
                    val scale = maxOf(dw / sw, dh / sh)
                    val cw = (dw / scale).roundToInt().coerceAtMost(cover.width)
                    val ch = (dh / scale).roundToInt().coerceAtMost(cover.height)
                    drawImage(
                        cover,
                        srcOffset = IntOffset((cover.width - cw) / 2, (cover.height - ch) / 2),
                        srcSize = androidx.compose.ui.unit.IntSize(cw, ch),
                        dstOffset = IntOffset(inset.roundToInt(), inset.roundToInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(dw.roundToInt(), dh.roundToInt()),
                    )
                } else {
                    drawRoundRect(board, Offset(pad * 0.5f, pad * 0.5f), Size(size.width - pad, size.height - pad), CornerRadius(pad))
                }
                // The hinge groove near the spine, and light sliding over as it turns.
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endX = pad * 3), size = Size(pad * 3, size.height))
                drawRect(Color.Black.copy(alpha = min(0.5f, angle.value / 180f)))
            } else {
                // Inside of the board: endpaper.
                drawRoundRect(Color(0xFFE9DFC8), Offset(pad * 0.5f, pad * 0.5f), Size(size.width - pad, size.height - pad), CornerRadius(pad * 0.6f))
                drawRect(Color.Black.copy(alpha = (180f - angle.value) / 180f * 0.6f))
            }
        }
    }
}

/** The board colour of a book: the average of its cover, toned down; else the scene's. */
fun boardColor(cover: Bitmap?, fallback: Color): Color {
    cover ?: return fallback
    return runCatching {
        val small = Bitmap.createScaledBitmap(cover, 8, 8, true)
        var r = 0; var g = 0; var b = 0
        for (x in 0 until 8) for (y in 0 until 8) {
            val p = small.getPixel(x, y)
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        lerp(Color(r / 64, g / 64, b / 64), Color.Black, 0.3f)
    }.getOrDefault(fallback)
}
