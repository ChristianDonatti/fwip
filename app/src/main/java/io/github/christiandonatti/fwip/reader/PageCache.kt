package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.graphics.Bitmap
import android.util.LruCache
import io.github.christiandonatti.fwip.data.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps recently decoded pages in memory so turning back and forth is instant. */
class PageCache(private val source: ComicSource, private val cropMargins: Boolean = false) {

    private val budget = (Runtime.getRuntime().maxMemory() / 4).toInt()

    private val cache = object : LruCache<String, Bitmap>(budget) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private val mutex = Mutex()

    /** Why a page failed to load, shown on screen to help diagnose broken files. */
    private val errors = java.util.concurrent.ConcurrentHashMap<Int, String>()

    fun errorFor(index: Int): String? = errors[index]

    private fun key(index: Int, w: Int, h: Int) = "$index:${w / 64}:${h / 64}"

    fun peek(index: Int, w: Int, h: Int): Bitmap? = cache.get(key(index, w, h))

    suspend fun get(index: Int, w: Int, h: Int): Bitmap? {
        if (index !in 0 until source.pageCount || w <= 0 || h <= 0) return null
        val k = key(index, w, h)
        cache.get(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cache.get(k) ?: runCatching {
                    val raw = source.decodePage(index, w, h)
                    if (raw != null && cropMargins) MarginCrop.crop(raw).also { if (it !== raw) raw.recycle() } else raw
                }
                    .onFailure {
                        android.util.Log.w("Fwip", "Failed to decode page $index", it)
                        val cause = generateSequence(it) { e -> e.cause }.last()
                        errors[index] = cause.javaClass.simpleName + (cause.message?.let { m -> ": $m" } ?: "")
                    }
                    .onSuccess { b -> if (b == null) errors[index] = str(R.string.error_image_format) }
                    .getOrNull()?.also { cache.put(k, it) }
            }
        }
    }

    fun clear() = cache.evictAll()
}
