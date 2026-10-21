package io.github.christiandonatti.fwip.balloons

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds panels by their gutters (recursive XY-cut): the page is split along full-width or
 * full-height strips of background colour, again and again, until no strip is left. Very precise
 * on classic layouts with white or black gutters; full-bleed or slanted layouts fall back to the
 * panels found by the ML model.
 */
object PanelCutter {
    private const val SIZE = 400
    private const val TOLERANCE = 28
    private const val FILL = 0.985f
    private const val MIN_GAP = 3
    private const val MIN_AREA = 0.02f

    fun cut(page: Bitmap): List<RectF> {
        val scale = SIZE.toFloat() / max(page.width, page.height)
        val w = (page.width * scale).roundToInt().coerceAtLeast(8)
        val h = (page.height * scale).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(page, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== page) small.recycle()

        val gray = IntArray(w * h) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        val border = buildList {
            for (x in 0 until w) { add(gray[x]); add(gray[(h - 1) * w + x]) }
            for (y in 0 until h) { add(gray[y * w]); add(gray[y * w + w - 1]) }
        }.sorted()
        val bg = border[border.size / 2]
        val isBg = BooleanArray(w * h) { abs(gray[it] - bg) < TOLERANCE }

        val out = mutableListOf<android.graphics.Rect>()
        split(isBg, w, 0, 0, w, h, 0, out)
        val minArea = MIN_AREA * w * h
        return out
            .filter { it.width() * it.height() > minArea }
            .map { RectF(it.left / w.toFloat(), it.top / h.toFloat(), it.right / w.toFloat(), it.bottom / h.toFloat()) }
    }

    private fun rowFilled(bg: BooleanArray, stride: Int, y: Int, x0: Int, x1: Int): Boolean {
        var n = 0
        for (x in x0 until x1) if (bg[y * stride + x]) n++
        return n >= FILL * (x1 - x0)
    }

    private fun colFilled(bg: BooleanArray, stride: Int, x: Int, y0: Int, y1: Int): Boolean {
        var n = 0
        for (y in y0 until y1) if (bg[y * stride + x]) n++
        return n >= FILL * (y1 - y0)
    }

    private fun split(
        bg: BooleanArray, stride: Int,
        x0In: Int, y0In: Int, x1In: Int, y1In: Int,
        depth: Int, out: MutableList<android.graphics.Rect>,
    ) {
        if (x1In - x0In < 8 || y1In - y0In < 8) return
        // Trim the empty margins around this block.
        var y0 = y0In
        var y1 = y1In
        var x0 = x0In
        var x1 = x1In
        while (y0 < y1 && rowFilled(bg, stride, y0, x0, x1)) y0++
        while (y1 > y0 && rowFilled(bg, stride, y1 - 1, x0, x1)) y1--
        while (x0 < x1 && colFilled(bg, stride, x0, y0, y1)) x0++
        while (x1 > x0 && colFilled(bg, stride, x1 - 1, y0, y1)) x1--
        if (x1 - x0 < 8 || y1 - y0 < 8) return

        if (depth < 6) {
            // Horizontal gutters first (rows of panels), then vertical ones.
            val rows = BooleanArray(y1 - y0) { rowFilled(bg, stride, y0 + it, x0, x1) }
            val hGaps = gaps(rows)
            if (hGaps.isNotEmpty()) {
                pieces(hGaps, rows.size).forEach { (a, b) -> split(bg, stride, x0, y0 + a, x1, y0 + b, depth + 1, out) }
                return
            }
            val cols = BooleanArray(x1 - x0) { colFilled(bg, stride, x0 + it, y0, y1) }
            val vGaps = gaps(cols)
            if (vGaps.isNotEmpty()) {
                pieces(vGaps, cols.size).forEach { (a, b) -> split(bg, stride, x0 + a, y0, x0 + b, y1, depth + 1, out) }
                return
            }
        }
        out += android.graphics.Rect(x0, y0, x1, y1)
    }

    /** Runs of empty lines at least [MIN_GAP] long, not touching the ends. */
    private fun gaps(mask: BooleanArray): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < mask.size) {
            if (mask[i]) {
                var j = i
                while (j < mask.size && mask[j]) j++
                if (j - i >= MIN_GAP && i > 0 && j < mask.size) res += i to j
                i = j
            } else {
                i++
            }
        }
        return res
    }

    private fun pieces(gaps: List<Pair<Int, Int>>, size: Int): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        var prev = 0
        for ((a, b) in gaps) {
            res += prev to a
            prev = b
        }
        res += prev to size
        return res
    }
}
