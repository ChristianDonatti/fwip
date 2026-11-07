package io.github.christiandonatti.fwip.book.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// Small tools shared by the scenes: repeatable randomness, smooth noise, cached still layers,
// and the moving touches (fire, embers, dust, leaves, rain on glass, birds, stars, fireflies).

/** A repeatable number in 0..1 for [i]: the same scene always looks the same. */
internal fun hash(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777215f
}

/** Smooth value noise in 0..1. */
internal fun noise(x: Float, seed: Int = 0): Float {
    val i = floor(x).toInt()
    val f = x - i
    val u = f * f * (3f - 2f * f)
    val a = hash(i + seed * 9973)
    val b = hash(i + 1 + seed * 9973)
    return a + (b - a) * u
}

/** Two octaves of [noise], for ridgelines and flicker that do not look regular. */
internal fun fbm(x: Float, seed: Int = 0): Float = noise(x, seed) * 0.65f + noise(x * 2.3f, seed + 7) * 0.35f

/** A still picture drawn once per size (walls, shelves, mountains), then just copied each frame. */
internal class StillLayer {
    private var image: ImageBitmap? = null
    private var key: Any? = null

    fun DrawScope.draw(key: Any?, block: DrawScope.() -> Unit) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return
        val k = listOf(w, h, key)
        if (image == null || this@StillLayer.key != k) {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(this, layoutDirection, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
            image = img
            this@StillLayer.key = k
        }
        drawImage(image!!)
    }
}

internal fun DrawScope.vignette(strength: Float = 0.55f) {
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = strength),
            center = center, radius = maxOf(size.width, size.height) * 0.72f,
        ),
    )
}

internal fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = c, radius = radius),
        radius = radius,
        center = c,
    )
}

/** Flames over a fire bed [w] wide whose base centre is [base]; [scale] sets the height. */
internal fun DrawScope.flames(base: Offset, w: Float, scale: Float, t: Float, seed: Int = 0, tongues: Int = 7) {
    val colors = listOf(Color(0xFFB8321A), Color(0xFFE8641E), Color(0xFFFFA53A), Color(0xFFFFE08A))
    val layers = listOf(1f, 0.78f, 0.55f, 0.32f)
    // Light on everything around, breathing with the fire.
    val flick = 0.75f + 0.25f * fbm(t * 3.1f, seed + 3)
    glow(Offset(base.x, base.y - scale * 0.35f), w * 1.7f * flick, Color(0xFFFF8A2A), 0.30f * flick)
    for ((li, lf) in layers.withIndex()) {
        for (i in 0 until tongues) {
            val u = (i + 0.5f) / tongues
            val x = base.x + (u - 0.5f) * w * (0.9f - li * 0.12f)
            val center = 1f - kotlin.math.abs(u - 0.5f) * 1.5f
            val hgt = scale * lf * (0.45f + 0.55f * center) * (0.6f + 0.6f * noise(t * 2.6f + i * 1.7f, seed + li))
            val half = w / tongues * (0.75f - li * 0.08f)
            val sway = (noise(t * 1.9f + i, seed + 11) - 0.5f) * half * 1.6f
            val p = Path().apply {
                moveTo(x - half, base.y)
                cubicTo(x - half * 1.1f, base.y - hgt * 0.45f, x + sway * 0.4f - half * 0.3f, base.y - hgt * 0.7f, x + sway, base.y - hgt)
                cubicTo(x + sway * 0.5f + half * 0.35f, base.y - hgt * 0.65f, x + half * 1.1f, base.y - hgt * 0.4f, x + half, base.y)
                close()
            }
            drawPath(p, colors[li].copy(alpha = 0.85f), blendMode = if (li > 0) BlendMode.Screen else BlendMode.SrcOver)
        }
    }
}

