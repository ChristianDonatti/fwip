package io.github.christiandonatti.fwip.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/**
 * Metadata from a ComicInfo.xml file (the ComicRack format used by most comic tools).
 * Every field is optional; files in the wild fill in only some of them.
 */
data class ComicMeta(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: Int? = null,
    val count: Int? = null,
    val summary: String? = null,
    val year: Int? = null,
    val month: Int? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val letterer: String? = null,
    val coverArtist: String? = null,
    val editor: String? = null,
    val publisher: String? = null,
    val imprint: String? = null,
    val genre: String? = null,
    val tags: String? = null,
    val storyArc: String? = null,
    val characters: String? = null,
    val language: String? = null,
    val ageRating: String? = null,
    /** "YesAndRightToLeft" means manga read right to left. */
    val manga: String? = null,
    val pageCount: Int? = null,
    /** Page indices marked as double-page spreads. */
    val doublePages: List<Int> = emptyList(),
) {
    val rightToLeft: Boolean get() = manga.equals("YesAndRightToLeft", ignoreCase = true)

    /** The issue number, unless the tagging tool wrote a placeholder like "None". */
    val issue: String? get() = issueOrNull(number)

    val numberValue: Float? get() = issue?.replace(',', '.')?.toFloatOrNull()

    /** "Saga #54" when the file says which series and issue it is. */
    val displayTitle: String?
        get() = when {
            series.isNullOrBlank() -> title?.takeIf { it.isNotBlank() }
            issue == null -> series
            else -> "$series #$issue"
        }

    /** The art team, without repeating a name that holds several jobs. */
    val artists: String?
        get() = listOfNotNull(penciller, inker, colorist, coverArtist)
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

    val date: String?
        get() = year?.let { y ->
            month?.takeIf { it in 1..12 }?.let { m -> "%02d/%d".format(m, y) } ?: y.toString()
        }

    fun toJson(): JSONObject = JSONObject().apply {
        title?.let { put("title", it) }
        series?.let { put("series", it) }
        number?.let { put("number", it) }
        volume?.let { put("volume", it) }
        count?.let { put("count", it) }
        summary?.let { put("summary", it) }
        year?.let { put("year", it) }
        month?.let { put("month", it) }
        writer?.let { put("writer", it) }
        penciller?.let { put("penciller", it) }
        inker?.let { put("inker", it) }
        colorist?.let { put("colorist", it) }
        letterer?.let { put("letterer", it) }
        coverArtist?.let { put("coverArtist", it) }
        editor?.let { put("editor", it) }
        publisher?.let { put("publisher", it) }
        imprint?.let { put("imprint", it) }
        genre?.let { put("genre", it) }
        tags?.let { put("tags", it) }
        storyArc?.let { put("storyArc", it) }
        characters?.let { put("characters", it) }
        language?.let { put("language", it) }
        ageRating?.let { put("ageRating", it) }
        manga?.let { put("manga", it) }
        pageCount?.let { put("pageCount", it) }
        if (doublePages.isNotEmpty()) put("doublePages", JSONArray(doublePages))
    }

    companion object {
        fun fromJson(o: JSONObject): ComicMeta {
            fun str(k: String) = if (o.has(k)) o.optString(k).takeIf { it.isNotBlank() } else null
            fun int(k: String) = if (o.has(k)) o.optInt(k) else null
            val dp = o.optJSONArray("doublePages")
            return ComicMeta(
                title = str("title"), series = str("series"), number = str("number"),
                volume = int("volume"), count = int("count"), summary = str("summary"),
                year = int("year"), month = int("month"), writer = str("writer"),
                penciller = str("penciller"), inker = str("inker"), colorist = str("colorist"),
                letterer = str("letterer"), coverArtist = str("coverArtist"), editor = str("editor"),
                publisher = str("publisher"), imprint = str("imprint"), genre = str("genre"),
                tags = str("tags"), storyArc = str("storyArc"), characters = str("characters"),
                language = str("language"), ageRating = str("ageRating"), manga = str("manga"),
                pageCount = int("pageCount"),
                doublePages = if (dp == null) emptyList() else (0 until dp.length()).map { dp.getInt(it) },
            )
        }

        /** Parses ComicInfo.xml; returns null when the file is not usable. */
        fun parse(bytes: ByteArray): ComicMeta? = runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(bytes.inputStream(), null)
            val values = HashMap<String, String>()
            val doubles = mutableListOf<Int>()
            var depth = 0
            var field: String? = null
            val text = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        val name = parser.name
                        if (name.equals("Page", ignoreCase = true)) {
                            val image = parser.getAttributeValue(null, "Image")?.toIntOrNull()
                            val double = parser.getAttributeValue(null, "DoublePage")
                            if (image != null && double.equals("true", ignoreCase = true)) doubles += image
                        } else if (depth == 2) {
                            field = name.lowercase()
                            text.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> if (field != null) text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (depth == 2 && field != null) {
                            val v = text.toString().trim()
                            if (v.isNotEmpty()) values[field!!] = v
                            field = null
                        }
                        depth--
                    }
                }
                event = parser.next()
            }
            fun s(k: String) = values[k]?.takeIf { it.isNotBlank() }
            fun i(k: String) = values[k]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val meta = ComicMeta(
                title = s("title"), series = s("series"), number = s("number"),
                volume = i("volume"), count = i("count"), summary = s("summary"),
                year = i("year"), month = i("month"), writer = s("writer"),
                penciller = s("penciller"), inker = s("inker"), colorist = s("colorist"),
                letterer = s("letterer"), coverArtist = s("coverartist"), editor = s("editor"),
                publisher = s("publisher"), imprint = s("imprint"), genre = s("genre"),
                tags = s("tags"), storyArc = s("storyarc"), characters = s("characters"),
                language = s("languageiso"), ageRating = s("agerating"), manga = s("manga"),
                pageCount = i("pagecount"), doublePages = doubles,
            )
            meta.takeIf { it != ComicMeta() }
        }.getOrNull()
    }
}

