package io.github.christiandonatti.fwip.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.christiandonatti.fwip.reader.CurlTurn
import io.github.christiandonatti.fwip.reader.ReaderLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Paper page turns for books. Readium shows text in a web view that cannot curl, so a turn
 * goes like this: the page on screen is drawn into a bitmap, that picture covers the page,
 * the navigator moves underneath without animation, the new page is drawn too, and the
 * picture of the old one curls away to reveal it (the same renderer as the comics).
 */
@Stable
class PageTurner(private val scope: CoroutineScope) {
    var navigator: EpubNavigatorFragment? = null
    /** Where the page view sits in the turner's overlay, in pixels. */
    var pageRect by mutableStateOf(Rect.Zero)
    /** Two pages side by side (the left one stays while the right one turns). */
    var spread by mutableStateOf(false)
    /** Curling is on (Paper turns, or the 3D book). Otherwise turns are Readium's own. */
    var enabled = false
    /** Text is being selected: horizontal drags belong to the selection handles. */
    var selecting = false
    var onTurned: () -> Unit = {}
    var onEnd: () -> Unit = {}
    /** Whether a place is on the book's last page; the navigator does not always say it cannot go on. */
    var isEnd: (Locator?) -> Boolean = { false }

    var turn by mutableStateOf<CurlTurn?>(null)
        private set
    /** The pages before and after the turn (whole spreads). */
    var before by mutableStateOf<Bitmap?>(null)
        private set
    var after by mutableStateOf<Bitmap?>(null)
        private set
    var forward by mutableStateOf(true)
        private set
    /** Bumped when a picture arrives, so the overlay redraws. */
    var version by mutableIntStateOf(0)
        private set

    private var job: Job? = null
    private var pending: Boolean? = null
    val busy: Boolean get() = turn != null

    /** The page view as it looks now. */
    private fun capture(): Bitmap? {
        val v: View = navigator?.publicationView ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return runCatching {
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            b
        }.getOrNull()
    }

    private fun makeTurn(forward: Boolean, touchY: Float): CurlTurn? {
        val r = pageRect
        if (r.width < 8f) return null
        val spine = if (spread) r.center.x else r.left
        val leaf = Rect(spine, r.top, r.right, r.bottom)
        val corner = Offset(leaf.right, if (touchY < leaf.center.y) leaf.top else leaf.bottom)
        val t = CurlTurn(0, 1, leaf, spine, corner, corner)
        if (!forward) t.point = t.end
        return t
    }

    /** Moves the navigator one page without animation; waits until the new page is drawn. */
    private suspend fun step(forward: Boolean): Boolean {
        val n = navigator ?: return false
        val old: Locator? = n.currentLocator.value
        val moved = if (forward) n.goForward(animated = false) else n.goBackward(animated = false)
        if (!moved) return false
        val now = withTimeoutOrNull(900) { n.currentLocator.first { it != old } }
        if (now == null && forward && isEnd(old)) return false
        // The web view paints a frame or two after reporting where it is; a new chapter takes longer,
        // so wait until the page shows something (up to about a second and a half).
        repeat(3) { withFrameNanos { } }
        if (now != null && now.href != old?.href) {
            delay(120)
            repeat(14) {
                if (capture()?.let(::hasInk) == true) return true
                delay(100)
            }
        } else {
            delay(40)
        }
        return true
    }