/** Sparks that rise from [from] over a [w] wide fire and fade out. */
internal fun DrawScope.embers(from: Offset, w: Float, rise: Float, t: Float, count: Int = 24, seed: Int = 40) {
    for (i in 0 until count) {
        val life = 2.2f + hash(i + seed) * 2.5f
        val phase = ((t + hash(i * 3 + seed) * life) % life) / life
        val x = from.x + (hash(i * 7 + seed) - 0.5f) * w + (noise(t * 0.8f + i, seed) - 0.5f) * w * 0.6f * phase
        val y = from.y - rise * phase
        val a = (1f - phase) * (0.5f + 0.5f * noise(t * 6f + i, seed + 2))
        drawCircle(Color(0xFFFFB04A).copy(alpha = a), radius = 1.2f + hash(i + seed * 2) * 1.8f, center = Offset(x, y))
    }
}

/** Specks of dust floating slowly, lit where [lit] says (for a beam of light). */
internal fun DrawScope.dust(t: Float, count: Int, seed: Int, color: Color = Color(0xFFFFF1D0), lit: (Offset) -> Float = { 1f }) {
    for (i in 0 until count) {
        val bx = hash(i + seed) * size.width
        val by = hash(i * 5 + seed) * size.height
        val x = (bx + (noise(t * 0.07f + i, seed) - 0.5f) * size.width * 0.12f + t * 4f) % size.width
        val y = (by + (noise(t * 0.06f + i * 2, seed + 1) - 0.5f) * size.height * 0.12f + size.height) % size.height
        val p = Offset(x, y)
        val a = lit(p) * (0.25f + 0.75f * noise(t * 0.9f + i, seed + 3))
        if (a > 0.02f) drawCircle(color.copy(alpha = a * 0.75f), radius = 0.8f + hash(i * 11 + seed) * 1.8f, center = p)
    }
}

/** Leaves falling and swinging, in autumn colours (or green). */
internal fun DrawScope.leaves(t: Float, count: Int, seed: Int, autumn: Boolean = true) {
    val palette = if (autumn) listOf(Color(0xFFD9822B), Color(0xFFE8B23A), Color(0xFFB4462A), Color(0xFF8DA243))
    else listOf(Color(0xFF6E9B45), Color(0xFF8DB255), Color(0xFF557F37))
    for (i in 0 until count) {
        val fall = 9f + hash(i + seed) * 9f
        val phase = ((t + hash(i * 3 + seed) * fall) % fall) / fall
        val baseX = hash(i * 7 + seed) * size.width * 1.2f - size.width * 0.1f
        val x = baseX + sin((t * 1.3f + i) * 0.9f) * size.width * 0.03f + phase * size.width * 0.08f
        val y = -20f + phase * (size.height + 40f)
        val r = 5f + hash(i * 13 + seed) * 6f
        val angle = sin(t * 1.7f + i) * 70f + i * 40f
        rotate(angle, Offset(x, y)) {
            drawOval(palette[i % palette.size].copy(alpha = 0.9f), topLeft = Offset(x - r, y - r * 0.45f), size = Size(r * 2, r * 0.9f))
            drawLine(Color.Black.copy(alpha = 0.25f), Offset(x - r, y), Offset(x + r, y), strokeWidth = 0.7f)
        }
    }
}

/** Birds gliding across now and then, as small dark Vs. */
internal fun DrawScope.birds(t: Float, seed: Int, top: Float, bottom: Float, color: Color = Color(0xFF2B2B33)) {
    for (i in 0 until 4) {
        val period = 28f + hash(i + seed) * 22f
        val phase = ((t + hash(i * 3 + seed) * period) % period) / period
        if (phase > 0.55f) continue
        val p = phase / 0.55f
        val x = -40f + p * (size.width + 80f)
        val y = top + (bottom - top) * hash(i * 5 + seed) + sin(p * 9f + i) * 6f
        val s = 6f + hash(i * 9 + seed) * 5f
        val flap = sin(t * 9f + i * 2f) * s * 0.45f
        val path = Path().apply {
            moveTo(x - s, y - flap)
            quadraticBezierTo(x - s * 0.4f, y - s * 0.2f, x, y)
            quadraticBezierTo(x + s * 0.4f, y - s * 0.2f, x + s, y - flap)
        }
        drawPath(path, color.copy(alpha = 0.75f), style = Stroke(width = 1.6f))
    }
}

