package io.github.christiandonatti.fwip.book.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Every scene in two parts: the still picture (drawn once, cached) and what moves, which is
 * also laid over the photo version. Coordinates are fractions of the screen, so the scene
 * fits any tablet or phone; the book covers the middle, so the life is near the edges.
 */
internal abstract class ScenePainter {
    private val still = StillLayer()

    /** The drawn version: the still part, then the moving part. */
    fun DrawScope.drawn(t: Float) {
        with(still) { draw(null) { paintStill() } }
        paintMoving(t, photo = false)
    }

    abstract fun DrawScope.paintStill()

    /** What moves; over a photo only the touches that fit any picture of the place. */
    abstract fun DrawScope.paintMoving(t: Float, photo: Boolean)
}

internal fun painterFor(scene: Scene): ScenePainter = when (scene) {
    Scene.Desk -> DeskPainter()
    Scene.Library -> LibraryPainter()
    Scene.Fireplace -> FireplacePainter()
    Scene.Mountain -> MountainPainter()
    Scene.Park -> ParkPainter()
    Scene.Rain -> RainPainter()
    Scene.Beach -> BeachPainter()
    Scene.Night -> NightPainter()
}

// ---------------------------------------------------------------- Desk, seen from above

internal class DeskPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        woodTop()
        props()
    }

    fun DrawScope.woodTop() {
        val plank = size.height / 5.5f
        var y = 0f
        var i = 0
        while (y < size.height) {
            val tone = lerp(Color(0xFF6A4630), Color(0xFF8A5E3E), hash(i * 17))
            drawRect(tone, Offset(0f, y), Size(size.width, plank))
            // Grain: long wavy lines, darker and lighter.
            for (g in 0 until 28) {
                val gy = y + plank * hash(i * 100 + g)
                val path = Path()
                path.moveTo(0f, gy)
                var x = 0f
                while (x <= size.width) {
                    path.lineTo(x, gy + sin(x / size.width * 9f + g + i) * plank * 0.04f + (fbm(x / size.width * 6f, i * 31 + g) - 0.5f) * plank * 0.08f)
                    x += 24f
                }
                drawPath(path, (if (g % 3 == 0) Color(0xFF3B2416) else Color(0xFFB07A50)).copy(alpha = 0.10f + hash(g + i) * 0.10f), style = Stroke(width = 0.8f + hash(g * 3 + i) * 1.6f))
            }
            // A knot here and there.
            if (hash(i * 7) > 0.5f) {
                val k = Offset(size.width * hash(i * 9), y + plank * (0.3f + 0.4f * hash(i * 11)))
                for (r in 0 until 5) drawOval(Color(0xFF3B2416).copy(alpha = 0.18f), Offset(k.x - 14f - r * 6f, k.y - 5f - r * 2f), Size(28f + r * 12f, 10f + r * 4f), style = Stroke(1.2f))
            }
            drawLine(Color(0xFF241409).copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5f)
            y += plank
            i++
        }
    }

    fun DrawScope.props() {
        val u = minOf(size.width, size.height)
        // Reading glasses, top left.
        val g = Offset(size.width * 0.085f, size.height * 0.16f)
        val lens = u * 0.055f
        rotate(-18f, g) {
            for (dx in listOf(-1f, 1f)) {
                val c = Offset(g.x + dx * lens * 1.15f, g.y)
                drawCircle(Color.Black.copy(alpha = 0.18f), lens, c + Offset(5f, 7f))
                drawCircle(Color(0xFFB9D3E0).copy(alpha = 0.18f), lens, c)
                drawCircle(Color(0xFF2A1A12), lens, c, style = Stroke(u * 0.007f))
                drawLine(Color(0xFF2A1A12), Offset(c.x + dx * lens, c.y), Offset(c.x + dx * lens * 2.6f, c.y + lens * 0.4f), strokeWidth = u * 0.006f)
            }
            drawLine(Color(0xFF2A1A12), Offset(g.x - lens * 0.2f, g.y - lens * 0.2f), Offset(g.x + lens * 0.2f, g.y - lens * 0.2f), strokeWidth = u * 0.006f)
        }
        // Pencil, bottom left.
        val p0 = Offset(size.width * 0.03f, size.height * 0.86f)
        rotate(-24f, p0) {
            drawRoundRect(Color.Black.copy(alpha = 0.2f), p0 + Offset(4f, 6f), Size(u * 0.32f, u * 0.022f), CornerRadius(4f))
            drawRect(Color(0xFFE8B23A), p0, Size(u * 0.28f, u * 0.022f))
            drawRect(Color(0xFFC9C3B6), p0 + Offset(u * 0.28f, 0f), Size(u * 0.02f, u * 0.022f))
            drawRect(Color(0xFFE07A8A), p0 + Offset(u * 0.30f, 0f), Size(u * 0.02f, u * 0.022f))
            drawPath(Path().apply { moveTo(p0.x, p0.y); lineTo(p0.x - u * 0.04f, p0.y + u * 0.011f); lineTo(p0.x, p0.y + u * 0.022f); close() }, Color(0xFFE9D3B0))
            drawPath(Path().apply { moveTo(p0.x - u * 0.028f, p0.y + u * 0.007f); lineTo(p0.x - u * 0.04f, p0.y + u * 0.011f); lineTo(p0.x - u * 0.028f, p0.y + u * 0.015f); close() }, Color(0xFF333333))
        }
        // A small succulent, top right.
        val pl = Offset(size.width * 0.93f, size.height * 0.14f)
        drawCircle(Color.Black.copy(alpha = 0.22f), u * 0.085f, pl + Offset(8f, 10f))
        drawCircle(Color(0xFFB5654A), u * 0.085f, pl)
        drawCircle(Color(0xFF4A3426), u * 0.072f, pl)
        for (ring in 0 until 3) {
            val n = 8 - ring * 2
            for (k in 0 until n) {
                val a = k * 360f / n + ring * 20f
                rotate(a, pl) {
                    val len = u * (0.07f - ring * 0.018f)
                    drawOval(lerp(Color(0xFF6F9C66), Color(0xFFA8CC93), ring / 2f), Offset(pl.x - len * 0.25f, pl.y - len), Size(len * 0.5f, len))
                }
            }
        }
        // Sticky note.
        val n0 = Offset(size.width * 0.04f, size.height * 0.44f)
        rotate(6f, n0) {
            drawRect(Color.Black.copy(alpha = 0.15f), n0 + Offset(4f, 6f), Size(u * 0.12f, u * 0.12f))
            drawRect(Color(0xFFF6E27A), n0, Size(u * 0.12f, u * 0.12f))
            for (l in 0 until 4) drawLine(Color(0xFF3D5A8A).copy(alpha = 0.55f), n0 + Offset(u * 0.015f, u * (0.03f + l * 0.022f)), n0 + Offset(u * (0.09f - l * 0.01f), u * (0.03f + l * 0.022f)), strokeWidth = 1.5f)
        }
        mug()
    }

    fun DrawScope.mug() {
        val u = minOf(size.width, size.height)
        val m = Offset(size.width * 0.92f, size.height * 0.80f)
        val r = u * 0.075f
        drawCircle(Color.Black.copy(alpha = 0.25f), r * 1.08f, m + Offset(r * 0.18f, r * 0.24f))
        drawRoundRect(Color(0xFFE9E2D6), Offset(m.x - r * 1.75f, m.y - r * 0.18f), Size(r * 0.8f, r * 0.36f), CornerRadius(r * 0.18f))
        drawCircle(Color(0xFFEFE9DE), r, m)
        drawCircle(Color(0xFFCFC6B6), r, m, style = Stroke(r * 0.08f))
        drawCircle(Color(0xFF3A2214), r * 0.8f, m)
        drawCircle(Color(0xFF7A4E2E).copy(alpha = 0.55f), r * 0.8f, m, style = Stroke(r * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.25f), r * 0.16f, m + Offset(-r * 0.32f, -r * 0.32f))
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        if (photo) props()
        val u = minOf(size.width, size.height)
        // Lamp light pooled at the top left, breathing very slightly.
        val breath = 0.94f + 0.06f * noise(t * 0.5f, 3)
        drawRect(
            Brush.radialGradient(
                0f to Color(0xFFFFE2A8).copy(alpha = 0.30f * breath), 0.5f to Color(0xFFFFC870).copy(alpha = 0.10f), 1f to Color.Transparent,
                center = Offset(size.width * 0.18f, size.height * 0.10f), radius = size.width * 0.75f,
            ),
            blendMode = BlendMode.Screen,
        )
        // Steam over the coffee: soft curls drifting up and away.
        val m = Offset(size.width * 0.92f, size.height * 0.80f)
        for (i in 0 until 4) {
            val life = 4.5f
            val phase = ((t + i * life / 4f) % life) / life
            val path = Path()
            val x0 = m.x + (noise(t * 0.4f + i, 21) - 0.5f) * u * 0.04f
            path.moveTo(x0, m.y)
            for (k in 1..12) {
                val s = k / 12f
                path.lineTo(x0 + sin(s * 6f + t * 1.4f + i) * u * 0.02f * s, m.y - s * u * 0.22f * (0.4f + phase))
            }
            drawPath(path, Color.White.copy(alpha = 0.16f * sin(phase * PI.toFloat())), style = Stroke(width = u * 0.016f))
        }
        dust(t, 40, 5) { p -> (1f - (p - Offset(size.width * 0.18f, size.height * 0.1f)).getDistance() / size.width).coerceIn(0f, 1f) }
        vignette(0.5f)
    }
}

