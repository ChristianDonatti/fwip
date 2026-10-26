package io.github.christiandonatti.fwip.data

import org.w3c.dom.Element

/** One issue of a ComicRack reading list (.cbl). */
data class CblBook(val series: String, val number: String, val volume: Int? = null, val year: Int? = null) {
    /** "Civil War #3 (2006)", for the list of what was not found. */
    val label: String get() = "$series #$number" + ((volume ?: year)?.let { " ($it)" } ?: "")
}

data class CblList(val name: String, val books: List<CblBook>)

/** A comic of the library, described the way reading lists name issues. */
data class CblCandidate(val id: String, val series: String, val number: String?, val volume: Int?, val year: Int?)

/** What an import found: the library comics in the list's order, and the issues missing here. */
data class CblMatch(val name: String, val ids: List<String>, val missing: List<CblBook>, val total: Int)

/**
 * ComicRack reading lists: an event or saga as an ordered list of issues (series, number, volume,
 * year). Komga, Kavita and YACReader read them, and fans share thousands of them.
 */
object ReadingLists {
    fun parse(bytes: ByteArray): CblList {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Plain lists only: no outside entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(bytes.inputStream())
        val root = doc.documentElement
        val name = root.getElementsByTagName("Name").let { nl ->
            (0 until nl.length).map { nl.item(it) }.firstOrNull { it.parentNode === root }?.textContent?.trim()
        }.orEmpty()
        val books = root.getElementsByTagName("Book").let { nl ->
            (0 until nl.length).mapNotNull { i ->
                val e = nl.item(i) as? Element ?: return@mapNotNull null
                val series = e.getAttribute("Series").trim()
                val number = e.getAttribute("Number").trim()
                if (series.isEmpty() || number.isEmpty()) null
                else CblBook(series, number, e.getAttribute("Volume").trim().toIntOrNull(), e.getAttribute("Year").trim().toIntOrNull())
            }
        }
        return CblList(name, books)
    }

    /** "The Amazing Spider-Man (2018)" and "Amazing Spider-Man" become the same key. */
    internal fun seriesNorm(s: String): String =
        seriesKey(s).removePrefix("the ").replace(Regex("\\s+(19|20)\\d\\d$"), "").replace(Regex("\\s+vol(ume)?\\s*\\d+$"), "").trim()

    /** "001", "#1" and "1.0" are the same issue; "½" is 0.5. */
    internal fun numberNorm(s: String?): String? {
        val t = s?.trim()?.removePrefix("#")?.trim()?.replace("½", "0.5")?.replace(',', '.') ?: return null
        if (t.isEmpty()) return null
        return t.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: t.lowercase()
    }

    fun match(list: CblList, library: List<CblCandidate>): CblMatch {
        val bySeries = library.groupBy { seriesNorm(it.series) }
        val ids = mutableListOf<String>()
        val missing = mutableListOf<CblBook>()
        for (book in list.books) {
            val number = numberNorm(book.number)
            val found = bySeries[seriesNorm(book.series)].orEmpty().filter { numberNorm(it.number) == number }
            val pick = when {
                found.size <= 1 -> found.firstOrNull()
                else -> {
                    // Several runs of the same series: the start year (volume) decides, then the year.
                    val target = book.volume ?: book.year
                    found.firstOrNull { book.volume != null && (it.volume == book.volume || it.year == book.volume) }
                        ?: found.firstOrNull { book.year != null && it.year == book.year }
                        ?: target?.let { t -> found.minByOrNull { c -> Math.abs((c.volume?.takeIf { v -> v > 1900 } ?: c.year ?: t) - t) } }
                        ?: found.first()
                }
            }
            if (pick != null && pick.id !in ids) ids += pick.id else if (pick == null) missing += book
        }
        return CblMatch(list.name, ids, missing, list.books.size)
    }
}
