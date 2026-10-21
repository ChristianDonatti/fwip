package io.github.christiandonatti.fwip.data

import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One day of reading on this device. */
data class DayStats(val millis: Long = 0L, val pages: Int = 0, val finished: Int = 0)

/**
 * Reading time, pages and finished comics per day. Time only counts while pages are being
 * turned (at most a few minutes per page), so a reader left open on the nightstand does not
 * add hours.
 */
class ReadingStats(dir: File) {
    private val file = File(dir, "stats.json")
    private val days: MutableMap<String, DayStats> = load()

    private var sessionComic: String? = null
    private var lastEvent = 0L
    private var lastPage = -1

    fun all(): Map<LocalDate, DayStats> = synchronized(days) {
        days.mapNotNull { (k, v) -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to v } }.toMap()
    }

    private fun add(millis: Long = 0, pages: Int = 0, finished: Int = 0) {
        synchronized(days) {
            val key = LocalDate.now().toString()
            val d = days[key] ?: DayStats()
            days[key] = DayStats(d.millis + millis, d.pages + pages, d.finished + finished)
            save()
        }
    }

    /** A comic was opened at [page]. */
    fun start(comicId: String, page: Int) {
        sessionComic = comicId
        lastEvent = System.currentTimeMillis()
        lastPage = page
    }

    /** The reader moved to [page]; [justFinished] when this turn reached the last page. */
    fun turned(comicId: String, page: Int, justFinished: Boolean) {
        val now = System.currentTimeMillis()
        if (sessionComic != comicId) start(comicId, page)
        val time = (now - lastEvent).coerceIn(0, MAX_PER_PAGE)
        // Forward turns count as pages read; jumps (the page grid) count at most one spread.
        val pages = (page - lastPage).coerceIn(0, 2)
        lastEvent = now
        lastPage = maxOf(lastPage, page)
        add(millis = time, pages = pages, finished = if (justFinished) 1 else 0)
    }

    /** The reader closed or the app went to the background. */
    fun stop() {
        if (sessionComic == null) return
        add(millis = (System.currentTimeMillis() - lastEvent).coerceIn(0, MAX_PER_PAGE))
        sessionComic = null
    }

    private fun load(): MutableMap<String, DayStats> = runCatching {
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { k ->
            o.getJSONObject(k).let { DayStats(it.optLong("ms"), it.optInt("pages"), it.optInt("done")) }
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun save() {
        val o = JSONObject()
        days.forEach { (k, v) -> o.put(k, JSONObject().put("ms", v.millis).put("pages", v.pages).put("done", v.finished)) }
        runCatching { file.writeText(o.toString()) }
    }

    companion object {
        private const val MAX_PER_PAGE = 3 * 60 * 1000L
    }
}
