package io.github.christiandonatti.fwip.reader

import android.graphics.Bitmap
import android.util.LruCache
import io.github.christiandonatti.fwip.data.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps recently decoded pages in memory so turning back and forth is instant. */
class PageCache(private val source: ComicSource) {

    private val budget = (Runtime.getRuntime().maxMemory() / 4).toInt()

    private val cache = object : LruCache<String, Bitmap>(budget) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private val mutex = Mutex()

    private fun key(index: Int, w: Int, h: Int) = "$index:${w / 64}:${h / 64}"

    fun peek(index: Int, w: Int, h: Int): Bitmap? = cache.get(key(index, w, h))

    suspend fun get(index: Int, w: Int, h: Int): Bitmap? {
        if (index !in 0 until source.pageCount || w <= 0 || h <= 0) return null
        val k = key(index, w, h)
        cache.get(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cache.get(k) ?: runCatching { source.decodePage(index, w, h) }
                    .onFailure { android.util.Log.w("Fwip", "Failed to decode page $index", it) }
                    .getOrNull()?.also { cache.put(k, it) }
            }
        }
    }

    fun clear() = cache.evictAll()
}
