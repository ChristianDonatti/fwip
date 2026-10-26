package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str

enum class Sharpness(@androidx.annotation.StringRes private val labelRes: Int, val amount: Int) {
    Off(R.string.sharp_off, 0), Light(R.string.sharp_light, 6), Strong(R.string.sharp_strong, 12);

    val label: String get() = str(labelRes)
}

/**
 * Touch-ups for scanned pages, applied once when a page is decoded:
 * - [color] stretches each colour channel between its darkest and brightest parts, which turns
 *   yellowed paper white again and brings faded ink back to black;
 * - [denoise] smooths grain and JPEG blotches, but only where the page is flat, so lines stay crisp;
 * - [sharp] is an unsharp mask that firms up soft scans.
 */
data class PageFilters(val color: Boolean = false, val denoise: Boolean = false, val sharp: Sharpness = Sharpness.Off) {
    val active: Boolean get() = color || denoise || sharp != Sharpness.Off

    fun apply(src: Bitmap): Bitmap {
        if (!active) return src
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src
        val px = IntArray(w * h)
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        soft.getPixels(px, 0, w, 0, 0, w, h)
        if (soft !== src) soft.recycle()
        process(px, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Works on packed ARGB pixels in place (kept apart from Bitmap so it can be tested). */
    fun process(px: IntArray, w: Int, h: Int) {
        if (color) levels(px)
        if (denoise) smooth(px, w, h)
        if (sharp != Sharpness.Off) sharpen(px, w, h, sharp.amount)
    }

    companion object {
        /** Per-channel auto levels from a sample of the page, ignoring the extreme 0.5%. */
        internal fun levels(px: IntArray) {
            val hist = Array(3) { IntArray(256) }
            val step = maxOf(1, px.size / 200_000)
            var n = 0
            var i = 0
            while (i < px.size) {
                val p = px[i]
                hist[0][(p shr 16) and 0xff]++
                hist[1][(p shr 8) and 0xff]++
                hist[2][p and 0xff]++
                n++
                i += step
            }
            val luts = Array(3) { c ->
                val lowCut = n / 200
                val highCut = n / 200
                var lo = 0
                var acc = 0
                while (lo < 255 && acc + hist[c][lo] <= lowCut) { acc += hist[c][lo]; lo++ }
                var hi = 255
                acc = 0
                while (hi > 0 && acc + hist[c][hi] <= highCut) { acc += hist[c][hi]; hi-- }
                // Pages that already use the whole range, or are nearly flat, are left alone.
                // The black point moves less than the white one: dark colour areas stay rich.
                lo = minOf(lo, 60)
                if (hi - lo < 80 || (lo <= 2 && hi >= 253)) null
                else IntArray(256) { v -> (((v - lo) * 255f) / (hi - lo)).toInt().coerceIn(0, 255) }
            }
            if (luts.all { it == null }) return
            val r = luts[0]
            val g = luts[1]
            val b = luts[2]
            for (k in px.indices) {
                val p = px[k]
                val rr = (p shr 16) and 0xff
                val gg = (p shr 8) and 0xff
                val bb = p and 0xff
                px[k] = (p and 0xff000000.toInt()) or
                    ((r?.get(rr) ?: rr) shl 16) or ((g?.get(gg) ?: gg) shl 8) or (b?.get(bb) ?: bb)
            }
        }

        /** 3x3 box average of each channel, from running sums (fast enough for whole pages). */
        internal fun blur(px: IntArray, w: Int, h: Int): IntArray {
            val tmp = IntArray(px.size)
            // Horizontal: sums of three, each channel kept in its own 10-bit lane.
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val a = px[row + maxOf(x - 1, 0)]
                    val b = px[row + x]
                    val c = px[row + minOf(x + 1, w - 1)]
                    val r = ((a shr 16) and 0xff) + ((b shr 16) and 0xff) + ((c shr 16) and 0xff)
                    val g = ((a shr 8) and 0xff) + ((b shr 8) and 0xff) + ((c shr 8) and 0xff)
                    val bl = (a and 0xff) + (b and 0xff) + (c and 0xff)
                    tmp[row + x] = (r shl 20) or (g shl 10) or bl
                }
            }
            val out = IntArray(px.size)
            for (y in 0 until h) {
                val up = maxOf(y - 1, 0) * w
                val mid = y * w
                val down = minOf(y + 1, h - 1) * w
                for (x in 0 until w) {
                    val a = tmp[up + x]
                    val b = tmp[mid + x]
                    val c = tmp[down + x]
                    val r = (((a shr 20) and 0x3ff) + ((b shr 20) and 0x3ff) + ((c shr 20) and 0x3ff)) / 9
                    val g = (((a shr 10) and 0x3ff) + ((b shr 10) and 0x3ff) + ((c shr 10) and 0x3ff)) / 9
                    val bl = ((a and 0x3ff) + (b and 0x3ff) + (c and 0x3ff)) / 9
                    out[mid + x] = (px[mid + x] and 0xff000000.toInt()) or (r shl 16) or (g shl 8) or bl
                }
            }
            return out
        }

        /** Replaces a pixel by its neighbourhood average only where it differs little from it (grain, not lines). */
        internal fun smooth(px: IntArray, w: Int, h: Int) {
            val avg = blur(px, w, h)
            for (k in px.indices) {
                val p = px[k]
                val m = avg[k]
                val d = Math.abs(((p shr 16) and 0xff) - ((m shr 16) and 0xff)) +
                    Math.abs(((p shr 8) and 0xff) - ((m shr 8) and 0xff)) +
                    Math.abs((p and 0xff) - (m and 0xff))
                if (d < 36) px[k] = m
            }
        }

        /** Unsharp mask: pushes each pixel away from its neighbourhood average by amount/10. */
        internal fun sharpen(px: IntArray, w: Int, h: Int, amount: Int) {
            val avg = blur(px, w, h)
            for (k in px.indices) {
                val p = px[k]
                val m = avg[k]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                val r2 = (r + (r - ((m shr 16) and 0xff)) * amount / 10).coerceIn(0, 255)
                val g2 = (g + (g - ((m shr 8) and 0xff)) * amount / 10).coerceIn(0, 255)
                val b2 = (b + (b - (m and 0xff)) * amount / 10).coerceIn(0, 255)
                px[k] = (p and 0xff000000.toInt()) or (r2 shl 16) or (g2 shl 8) or b2
            }
        }
    }
}
