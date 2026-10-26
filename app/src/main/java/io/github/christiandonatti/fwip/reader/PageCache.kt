package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import io.github.christiandonatti.fwip.data.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps decoded pages in memory so turning back and forth is instant.
 *
 * Pages around the one being read are [hold]-ed: they stay in memory whatever else gets
 * decoded, so the spread on screen and its neighbours (needed mid-turn) are never dropped to
 * make room for pages further away.
 */
class PageCache(
    private val source: ComicSource,
    private val cropMargins: Boolean = false,
    budget: Int = (Runtime.getRuntime().maxMemory() / 4).toInt(),
    private val filters: PageFilters = PageFilters(),
) {

    private val cache = object : LruCache<String, Bitmap>(budget) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Pages that must stay in memory, by page index. */
    @Volatile private var keep: Set<Int> = emptySet()
    private val held = ConcurrentHashMap<String, Bitmap>()

    /** One lock per page and size: a page still being extracted never holds up the others. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Why a page failed to load, shown on screen to help diagnose broken files. */
    private val errors = ConcurrentHashMap<Int, String>()

    fun errorFor(index: Int): String? = errors[index]

    private fun key(index: Int, w: Int, h: Int) = "$index:${w / 64}:${h / 64}"
    private fun indexOf(key: String) = key.substringBefore(':').toIntOrNull()

    fun peek(index: Int, w: Int, h: Int): Bitmap? {
        val k = key(index, w, h)
        return cache.get(k) ?: held[k]
    }

    /** Keeps [pages] in memory until the next call; the rest can be dropped when space runs out. */
    fun hold(pages: Set<Int>) {
        keep = pages
        held.keys.removeAll { indexOf(it) !in pages }
        cache.snapshot().forEach { (k, b) -> if (indexOf(k) in pages) held[k] = b }
    }

    suspend fun get(index: Int, w: Int, h: Int): Bitmap? {
        if (index !in 0 until source.pageCount || w <= 0 || h <= 0) return null
        val k = key(index, w, h)
        peek(index, w, h)?.let { return it }
        return withContext(Dispatchers.IO) {
            locks.getOrPut(k) { Mutex() }.withLock {
                peek(index, w, h) ?: runCatching {
                    val raw = source.decodePage(index, w, h)
                    val cropped = if (raw != null && cropMargins) MarginCrop.crop(raw).also { if (it !== raw) raw.recycle() } else raw
                    if (cropped != null && filters.active) filters.apply(cropped).also { if (it !== cropped) cropped.recycle() } else cropped
                }
                    .onFailure {
                        android.util.Log.w("Fwip", "Failed to decode page $index", it)
                        val cause = generateSequence(it) { e -> e.cause }.last()
                        errors[index] = cause.javaClass.simpleName + (cause.message?.let { m -> ": $m" } ?: "")
                    }
                    .onSuccess { b -> if (b == null) errors[index] = str(R.string.error_image_format) }
                    .getOrNull()?.also {
                        cache.put(k, it)
                        if (index in keep) held[k] = it
                    }
            }
        }
    }

    fun clear() {
        held.clear()
        cache.evictAll()
    }

    companion object {
        /**
         * Page pixels live outside the Java heap, so the room for them follows the device's
         * memory: a good share on a big tablet, less on a small phone.
         */
        fun budgetFor(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val total = am?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem } ?: 0L
            val mb = 1024L * 1024
            val budget = if (total > 0) (total / 16).coerceIn(160 * mb, 512 * mb) else 160 * mb
            return if (am?.isLowRamDevice == true) (96 * mb).toInt() else budget.toInt()
        }
    }
}
