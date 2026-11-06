package io.github.christiandonatti.fwip.book

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A bookmark: where, which chapter, when. [locator] is Readium's JSON. */
data class BookMark(val id: String, val locator: String, val chapter: String, val progression: Double, val created: Long)

/** A highlighted passage, maybe with a note. */
data class BookHighlight(
    val id: String,
    val locator: String,
    val text: String,
    val color: Int,
    val note: String,
    val chapter: String,
    val progression: Double,
    val created: Long,
)

/**
 * What the reader keeps per book: the exact place (beyond the page number the library knows),
 * bookmarks, highlights and notes, and how fast it goes (for "time left").
 */
class BookNotes private constructor(private val file: File) {
    var lastLocator: String? = null
        private set
    val marks = mutableListOf<BookMark>()
    val highlights = mutableListOf<BookHighlight>()
    /** Reading speed for this book: positions read and the time it took. */
    var positionsRead = 0
        private set
    var msRead = 0L
        private set

    private fun load() {
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        lastLocator = o.optString("last").takeIf { it.isNotBlank() }
        positionsRead = o.optInt("posRead")
        msRead = o.optLong("msRead")
        o.optJSONArray("marks")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { m ->
                marks += BookMark(m.getString("id"), m.getString("loc"), m.optString("ch"), m.optDouble("p", 0.0), m.optLong("t"))
            }
        }
        o.optJSONArray("hl")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { h ->
                highlights += BookHighlight(
                    h.getString("id"), h.getString("loc"), h.optString("text"), h.optInt("color"), h.optString("note"),
                    h.optString("ch"), h.optDouble("p", 0.0), h.optLong("t"),
                )
            }
        }
    }

    @Synchronized
    fun save() {
        val o = JSONObject()
            .put("last", lastLocator)
            .put("posRead", positionsRead)
            .put("msRead", msRead)
            .put("marks", JSONArray().apply { marks.forEach { m -> put(JSONObject().put("id", m.id).put("loc", m.locator).put("ch", m.chapter).put("p", m.progression).put("t", m.created)) } })
            .put("hl", JSONArray().apply {
                highlights.forEach { h ->
                    put(JSONObject().put("id", h.id).put("loc", h.locator).put("text", h.text).put("color", h.color).put("note", h.note).put("ch", h.chapter).put("p", h.progression).put("t", h.created))
                }
            })
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    fun setLast(locator: String) { lastLocator = locator }

    fun addReading(positions: Int, ms: Long) {
        positionsRead += positions
        msRead += ms
        Speed.add(file.parentFile!!, positions, ms)
    }

    /** Positions per minute, from this book if there is enough, else from all books, else a usual pace. */
    fun speed(): Double {
        if (positionsRead >= 12 && msRead > 60_000) return positionsRead / (msRead / 60_000.0)
        return Speed.average(file.parentFile!!) ?: DEFAULT_SPEED
    }

    /** Markdown with the book's highlights and notes, for sharing or keeping elsewhere. */
    fun markdown(title: String, author: String?): String = buildString {
        append("# ").append(title).append('\n')
        if (!author.isNullOrBlank()) append("_").append(author).append("_\n")
        append('\n')
        var chapter: String? = null
        highlights.sortedBy { it.progression }.forEach { h ->
            if (h.chapter.isNotBlank() && h.chapter != chapter) {
                chapter = h.chapter
                append("## ").append(h.chapter).append("\n\n")
            }
            append("> ").append(h.text.replace("\n", "\n> ")).append("\n\n")
            if (h.note.isNotBlank()) append(h.note).append("\n\n")
        }
    }

    /** The overall reading pace, over every book. */
    private object Speed {
        private fun file(dir: File) = File(dir, "_speed.json")

        @Synchronized
        fun add(dir: File, positions: Int, ms: Long) {
            val f = file(dir)
            val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
            o.put("p", o.optInt("p") + positions).put("ms", o.optLong("ms") + ms)
            runCatching { f.writeText(o.toString()) }
        }

        fun average(dir: File): Double? {
            val o = runCatching { JSONObject(file(dir).readText()) }.getOrNull() ?: return null
            val p = o.optInt("p")
            val ms = o.optLong("ms")
            return if (p >= 20 && ms > 120_000) p / (ms / 60_000.0) else null
        }
    }

    companion object {
        /** About 1 000 characters a position, read at an ordinary pace. */
        const val DEFAULT_SPEED = 1.3

        fun dir(context: Context): File = File(context.filesDir, "booknotes").apply { mkdirs() }

        fun load(context: Context, bookId: String): BookNotes {
            val name = java.security.MessageDigest.getInstance("SHA-1").digest(bookId.toByteArray()).joinToString("") { "%02x".format(it) }
            return BookNotes(File(dir(context), "$name.json")).apply { load() }
        }

        /** "1 h 05 min", "12 min", "less than a minute". */
        fun timeLabel(minutes: Double, lessThanMinute: String): String {
            if (minutes < 1) return lessThanMinute
            val m = Math.round(minutes).toInt()
            return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)} min"
        }
    }
}