// ---------------------------------------------------------------- Library

internal class LibraryPainter : ScenePainter() {
    private val spines = listOf(0xFF7A2E2A, 0xFF2F4A6B, 0xFF365A3C, 0xFFA8792F, 0xFF5C3A28, 0xFF6B4C7A, 0xFF8C3B24, 0xFF2E3A2F, 0xFFB59B6A, 0xFF46302A)

    override fun DrawScope.paintStill() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1A12), Color(0xFF1A0F0A))))
        // The tall window at the back, behind the book.
        val win = Rect(size.width * 0.38f, size.height * 0.05f, size.width * 0.62f, size.height * 0.66f)
        val arch = Path().apply {
            moveTo(win.left, win.bottom); lineTo(win.left, win.top + win.width / 2)
            arcTo(Rect(win.left, win.top, win.right, win.top + win.width), 180f, 180f, false)
            lineTo(win.right, win.bottom); close()
        }
        drawPath(arch, Brush.verticalGradient(listOf(Color(0xFFFFF0CF), Color(0xFFE8C98E), Color(0xFFB98B54)), startY = win.top, endY = win.bottom))
        drawPath(arch, Color(0xFF3B2416), style = Stroke(size.width * 0.012f))
        drawLine(Color(0xFF3B2416), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), strokeWidth = size.width * 0.006f)
        for (k in 1..3) drawLine(Color(0xFF3B2416), Offset(win.left, win.top + win.height * k / 4f + win.width * 0.2f), Offset(win.right, win.top + win.height * k / 4f + win.width * 0.2f), strokeWidth = size.width * 0.005f)
        // Floor and rug.
        val floor = size.height * 0.78f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3B2416), Color(0xFF24150D)), startY = floor, endY = size.height), Offset(0f, floor), Size(size.width, size.height - floor))
        drawOval(Color(0xFF6B2420), Offset(size.width * 0.2f, size.height * 0.84f), Size(size.width * 0.6f, size.height * 0.2f))
        drawOval(Color(0xFFB0813F).copy(alpha = 0.5f), Offset(size.width * 0.24f, size.height * 0.86f), Size(size.width * 0.52f, size.height * 0.16f), style = Stroke(6f))
        // Shelves on both sides, full of books.
        shelves(0f, size.width * 0.34f, 1)
        shelves(size.width * 0.66f, size.width, 2)
        // A ladder leaning on the right shelves.
        val lx = size.width * 0.80f
        drawLine(Color(0xFF8A5E3E), Offset(lx, size.height * 0.08f), Offset(lx - size.width * 0.05f, size.height * 0.95f), strokeWidth = 7f)
        drawLine(Color(0xFF8A5E3E), Offset(lx + size.width * 0.045f, size.height * 0.08f), Offset(lx - size.width * 0.005f, size.height * 0.95f), strokeWidth = 7f)
        for (k in 0 until 9) {
            val f = 0.1f + k * 0.1f
            val y = size.height * (0.08f + 0.87f * f)
            val x = lx - size.width * 0.05f * f
            drawLine(Color(0xFF6E4A30), Offset(x, y), Offset(x + size.width * 0.045f, y), strokeWidth = 5f)
        }
    }

    private fun DrawScope.shelves(x0: Float, x1: Float, seed: Int) {
        drawRect(Color(0xFF3A2416), Offset(x0, 0f), Size(x1 - x0, size.height))
        val rows = 6
        val rowH = size.height * 0.95f / rows
        for (r in 0 until rows) {
            val bottom = rowH * (r + 1)
            var x = x0 + 10f
            var i = 0
            while (x < x1 - 14f) {
                val k = seed * 1000 + r * 100 + i
                if (hash(k * 5) > 0.93f) { x += size.width * 0.02f; i++; continue }
                val w = size.width * (0.008f + hash(k) * 0.011f)
                val h = rowH * (0.62f + hash(k * 3) * 0.3f)
                val c = Color(spines[(hash(k * 7) * spines.size).toInt().coerceIn(0, spines.size - 1)])
                val lean = if (hash(k * 11) > 0.95f) 8f else 0f
                rotate(lean, Offset(x + w, bottom)) {
                    drawRect(lerp(c, Color.Black, hash(k * 13) * 0.3f), Offset(x, bottom - h), Size(w, h))
                    drawRect(Color.Black.copy(alpha = 0.25f), Offset(x + w - 2f, bottom - h), Size(2f, h))
                    if (hash(k * 17) > 0.4f) {
                        drawRect(Color(0xFFD9B66A).copy(alpha = 0.7f), Offset(x + 1f, bottom - h * 0.85f), Size(w - 2f, 2f))
                        drawRect(Color(0xFFD9B66A).copy(alpha = 0.7f), Offset(x + 1f, bottom - h * 0.78f), Size(w - 2f, 2f))
                    }
                }
                x += w + 1f + lean
                i++
            }
            drawRect(Color(0xFF5A3A24), Offset(x0, bottom), Size(x1 - x0, 9f))
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(x0, bottom + 9f), Size(x1 - x0, 6f))
        }
        // Uprights.
        drawRect(Color(0xFF4A2E1C), Offset(x0, 0f), Size(12f, size.height))
        drawRect(Color(0xFF4A2E1C), Offset(x1 - 12f, 0f), Size(12f, size.height))
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        // A beam of light from the window to the floor, with dust floating in it.
        val a = Offset(size.width * 0.40f, size.height * 0.1f)
        val b = Offset(size.width * 0.62f, size.height * 0.1f)
        val beam = Path().apply {
            moveTo(a.x, a.y); lineTo(b.x, b.y)
            lineTo(size.width * 0.42f, size.height); lineTo(size.width * 0.08f, size.height); close()
        }
        val sway = 0.85f + 0.15f * noise(t * 0.2f, 9)
        drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFFE6B0).copy(alpha = 0.16f * sway), Color(0xFFFFE6B0).copy(alpha = 0.03f))), blendMode = BlendMode.Screen)
        dust(t, 110, 13) { p ->
            val f = (p.y / size.height).coerceIn(0f, 1f)
            val left = a.x + (size.width * 0.08f - a.x) * f
            val right = b.x + (size.width * 0.42f - b.x) * f
            if (p.x in left..right) 1f else 0.08f
        }
        if (!photo) vignette(0.6f) else vignette(0.4f)
    }
}

