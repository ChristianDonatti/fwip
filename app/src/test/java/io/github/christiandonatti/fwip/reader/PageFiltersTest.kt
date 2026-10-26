package io.github.christiandonatti.fwip.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFiltersTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xff
    private fun g(p: Int) = (p shr 8) and 0xff
    private fun b(p: Int) = p and 0xff

    @Test
    fun yellowedPaperTurnsWhiteAndInkBlack() {
        // A page of old yellow paper with some faded ink.
        val px = IntArray(100 * 100) { i -> if (i % 7 == 0) rgb(50, 48, 44) else rgb(232, 220, 170) }
        PageFilters(color = true).process(px, 100, 100)
        val paper = px[1]
        assertTrue("paper ${r(paper)},${g(paper)},${b(paper)}", r(paper) >= 250 && g(paper) >= 250 && b(paper) >= 250)
        val ink = px[0]
        assertTrue("ink ${r(ink)},${g(ink)},${b(ink)}", r(ink) <= 10 && b(ink) <= 10)
    }

    @Test
    fun cleanPagesAreLeftAlone() {
        val px = IntArray(50 * 50) { i -> if (i % 3 == 0) rgb(0, 0, 0) else rgb(255, 255, 255) }
        val before = px.copyOf()
        PageFilters(color = true).process(px, 50, 50)
        assertTrue(before.contentEquals(px))
    }

    @Test
    fun sharpenKeepsFlatAreasAndBoostsEdges() {
        val w = 20
        val px = IntArray(w * w) { i -> if (i % w < 10) rgb(100, 100, 100) else rgb(150, 150, 150) }
        PageFilters(sharp = Sharpness.Strong).process(px, w, w)
        assertEquals(100, r(px[2 * w + 3]))
        assertTrue(r(px[2 * w + 9]) < 100)
        assertTrue(r(px[2 * w + 10]) > 150)
    }

    @Test
    fun denoiseSmoothsGrainButNotLines() {
        val w = 30
        val px = IntArray(w * w) { i -> val x = i % w; if (x == 15) rgb(0, 0, 0) else rgb(200 + (i % 2) * 8, 200, 200) }
        PageFilters(denoise = true).process(px, w, w)
        assertEquals(0, r(px[5 * w + 15]))
        val a = r(px[5 * w + 4])
        val b = r(px[5 * w + 5])
        assertTrue("grain $a $b", Math.abs(a - b) <= 4)
    }

    @Test
    fun splitMapKeepsRealPages() {
        val m = PageMap(5, setOf(2), rtl = false)
        assertEquals(6, m.count)
        assertEquals(listOf(0, 1, 2, 2, 3, 4), (0 until m.count).map(m::real))
        assertEquals(listOf(0, 0, 1, 2, 0, 0), (0 until m.count).map(m::half))
        val manga = PageMap(5, setOf(2), rtl = true)
        assertEquals(listOf(0, 0, 2, 1, 0, 0), (0 until manga.count).map(manga::half))
        assertTrue(PageMap(3, emptySet(), false).identity)
    }
}
