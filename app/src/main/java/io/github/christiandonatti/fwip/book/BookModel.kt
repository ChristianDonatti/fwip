package io.github.christiandonatti.fwip.book

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.christiandonatti.fwip.book.scene.Scene
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** How a book is bound: hard boards, a soft cover, or a few sheets folded and stapled (a comic). */
enum class Edition { Hardcover, Paperback, Stapled }

/** The binding the reader asked for; [Auto] goes by the kind of book and how long it is. */
enum class EditionChoice {
    Auto, Hardcover, Paperback, Stapled;

    fun resolve(comic: Boolean, pages: Int): Edition = when (this) {
        Hardcover -> Edition.Hardcover
        Paperback -> Edition.Paperback
        Stapled -> Edition.Stapled
        Auto -> when {
            comic && pages <= 64 -> Edition.Stapled       // a single issue
            comic && pages <= 240 -> Edition.Paperback    // a collected edition
            comic -> Edition.Hardcover                    // an omnibus
            pages <= 360 -> Edition.Paperback
            else -> Edition.Hardcover
        }
    }
}

/**
 * The shape of an open book seen from above and a little in front: the paper rises from the
 * gutter and flattens out towards the edges, so the top and bottom edges of each page curve,
 * and the block of sheets under it shows at the front. [page] is where the pages lie flat; the
 * live pages go in [content], a little shorter, so the curved paper always surrounds them.
 */
class BookShape(
    val page: Rect,
    val spread: Boolean,
    val edition: Edition,
    fraction: Float,
    pages: Int,
) {
    val height = page.height
    val gutter = if (spread) page.center.x else page.left
    private val half = (page.right - gutter).coerceAtLeast(1f)
    private val curve = height * when (edition) {
        Edition.Hardcover -> 0.020f
        Edition.Paperback -> 0.028f
        Edition.Stapled -> 0.010f
    }
    val thickness = height * when (edition) {
        Edition.Stapled -> (0.002f + pages * 0.00012f).coerceAtMost(0.012f)
        Edition.Paperback -> (0.006f + pages * 0.00008f).coerceAtMost(0.045f)
        Edition.Hardcover -> (0.008f + pages * 0.00008f).coerceAtMost(0.05f)
    }
    private val read = fraction.coerceIn(0f, 1f)
    val stackLeft = if (spread) thickness * (0.1f + 0.9f * read) else thickness * 0.1f
    val stackRight = thickness * (0.1f + 0.9f * (1f - read))
    val overhang = if (edition == Edition.Hardcover) height * 0.016f else 0f
    val boardThick = (height * when (edition) {
        Edition.Hardcover -> 0.011f
        Edition.Paperback -> 0.0035f
        Edition.Stapled -> 0.0022f
    }).coerceAtLeast(1.5f)

    /** Height of the paper over the table at [x], in px of the screen. */
    fun lift(x: Float): Float {
        val u = (abs(x - gutter) / half).coerceIn(0f, 1f)
        return curve * (1f - exp(-7f * u)) * (1f - 0.35f * u * u)
    }

    /** How steeply the paper rises at [x], screen px per px. */
    fun slope(x: Float): Float = (lift(x + 1.5f) - lift(x - 1.5f)) / 3f

    val maxLift: Float = run { var m = 0f; var x = gutter; while (x <= page.right) { m = max(m, lift(x)); x += 2f }; m }
    val content = Rect(page.left, page.top, page.right, page.bottom - maxLift)

    fun topY(x: Float) = page.top - lift(x)
    fun bottomY(x: Float) = page.bottom - lift(x)
    fun stack(x: Float) = if (spread && x < gutter) stackLeft else stackRight

    /** Points across the book, with one right on the gutter. */
    val xs: FloatArray = run {
        val n = 72
        val list = (0..n).map { page.left + page.width * it / n }.toMutableList()
        if (spread) list += gutter
        list.sorted().toFloatArray()
    }

    /** The top sheet: the paper you read, with its curved edges. */
    val sheet: Path = Path().apply {
        moveTo(xs.first(), topY(xs.first()))
        xs.forEach { lineTo(it, topY(it)) }
        for (i in xs.indices.reversed()) lineTo(xs[i], bottomY(xs[i]))
        close()
    }

    /** The front of the block of sheets, under the bottom edge of the pages. */
    val face: Path = Path().apply {
        moveTo(xs.first(), bottomY(xs.first()) - 0.5f)
        xs.forEach { lineTo(it, bottomY(it) - 0.5f) }
        for (i in xs.indices.reversed()) lineTo(xs[i], bottomY(xs[i]) + stack(xs[i]))
        close()
    }

    /** Everything, boards included: for the shadow and the light of the place. */
    val bounds: Rect = Rect(
        page.left - stackLeft * 0.3f - overhang - 2f,
        page.top - maxLift - overhang * 1.2f,
        page.right + stackRight * 0.3f + overhang + 2f,
        page.bottom + max(stackLeft, stackRight) + boardThick + overhang * 0.6f,
    )
}

