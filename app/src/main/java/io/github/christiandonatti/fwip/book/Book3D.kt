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

/** A comic book page (6.625 x 10.25 in). */
const val COMIC_ASPECT = 0.65f

/**
 * Where the pages of the 3D book go in a [w] x [h] area: a two-page spread on a wide screen
 * lying sideways, one page otherwise, [scale] of the room left by the bars.
 */
fun bookPageRect(w: Float, h: Float, top: Float, bottom: Float, scale: Float, spread: Boolean, page: Float = PAGE_ASPECT): Rect {
    val roomW = w * scale
    val roomH = (h - top - bottom) * scale
    val aspect = if (spread) page * 2f else page
    var pw = roomW
    var ph = pw / aspect
    if (ph > roomH) { ph = roomH; pw = ph * aspect }
    val left = (w - pw) / 2f
    val t = top + (h - top - bottom - ph) / 2f
    return Rect(left, t, left + pw, t + ph)
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
