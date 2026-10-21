package io.github.christiandonatti.fwip.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pages marked to come back to, per comic. One small JSON file; [updated] lets two devices
 * agree on the newest list when they sync.
 */
class BookmarkStore private constructor(private val file: File) {

    data class Entry(val pages: Set<Int>, val updated: Long)

    private val map: MutableMap<String, Entry> = load()

    fun pages(comicId: String): Set<Int> = synchronized(map) { map[comicId]?.pages.orEmpty() }

    fun all(): Map<String, Entry> = synchronized(map) { map.toMap() }

    fun set(comicId: String, pages: Set<Int>, updated: Long = System.currentTimeMillis()) {
        synchronized(map) {
            // An emptied list is kept (with its time) so the removal also reaches other devices.
            map[comicId] = Entry(pages, updated)
            save()
        }
    }

    private fun load(): MutableMap<String, Entry> = runCatching {
        val o = JSONObject(file.readText())
        val out = mutableMapOf<String, Entry>()
        o.keys().forEach { k ->
            val e = o.getJSONObject(k)
            val arr = e.getJSONArray("pages")
            out[k] = Entry((0 until arr.length()).map { arr.getInt(it) }.toSet(), e.optLong("t"))
        }
        out
    }.getOrDefault(mutableMapOf())

    private fun save() {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONObject().put("pages", JSONArray(v.pages.sorted())).put("t", v.updated)) }
        runCatching { file.writeText(o.toString()) }
    }

    companion object {
        @Volatile private var instance: BookmarkStore? = null

        fun get(context: Context): BookmarkStore = instance ?: synchronized(this) {
            instance ?: BookmarkStore(File(context.applicationContext.filesDir, "bookmarks.json")).also { instance = it }
        }
    }
}