// ---------------------------------------------------------------- By the fire

internal class FireplacePainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1712), Color(0xFF160B08))))
        // Wallpaper stripes.
        var x = 0f
        while (x < size.width) { drawRect(Color.White.copy(alpha = 0.025f), Offset(x, 0f), Size(size.width * 0.012f, size.height)); x += size.width * 0.04f }
        val floor = size.height * 0.80f
        drawRect(Color(0xFF2E1A10), Offset(0f, floor), Size(size.width, size.height - floor))
        for (k in 0 until 8) drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, floor + k * (size.height - floor) / 8f), Offset(size.width, floor + k * (size.height - floor) / 8f), strokeWidth = 2f)
        // The fireplace on the left.
        val fp = Rect(size.width * 0.02f, size.height * 0.36f, size.width * 0.32f, floor)
        drawRect(Color(0xFF6B3B2B), fp.topLeft, fp.size)
        val brickH = fp.height / 14f
        var row = 0
        var y = fp.top
        while (y < fp.bottom) {
            drawLine(Color(0xFF3A1E14), Offset(fp.left, y), Offset(fp.right, y), strokeWidth = 2f)
            var bx = fp.left + if (row % 2 == 0) 0f else fp.width / 12f
            while (bx < fp.right) { drawLine(Color(0xFF3A1E14), Offset(bx, y), Offset(bx, y + brickH), strokeWidth = 2f); bx += fp.width / 6f }
            for (k in 0 until 6) drawRect(Color.Black.copy(alpha = hash(row * 13 + k) * 0.18f), Offset(fp.left + k * fp.width / 6f, y), Size(fp.width / 6f, brickH))
            y += brickH; row++
        }
        // Mantel and what stands on it.
        drawRect(Color(0xFF3A2216), Offset(fp.left - size.width * 0.015f, fp.top - size.height * 0.03f), Size(fp.width + size.width * 0.03f, size.height * 0.035f))
        val clock = Offset(fp.center.x, fp.top - size.height * 0.08f)
        drawCircle(Color(0xFF8A6A3A), size.height * 0.045f, clock)
        drawCircle(Color(0xFFEDE3CC), size.height * 0.036f, clock)
        drawLine(Color(0xFF2A1A12), clock, clock + Offset(0f, -size.height * 0.026f), strokeWidth = 2.5f)
        drawLine(Color(0xFF2A1A12), clock, clock + Offset(size.height * 0.018f, 0f), strokeWidth = 2.5f)
        for (cx in listOf(fp.left + fp.width * 0.12f, fp.right - fp.width * 0.12f)) drawRect(Color(0xFFEDE3CC), Offset(cx - 6f, fp.top - size.height * 0.11f), Size(12f, size.height * 0.08f))
        // The opening and the logs.
        val box = Rect(fp.left + fp.width * 0.16f, fp.top + fp.height * 0.32f, fp.right - fp.width * 0.16f, floor)
        drawRoundRect(Color(0xFF0E0504), box.topLeft, box.size, CornerRadius(box.width * 0.3f, box.width * 0.3f))
        val logY = floor - fp.height * 0.08f
        rotate(-8f, Offset(box.center.x, logY)) { drawRoundRect(Color(0xFF3A2216), Offset(box.left + box.width * 0.08f, logY - 12f), Size(box.width * 0.8f, 24f), CornerRadius(12f)) }
        rotate(10f, Offset(box.center.x, logY)) { drawRoundRect(Color(0xFF4A2C1C), Offset(box.left + box.width * 0.12f, logY - 6f), Size(box.width * 0.76f, 22f), CornerRadius(11f)) }
        // A snowy window and an armchair on the right.
        val win = Rect(size.width * 0.78f, size.height * 0.12f, size.width * 0.96f, size.height * 0.52f)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1630), Color(0xFF1E3358)), startY = win.top, endY = win.bottom), win.topLeft, win.size)
        drawCircle(Color(0xFFF2EEDC), size.height * 0.035f, Offset(win.left + win.width * 0.7f, win.top + win.height * 0.25f))
        drawRect(Color(0xFF3A2216), win.topLeft, win.size, style = Stroke(size.width * 0.01f))
        drawLine(Color(0xFF3A2216), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), strokeWidth = size.width * 0.006f)
        drawLine(Color(0xFF3A2216), Offset(win.left, win.center.y), Offset(win.right, win.center.y), strokeWidth = size.width * 0.006f)
        val chair = Rect(size.width * 0.74f, size.height * 0.56f, size.width * 1.02f, floor + size.height * 0.08f)
        drawRoundRect(Color(0xFF5A1C1C), chair.topLeft, chair.size, CornerRadius(chair.width * 0.18f))
        drawRoundRect(Color(0xFF6E2424), Offset(chair.left - chair.width * 0.06f, chair.top + chair.height * 0.35f), Size(chair.width * 0.22f, chair.height * 0.6f), CornerRadius(chair.width * 0.1f))
        drawRoundRect(Color(0xFF4A1616), Offset(chair.left + chair.width * 0.12f, chair.top + chair.height * 0.55f), Size(chair.width * 0.8f, chair.height * 0.25f), CornerRadius(chair.width * 0.06f))
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        if (!photo) {
            val fp = Rect(size.width * 0.02f, size.height * 0.36f, size.width * 0.32f, size.height * 0.80f)
            val base = Offset(fp.center.x, size.height * 0.80f - fp.height * 0.08f)
            clipRect(fp.left + fp.width * 0.16f, fp.top + fp.height * 0.2f, fp.right - fp.width * 0.16f, size.height * 0.80f) {
                flames(base, fp.width * 0.6f, fp.height * 0.55f, t, seed = 1)
            }
            embers(Offset(base.x, base.y - fp.height * 0.3f), fp.width * 0.4f, size.height * 0.5f, t, 26)
            // Candles on the mantel.
            for ((i, cx) in listOf(fp.left + fp.width * 0.12f, fp.right - fp.width * 0.12f).withIndex()) {
                val tip = Offset(cx, fp.top - size.height * 0.11f)
                val f = 0.8f + 0.2f * noise(t * 5f + i * 3, 7)
                glow(tip, size.height * 0.05f * f, Color(0xFFFFB04A), 0.4f)
                drawOval(Color(0xFFFFD27A), Offset(tip.x - 4f, tip.y - 16f * f), Size(8f, 16f * f))
            }
            // Snow outside the window.
            val win = Rect(size.width * 0.78f, size.height * 0.12f, size.width * 0.96f, size.height * 0.52f)
            clipRect(win.left, win.top, win.right, win.bottom) {
                for (i in 0 until 40) {
                    val ph = ((t * (0.05f + hash(i) * 0.05f) + hash(i * 3)) % 1f)
                    val x = win.left + (hash(i * 7) + sin(t * 0.5f + i) * 0.03f) * win.width
                    drawCircle(Color.White.copy(alpha = 0.8f), 1.5f + hash(i * 5) * 1.5f, Offset(x, win.top + ph * win.height))
                }
            }
        } else {
            embers(Offset(size.width * FIRE_X, size.height * FIRE_Y), size.width * 0.12f, size.height * 0.45f, t, 22)
        }
        // The whole room flickers with the fire.
        val f = fbm(t * 2.7f, 5)
        drawRect(Color(0xFFFF8A2A).copy(alpha = 0.03f + 0.07f * f), blendMode = BlendMode.Screen)
        drawRect(Color.Black.copy(alpha = 0.05f * (1f - f)))
        vignette(0.6f)
    }

    companion object {
        /** Where the fire is in the photo, as a share of the screen (set from the chosen picture). */
        var FIRE_X = 0.2f
        var FIRE_Y = 0.7f
    }
}