/** Raindrops on a window: still beads, and some that run down leaving a wet trail. */
internal fun DrawScope.rainOnGlass(t: Float, seed: Int, density: Float = 1f) {
    val beads = (170 * density).toInt()
    for (i in 0 until beads) {
        val x = hash(i + seed) * size.width
        val y = hash(i * 3 + seed) * size.height
        val r = 1.5f + hash(i * 7 + seed) * 3.5f
        drawCircle(Color.Black.copy(alpha = 0.16f), r, Offset(x, y + r * 0.25f))
        drawCircle(Color.White.copy(alpha = 0.10f), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.32f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
    for (i in 0 until (14 * density).toInt()) {
        val period = 4f + hash(i + seed * 5) * 7f
        val phase = ((t + hash(i * 3 + seed * 5) * period) % period) / period
        val x = hash(i * 11 + seed * 5) * size.width + sin(phase * 6f + i) * 4f
        val y = -10f + phase * phase * (size.height + 20f)
        val r = 3f + hash(i * 13 + seed) * 2.5f
        drawLine(Color.White.copy(alpha = 0.10f), Offset(x, maxOf(0f, y - size.height * 0.35f * phase)), Offset(x, y), strokeWidth = r * 0.7f)
        drawCircle(Color.Black.copy(alpha = 0.18f), r, Offset(x, y + r * 0.3f))
        drawCircle(Color.White.copy(alpha = 0.16f), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.4f))
    }
}

/** Rain falling outside, as faint slanted streaks. */
internal fun DrawScope.rainStreaks(t: Float, seed: Int, count: Int = 120, alpha: Float = 0.16f) {
    for (i in 0 until count) {
        val speed = 1.1f + hash(i + seed) * 0.6f
        val phase = ((t * speed + hash(i * 3 + seed)) % 1f)
        val x = hash(i * 7 + seed) * (size.width + 100f) - phase * 60f
        val y = phase * (size.height + 80f) - 40f
        val len = 18f + hash(i * 5 + seed) * 22f
        drawLine(Color(0xFFCFE0FF).copy(alpha = alpha), Offset(x, y), Offset(x - len * 0.25f, y + len), strokeWidth = 1f)
    }
}

/** Stars that twinkle, mostly in the upper part of the sky. */
internal fun DrawScope.stars(t: Float, seed: Int, count: Int, bottom: Float) {
    for (i in 0 until count) {
        val x = hash(i + seed) * size.width
        val y = hash(i * 3 + seed).let { it * it } * bottom
        val base = 0.3f + hash(i * 5 + seed) * 0.7f
        val tw = 0.55f + 0.45f * sin(t * (1.5f + hash(i * 7 + seed) * 3f) + i)
        val r = 0.6f + hash(i * 11 + seed).let { it * it * it } * 2.2f
        drawCircle(Color.White.copy(alpha = (base * tw).coerceIn(0f, 1f)), r, Offset(x, y))
        if (r > 2f) glow(Offset(x, y), r * 4f, Color(0xFFCFE0FF), 0.18f * tw)
    }
}

/** Fireflies drifting and blinking over the lower part of the scene. */
internal fun DrawScope.fireflies(t: Float, seed: Int, count: Int, top: Float) {
    for (i in 0 until count) {
        val x = (hash(i + seed) + (noise(t * 0.12f + i, seed) - 0.5f) * 0.25f) * size.width
        val y = top + (hash(i * 3 + seed) + (noise(t * 0.1f + i * 3, seed + 1) - 0.5f) * 0.2f) * (size.height - top)
        val blink = (sin(t * (0.8f + hash(i * 5 + seed)) + i * 2f) * 0.5f + 0.5f).let { it * it * it }
        glow(Offset(x, y), 14f, Color(0xFFDFFF7A), 0.55f * blink)
        drawCircle(Color(0xFFF4FFC0).copy(alpha = blink), 1.8f, Offset(x, y))
    }
}

/** Soft fog drifting sideways in a band. */
internal fun DrawScope.mist(t: Float, seed: Int, y: Float, height: Float, color: Color = Color.White, alpha: Float = 0.16f) {
    for (i in 0 until 7) {
        val w = size.width * (0.35f + hash(i + seed) * 0.4f)
        val x = ((hash(i * 3 + seed) * size.width * 1.6f + t * (6f + hash(i * 5 + seed) * 8f)) % (size.width * 1.6f)) - size.width * 0.3f
        val cy = y + (hash(i * 7 + seed) - 0.5f) * height
        drawOval(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(x + w / 2, cy), radius = w / 2),
            topLeft = Offset(x, cy - height * 0.5f), size = Size(w, height),
        )
    }
}

