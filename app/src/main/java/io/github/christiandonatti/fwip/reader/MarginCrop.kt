package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Trims plain white or black borders around scanned pages so the artwork uses more of the
 * screen. Conservative on purpose: only near-uniform borders go, never more than a quarter of
 * a side, and nothing at all when the page is art up to the edge.
 */
object MarginCrop {
    private const val SAMPLE = 240
    private const val TOLERANCE = 30
    private const val FILL = 0.992f
    private const val MAX_CUT = 0.25f

    fun crop(page: Bitmap): Bitmap {
        val scale = SAMPLE.toFloat() / max(page.width, page.height)
        if (scale >= 1f && max(page.width, page.height) < 64) return page
        val w = (page.width * scale).roundToInt().coerceAtLeast(16)
        val h = (page.height * scale).roundToInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(page, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== page) small.recycle()

        fun lum(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        val corners = listOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]).map(::lum).sorted()
        val bg = (corners[1] + corners[2]) / 2
        // Only paper-white or pure black borders count; anything else is artwork.
        if (bg in 46..214) return page

        fun plainRow(y: Int): Boolean {
            var n = 0
            for (x in 0 until w) if (abs(lum(px[y * w + x]) - bg) < TOLERANCE) n++
            return n >= FILL * w
        }
        fun plainCol(x: Int, y0: Int, y1: Int): Boolean {
            var n = 0
            for (y in y0 until y1) if (abs(lum(px[y * w + x]) - bg) < TOLERANCE) n++
            return n >= FILL * (y1 - y0)
        }

        val maxY = (h * MAX_CUT).toInt()
        val maxX = (w * MAX_CUT).toInt()
        var top = 0
        while (top < maxY && plainRow(top)) top++
        var bottom = h
        while (h - bottom < maxY && bottom - 1 > top && plainRow(bottom - 1)) bottom--
        var left = 0
        while (left < maxX && plainCol(left, top, bottom)) left++
        var right = w
        while (w - right < maxX && right - 1 > left && plainCol(right - 1, top, bottom)) right--

        // Keep a hair of margin so lines touching the edge are not shaved off.
        val pad = 1
        top = (top - pad).coerceAtLeast(0)
        left = (left - pad).coerceAtLeast(0)
        bottom = (bottom + pad).coerceAtMost(h)
        right = (right + pad).coerceAtMost(w)
        if (top <= 1 && left <= 1 && bottom >= h - 1 && right >= w - 1) return page

        val fx = page.width / w.toFloat()
        val fy = page.height / h.toFloat()
        val x0 = (left * fx).toInt().coerceIn(0, page.width - 1)
        val y0 = (top * fy).toInt().coerceIn(0, page.height - 1)
        val x1 = (right * fx).roundToInt().coerceIn(x0 + 1, page.width)
        val y1 = (bottom * fy).roundToInt().coerceIn(y0 + 1, page.height)
        return Bitmap.createBitmap(page, x0, y0, x1 - x0, y1 - y0)
    }
}
