package io.github.christiandonatti.fwip.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Turn bookkeeping for [CurlReader], kept outside composition so gestures and commands share it. */
@Stable
private class CurlController(private val host: ReaderHost, start: Int) {
    var current by mutableIntStateOf(start.coerceIn(0, host.spreads.lastIndex))
    var turn by mutableStateOf<CurlTurn?>(null)
    var animating by mutableStateOf(false)
    var size by mutableStateOf(IntSize.Zero)
    var zoom: ZoomState? = null

    val w get() = size.width.toFloat().coerceAtLeast(1f)
    val h get() = size.height.toFloat().coerceAtLeast(1f)

    var onEnd: () -> Unit = {}

    fun bitmap(page: Int) = host.cache.peek(page, host.wFor(page), host.decodeH)

    private fun sizeOf(page: Int): IntSize? = bitmap(page)?.let { IntSize(it.width, it.height) }

    /** Logical (left-to-right) placement of a spread; manga mode mirrors only when drawing. */
    fun logical(spread: Int) = SpreadLayout.place(host.spreads[spread], host.double, rtl = false, w, h, ::sizeOf, host::isWide)

    /** Whether going [forward] from the current spread is possible at all. */
    fun canGo(forward: Boolean) = if (forward) current < host.spreads.lastIndex else current > 0

    fun makeTurn(forward: Boolean, touchY: Float): CurlTurn? {
        val from = if (forward) current else current - 1
        val to = from + 1
        if (from < 0 || to > host.spreads.lastIndex) return null
        val page: Rect = logical(from).firstOrNull { it.slot != Slot.Left }?.rect ?: return null
        val spine = if (host.double) w / 2f else page.left
        // Only the part right of the spine turns; for a wide image that is its right half.
        val leaf = Rect(maxOf(page.left, spine), page.top, page.right, page.bottom)
        if (leaf.width < 8f) return null
        val corner = Offset(leaf.right, if (touchY < leaf.center.y) leaf.top else leaf.bottom)
        val t = CurlTurn(from, to, leaf, spine, corner, corner)
        if (!forward) t.point = t.end
        return t
    }

    /** Lets go of a dragged page: it either finishes turning or falls back. */
    suspend fun settle(t: CurlTurn, complete: Boolean) {
        animating = true
        try {
            val target = if (complete) t.end else t.corner
            val distance = (target - t.point).getDistance()
            val anim = Animatable(t.point, Offset.VectorConverter)
            anim.animateTo(target, tween((180 + distance / w * 260).toInt().coerceIn(180, 420), easing = FastOutSlowInEasing)) {
                t.point = value
            }
            current = if (complete) t.to else t.from
        } finally {
            turn = null
            animating = false
        }
    }

    /** A full turn with no finger: the corner lifts in an arc and lands on the other side. */
    suspend fun autoTurn(forward: Boolean) {
        if (animating || turn != null) return
        if (!canGo(forward)) {
            if (forward) onEnd()
            return
        }
        val t = makeTurn(forward, touchY = Float.MAX_VALUE)
        if (t == null) {
            // A layout with nothing to lift (e.g. a lone left page before a wide one): just move.
            zoom?.snapReset()
            current += if (forward) 1 else -1
            return
        }
        zoom?.snapReset()
        turn = t
        animating = true
        try {
            val lift = t.leaf.height * 0.16f
            val anim = Animatable(if (forward) 0f else 1f)
            anim.animateTo(if (forward) 1f else 0f, tween(520, easing = FastOutSlowInEasing)) {
                val x = t.corner.x + (t.end.x - t.corner.x) * value
                t.point = Offset(x, t.corner.y - lift * sin(PI * value).toFloat())
            }
            current = if (forward) t.to else t.from
        } finally {
            turn = null
            animating = false
        }
    }
}

/**
 * Reader where pages turn like paper: drag anywhere and the page curls under your finger,
 * or tap the sides / use the volume keys for an animated turn.
 */