/** A puffy cloud of overlapping circles centred on [c], [w] wide. */
internal fun DrawScope.cloud(c: Offset, w: Float, color: Color, seed: Int) {
    for (i in 0 until 9) {
        val u = i / 8f
        val x = c.x + (u - 0.5f) * w
        val r = w * (0.12f + 0.12f * sin(u * PI.toFloat())) * (0.8f + hash(i + seed) * 0.4f)
        drawCircle(color, r, Offset(x, c.y - r * 0.35f + hash(i * 3 + seed) * r * 0.3f))
    }
}

/** A ridge of hills or mountains: noise along x, filled down to the bottom. */
internal fun DrawScope.ridge(baseY: Float, amp: Float, freq: Float, seed: Int, color: Color, snow: Float = 0f): Path {
    val path = Path()
    val steps = 120
    path.moveTo(0f, size.height)
    val pts = ArrayList<Offset>(steps + 1)
    for (s in 0..steps) {
        val x = size.width * s / steps
        val n = fbm(x / size.width * freq, seed)
        val peak = (n * n) * 1.6f
        val y = baseY - amp * peak
        pts += Offset(x, y)
        path.lineTo(x, y)
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
    if (snow > 0f) {
        val thr = baseY - amp * 0.55f
        val snowPath = Path()
        var open = false
        for (p in pts) {
            if (p.y < thr) {
                if (!open) { snowPath.moveTo(p.x, p.y); open = true } else snowPath.lineTo(p.x, p.y)
            }
        }
        if (open) {
            for (p in pts.reversed()) if (p.y < thr) snowPath.lineTo(p.x, p.y + (thr - p.y) * snow + (hash((p.x).toInt()) * 4f))
            snowPath.close()
            drawPath(snowPath, lerp(Color.White, color, 0.15f).copy(alpha = 0.92f))
        }
    }
    return path
}

/** Pine trees standing on a line, as dark triangles in tiers. */
internal fun DrawScope.pines(fromX: Float, toX: Float, groundY: Float, height: Float, color: Color, seed: Int, count: Int) {
    for (i in 0 until count) {
        val x = fromX + (toX - fromX) * (i + hash(i + seed) * 0.6f) / count
        val h = height * (0.6f + hash(i * 3 + seed) * 0.6f)
        val w = h * 0.34f
        val y = groundY + hash(i * 5 + seed) * height * 0.08f
        drawRect(lerp(color, Color.Black, 0.2f), Offset(x - w * 0.05f, y - h * 0.12f), Size(w * 0.1f, h * 0.14f))
        for (k in 0 until 3) {
            val top = y - h + h * 0.25f * k
            val bw = w * (0.55f + 0.25f * k)
            drawPath(Path().apply { moveTo(x, top); lineTo(x - bw / 2, top + h * 0.42f); lineTo(x + bw / 2, top + h * 0.42f); close() }, color)
        }
    }
}

internal fun DrawScope.verticalSky(vararg stops: Pair<Float, Color>, until: Float = size.height) {
    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = until), size = Size(size.width, until))
}

internal fun twoPi(x: Float) = (x * 2f * PI).toFloat()
internal fun cosf(x: Float) = cos(x)
internal fun IntSize.toSizeF() = Size(width.toFloat(), height.toFloat())