private val Ink = Color.Black

/**
 * Behind the pages: the shadow on the table, the boards or the soft cover, the block of sheets
 * with its layered edges, and the curved paper around the live pages.
 */
@Composable
fun BookBody(shape: BookShape, cover: Color, paper: Color) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        if (shape.page.width < 8f) return@Canvas
        val s = shape
        val b = s.bounds
        val h = s.height

        // Shadow: a wide soft one, as from a lamp above and in front, and a tight one where it touches.
        softShadow(RoundRect(b, CornerRadius(s.overhang + 4f)), Offset(h * 0.012f, h * 0.03f), h * 0.05f, 0.42f)
        softShadow(RoundRect(b, CornerRadius(s.overhang + 4f)), Offset(0f, h * 0.006f), h * 0.01f, 0.5f)

        when (s.edition) {
            Edition.Hardcover -> {
                // The boards, larger than the pages, and the thickness of the board at the front.
                val r = CornerRadius(s.overhang * 0.45f)
                val top = Rect(b.left, b.top, b.right, b.bottom - s.boardThick)
                drawRoundRect(lerp(cover, Ink, 0.6f), Offset(top.left, top.top + s.boardThick), top.size, r)
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(lerp(cover, Color.White, 0.10f), cover, lerp(cover, Ink, 0.22f)),
                        start = top.topLeft, end = top.bottomRight,
                    ),
                    top.topLeft, top.size, r,
                )
                cloth(top, cover)
                drawRoundRect(Color.White.copy(alpha = 0.10f), top.topLeft, top.size, r, style = Stroke(1.2f))
                if (s.spread) {
                    // The hinge of the spine between the boards.
                    val w = s.overhang * 2.2f
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.45f), Color.Transparent), startX = s.gutter - w, endX = s.gutter + w),
                        Offset(s.gutter - w, top.top), Size(w * 2, top.height + s.boardThick),
                    )
                }
                // The block's own shadow on the boards.
                softShadow(
                    RoundRect(Rect(s.page.left - s.stackLeft * 0.3f, s.page.top, s.page.right + s.stackRight * 0.3f, s.page.bottom + max(s.stackLeft, s.stackRight)), CornerRadius(2f)),
                    Offset.Zero, s.overhang * 0.4f, 0.55f,
                )
            }
            else -> {
                // A soft cover: as big as the pages, a coloured line under the block.
                val c = lerp(cover, Ink, 0.15f)
                val p = Path().apply {
                    val l = s.page.left - s.stackLeft * 0.2f - 1f
                    val rr = s.page.right + s.stackRight * 0.2f + 1f
                    moveTo(l, s.topY(s.page.left) + 2f)
                    s.xs.forEach { lineTo(it, s.topY(it) + 2f) }
                    lineTo(rr, s.topY(s.page.right) + 2f)
                    lineTo(rr, s.bottomY(s.page.right) + s.stackRight + s.boardThick)
                    for (i in s.xs.indices.reversed()) lineTo(s.xs[i], s.bottomY(s.xs[i]) + s.stack(s.xs[i]) + s.boardThick)
                    lineTo(l, s.bottomY(s.page.left) + s.stack(s.page.left) + s.boardThick)
                    close()
                }
                drawPath(p, c)
            }
        }

        // The outer edges of the block: sheets stepping out under the top one.
        fun sideEdge(x0: Float, dir: Float, st: Float) {
            if (st < 1f) return
            val steps = max(2, (st / 1.8f).toInt()).coerceAtMost(14)
            for (i in steps downTo 1) {
                val f = i.toFloat() / steps
                val dx = dir * st * 0.22f * f
                val p = Path().apply {
                    moveTo(x0, s.topY(x0) + 1f)
                    lineTo(x0 + dx, s.topY(x0) + st * f * 0.4f + 1f)
                    lineTo(x0 + dx, s.bottomY(x0) + st * f)
                    lineTo(x0, s.bottomY(x0))
                    close()
                }
                drawPath(p, lerp(paper, Color(0xFF7A705F), 0.22f + 0.3f * f))
            }
        }
        sideEdge(s.page.left, -1f, if (s.spread) s.stackLeft else s.stackLeft * 2f)
        sideEdge(s.page.right, 1f, s.stackRight)

        // The front of the block: sheet after sheet, following the curve of the paper.
        drawPath(
            s.face,
            Brush.verticalGradient(
                listOf(lerp(paper, Ink, 0.05f), lerp(paper, Color(0xFF6E6455), 0.45f)),
                startY = s.page.bottom - s.maxLift, endY = s.page.bottom + max(s.stackLeft, s.stackRight),
            ),
        )
        val lines = (max(s.stackLeft, s.stackRight) / 1.4f).toInt().coerceAtMost(40)
        for (i in 1 until lines) {
            val f = i.toFloat() / lines
            val p = Path()
            s.xs.forEachIndexed { k, x ->
                val y = s.bottomY(x) + s.stack(x) * f
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Ink.copy(alpha = 0.05f + 0.08f * ((i * 7919) % 13) / 13f), style = Stroke(0.6f))
        }
        if (s.spread) {
            val w = (s.page.right - s.gutter) * 0.05f
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.35f), Color.Transparent), startX = s.gutter - w, endX = s.gutter + w),
                Offset(s.gutter - w, s.page.bottom - s.maxLift), Size(w * 2, s.maxLift + max(s.stackLeft, s.stackRight) + 2f),
            )
        }

        // The paper you read: its curved edges reach past the live pages.
        drawPath(s.sheet, paper)
        if (s.edition == Edition.Hardcover && s.spread) headbands(s)
    }
}

