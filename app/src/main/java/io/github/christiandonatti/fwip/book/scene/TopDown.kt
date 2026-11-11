package io.github.christiandonatti.fwip.book.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

// The photos show each place from straight above, a surface for the book to lie on. What moves
// over them is light and small things crossing that surface, each coming from the side the
// photo is lit from.

/** A soft pool of light at [c], as a fraction of the screen. */
private fun DrawScope.pool(c: Offset, radius: Float, color: Color, alpha: Float) =
    glow(Offset(size.width * c.x, size.height * c.y), maxOf(size.width, size.height) * radius, color, alpha)

/** Soft shadows drifting slowly across: clouds, or leaves overhead when [small]. */
private fun DrawScope.driftingShade(t: Float, count: Int, seed: Int, small: Boolean, alpha: Float) {
    val span = size.width * 1.6f
    for (i in 0 until count) {
        val speed = if (small) 0f else size.width * (0.006f + 0.006f * hash(i + seed))
        val w = size.width * (if (small) 0.10f + 0.10f * hash(i * 3 + seed) else 0.35f + 0.25f * hash(i * 3 + seed))
        val x = ((hash(i * 7 + seed) * span + t * speed) % span) - size.width * 0.3f +
            (if (small) sin(t * (0.35f + 0.2f * hash(i + seed)) + i) * size.width * 0.012f else 0f)
        val y = size.height * hash(i * 11 + seed) + (if (small) sin(t * 0.3f + i * 2f) * size.height * 0.01f else 0f)
        val c = Offset(x, y)
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = alpha), Color.Transparent), center = c, radius = w / 2),
            Offset(c.x - w / 2, c.y - w * 0.35f), Size(w, w * 0.7f),
        )
    }
}

/** Thin shadows of rain running down a window, falling across the table from the left. */
private fun DrawScope.rainShadows(t: Float, seed: Int) {
    for (i in 0 until 26) {
        val life = 5f + hash(i + seed) * 7f
        val phase = ((t + hash(i * 3 + seed) * life) % life) / life
        val y = size.height * hash(i * 5 + seed)
        val len = size.width * (0.05f + 0.10f * hash(i * 9 + seed))
        val x = -len + phase * size.width * (0.5f + 0.4f * hash(i * 13 + seed))
        val a = 0.10f * (1f - phase)
        drawLine(Color.Black.copy(alpha = a), Offset(x, y), Offset(x + len, y + len * 0.04f), strokeWidth = 2f + 3f * hash(i + seed * 3))
    }
}

/** What moves over the photo of [scene] seen from above, at time [t] in seconds. */
internal fun DrawScope.topDownTouches(scene: Scene, t: Float) {
    when (scene) {
        Scene.Fireplace -> {
            // The fire is at the left: its light swells and flickers, and sparks drift over the rug.
            val f = 0.75f + 0.25f * fbm(t * 2.2f, 3)
            pool(Offset(0.0f, 0.45f), 0.55f, Color(0xFFFF9A3C), 0.22f * f)
            pool(Offset(0.02f, 0.30f), 0.25f, Color(0xFFFFC46A), 0.14f * fbm(t * 5f, 9))
            for (i in 0 until 10) {
                val life = 3f + hash(i + 50) * 3f
                val phase = ((t + hash(i * 3 + 50) * life) % life) / life
                val x = size.width * (0.04f + phase * (0.10f + 0.12f * hash(i * 7 + 50)))
                val y = size.height * (0.25f + 0.4f * hash(i * 11 + 50)) - phase * size.height * 0.08f + sin(t * 2f + i) * 6f
                drawCircle(Color(0xFFFFB04A).copy(alpha = (1f - phase) * 0.8f), 1.5f + hash(i) * 1.5f, Offset(x, y))
            }
        }
        Scene.Library -> {
            // The lamp at the top left breathes; dust floats where its light falls.
            pool(Offset(0.22f, 0.12f), 0.5f, Color(0xFFFFD08A), 0.10f + 0.04f * noise(t * 0.5f, 3))
            dust(t, 60, 13) { p ->
                val d = (p - Offset(size.width * 0.3f, size.height * 0.2f)).getDistance() / size.width
                (1f - d * 1.8f).coerceIn(0f, 1f)
            }
        }
        Scene.Desk -> {
            pool(Offset(0.15f, 0.08f), 0.6f, Color(0xFFFFF0D0), 0.08f + 0.03f * noise(t * 0.3f, 5))
            dust(t, 30, 21) { p -> (1f - p.x / size.width).coerceIn(0f, 1f) * 0.6f }
        }
        Scene.Mountain -> driftingShade(t, 3, 31, small = false, alpha = 0.16f)
        Scene.Park -> {
            // Leaves overhead stir, so the light through them shifts; one falls now and then.
            driftingShade(t, 14, 41, small = true, alpha = 0.10f)
            leaves(t, 3, 43, autumn = false)
        }
        Scene.Rain -> {
            rainShadows(t, 61)
            // Lightning, far away, now and then.
            val ph = t % 37f
            if (ph in 0.0f..0.10f || ph in 0.22f..0.28f) drawRect(Color(0xFFCFE0FF).copy(alpha = 0.10f))
        }
        Scene.Beach -> {
            // Low sun from the right, with the air shimmering a little.
            pool(Offset(1.0f, 0.2f), 0.6f, Color(0xFFFFC27A), 0.10f + 0.04f * noise(t * 0.4f, 7))
            dust(t, 18, 71, Color(0xFFFFE9C0)) { p -> (p.x / size.width).coerceIn(0f, 1f) * 0.5f }
        }
        Scene.Night -> {
            // The lantern at the top right flickers; fireflies wander over the blanket.
            pool(Offset(0.92f, 0.08f), 0.35f, Color(0xFFFFB45A), 0.12f + 0.06f * fbm(t * 3f, 5))
            fireflies(t, 81, 7, 0f)
        }
    }
    vignette(0.42f)
}