internal fun isComicInfo(path: String): Boolean =
    path.replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

/** This metadata with every field [other] has replacing ours. */
fun ComicMeta.overlaidWith(other: ComicMeta): ComicMeta = ComicMeta(
    title = other.title ?: title,
    series = other.series ?: series,
    number = other.number ?: number,
    volume = other.volume ?: volume,
    count = other.count ?: count,
    summary = other.summary ?: summary,
    year = other.year ?: year,
    month = other.month ?: month,
    writer = other.writer ?: writer,
    penciller = other.penciller ?: penciller,
    inker = other.inker ?: inker,
    colorist = other.colorist ?: colorist,
    letterer = other.letterer ?: letterer,
    coverArtist = other.coverArtist ?: coverArtist,
    editor = other.editor ?: editor,
    publisher = other.publisher ?: publisher,
    imprint = other.imprint ?: imprint,
    genre = other.genre ?: genre,
    tags = other.tags ?: tags,
    storyArc = other.storyArc ?: storyArc,
    characters = other.characters ?: characters,
    language = other.language ?: language,
    ageRating = other.ageRating ?: ageRating,
    manga = other.manga ?: manga,
    pageCount = pageCount ?: other.pageCount,
    doublePages = doublePages.ifEmpty { other.doublePages },
)

/**
 * Metadata from Comic Vine or typed in by the user, kept apart from the file's own
 * ComicInfo.xml. [updated] lets synced devices keep the newest.
 */
data class OnlineMeta(
    val meta: ComicMeta,
    /** "comicvine" or "manual". */
    val source: String,
    val issueId: Long? = null,
    val volumeId: Long? = null,
    val updated: Long = System.currentTimeMillis(),
    /** Comic Vine credits are fetched separately, the first time the details are shown. */
    val creditsLoaded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("meta", meta.toJson())
        .put("source", source)
        .put("t", updated)
        .put("credits", creditsLoaded)
        .apply {
            issueId?.let { put("issue", it) }
            volumeId?.let { put("volume", it) }
        }

    companion object {
        fun fromJson(o: JSONObject) = OnlineMeta(
            meta = ComicMeta.fromJson(o.optJSONObject("meta") ?: JSONObject()),
            source = o.optString("source", "manual"),
            issueId = if (o.has("issue")) o.optLong("issue") else null,
            volumeId = if (o.has("volume")) o.optLong("volume") else null,
            updated = o.optLong("t"),
            creditsLoaded = o.optBoolean("credits"),
        )
    }
}

/** Tagging tools sometimes write "None", "null" or "-" where a comic has no issue number. */
fun issueOrNull(raw: String?): String? {
    val n = raw?.trim()?.removePrefix("#")?.trim() ?: return null
    if (n.isEmpty()) return null
    return if (n.lowercase() in setOf("none", "null", "nil", "n/a", "na", "-", "--", "?", "undefined", "nan")) null else n
}