/** The little striped bands at the top and bottom of a hardcover's spine. */
private fun DrawScope.headbands(s: BookShape) {
    val w = (s.page.right - s.gutter) * 0.024f
    val hgt = s.height * 0.008f
    listOf(s.topY(s.gutter) - hgt * 0.75f, s.bottomY(s.gutter) - hgt * 0.25f).forEach { y ->
        drawRoundRect(Color(0xFF8E2B2B), Offset(s.gutter - w / 2, y), Size(w, hgt), CornerRadius(hgt / 2))
        for (i in 0 until 6) {
            val x = s.gutter - w / 2 + w * (i + 0.5f) / 6
            drawLine((if (i % 2 == 0) Ink else Color.White).copy(alpha = 0.25f), Offset(x, y), Offset(x, y + hgt), 1f)
        }
    }
}

private fun DrawScope.cloth(r: Rect, cover: Color) {
    val light = Color.White.copy(alpha = 0.035f)
    var y = r.top + 2f
    while (y < r.bottom) { drawLine(light, Offset(r.left + 2f, y), Offset(r.right - 2f, y), 0.8f); y += 2.4f }
    val dark = lerp(cover, Ink, 0.3f).copy(alpha = 0.06f)
    var x = r.left + 2f
    while (x < r.right) { drawLine(dark, Offset(x, r.top + 2f), Offset(x, r.bottom - 2f), 0.8f); x += 3.1f }
}