// ---------------------------------------------------------------- Mountains

internal class MountainPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        verticalSky(0f to Color(0xFF3E6FA8), 0.45f to Color(0xFF9CC3E3), 0.62f to Color(0xFFF3D9B8), until = size.height * 0.7f)
        drawRect(Color(0xFFF3D9B8), Offset(0f, size.height * 0.6f), Size(size.width, size.height * 0.4f))
        glow(Offset(size.width * 0.8f, size.height * 0.34f), size.height * 0.45f, Color(0xFFFFE9C4), 0.6f)
        drawCircle(Color(0xFFFFF6E0), size.height * 0.04f, Offset(size.width * 0.8f, size.height * 0.34f))
        ridge(size.height * 0.58f, size.height * 0.38f, 3.2f, 11, Color(0xFFAFC0D6), snow = 0.5f)
        ridge(size.height * 0.64f, size.height * 0.32f, 4.1f, 23, Color(0xFF8499B6), snow = 0.35f)
        ridge(size.height * 0.72f, size.height * 0.24f, 5.3f, 37, Color(0xFF5F7695))
        ridge(size.height * 0.82f, size.height * 0.16f, 6.5f, 51, Color(0xFF3D536E))
        ridge(size.height * 0.93f, size.height * 0.12f, 8f, 67, Color(0xFF223447))
        pines(0f, size.width * 0.26f, size.height * 0.92f, size.height * 0.22f, Color(0xFF14212E), 3, 9)
        pines(size.width * 0.74f, size.width, size.height * 0.92f, size.height * 0.24f, Color(0xFF14212E), 5, 9)
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        if (!photo) {
            for (i in 0 until 4) {
                val x = ((hash(i * 3) * size.width * 1.5f + t * (5f + i * 2f)) % (size.width * 1.5f)) - size.width * 0.25f
                cloud(Offset(x, size.height * (0.1f + 0.08f * i)), size.width * (0.16f + hash(i) * 0.1f), Color.White.copy(alpha = 0.55f), i * 9)
            }
        }
        mist(t, 3, size.height * 0.68f, size.height * 0.12f, alpha = if (photo) 0.12f else 0.18f)
        mist(t * 0.7f, 8, size.height * 0.82f, size.height * 0.1f, alpha = if (photo) 0.08f else 0.14f)
        birds(t, 4, size.height * 0.12f, size.height * 0.35f)
        vignette(0.35f)
    }
}

