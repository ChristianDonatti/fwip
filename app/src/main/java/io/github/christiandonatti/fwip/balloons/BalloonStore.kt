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
                        runCatching { detector.detect(keys.map { todo.getValue(it) }) }
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
                    if (d.getInt(4) == 0) DetectionKind.Balloon else DetectionKind.Panel,
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
                        .put(if (d.kind == DetectionKind.Balloon) 0 else 1)
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
        private const val VERSION = 1

        @Volatile private var instance: BalloonStore? = null

        /** One store (and one loaded model) for the whole app. */
        fun get(context: Context): BalloonStore =
            instance ?: synchronized(this) {
                instance ?: BalloonStore(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Orders the balloons of a page the way a reader would: panel by panel (rows top to bottom,
 * left to right, or right to left for manga), and inside each panel the same way.
 */
fun readingOrder(detections: List<Detection>, rtl: Boolean): List<RectF> {
    val balloons = detections.filter { it.kind == DetectionKind.Balloon }.map { it.rect }
    val panels = detections.filter { it.kind == DetectionKind.Panel }.map { it.rect }

    class Unit(val rect: RectF, val members: MutableList<RectF> = mutableListOf())

    val units = panels.map { Unit(it) }.toMutableList()
    for (b in balloons) {
        val area = b.width() * b.height()
        val best = units.maxByOrNull { overlap(it.rect, b) }
        if (best != null && area > 0f && overlap(best.rect, b) / area > 0.5f) {
            best.members += b
        } else {
            // A balloon outside every panel (or spanning gutters) is its own stop.
            units += Unit(RectF(b), mutableListOf(b))
        }
    }
    return rows(units.filter { it.members.isNotEmpty() }, { it.rect }, rtl)
        .flatMap { unit -> rows(unit.members, { it }, rtl) }
}

private fun overlap(a: RectF, b: RectF): Float {
    val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    return if (w > 0f && h > 0f) w * h else 0f
}

/** Groups items into rows by vertical overlap, then reads each row horizontally. */
private fun <T> rows(items: List<T>, rectOf: (T) -> RectF, rtl: Boolean): List<T> {
    val sorted = items.sortedBy { rectOf(it).top }
    val out = mutableListOf<MutableList<T>>()
    var rowTop = 0f
    var rowBottom = 0f
    for (item in sorted) {
        val r = rectOf(item)
        val current = out.lastOrNull()
        val shared = minOf(rowBottom, r.bottom) - maxOf(rowTop, r.top)
        if (current != null && shared > 0.5f * minOf(r.height(), rowBottom - rowTop)) {
            current += item
            rowTop = minOf(rowTop, r.top)
            rowBottom = maxOf(rowBottom, r.bottom)
        } else {
            out += mutableListOf(item)
            rowTop = r.top
            rowBottom = r.bottom
        }
    }
    return out.flatMap { row -> if (rtl) row.sortedByDescending { rectOf(it).right } else row.sortedBy { rectOf(it).left } }
}