/** A blurred shape for shadows; plain fading layers where blur is not drawn by the GPU. */
private fun DrawScope.softShadow(r: RoundRect, offset: Offset, radius: Float, alpha: Float) {
    if (Build.VERSION.SDK_INT >= 28 && radius >= 1f) {
        drawIntoCanvas { c ->
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
                maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.drawRoundRect(
                r.left + offset.x, r.top + offset.y, r.right + offset.x, r.bottom + offset.y,
                r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, p,
            )
        }
    } else {
        for (i in 5 downTo 1) {
            val g = radius * i / 5f
            drawRoundRect(
                Ink.copy(alpha = alpha / 6f),
                Offset(r.left + offset.x - g, r.top + offset.y - g),
                Size(r.width + g * 2, r.height + g * 2),
                CornerRadius(r.topLeftCornerRadius.x + g),
            )
        }
    }
}

/**
 * Over the pages: light and shade from the curve of the paper (the page sinking into the
 * gutter turns away from the light), a broad fall-off across the book, and a sheen on the
 * glossy paper of comics. Not drawn while a page turns or a comic page is zoomed in.
 */
@Composable
fun BookFront(shape: BookShape, light: SceneLight) {
    Canvas(Modifier.fillMaxSize()) {
        val s = shape
        if (s.page.width < 8f) return@Canvas
        val towards = light.towardsX
        // Shade per column from the slope; one gradient across the whole book.
        val n = 48
        val stops = Array(n + 1) { i ->
            val x = s.page.left + s.page.width * i / n
            val tilt = -s.slope(x) * 3.2f * towards
            val c = if (tilt < 0f) Ink.copy(alpha = min(0.5f, -tilt * 0.85f)) else Color.White.copy(alpha = min(0.10f, tilt * 0.25f))
            (i.toFloat() / n) to c
        }
        val top = s.page.top - s.maxLift - 2f
        val area = Rect(s.page.left, top, s.page.right, s.page.bottom + 2f)
        clipPath(s.sheet) {
            drawRect(Brush.horizontalGradient(*stops, startX = s.page.left, endX = s.page.right), area.topLeft, area.size)
            // The deep fold of the gutter.
            val half = s.page.right - s.gutter
            if (s.spread) {
                val g = half * 0.04f
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.42f), Color.Transparent), startX = s.gutter - g, endX = s.gutter + g),
                    Offset(s.gutter - g, top), Size(g * 2, area.height),
                )
            } else {
                val g = half * 0.06f
                drawRect(
                    Brush.horizontalGradient(listOf(Ink.copy(alpha = 0.45f), Color.Transparent), startX = s.gutter, endX = s.gutter + g),
                    Offset(s.gutter, top), Size(g, area.height),
                )
            }
            // Brighter on the side of the light, dimmer away from it.
            val dir = light.direction
            val c = area.center
            val reach = max(area.width, area.height) * 0.6f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, Ink.copy(alpha = 0.12f)),
                    start = c + dir * reach, end = c - dir * reach,
                ),
                area.topLeft, area.size,
            )
            if (s.edition == Edition.Stapled) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                        start = Offset(area.left + area.width * 0.55f, area.top), end = Offset(area.left + area.width * 0.78f, area.bottom),
                    ),
                    area.topLeft, area.size,
                )
            }
        }
    }
}