// ---------------------------------------------------------------- Park

internal class ParkPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        verticalSky(0f to Color(0xFF6FA8DC), 1f to Color(0xFFD7ECF6), until = size.height * 0.56f)
        // Far trees.
        for (i in 0 until 26) {
            val x = size.width * i / 25f
            val r = size.height * (0.05f + hash(i * 3) * 0.05f)
            drawCircle(Color(0xFF6E8F6A), r, Offset(x, size.height * 0.56f - r * 0.4f))
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF8DBA6A), Color(0xFF5E8E43), Color(0xFF3F6B2E)), startY = size.height * 0.55f, endY = size.height), Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.45f))
        // The path.
        drawPath(Path().apply {
            moveTo(size.width * 0.47f, size.height * 0.56f); lineTo(size.width * 0.53f, size.height * 0.56f)
            cubicTo(size.width * 0.6f, size.height * 0.75f, size.width * 0.75f, size.height * 0.9f, size.width * 0.82f, size.height)
            lineTo(size.width * 0.38f, size.height)
            cubicTo(size.width * 0.44f, size.height * 0.85f, size.width * 0.42f, size.height * 0.7f, size.width * 0.47f, size.height * 0.56f)
            close()
        }, Color(0xFFD8C9A3))
        // Lamp post on the left, bench on the right.
        drawRect(Color(0xFF2B2B2B), Offset(size.width * 0.18f, size.height * 0.4f), Size(8f, size.height * 0.5f))
        drawRoundRect(Color(0xFF2B2B2B), Offset(size.width * 0.18f - 14f, size.height * 0.36f), Size(36f, 44f), CornerRadius(8f))
        drawRoundRect(Color(0xFFFFF2C8), Offset(size.width * 0.18f - 8f, size.height * 0.37f), Size(24f, 30f), CornerRadius(5f))
        val bench = Rect(size.width * 0.80f, size.height * 0.80f, size.width * 0.97f, size.height * 0.86f)
        drawRect(Color(0xFF6B4A30), bench.topLeft, Size(bench.width, 10f))
        drawRect(Color(0xFF6B4A30), Offset(bench.left, bench.top - 30f), Size(bench.width, 10f))
        drawRect(Color(0xFF2B2B2B), Offset(bench.left + 10f, bench.top), Size(6f, 40f))
        drawRect(Color(0xFF2B2B2B), Offset(bench.right - 16f, bench.top), Size(6f, 40f))
    }

    private fun DrawScope.tree(x: Float, ground: Float, h: Float, t: Float, seed: Int) {
        drawRect(Color(0xFF4A3426), Offset(x - h * 0.04f, ground - h * 0.55f), Size(h * 0.08f, h * 0.55f))
        val sway = sin(t * 0.6f + seed) * h * 0.01f
        val greens = listOf(Color(0xFF2F5A2A), Color(0xFF3E7234), Color(0xFF5C9447), Color(0xFF7DB25A))
        for ((li, g) in greens.withIndex()) {
            for (i in 0 until 9) {
                val a = hash(i + seed * 31 + li * 7) * 2f * PI.toFloat()
                val d = h * 0.22f * hash(i * 3 + seed + li) * (1f - li * 0.15f)
                val c = Offset(x + kotlin.math.cos(a) * d * 1.3f + sway * (li + 1), ground - h * 0.72f + kotlin.math.sin(a) * d - li * h * 0.03f)
                drawCircle(g, h * (0.13f - li * 0.015f), c)
            }
        }
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        if (!photo) {
            for (i in 0 until 3) {
                val x = ((hash(i * 3 + 1) * size.width * 1.5f + t * (4f + i * 2f)) % (size.width * 1.5f)) - size.width * 0.25f
                cloud(Offset(x, size.height * (0.08f + 0.08f * i)), size.width * 0.14f, Color.White.copy(alpha = 0.8f), i * 5)
            }
            tree(size.width * 0.06f, size.height * 0.98f, size.height * 0.95f, t, 1)
            tree(size.width * 0.95f, size.height * 0.96f, size.height * 0.9f, t, 2)
            // Dappled light on the grass.
            for (i in 0 until 8) {
                val c = Offset(size.width * hash(i * 5 + 2) + sin(t * 0.3f + i) * 20f, size.height * (0.7f + 0.25f * hash(i * 9)))
                drawOval(Color(0xFFFFF4C2).copy(alpha = 0.10f), Offset(c.x - 40f, c.y - 10f), Size(80f, 20f))
            }
        }
        // Sun rays from the top left.
        for (i in 0 until 4) {
            val a = 0.06f + 0.04f * noise(t * 0.2f + i, 3)
            drawPath(Path().apply {
                moveTo(size.width * (0.02f + i * 0.06f), 0f); lineTo(size.width * (0.08f + i * 0.06f), 0f)
                lineTo(size.width * (0.45f + i * 0.12f), size.height); lineTo(size.width * (0.32f + i * 0.12f), size.height); close()
            }, Color(0xFFFFF6D0).copy(alpha = a), blendMode = BlendMode.Screen)
        }
        leaves(t, 18, 31, autumn = true)
        birds(t, 7, size.height * 0.08f, size.height * 0.3f)
        vignette(0.3f)
    }
}

