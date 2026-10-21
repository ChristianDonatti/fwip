package io.github.christiandonatti.fwip.data

import android.net.Uri

enum class ComicFormat(val label: String) {
    ZIP("CBZ"),
    RAR("CBR"),
    SEVEN_ZIP("CB7"),
    PDF("PDF");

    companion object {
        fun fromFileName(name: String): ComicFormat? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "cbz", "zip" -> ZIP
                "cbr", "rar" -> RAR
                "cb7", "7z" -> SEVEN_ZIP
                "pdf" -> PDF
                else -> null
            }
    }
}

data class Comic(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val title: String,
    val folder: String,
    val format: ComicFormat,
    val size: Long,
    val lastModified: Long,
    /** Set for books read straight from a server; never saved with the library. */
    val remote: io.github.christiandonatti.fwip.remote.RemoteBook? = null,
)

data class ReadingProgress(
    val page: Int,
    val pageCount: Int,
    val lastRead: Long,
) {
    val started: Boolean get() = page > 0
    val finished: Boolean get() = pageCount > 0 && page >= pageCount - 1
    val fraction: Float get() = if (pageCount <= 1) 0f else page.toFloat() / (pageCount - 1)
}

data class LibraryItem(
    val comic: Comic,
    val coverPath: String?,
    val color: Int?,
    val progress: ReadingProgress?,
    val meta: ComicMeta? = null,
) {
    /** What the file name says, for comics without metadata. */
    val parsed: ParsedName by lazy { parseFileName(comic.fileName) }

    /** "Series #Number" from the metadata when known, otherwise read from the file name. */
    val title: String = meta?.displayTitle ?: parseFileName(comic.fileName).display

    /**
     * Series from the metadata when known, otherwise from the file name. A start year in the
     * metadata keeps relaunches apart: "Batman (2011)" and "Batman (2016)" are two shelves.
     */
    val series: SeriesInfo = meta?.series?.takeIf { it.isNotBlank() }
        ?.let { SeriesInfo(seriesLabel(it, meta.volume), meta.numberValue) }
        ?: parseFileName(comic.fileName).let { SeriesInfo(it.series, it.sortNumber) }

    /** Issue number to match against an online database. */
    val issueNumber: String?
        get() = meta?.number?.takeIf { it.isNotBlank() } ?: parsed.number ?: parsed.volume?.toString()
}

/** "Batman (2016)" when the volume is a start year. */
fun seriesLabel(name: String, volume: Int?): String = if (volume != null && volume > 1900) "$name ($volume)" else name

/** Turns "Batman_Vol.2_#07" into "Batman Vol.2 #07". */
fun prettyTitle(fileName: String): String =
    fileName.substringBeforeLast('.')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { fileName }

/**
 * Natural ordering so "page2.jpg" comes before "page10.jpg".
 * Compares runs of digits by numeric value and everything else case-insensitively.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ei
                j = ej
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
