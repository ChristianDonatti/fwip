package io.github.christiandonatti.fwip.data

/**
 * What a comic's file name says about it, for files without ComicInfo.xml. Understands the
 * usual naming of digital comics: "Batman 001 (2016) (Digital) (Zone-Empire).cbr",
 * "Amazing Spider-Man v5 #12", "Saga Vol. 1", "Batman 001 - The Court of Owls", scene names
 * with dots or underscores, and so on.
 */
data class ParsedName(
    val series: String,
    /** Issue number without leading zeros ("1", "12.5"), when there is one. */
    val number: String? = null,
    /** Volume of the series (v2, Vol. 3), when given. */
    val volume: Int? = null,
    val year: Int? = null,
    /** Total issues, from "(of 13)". */
    val count: Int? = null,
    /** Story title after the number ("The Court of Owls"). */
    val title: String? = null,
) {
    /** Number to sort by: the issue, or the volume of a collected edition. */
    val sortNumber: Float? get() = number?.toFloatOrNull() ?: if (number == null) volume?.toFloat() else null

    /** "Batman #1", "Saga Vol. 1", or just the series. */
    val display: String
        get() = when {
            number != null -> "$series #$number"
            volume != null -> "$series Vol. $volume"
            else -> series
        }
}

private val brackets = Regex("""[(\[{]([^)\]}]*)[)\]}]""")
private val yearOnly = Regex("""((?:19|20)\d\d)(?:\s*-\s*(?:(?:19|20)\d\d)?)?""")
private val ofCount = Regex("""(?i)of\s*(\d{1,4})""")
private val looseOf = Regex("""(?i)\s+of\s+(\d{1,4})\b""")
private val hashNumber = Regex("""#\s*(-?\d+(?:\.\d+)?)""")
private val trailingNumber = Regex("""(?:^|\s)(-?\d{1,4}(?:\.\d+)?)$""")
private val trailingVolume = Regex("""(?i)(?:^|\s)(?:vol(?:ume)?\.?\s*|v)(\d{1,3})$""")
private val spaces = Regex("""\s+""")
private val bareYear = Regex("""(?<=\d)\s+((?:19|20)\d\d)$""")

/** "001" -> "1", "000.5" -> "0.5". */
internal fun cleanNumber(n: String): String {
    val neg = n.startsWith("-")
    val body = n.removePrefix("-")
    val whole = body.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val frac = body.substringAfter('.', "")
    return (if (neg) "-" else "") + whole + (if (frac.isNotEmpty()) ".$frac" else "")
}

private fun String.tidy(): String = trim().trimEnd('-', ',', ':', '#', '–').trim()

fun parseFileName(fileName: String): ParsedName {
    var s = fileName
    if (ComicFormat.fromFileName(s) != null) s = s.substringBeforeLast('.')
    s = s.replace('_', ' ')
    // Scene names use dots instead of spaces ("The.Walking.Dead.100.2012").
    if (!s.contains(' ') && s.contains('.')) s = s.replace('.', ' ')

    var year: Int? = null
    var count: Int? = null
    for (m in brackets.findAll(s)) {
        val inner = m.groupValues[1].trim()
        yearOnly.matchEntire(inner)?.let { if (year == null) year = it.groupValues[1].toInt() }
        ofCount.matchEntire(inner)?.let { count = it.groupValues[1].toInt() }
    }
    s = brackets.replace(s, " ")
    looseOf.find(s)?.let { m ->
        count = m.groupValues[1].toInt()
        s = s.removeRange(m.range)
    }
    s = spaces.replace(s, " ").trim()
    // A bare year right after the issue number ("Dead 100 2012").
    bareYear.find(s)?.let { m ->
        if (year == null) year = m.groupValues[1].toInt()
        s = s.substring(0, m.range.first).trim()
    }

    // "Series #12 - Title"
    hashNumber.find(s)?.let { m ->
        val (series, volume) = splitVolume(s.substring(0, m.range.first).tidy())
        val title = s.substring(m.range.last + 1).trim().trimStart('-', '–', ':').trim().ifEmpty { null }
        return ParsedName(series.ifEmpty { s }, cleanNumber(m.groupValues[1]), volume, year, count, title)
    }

    // "Series 001 - Title": the first " - " part that ends in a number holds series and issue.
    val parts = s.split(Regex("""\s+[-–]\s+"""))
    for (i in parts.indices) {
        val head = parts.subList(0, i + 1).joinToString(" - ")
        val title = parts.drop(i + 1).joinToString(" - ").ifBlank { null }
        trailingVolume.find(head)?.let { v ->
            val series = head.substring(0, v.range.first).tidy()
            if (series.isNotEmpty()) return ParsedName(series, null, v.groupValues[1].toInt(), year, count, title)
        }
        trailingNumber.find(head)?.let { n ->
            val before = head.substring(0, n.range.first).tidy()
            if (before.isNotEmpty()) {
                val (series, volume) = splitVolume(before)
                return ParsedName(series, cleanNumber(n.groupValues[1]), volume, year, count, title)
            }
        }
    }
    val (series, volume) = splitVolume(s.tidy())
    return ParsedName(series.ifEmpty { s.ifEmpty { fileName } }, null, volume, year, count, null)
}

/** "Batman v2" -> ("Batman", 2). */
private fun splitVolume(text: String): Pair<String, Int?> {
    val v = trailingVolume.find(text) ?: return text to null
    val series = text.substring(0, v.range.first).tidy()
    return if (series.isEmpty()) text to null else series to v.groupValues[1].toInt()
}
