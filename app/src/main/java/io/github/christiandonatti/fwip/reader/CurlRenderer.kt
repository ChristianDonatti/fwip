package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot
import kotlin.math.min

/**
 * One page turn in progress, in "logical" coordinates where pages always turn right to left
 * (manga mode mirrors the drawing, not the maths).
 *
 * The leaf is the page being lifted. Its outer corner [corner] follows [point]; the fold is the
 * perpendicular bisector between the two, and the part of the leaf beyond the fold is drawn
 * reflected over it, showing the back of the paper.
 */
@Stable
class CurlTurn(
    val from: Int,
    val to: Int,
    val leaf: Rect,
    val spine: Float,
    val corner: Offset,
    point: Offset,
) {
    /** Where the corner lands when the page is fully turned: mirrored across the spine. */
    val end = Offset(2f * spine - corner.x, corner.y)

    var point by mutableStateOf(point)

    /** 0 = flat on the original side, 1 = fully turned. */
    val progress: Float
        get() = ((corner.x - point.x) / (corner.x - end.x)).coerceIn(0f, 1f)

    /** Keeps the corner where a page attached at the spine can physically reach. */
    fun constrain(p: Offset): Offset {
        val w = corner.x - spine
        val otherY = if (corner.y >= leaf.center.y) leaf.top else leaf.bottom
        var q = p
        val s1 = Offset(spine, corner.y)
        val d1 = q - s1
        val l1 = d1.getDistance()
        if (l1 > w) q = s1 + d1 * (w / l1)
        val s2 = Offset(spine, otherY)
        val diag = hypot(w, leaf.height)
        val d2 = q - s2
        val l2 = d2.getDistance()
        if (l2 > diag) q = s2 + d2 * (diag / l2)
        return q
    }
}

/** What is visible around a turn. Any of these can be missing (cover, last page, still loading). */
class CurlPages(
    val fromLeft: Pair<Bitmap, Rect>?,
    val fromLeaf: Bitmap?,
    val back: Bitmap?,
    val toRight: Pair<Bitmap, Rect>?,
    /** Single-page mode: the back of the leaf is plain paper showing the page through it. */
    val paperBack: Boolean,
)

class CurlRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val matrix = Matrix()
    private val tmp = RectF()

    fun draw(
        c: Canvas,
        width: Float,
        turn: CurlTurn,
        pages: CurlPages,
        rtl: Boolean,
        background: Int,
        paper: Int,
    ) {
        c.save()
        if (rtl) c.scale(-1f, 1f, width / 2f, 0f)
        c.drawColor(background)

        // What lies underneath: the left page that stays, and the page that gets revealed.
        pages.fromLeft?.let { (b, r) -> page(c, b, r, rtl, mirror = false) }
        pages.toRight?.let { (b, r) -> page(c, b, r, rtl, mirror = false) }

        val leaf = turn.leaf
        val corner = turn.corner
        val p = turn.constrain(turn.point)
        val d = p - corner
        val len = d.getDistance()
        if (len < 1f) {
            pages.fromLeaf?.let { page(c, it, leaf, rtl, mirror = false) }
            c.restore()
            return
        }
        val n = Offset(d.x / len, d.y / len)
        val m = (corner + p) / 2f

        // 1. The part of the leaf that is still lying flat.
        c.save()
        halfPlane(m, n, +1f)
        c.clipPath(path)
        clipRect(c, leaf)
        pages.fromLeaf?.let { page(c, it, leaf, rtl, mirror = false) }
        c.restore()

        // 2. Shadow the lifted page casts on what is revealed, darkest along the fold.
        c.save()
        halfPlane(m, n, -1f)
        c.clipPath(path)
        clipRect(c, leaf)
        val shadowW = min(len * 0.45f, leaf.width * 0.28f) + 6f
        shadePaint.shader = LinearGradient(
            m.x, m.y, m.x - n.x * shadowW, m.y - n.y * shadowW,
            intArrayOf(0x88000000.toInt(), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, width, leaf.bottom + 1000f, shadePaint)
        c.restore()

        // 3. The flap: the lifted part, mirrored over the fold, showing the back of the page.
        c.save()
        reflection(m, n)
        c.concat(matrix)
        clipRect(c, leaf)
        halfPlane(m, n, -1f)
        c.clipPath(path)
        fillPaint.color = paper
        c.drawRect(toRectF(leaf), fillPaint)
        if (pages.paperBack) {
            // Single page: we see the printed side through thin paper, mirrored by the fold itself.
            pages.fromLeaf?.let { page(c, it, leaf, rtl, mirror = false) }
            fillPaint.color = (paper and 0x00FFFFFF) or (0xD9 shl 24)
            c.drawRect(toRectF(leaf), fillPaint)
        } else {
            // Double page: the back of this sheet is the next spread's left page.
            pages.back?.let { page(c, it, leaf, rtl, mirror = true) }
        }
        // Curvature: dark close to the fold, a soft highlight further out.
        val depth = len / 2f
        shadePaint.shader = LinearGradient(
            m.x, m.y, m.x - n.x * depth, m.y - n.y * depth,
            intArrayOf(0x55000000, 0x10000000, 0x22FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.18f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(toRectF(leaf), shadePaint)
        c.restore()

        c.restore()
    }

    private fun page(c: Canvas, b: Bitmap, r: Rect, rtl: Boolean, mirror: Boolean) {
        c.save()
        // Undo the manga mirroring for the artwork itself, and mirror the back side of a sheet.
        if (rtl xor mirror) c.scale(-1f, 1f, r.center.x, r.center.y)
        c.drawBitmap(b, null, toRectF(r), bitmapPaint)
        c.restore()
    }

    private fun toRectF(r: Rect): RectF {
        tmp.set(r.left, r.top, r.right, r.bottom)
        return tmp
    }

    private fun clipRect(c: Canvas, r: Rect) {
        c.clipRect(r.left, r.top, r.right, r.bottom)
    }

    /** Fills [path] with the half plane on the [sign] side of the line through [m] with normal [n]. */
    private fun halfPlane(m: Offset, n: Offset, sign: Float) {
        val big = 100_000f
        val u = Offset(-n.y, n.x)
        val a = m + u * big
        val b = m - u * big
        val off = n * (sign * big)
        path.reset()
        path.moveTo(a.x, a.y)
        path.lineTo(b.x, b.y)
        path.lineTo(b.x + off.x, b.y + off.y)
        path.lineTo(a.x + off.x, a.y + off.y)
        path.close()
    }

    /** Sets [matrix] to the reflection across the line through [m] with unit normal [n]. */
    private fun reflection(m: Offset, n: Offset) {
        val a = 1f - 2f * n.x * n.x
        val b = -2f * n.x * n.y
        val d = 1f - 2f * n.y * n.y
        val k = 2f * (m.x * n.x + m.y * n.y)
        matrix.setValues(
            floatArrayOf(
                a, b, k * n.x,
                b, d, k * n.y,
                0f, 0f, 1f,
            )
        )
    }
}
