package io.github.christiandonatti.fwip.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadingLightTest {
    @Test
    fun sunTimesForCuritiba() {
        // Curitiba, 5 November: sunrise about 5:30, sunset about 18:35 (no daylight saving).
        val t = SunTimes.compute(-25.43, -49.27, LocalDate.of(2026, 11, 5), ZoneId.of("America/Sao_Paulo"))
        assertNotNull(t)
        val (rise, set) = t!!
        assertTrue("rise $rise", rise in (5 * 60 + 10)..(5 * 60 + 50))
        assertTrue("set $set", set in (18 * 60 + 15)..(19 * 60 + 10))
    }

    @Test
    fun polarNightHasNoSun() {
        val t = SunTimes.compute(78.2, 15.6, LocalDate.of(2026, 12, 21), ZoneId.of("Arctic/Longyearbyen"))
        assertEquals(null, t)
    }

    @Test
    fun warmHoursRampAtBothEnds() {
        val start = 21 * 60
        val end = 7 * 60
        assertEquals(0f, LightSettings.nightFactor(12 * 60, start, end), 0.001f)
        assertEquals(0f, LightSettings.nightFactor(start, start, end), 0.001f)
        assertEquals(0.5f, LightSettings.nightFactor(start + 22, start, end), 0.03f)
        assertEquals(1f, LightSettings.nightFactor(2 * 60, start, end), 0.001f)
        assertEquals(1f, LightSettings.nightFactor(23 * 60 + 59, start, end), 0.001f)
        assertTrue(LightSettings.nightFactor(end - 10, start, end) in 0.1f..0.4f)
        assertEquals(0f, LightSettings.nightFactor(end + 1, start, end), 0.001f)
    }

    @Test
    fun lightBelowTheMinimumAddsAVeil() {
        assertEquals(-1f to 0f, LightSettings.screenFor(-1f))
        val (screen, veil) = LightSettings.screenFor(0f)
        assertEquals(0.01f, screen, 0.0001f)
        assertEquals(0.6f, veil, 0.0001f)
        val (full, none) = LightSettings.screenFor(1f)
        assertEquals(1f, full, 0.0001f)
        assertEquals(0f, none, 0.0001f)
        assertTrue(LightSettings.levelForLux(0f) < LightSettings.DIM_ZONE)
        assertTrue(LightSettings.levelForLux(1000f) > 0.8f)
    }

    @Test
    fun inkPagesUseSixteenGreysBetweenPaperAndInk() {
        val w = 64
        val px = IntArray(w * 4) { i -> val v = (i % w) * 4; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        PageFilters.inkScreen(px, w, color = false)
        val greys = px.map { (it shr 16) and 0xff }.toSet()
        assertTrue("${greys.size} levels", greys.size in 2..16)
        assertTrue(greys.minOrNull()!! >= 28)
        assertTrue(greys.maxOrNull()!! <= 236)
        // Grey stays grey (blue a touch lower: warm paper).
        px.forEach { p -> assertTrue(((p shr 16) and 0xff) - (p and 0xff) in -4..13) }
    }

    @Test
    fun colourInkKeepsHueButSoftens() {
        val px = intArrayOf((0xFF shl 24) or (255 shl 16))
        PageFilters.inkScreen(px, 1, color = true)
        val r = (px[0] shr 16) and 0xff
        val g = (px[0] shr 8) and 0xff
        assertTrue("red $r green $g", r > g + 40)
        assertTrue(r <= 236)
    }
}