/** Staples in the middle of a comic, seen on its centre pages only. */
@Composable
fun Staples(shape: BookShape) {
    Canvas(Modifier.fillMaxSize()) {
        val s = shape
        if (!s.spread) return@Canvas
        listOf(0.26f, 0.74f).forEach { f ->
            val y = s.page.top + s.height * f
            val len = s.height * 0.045f
            val w = max(2.4f, s.height * 0.0035f)
            drawRoundRect(Ink.copy(alpha = 0.3f), Offset(s.gutter - w / 2 + 1f, y - len / 2 + 1.5f), Size(w, len), CornerRadius(w / 2))
            drawRoundRect(Color(0xFFA3A9AF), Offset(s.gutter - w / 2, y - len / 2), Size(w, len), CornerRadius(w / 2))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(s.gutter - w * 0.15f, y - len / 2 + 2f), Offset(s.gutter - w * 0.15f, y + len / 2 - 2f), 0.8f)
        }
    }
}

/** The light of a place: where it comes from, its colour and how strong it is. */
data class SceneLight(val color: Color, val strength: Float, val fromDegrees: Float, val flicker: Boolean = false) {
    /** Unit vector pointing towards the light, on screen (y down). */
    val direction: Offset get() {
        val a = Math.toRadians(fromDegrees.toDouble())
        return Offset(cos(a).toFloat(), -sin(a).toFloat())
    }
    val towardsX: Float get() = direction.x.coerceIn(-1f, 1f).let { if (abs(it) < 0.25f) -0.5f else it }

    companion object {
        /** No colour, a lamp above and to the left: the book on its own. */
        val Neutral = SceneLight(Color.White, 0f, 125f)

        fun of(scene: Scene): SceneLight = when (scene) {
            Scene.Desk -> SceneLight(Color(0xFFFFE0B5), 0.16f, 135f)
            Scene.Library -> SceneLight(Color(0xFFFFD7A3), 0.20f, 110f)
            Scene.Fireplace -> SceneLight(Color(0xFFFFAE72), 0.26f, 200f, flicker = true)
            Scene.Mountain -> SceneLight(Color(0xFFE3ECF7), 0.10f, 60f)
            Scene.Park -> SceneLight(Color(0xFFF1F6D8), 0.10f, 100f)
            Scene.Rain -> SceneLight(Color(0xFFC6D2E6), 0.22f, 170f)
            Scene.Beach -> SceneLight(Color(0xFFFFC789), 0.24f, 15f)
            Scene.Night -> SceneLight(Color(0xFFB6C2E8), 0.30f, 80f)
        }
    }
}

/**
 * The colour of the place falling on the book: warm by the fire, blue by the rainy window,
 * golden at the beach. Multiplied over the book only; by the fire it flickers a little.
 */
