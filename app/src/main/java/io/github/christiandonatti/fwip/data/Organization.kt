package io.github.christiandonatti.fwip.data

/** Series name and issue number guessed from a title like "Batman Vol.2 #07" or "Blacksad - Livro 1". */
data class SeriesInfo(val name: String, val number: Float?)

private val seriesPattern = Regex(
    """^(?<name>.*?)[\s._\-–,:]*(?:#|n[º°o]\.?\s*|vol(?:ume)?\.?\s*|v\.?\s*|livro\s*|book\s*|tomo\s*|cap(?:[ií]tulo)?\.?\s*|ch(?:apter)?\.?\s*|ed(?:i[cç][aã]o)?\.?\s*|issue\s*)?\(?(?<num>\d{1,4}(?:\.\d)?)\)?(?:\s*(?:\(.*\)|\[.*]))*\s*$""",
    RegexOption.IGNORE_CASE,
)

fun parseSeries(title: String): SeriesInfo {
    val m = seriesPattern.matchEntire(title.trim())
    val name = m?.groups?.get("name")?.value?.trim(' ', '-', '_', '.', '–')
    if (m == null || name.isNullOrBlank()) return SeriesInfo(title.trim(), null)
    return SeriesInfo(name, m.groups["num"]?.value?.toFloatOrNull())
}

fun seriesKey(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** A user-made collection. */
data class ComicCollection(val id: String, val name: String, val comicIds: List<String>)

/** Groups the library can be browsed by. */
sealed interface Shelf {
    val title: String

    data class Series(val key: String, override val title: String) : Shelf
    data class Folder(val name: String) : Shelf {
        override val title: String get() = name.ifBlank { "Pasta principal" }
    }
    data class Custom(val id: String, override val title: String) : Shelf
    data class Smart(val kind: SmartKind) : Shelf {
        override val title: String get() = kind.title
    }
}

enum class SmartKind(val title: String) {
    Reading("Lendo agora"),
    Unread("Não lidas"),
    Finished("Lidas"),
    Recent("Adicionadas recentemente"),
}

enum class SortOrder(val label: String) { Title("Título"), Recent("Lidas por último"), Added("Adicionadas") }

fun List<LibraryItem>.sortedFor(order: SortOrder): List<LibraryItem> = when (order) {
    SortOrder.Title -> sortedWith(
        compareBy<LibraryItem> { seriesKey(it.series.name) }
            .thenBy { it.series.number ?: Float.MAX_VALUE }
            .thenBy(NaturalOrder) { it.comic.title }
    )
    SortOrder.Recent -> sortedByDescending { it.progress?.lastRead ?: 0L }
    SortOrder.Added -> sortedByDescending { it.comic.lastModified }
}

fun LibraryItem.matches(kind: SmartKind): Boolean = when (kind) {
    SmartKind.Reading -> progress?.started == true && progress.finished.not()
    SmartKind.Unread -> progress?.started != true && progress?.finished != true
    SmartKind.Finished -> progress?.finished == true
    SmartKind.Recent -> true
}
