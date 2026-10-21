package io.github.christiandonatti.fwip.balloons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Detections per comic page, kept in memory and on disk so each page only goes through the
 * model once.
 */
class BalloonStore private constructor(private val context: Context) {

    private val dir = File(context.filesDir, "balloons").apply { mkdirs() }
    private val memory = HashMap<String, MutableMap<Int, List<Detection>>>()
    private val mutex = Mutex()
    private var detector: BalloonDetector? = null
    private var detectorFailed = false

    /**
     * Detections for [pages] of a comic, running the model only on pages never seen before.
     * [bitmapOf] provides the decoded page when needed. Returns null for pages that failed.
     */
    suspend fun detectionsFor(comicId: String, pages: List<Int>, bitmapOf: suspend (Int) -> Bitmap?): Map<Int, List<Detection>> {
        val known = synchronized(memory) { memory[comicId] }
        if (known != null && pages.all { it in known }) return pages.associateWith { known.getValue(it) }
        val missingBitmaps = mutableMapOf<Int, Bitmap>()
        val stored = synchronized(memory) { memory[comicId] } ?: withContext(Dispatchers.IO) { loadFile(comicId) }
        for (p in pages) if (p !in stored) bitmapOf(p)?.let { missingBitmaps[p] = it }
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val all = synchronized(memory) { memory.getOrPut(comicId) { stored } }
                val todo = missingBitmaps.filterKeys { it !in all }
                if (todo.isNotEmpty()) {
                    val detector = detector()
                    if (detector != null) {
                        val keys = todo.keys.toList()
                        runCatching {
                            val bitmaps = keys.map { todo.getValue(it) }
                            detector.detect(bitmaps).mapIndexed { i, found ->
                                val cuts = runCatching { PanelCutter.cut(bitmaps[i]) }.getOrDefault(emptyList())
                                found + cuts.map { Detection(it, DetectionKind.Cut, 1f) }
                            }
                        }
                            .onFailure { Log.w("Fwip", "Balloon detection failed", it) }
                            .getOrNull()
                            ?.let { results ->
                                synchronized(memory) { keys.forEachIndexed { i, p -> all[p] = results[i] } }
                                withContext(Dispatchers.IO) { saveFile(comicId, synchronized(memory) { all.toMap() }) }
                            }
                    }
                }
                synchronized(memory) { pages.filter { it in all }.associateWith { all.getValue(it) } }
            }
        }
    }

    private fun detector(): BalloonDetector? {
        if (detectorFailed) return null
        detector?.let { return it }
        return runCatching { BalloonDetector(context) }
            .onFailure {
                detectorFailed = true
                Log.e("Fwip", "Could not load the balloon model", it)
            }
            .getOrNull()
            ?.also { detector = it }
    }

    private fun file(comicId: String) = File(dir, sha1(comicId) + ".json")

    private fun loadFile(comicId: String): MutableMap<Int, List<Detection>> = runCatching {
        val root = JSONObject(file(comicId).readText())
        if (root.optInt("v") != VERSION) return@runCatching mutableMapOf<Int, List<Detection>>()
        val pages = root.getJSONObject("pages")
        pages.keys().asSequence().associate { key ->
            val arr = pages.getJSONArray(key)
            key.toInt() to (0 until arr.length()).map { i ->
                val d = arr.getJSONArray(i)
                Detection(
                    RectF(d.getDouble(0).toFloat(), d.getDouble(1).toFloat(), d.getDouble(2).toFloat(), d.getDouble(3).toFloat()),
                    DetectionKind.of(d.getInt(4)),
                    d.getDouble(5).toFloat(),
                )
            }
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveFile(comicId: String, pages: Map<Int, List<Detection>>) {
        val obj = JSONObject()
        pages.forEach { (page, list) ->
            val arr = JSONArray()
            list.forEach { d ->
                arr.put(
                    JSONArray()
                        .put(d.rect.left.toDouble()).put(d.rect.top.toDouble())
                        .put(d.rect.right.toDouble()).put(d.rect.bottom.toDouble())
                        .put(d.kind.code)
                        .put(d.score.toDouble())
                )
            }
            obj.put(page.toString(), arr)
        }
        runCatching { file(comicId).writeText(JSONObject().put("v", VERSION).put("pages", obj).toString()) }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val VERSION = 2

        @Volatile private var instance: BalloonStore? = null

        /** One store (and one loaded model) for the whole app. */
        fun get(context: Context): BalloonStore =
            instance ?: synchronized(this) {
                instance ?: BalloonStore(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Orders the balloons of a page the way a reader would: panel by panel, and inside each panel
 * from top to bottom and left to right (right to left for manga).
 */
fun readingOrder(detections: List<Detection>, rtl: Boolean): List<RectF> {
    val balloons = detections.filter { it.kind == DetectionKind.Balloon }.map { it.rect }
    val panels = bestPanels(detections)

    class Unit(val rect: RectF, val members: MutableList<RectF> = mutableListOf())

    val units = panels.map { Unit(it) }.toMutableList()
    for (b in balloons) {
        // A balloon crossing a gutter belongs to the panel holding most of it.
        val best = units.maxByOrNull { overlap(it.rect, b) }
        if (best != null && overlap(best.rect, b) > 0f) {
            best.members += b
        } else {
            units += Unit(RectF(b), mutableListOf(b))
        }
    }
    return xyOrder(units.filter { it.members.isNotEmpty() }, { it.rect }, rtl)
        .flatMap { unit -> xyOrder(unit.members, { it }, rtl) }
}

/** Panels of a page in reading order; empty when the page is a single image. */
fun panelOrder(detections: List<Detection>, rtl: Boolean): List<RectF> {
    val panels = bestPanels(detections)
    return if (panels.size >= 2) xyOrder(panels, { it }, rtl) else emptyList()
}

/**
 * Gutter cuts are exact when the page has gutters; otherwise the model's panels, minus boxes
 * that sit inside another one.
 */
private fun bestPanels(detections: List<Detection>): List<RectF> {
    val cuts = detections.filter { it.kind == DetectionKind.Cut }.map { it.rect }
    if (cuts.size >= 2) return cuts
    val ml = detections.filter { it.kind == DetectionKind.Panel }.map { it.rect }
    return ml.filter { p ->
        val area = p.width() * p.height()
        ml.none { o -> o !== p && o.width() * o.height() > area && overlap(o, p) > 0.85f * area }
    }
}

private fun overlap(a: RectF, b: RectF): Float {
    val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    return if (w > 0f && h > 0f) w * h else 0f
}

/**
 * Reading order by recursive XY-cut on the boxes themselves: split the group into bands
 * wherever a horizontal line crosses no box (read top to bottom); if there is none, into
 * columns wherever a vertical line crosses no box (left to right, or right to left for manga);
 * repeat inside each part. This follows the page layout the way gutters do: a tall panel next
 * to two stacked ones is read before or after the whole stack, never in the middle of it.
 * Boxes are shrunk a little first so slightly overlapping detections still separate.
 */
internal fun <T> xyOrder(items: List<T>, rectOf: (T) -> RectF, rtl: Boolean, depth: Int = 0): List<T> {
    if (items.size <= 1 || depth > 12) return items
    val shrunk = items.associateWith { item ->
        val r = rectOf(item)
        val dx = r.width() * SHRINK
        val dy = r.height() * SHRINK
        RectF(r.left + dx, r.top + dy, r.right - dx, r.bottom - dy)
    }
    val rows = bands(items, { shrunk.getValue(it).top }, { shrunk.getValue(it).bottom })
    if (rows.size > 1) return rows.flatMap { xyOrder(it, rectOf, rtl, depth + 1) }
    val cols = bands(items, { shrunk.getValue(it).left }, { shrunk.getValue(it).right })
    if (cols.size > 1) return (if (rtl) cols.reversed() else cols).flatMap { xyOrder(it, rectOf, rtl, depth + 1) }
    // Tangled boxes that no straight line separates: higher first, then by side.
    return items.sortedWith(
        compareBy<T> { (((rectOf(it).top + rectOf(it).bottom) / 2f) * 10f).toInt() }
            .thenBy { if (rtl) -rectOf(it).right else rectOf(it).left }
    )
}

private const val SHRINK = 0.08f

/** Splits items into consecutive groups along one axis, cutting wherever no box spans the gap. */
private fun <T> bands(items: List<T>, start: (T) -> Float, end: (T) -> Float): List<List<T>> {
    val groups = mutableListOf<MutableList<T>>()
    var reach = Float.NEGATIVE_INFINITY
    for (item in items.sortedBy(start)) {
        if (groups.isEmpty() || start(item) >= reach) {
            groups += mutableListOf(item)
            reach = end(item)
        } else {
            groups.last() += item
            reach = maxOf(reach, end(item))
        }
    }
    return groups
}