@Composable
fun CurlReader(
    host: ReaderHost,
    startSpread: Int,
    commands: Flow<NavCommand>,
    onSpreadChanged: (Int) -> Unit,
    onZoomState: (ZoomState) -> Unit,
    onTap: (Offset, IntSize, ZoomState) -> Unit,
    onDoubleTap: (Offset, ZoomState) -> Boolean,
    onEnd: () -> Unit,
) {
    val spreads = host.spreads
    val ctl = remember(host) { CurlController(host, startSpread) }
    ctl.onEnd = onEnd
    val renderer = remember { CurlRenderer() }
    val scope = rememberCoroutineScope()
    // Bumped when a neighbour finishes decoding so a turn in progress redraws with it.
    var loadedVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(ctl.current) { onSpreadChanged(ctl.current) }

    LaunchedEffect(ctl.current, host) {
        for (n in listOf(ctl.current + 1, ctl.current - 1, ctl.current + 2)) {
            spreads.getOrNull(n)?.forEach { host.cache.get(it, host.wFor(it), host.decodeH) }
            loadedVersion++
        }
    }

    LaunchedEffect(ctl, commands) {
        commands.collect { cmd ->
            when (cmd) {
                is NavCommand.Go -> repeat(abs(cmd.delta)) { ctl.autoTurn(cmd.delta > 0) }
                is NavCommand.Seek -> if (!ctl.animating) {
                    ctl.turn = null
                    ctl.current = cmd.spread.coerceIn(0, spreads.lastIndex)
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { ctl.size = it }
            // Finger-driven turns. The zoomable page below sees touches first; we only take
            // over once a horizontal swipe starts on an unzoomed page.
            .pointerInput(ctl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (ctl.animating || ctl.turn != null || ctl.zoom?.isZoomed == true) return@awaitEachGesture
                    var overSlop = 0f
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        overSlop = over
                        change.consume()
                    } ?: return@awaitEachGesture

                    fun lx(x: Float) = if (host.rtl) ctl.w - x else x
                    val forward = (if (host.rtl) -overSlop else overSlop) < 0
                    val t = ctl.makeTurn(forward, down.position.y)
                    if (t == null) {
                        if (!ctl.canGo(forward)) {
                            if (forward) ctl.onEnd()
                        } else {
                            ctl.current += if (forward) 1 else -1
                        }
                        return@awaitEachGesture
                    }
                    ctl.turn = t
                    val start = if (forward) t.corner else t.end
                    // The corner travels twice the leaf width while the finger crosses the screen.
                    val gain = ((t.corner.x - t.end.x) / ctl.w * 1.15f).coerceAtLeast(1f)
                    val origin = Offset(lx(down.position.x), down.position.y)
                    val tracker = VelocityTracker()

                    fun follow(x: Float, y: Float, time: Long) {
                        val pos = Offset(lx(x), y)
                        tracker.addPosition(time, pos)
                        t.point = t.constrain(start + Offset((pos.x - origin.x) * gain, pos.y - origin.y))
                    }
                    follow(slop.position.x, slop.position.y, slop.uptimeMillis)

                    val finished = drag(slop.id) { change ->
                        follow(change.position.x, change.position.y, change.uptimeMillis)
                        change.consume()
                    }
                    val vx = tracker.calculateVelocity().x
                    val fling = 900f
                    val complete = when {
                        !finished -> !forward // cancelled, e.g. a second finger: put the page back
                        forward -> vx < -fling || (t.progress > 0.4f && vx < fling)
                        else -> !(vx > fling || (t.progress < 0.6f && vx > -fling))
                    }
                    scope.launch { ctl.settle(t, complete) }
                }
            }
    ) {
        // The page at rest, zoomable. Re-created per spread so zoom resets on every turn.
        key(ctl.current) {
            val zoom = remember { ZoomState() }
            LaunchedEffect(zoom) {
                ctl.zoom = zoom
                onZoomState(zoom)
            }
            Zoomable(
                state = zoom,
                onTap = { pos, size -> onTap(pos, size, zoom) },
                onDoubleTap = { pos -> onDoubleTap(pos, zoom) },
                modifier = Modifier.fillMaxSize(),
            ) {
                SpreadView(spreads[ctl.current], host.double, host.rtl, host.cache, host.decodeW, host.decodeH, isWide = host::isWide)
            }
        }

        // The turn itself, drawn over everything while it lasts.
        ctl.turn?.let { t ->
            Canvas(Modifier.fillMaxSize()) {
                if (loadedVersion < 0) return@Canvas
                val fromPlaced = ctl.logical(t.from)
                val toPlaced = ctl.logical(t.to)
                val spine = t.spine
                fun draw(p: PlacedPage?, clip: Boolean = false) = p?.let { pp ->
                    ctl.bitmap(pp.page)?.let { PageDraw(it, pp.rect, clipLeft = if (clip && host.double && pp.slot == Slot.Full) spine else null) }
                }
                val toWide = toPlaced.firstOrNull { it.slot == Slot.Full && host.double }
                val pages = CurlPages(
                    fromLeft = draw(fromPlaced.firstOrNull { it.slot == Slot.Left }),
                    fromLeaf = draw(fromPlaced.firstOrNull { it.slot != Slot.Left }),
                    back = if (toWide != null) {
                        // The back of the sheet is the half of the wide image that lands on the left.
                        ctl.bitmap(toWide.page)?.let { b ->
                            val half = if (host.rtl) android.graphics.Rect(b.width / 2, 0, b.width, b.height)
                            else android.graphics.Rect(0, 0, b.width / 2, b.height)
                            PageDraw(b, t.leaf, src = half)
                        }
                    } else {
                        toPlaced.firstOrNull { it.slot == Slot.Left }?.let { pp -> ctl.bitmap(pp.page)?.let { PageDraw(it, t.leaf) } }
                    },
                    toRight = draw(toPlaced.firstOrNull { it.slot != Slot.Left }, clip = true),
                    paperBack = !host.double,
                )
                drawIntoCanvas {
                    renderer.draw(
                        it.nativeCanvas, size.width, t, pages, host.rtl,
                        background = FwipColors.ReaderBg.toArgb(),
                        paper = 0xFFF7F4EC.toInt(),
                    )
                }
            }
        }
    }
}