// ---------------------------------------------------------------- Rainy window, city at night

internal class RainPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1422), Color(0xFF16243A), Color(0xFF1F2C40))))
        // Buildings far away with lit windows.
        for (i in 0 until 14) {
            val w = size.width * (0.05f + hash(i) * 0.06f)
            val x = size.width * i / 13f - w / 2
            val h = size.height * (0.25f + hash(i * 3) * 0.35f)
            val top = size.height * 0.85f - h
            drawRect(Color(0xFF101A2A), Offset(x, top), Size(w, h + size.height * 0.2f))
            var wy = top + 10f
            var r = 0
            while (wy < size.height * 0.85f) {
                var wx = x + 6f
                var c = 0
                while (wx < x + w - 8f) {
                    if (hash(i * 1000 + r * 37 + c) > 0.55f) drawRect(Color(0xFFFFD58A).copy(alpha = 0.55f), Offset(wx, wy), Size(5f, 7f))
                    wx += 11f; c++
                }
                wy += 14f; r++
            }
        }
        // City lights, out of focus.
        val colors = listOf(Color(0xFFFFB25A), Color(0xFFFFE2A0), Color(0xFF5AD1D6), Color(0xFFFF7AA2), Color(0xFFB9C7FF))
        for (i in 0 until 70) {
            val c = Offset(size.width * hash(i * 7 + 1), size.height * (0.45f + 0.5f * hash(i * 11 + 3)))
            val r = size.height * (0.01f + hash(i * 13) * 0.04f)
            drawCircle(colors[i % colors.size].copy(alpha = 0.18f + hash(i * 17) * 0.25f), r, c)
        }
        drawRect(Color(0xFF0B1422).copy(alpha = 0.25f))
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        rainStreaks(t, 3, 140, if (photo) 0.12f else 0.16f)
        rainOnGlass(t, 9)
        // A flash of lightning now and then.
        val cycle = 34f
        val ph = t % cycle
        if (ph in 0.0f..0.12f || ph in 0.25f..0.32f) drawRect(Color.White.copy(alpha = 0.12f))
        if (!photo) {
            // The window frame and the sill, with a candle and a plant.
            val frame = size.width * 0.025f
            drawRect(Color(0xFF2A1C14), Offset(0f, 0f), Size(frame, size.height))
            drawRect(Color(0xFF2A1C14), Offset(size.width - frame, 0f), Size(frame, size.height))
            drawRect(Color(0xFF2A1C14), Offset(0f, 0f), Size(size.width, frame))
            drawRect(Color(0xFF2A1C14), Offset(size.width * 0.5f - frame / 2, 0f), Size(frame, size.height))
            drawRect(Color(0xFF2A1C14), Offset(0f, size.height * 0.45f), Size(size.width, frame * 0.7f))
            val sill = size.height * 0.9f
            drawRect(Color(0xFF3B2A1E), Offset(0f, sill), Size(size.width, size.height - sill))
            drawRect(Color(0xFF4E382A), Offset(0f, sill), Size(size.width, 8f))
            val cx = size.width * 0.07f
            drawRect(Color(0xFFEDE3CC), Offset(cx - 10f, sill - size.height * 0.1f), Size(20f, size.height * 0.1f))
            val f = 0.8f + 0.2f * noise(t * 5f, 4)
            glow(Offset(cx, sill - size.height * 0.12f), size.height * 0.12f * f, Color(0xFFFFB04A), 0.35f)
            drawOval(Color(0xFFFFD27A), Offset(cx - 5f, sill - size.height * 0.1f - 22f * f), Size(10f, 20f * f))
            val px = size.width * 0.93f
            drawRect(Color(0xFFB5654A), Offset(px - 30f, sill - 56f), Size(60f, 56f))
            for (k in 0 until 7) rotate(-60f + k * 20f, Offset(px, sill - 56f)) {
                drawOval(Color(0xFF4F7F3C), Offset(px - 8f, sill - 56f - 70f), Size(16f, 70f))
            }
        }
        vignette(0.55f)
    }
}