    /** Waits until the page view shows something (text or a picture), at most [maxMs]. */
    suspend fun awaitPainted(maxMs: Long) {
        val until = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < until) {
            if (capture()?.let(::hasInk) == true) return
            delay(150)
        }
    }

    /** Whether a page picture has anything on it besides the paper (a blank web view has not painted yet). */
    private fun hasInk(b: Bitmap): Boolean {
        val bg = b.getPixel(b.width / 2, 2)
        fun far(c: Int): Boolean {
            val d = abs(((c shr 16) and 255) - ((bg shr 16) and 255)) + abs(((c shr 8) and 255) - ((bg shr 8) and 255)) + abs((c and 255) - (bg and 255))
            return d > 60
        }
        for (gy in 1 until 40) for (gx in 1 until 24) {
            if (far(b.getPixel(b.width * gx / 24, b.height * gy / 40))) return true
        }
        return false
    }

    /** A turn from a tap, a key or a page turner. */
    fun turnPage(forward: Boolean) {
        if (busy) { pending = forward; return }
        job = scope.launch { autoTurn(forward) }
    }

    private suspend fun autoTurn(forward: Boolean) {
        val now = capture() ?: return
        val t = makeTurn(forward, Float.MAX_VALUE) ?: return
        begin(t, forward, now)
        // Show the picture before the page underneath changes.
        repeat(2) { withFrameNanos { } }
        if (!step(forward)) {
            finish()
            if (forward) onEnd()
            return
        }
        arrive(capture())
        onTurned()
        try {
            val lift = t.leaf.height * ReaderLook.lift
            val anim = Animatable(if (forward) 0f else 1f)
            anim.animateTo(if (forward) 1f else 0f, tween((560 * ReaderLook.speed).toInt(), easing = FastOutSlowInEasing)) {
                val x = t.corner.x + (t.end.x - t.corner.x) * value
                t.point = Offset(x, t.corner.y - lift * sin(PI * value).toFloat())
            }
        } finally {
            finish()
        }
    }

    private fun begin(t: CurlTurn, forward: Boolean, now: Bitmap) {
        this.forward = forward
        // Forward: the page now lifts away. Backward: the page now is what the turn ends on.
        if (forward) { before = now; after = null } else { after = now; before = null }
        turn = t
        version++
    }

    private fun arrive(b: Bitmap?) {
        if (forward) after = b else before = b
        version++
    }

    private fun finish() {
        turn = null
        before = null
        after = null
        version++
        pending?.let { p -> pending = null; turnPage(p) }
    }

    // ---------------------------------------------------------------- Finger-driven turns

    private var dragTurn: CurlTurn? = null
    private var dragStep: Job? = null
    private var dragOrigin = Offset.Zero
    private var dragStart = Offset.Zero
    private var gain = 1f

    /** A horizontal drag began at [x],[y] (page view coordinates). Returns whether it is ours. */
    fun dragStart(x: Float, y: Float, dx: Float): Boolean {
        if (!enabled || busy) return false
        val forward = dx < 0
        val now = capture() ?: return false
        val p = Offset(x + pageRect.left, y + pageRect.top)
        val t = makeTurn(forward, p.y) ?: return false
        begin(t, forward, now)
        dragTurn = t
        dragOrigin = p
        dragStart = if (forward) t.corner else t.end
        gain = ((t.corner.x - t.end.x) / pageRect.width * 1.15f).coerceAtLeast(1f)
        dragStep = scope.launch {
            repeat(2) { withFrameNanos { } }
            if (step(forward)) arrive(capture()) else {
                // Nothing that way: the page just goes back down.
                dragTurn = null
                finish()
                if (forward) onEnd()
            }
        }
        return true
    }

    fun dragMove(x: Float, y: Float) {
        val t = dragTurn ?: return
        val p = Offset(x + pageRect.left, y + pageRect.top)
        t.point = t.constrain(dragStart + Offset((p.x - dragOrigin.x) * gain, p.y - dragOrigin.y))
    }

    /** The finger let go ([finished] false: the gesture was cancelled). [vx] in pixels a second. */
    fun dragEnd(vx: Float, finished: Boolean) {
        val t = dragTurn ?: return
        dragTurn = null
        val fwd = forward
        val fling = 900f
        val complete = when {
            !finished -> false
            fwd -> vx < -fling || (t.progress > 0.4f && vx < fling)
            else -> vx > fling || (t.progress < 0.6f && vx > -fling)
        }
        scope.launch {
            dragStep?.join()
            if (turn == null) return@launch
            if (complete) onTurned()
            val target = if (complete == fwd) t.end else t.corner
            val distance = (target - t.point).getDistance()
            Animatable(t.point, Offset.VectorConverter).animateTo(
                target,
                tween(((180 + distance / pageRect.width.coerceAtLeast(1f) * 260).coerceIn(180f, 420f) * ReaderLook.speed).toInt(), easing = FastOutSlowInEasing),
            ) { t.point = value }
            // Put back: the navigator already moved, so it moves back while the picture still covers it.
            if (!complete) step(!fwd)
            finish()
        }
    }
}

/**
 * Holds the navigator and takes horizontal swipes away from it when pages turn like paper:
 * taps, long presses (selection) and vertical moves still reach the text.
 */
class TouchGate(context: Context) : FrameLayout(context) {
    var turner: PageTurner? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 0.8f
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragging = false
    private var tracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val t = turner ?: return false
        if (!t.enabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downAt = ev.eventTime
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (ev.pointerCount > 1 || t.selecting) return false
                // Held still first: that is a long press selecting text, not a page turn.
                if (ev.eventTime - downAt > ViewConfiguration.getLongPressTimeout() && abs(ev.x - downX) < slop * 3) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f && t.dragStart(downX, downY, dx)) {
                    dragging = true
                    t.dragMove(ev.x, ev.y)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return false
        val t = turner ?: return false
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> t.dragMove(ev.x, ev.y)
            MotionEvent.ACTION_UP -> {
                tracker?.computeCurrentVelocity(1000)
                t.dragEnd(tracker?.xVelocity ?: 0f, finished = true)
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                t.dragEnd(0f, finished = false)
                dragging = false
            }
        }
        return true
    }
}