@Composable
fun SceneLightLayer(shape: BookShape, light: SceneLight, motion: Boolean) {
    if (light.strength <= 0f) return
    val flicker = if (light.flicker && motion) {
        val t = rememberInfiniteTransition(label = "flicker")
        val a by t.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        val b by t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(530, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
        a * b
    } else 1f
    Canvas(Modifier.fillMaxSize()) {
        val b = shape.bounds
        drawRoundRect(
            light.color.copy(alpha = (light.strength * flicker).coerceIn(0f, 0.6f)),
            b.topLeft, b.size, CornerRadius(shape.overhang + 4f),
            blendMode = BlendMode.Multiply,
        )
    }
}

/**
 * The cover, hinged on the spine. It opens when the first page is there to show, swinging
 * over to the left, and closes again over the right-hand page when [closed] (the end of the
 * book). Shut, the place shows where the left page would be.
 */
@Composable
fun BookCover(
    shape: BookShape,
    cover: ImageBitmap?,
    board: Color,
    ready: Boolean,
    closed: Boolean,
    startOpen: Boolean = false,
    place: @Composable () -> Unit = {},
    onOpened: () -> Unit = {},
) {
    val angle = remember { Animatable(if (startOpen) 180f else 0f) }
    val fade = remember { Animatable(if (startOpen) 0f else 1f) }
    var first by remember { mutableStateOf(!startOpen) }
    LaunchedEffect(ready, closed) {
        if (!ready) return@LaunchedEffect
        if (closed) {
            fade.snapTo(1f)
            angle.animateTo(0f, tween(1150, easing = FastOutSlowInEasing))
        } else {
            if (first) kotlinx.coroutines.delay(250)
            first = false
            angle.animateTo(180f, tween(1250, easing = FastOutSlowInEasing))
            fade.animateTo(0f, tween(260, easing = LinearEasing))
            onOpened()
        }
    }
    val a = angle.value
    if ((a >= 180f && fade.value <= 0f) || shape.page.width < 8f) return
    val density = LocalDensity.current
    val page = shape.page
    val spread = shape.spread
    val leaf = if (spread) Rect(page.center.x, page.top, page.right, page.bottom) else page
    if (spread) {
        // A shut book is only as wide as its cover: the place shows where the left page will be.
        val reach = if (a <= 90f) 0f else -cos(Math.toRadians(a.toDouble())).toFloat() * leaf.width
        val margin = 72f * density.density
        val hide = Rect(page.left - margin, page.top - margin, page.center.x - reach, page.bottom + margin * 1.4f)
        if (hide.width > 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value }
                    .drawWithContent { clipRect(hide.left, hide.top, hide.right, hide.bottom) { this@drawWithContent.drawContent() } },
            ) { place() }
        }
    }
    val o = shape.overhang
    val lift = shape.maxLift
    val thick = shape.boardThick
    // The cover is a little bigger than the pages on a hardcover, as big on the others.
    val box = Rect(leaf.left, leaf.top - lift - o, leaf.right + shape.stackRight * 0.3f + o, leaf.bottom + o + thick)
    Box(
        Modifier
            .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
            .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = -a
                cameraDistance = 14f * density.density * 72f
                alpha = fade.value
            },
    ) {
        val front = a < 90f
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(if (shape.edition == Edition.Hardcover) o * 0.45f else 2f)
            val faceSize = Size(size.width, size.height - thick)
            // The thickness of the cover, seen at the front edge.
            drawRoundRect(lerp(board, Ink, 0.6f), Offset(0f, thick), faceSize, r)
            if (front) {
                if (cover != null) {
                    // A dust jacket, or the printed cover: the picture fills it.
                    val sw = cover.width.toFloat()
                    val sh = cover.height.toFloat()
                    val scale = max(faceSize.width / sw, faceSize.height / sh)
                    val cw = (faceSize.width / scale).roundToInt().coerceAtMost(cover.width)
                    val ch = (faceSize.height / scale).roundToInt().coerceAtMost(cover.height)
                    drawImage(
                        cover,
                        srcOffset = IntOffset((cover.width - cw) / 2, (cover.height - ch) / 2),
                        srcSize = IntSize(cw, ch),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(faceSize.width.roundToInt(), faceSize.height.roundToInt()),
                    )
                } else {
                    drawRoundRect(board, Offset.Zero, faceSize, r)
                }
                // The hinge groove by the spine, a glossy sheen, and the light leaving it as it turns.
                val groove = if (shape.edition == Edition.Hardcover) o * 2.2f else faceSize.width * 0.03f
                drawRect(Brush.horizontalGradient(listOf(Ink.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent), endX = groove * 1.6f), size = Size(groove * 1.6f, faceSize.height))
                drawRect(
                    Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), start = Offset(faceSize.width * 0.4f, 0f), end = Offset(faceSize.width, faceSize.height * 0.8f)),
                    size = faceSize,
                )
                drawRect(Ink.copy(alpha = min(0.5f, a / 180f)), size = faceSize)
            } else {
                // Inside of the cover: endpaper on a hardcover, the back of the cover otherwise.
                val inside = if (shape.edition == Edition.Hardcover) Color(0xFFE9DFC8) else lerp(board, Color.White, 0.75f)
                drawRoundRect(inside, Offset(o * 0.4f, o * 0.4f), Size(faceSize.width - o * 0.8f, faceSize.height - o * 0.8f), r)
                drawRect(Ink.copy(alpha = (180f - a) / 180f * 0.6f), size = faceSize)
            }
        }
    }
}