// ---------------------------------------------------------------- Beach at sunset

internal class BeachPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        val horizon = size.height * 0.56f
        verticalSky(0f to Color(0xFF2B2D5C), 0.4f to Color(0xFF8E4B7B), 0.75f to Color(0xFFF08A5D), 1f to Color(0xFFFFD27A), until = horizon)
        for (i in 0 until 6) {
            val y = size.height * (0.18f + i * 0.06f)
            drawOval(Color(0xFFFFB38A).copy(alpha = 0.25f), Offset(size.width * hash(i * 3) * 0.6f, y), Size(size.width * (0.3f + hash(i) * 0.3f), size.height * 0.018f))
        }
        val sun = Offset(size.width * 0.24f, horizon)
        glow(sun, size.height * 0.4f, Color(0xFFFFD9A0), 0.7f)
        clipRect(0f, 0f, size.width, horizon) { drawCircle(Color(0xFFFFEBB8), size.height * 0.07f, sun) }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF6B4A7A), Color(0xFF2F2E55)), startY = horizon, endY = size.height * 0.8f), Offset(0f, horizon), Size(size.width, size.height * 0.24f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE2B58A), Color(0xFFB98057)), startY = size.height * 0.8f, endY = size.height), Offset(0f, size.height * 0.8f), Size(size.width, size.height * 0.2f))
    }

    private fun DrawScope.palm(base: Offset, h: Float, t: Float) {
        val top = Offset(base.x - h * 0.18f, base.y - h)
        val trunk = Path().apply { moveTo(base.x - 10f, base.y); quadraticBezierTo(base.x + h * 0.08f, base.y - h * 0.5f, top.x - 6f, top.y); lineTo(top.x + 6f, top.y); quadraticBezierTo(base.x + h * 0.12f, base.y - h * 0.5f, base.x + 10f, base.y); close() }
        drawPath(trunk, Color(0xFF1E1A2B))
        val sway = sin(t * 0.8f) * 5f
        for (k in 0 until 7) {
            val a = -170f + k * 32f + sway
            rotate(a, top) {
                val leaf = Path().apply {
                    moveTo(top.x, top.y)
                    quadraticBezierTo(top.x + h * 0.25f, top.y - h * 0.08f, top.x + h * 0.42f, top.y + h * 0.06f)
                    quadraticBezierTo(top.x + h * 0.22f, top.y + h * 0.02f, top.x, top.y)
                }
                drawPath(leaf, Color(0xFF1E1A2B))
            }
        }
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        if (!photo) {
            val horizon = size.height * 0.56f
            // The sun's path on the water, glittering.
            for (i in 0 until 40) {
                val y = horizon + (size.height * 0.24f) * (i / 40f)
                val w = size.width * (0.02f + 0.08f * (i / 40f)) * (0.5f + noise(t * 1.5f + i, 3))
                val x = size.width * 0.24f + (noise(t * 0.8f + i * 1.3f, 5) - 0.5f) * size.width * 0.05f * (1f + i / 20f)
                drawLine(Color(0xFFFFD9A0).copy(alpha = 0.6f * (1f - i / 50f)), Offset(x - w / 2, y), Offset(x + w / 2, y), strokeWidth = 2f)
            }
            // Waves coming in and going out.
            val wave = size.height * 0.8f + sin(t * 0.55f) * size.height * 0.02f
            val path = Path().apply {
                moveTo(0f, size.height * 0.79f)
                var x = 0f
                while (x <= size.width) { lineTo(x, wave + sin(x / size.width * 14f + t) * 5f); x += 20f }
                lineTo(size.width, size.height * 0.79f); close()
            }
            drawPath(path, Color(0xFF8C7BA0).copy(alpha = 0.55f))
            val foam = Path().apply {
                var x = 0f
                moveTo(0f, wave)
                while (x <= size.width) { lineTo(x, wave + sin(x / size.width * 14f + t) * 5f); x += 20f }
            }
            drawPath(foam, Color.White.copy(alpha = 0.55f), style = Stroke(3f))
            palm(Offset(size.width * 0.93f, size.height * 1.02f), size.height * 0.75f, t)
        }
        birds(t, 11, size.height * 0.15f, size.height * 0.4f, Color(0xFF1E1A2B))
        vignette(0.4f)
    }
}

