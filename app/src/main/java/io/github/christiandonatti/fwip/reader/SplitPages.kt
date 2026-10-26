package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import io.github.christiandonatti.fwip.data.ComicSource

/**
 * Pages as the reader shows them when wide (double-page) images are split in two: each wide page
 * becomes two halves, in reading order (right half first in manga). Progress and bookmarks keep
 * using the real page numbers.
 */
class PageMap(realCount: Int, split: Set<Int>, rtl: Boolean) {
    private val reals: IntArray
    /** 0 the whole page, 1 its left half, 2 its right half. */
    private val halves: IntArray

    init {
        val r = ArrayList<Int>(realCount + split.size)
        val h = ArrayList<Int>(realCount + split.size)
        for (p in 0 until realCount) {
            if (p in split) {
                r += p; h += if (rtl) 2 else 1
                r += p; h += if (rtl) 1 else 2
            } else {
                r += p; h += 0
            }
        }
        reals = r.toIntArray()
        halves = h.toIntArray()
    }

    val count: Int get() = reals.size
    val identity: Boolean = reals.size == realCount

    fun real(v: Int): Int = reals[v.coerceIn(0, reals.size - 1)]
    fun half(v: Int): Int = halves[v.coerceIn(0, halves.size - 1)]
}

/** A comic seen through a [PageMap]: halves of wide pages are cut out as their own pages. */
class SplitSource(private val source: ComicSource, private val map: PageMap) : ComicSource {
    override val pageCount: Int get() = map.count

    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val real = map.real(index)
        val half = map.half(index)
        if (half == 0) return source.decodePage(real, maxWidth, maxHeight)
        // Decoded twice as wide so each half keeps the full sharpness of a single page.
        val whole = source.decodePage(real, maxWidth * 2, maxHeight) ?: return null
        val w = whole.width / 2
        val part = Bitmap.createBitmap(whole, if (half == 1) 0 else whole.width - w, 0, w, whole.height)
        if (part !== whole) whole.recycle()
        return part
    }

    override fun pageSize(index: Int): android.util.Size? {
        val size = source.pageSize(map.real(index)) ?: return null
        return if (map.half(index) == 0) size else android.util.Size(size.width / 2, size.height)
    }

    override fun readComicInfo(): ByteArray? = source.readComicInfo()

    /** The real source belongs to whoever opened it. */
    override fun close() {}
}