// ---------------------------------------------------------------- Starry night, by a tent

internal class NightPainter : ScenePainter() {
    override fun DrawScope.paintStill() {
        verticalSky(0f to Color(0xFF040715), 0.6f to Color(0xFF0E1A3A), 1f to Color(0xFF23325E), until = size.height * 0.75f)
        // The Milky Way: a soft diagonal band of light and dust.
        rotate(-28f, center) {
            drawOval(Brush.radialGradient(listOf(Color(0xFF9FB4E8).copy(alpha = 0.18f), Color.Transparent), center = center, radius = size.width * 0.5f), Offset(center.x - size.width * 0.7f, center.y - size.height * 0.4f), Size(size.width * 1.4f, size.height * 0.28f))
            for (i in 0 until 900) {
                val x = center.x + (hash(i * 3) - 0.5f) * size.width * 1.4f
                val y = center.y - size.height * 0.26f + (hash(i * 7) + hash(i * 11) - 1f) * size.height * 0.14f
                drawCircle(Color.White.copy(alpha = 0.15f + hash(i * 13) * 0.3f), 0.7f, Offset(x, y))
            }
        }
        drawCircle(Color(0xFFF4F1E0), size.height * 0.045f, Offset(size.width * 0.86f, size.height * 0.14f))
        drawCircle(Color(0xFF060A1C), size.height * 0.045f, Offset(size.width * 0.875f, size.height * 0.128f))
        ridge(size.height * 0.74f, size.height * 0.16f, 4f, 3, Color(0xFF141E36))
        pines(0f, size.width, size.height * 0.88f, size.height * 0.2f, Color(0xFF080D1C), 9, 22)
        drawRect(Color(0xFF070B16), Offset(0f, size.height * 0.88f), Size(size.width, size.height * 0.12f))
        // The tent, glowing from inside.
        val tb = Offset(size.width * 0.08f, size.height * 0.95f)
        val tw = size.width * 0.16f
        val th = size.height * 0.22f
        drawPath(Path().apply { moveTo(tb.x, tb.y); lineTo(tb.x + tw / 2, tb.y - th); lineTo(tb.x + tw, tb.y); close() }, Color(0xFF6B4A2A))
        drawPath(Path().apply { moveTo(tb.x + tw * 0.38f, tb.y); lineTo(tb.x + tw / 2, tb.y - th * 0.6f); lineTo(tb.x + tw * 0.62f, tb.y); close() }, Color(0xFFFFB25A))
    }

    override fun DrawScope.paintMoving(t: Float, photo: Boolean) {
        stars(t, 5, if (photo) 90 else 260, size.height * (if (photo) 0.45f else 0.7f))
        // A shooting star every half minute or so.
        val cycle = 27f
        val ph = (t % cycle) / 1.1f
        if (ph < 1f) {
            val a = Offset(size.width * 0.15f, size.height * 0.08f) + Offset(size.width * 0.3f, size.height * 0.12f) * ph
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.8f)), a - Offset(80f, 32f), a), a - Offset(80f, 32f), a, strokeWidth = 2f)
        }
        if (!photo) {
            val fire = Offset(size.width * 0.29f, size.height * 0.95f)
            flames(fire, size.width * 0.05f, size.height * 0.09f, t, seed = 4, tongues = 5)
            embers(fire - Offset(0f, size.height * 0.05f), size.width * 0.03f, size.height * 0.3f, t, 14)
        }
        fireflies(t, 8, 22, size.height * 0.6f)
        vignette(0.5f)
    }
}
