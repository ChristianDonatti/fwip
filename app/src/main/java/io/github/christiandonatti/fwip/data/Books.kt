package io.github.christiandonatti.fwip.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import org.apache.commons.compress.archivers.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** Formats read as books (reflowing text) rather than comics (pictures). */
val ComicFormat.isBook: Boolean get() = this == ComicFormat.EPUB || this == ComicFormat.MOBI || this == ComicFormat.FB2 || this == ComicFormat.TXT

/** What a book file says about itself: from an EPUB's package, an FB2's description, or a MOBI's header. */
data class BookInfo(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val series: String? = null,
    val seriesIndex: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val language: String? = null,
    val cover: ByteArray? = null,
) {
    /** The same facts as a ComicInfo.xml, so books join series, search and the rest of the library. */
    fun toComicInfo(): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val b = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ComicInfo>\n")
        fun put(tag: String, v: String?) { if (!v.isNullOrBlank()) b.append("  <$tag>").append(esc(v.trim())).append("</$tag>\n") }
        put("Title", title)
        put("Series", series)
        put("Number", seriesIndex?.let(::cleanIndex))
        put("Summary", description)
        put("Writer", authors.joinToString(", ").ifBlank { null })
        put("Publisher", publisher)
        put("Year", year?.toString())
        put("LanguageISO", language)
        b.append("</ComicInfo>\n")
        return b.toString().toByteArray()
    }

    companion object {
        /** "1.0" -> "1", "2.50" -> "2.5". */
        fun cleanIndex(s: String): String {
            val d = s.trim().toDoubleOrNull() ?: return s.trim()
            return if (d == Math.floor(d)) d.toLong().toString() else d.toString().trimEnd('0').trimEnd('.')
        }

        /** Text without HTML tags or runs of spaces (descriptions are often HTML). */
        fun plain(html: String): String = html
            .replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .lines().joinToString("\n") { it.trim().replace(Regex("\\s+"), " ") }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

/** Reads titles, authors, series and covers out of book files. */
object BookInfoReader {
    private fun local(name: String) = name.substringAfter(':')

    private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(bytes.inputStream(), null)
    }

    private fun entry(zip: ZipFile, path: String): ByteArray? {
        val e = zip.getEntry(path) ?: zip.entries.toList().firstOrNull { it.name.equals(path, ignoreCase = true) } ?: return null
        return zip.getInputStream(e).use { it.readBytes() }
    }

    /** The package document's path inside an EPUB. */
    fun opfPath(zip: ZipFile): String? {
        val container = entry(zip, "META-INF/container.xml") ?: return null
        val p = parser(container)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "rootfile") {
                p.getAttributeValue(null, "full-path")?.let { return it }
            }
        }
        return null
    }

    fun epub(zip: ZipFile): BookInfo {
        val opf = opfPath(zip) ?: return BookInfo()
        val bytes = entry(zip, opf) ?: return BookInfo()
        val base = opf.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val p = parser(bytes)
        var title: String? = null
        val authors = mutableListOf<String>()
        val others = mutableListOf<String>()
        var description: String? = null
        var publisher: String? = null
        var date: String? = null
        var language: String? = null
        var series: String? = null
        var seriesIndex: String? = null
        var coverId: String? = null
        val collectionIds = mutableMapOf<String, String>()
        val refines = mutableMapOf<String, MutableMap<String, String>>()
        // Manifest: id -> (href, media type, properties)
        val manifest = mutableMapOf<String, Triple<String, String, String>>()
        val roles = mutableMapOf<String, String>()
        var field: String? = null
        var fieldRole: String? = null
        var fieldId: String? = null
        var metaProperty: String? = null
        var metaRefines: String? = null
        var metaId: String? = null
        val text = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    val n = p.name
                    when {
                        n.startsWith("dc:") || n in setOf("title", "creator", "description", "publisher", "date", "language") && field == null -> {
                            field = local(n); text.setLength(0)
                            fieldRole = p.getAttributeValue(null, "opf:role") ?: p.getAttributeValue(null, "role")
                            fieldId = p.getAttributeValue(null, "id")
                        }
                        local(n) == "meta" -> {
                            val name = p.getAttributeValue(null, "name")
                            val content = p.getAttributeValue(null, "content")
                            when (name) {
                                "cover" -> coverId = content
                                "calibre:series" -> series = content
                                "calibre:series_index" -> seriesIndex = content
                            }
                            metaProperty = p.getAttributeValue(null, "property")
                            metaRefines = p.getAttributeValue(null, "refines")?.removePrefix("#")
                            metaId = p.getAttributeValue(null, "id")
                            text.setLength(0)
                        }
                        local(n) == "item" -> {
                            val id = p.getAttributeValue(null, "id") ?: ""
                            val href = p.getAttributeValue(null, "href") ?: ""
                            manifest[id] = Triple(href, p.getAttributeValue(null, "media-type") ?: "", p.getAttributeValue(null, "properties") ?: "")
                        }
                    }
                }
                XmlPullParser.TEXT -> text.append(p.text)
                XmlPullParser.END_TAG -> {
                    val n = local(p.name)
                    if (field != null && n == field) {
                        val v = text.toString().trim()
                        when (field) {
                            "title" -> if (title == null) title = v
                            "creator" -> {
                                if (fieldId != null) roles[fieldId!!] = v
                                if (fieldRole == null || fieldRole == "aut") authors += v else others += v
                            }
                            "description" -> description = v
                            "publisher" -> publisher = v
                            "date" -> if (date == null) date = v
                            "language" -> if (language == null) language = v
                        }
                        field = null
                    } else if (n == "meta" && metaProperty != null) {
                        val v = text.toString().trim()
                        if (metaRefines != null) refines.getOrPut(metaRefines!!) { mutableMapOf() }[metaProperty!!] = v
                        else if (metaProperty == "belongs-to-collection") collectionIds[metaId ?: v] = v
                        metaProperty = null
                    }
                }
            }
        }
        // EPUB 3 roles live in refining metas: a creator refined as "aut" is an author.
        roles.forEach { (id, name) ->
            val role = refines[id]?.get("role")
            if (role != null && role != "aut") { authors.remove(name); if (name !in others) others += name }
        }
        if (series == null) {
            collectionIds.entries.firstOrNull { (id, _) -> refines[id]?.get("collection-type").let { it == null || it == "series" } }?.let { (id, name) ->
                series = name
                seriesIndex = refines[id]?.get("group-position")
            }
        }
        val coverHref = manifest.values.firstOrNull { "cover-image" in it.third.split(' ') }?.first
            ?: coverId?.let { manifest[it]?.first }
            ?: manifest.entries.firstOrNull { (id, v) -> v.second.startsWith("image/") && (id.contains("cover", true) || v.first.contains("cover", true)) }?.value?.first
        val cover = coverHref?.let { href ->
            val path = java.net.URLDecoder.decode(base + href, "UTF-8").let(::normalize)
            runCatching { entry(zip, path) }.getOrNull()
        }
        return BookInfo(
            title = title,
            authors = authors.ifEmpty { others.take(1) },
            series = series?.takeIf { it.isNotBlank() },
            seriesIndex = seriesIndex,
            description = description?.let(BookInfo::plain)?.takeIf { it.isNotBlank() },
            publisher = publisher,
            year = date?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() },
            language = language,
            cover = cover,
        )
    }

    /** "OEBPS/Text/../Images/c.jpg" -> "OEBPS/Images/c.jpg". */
    fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(part)
            }
        }
        return out.joinToString("/")
    }

    /** An FB2 (FictionBook) file's description, and its cover picture. */
    fun fb2(bytes: ByteArray): BookInfo {
        val p = parser(bytes)
        var inTitleInfo = false
        var inPublish = false
        var inAuthor = false
        var inAnnotation = false
        val authorParts = mutableListOf<String>()
        val authors = mutableListOf<String>()
        var title: String? = null
        var series: String? = null
        var index: String? = null
        var lang: String? = null
        var date: String? = null
        var publisher: String? = null
        var coverId: String? = null
        val annotation = StringBuilder()
        val text = StringBuilder()
        var cover: ByteArray? = null
        var binaryId: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (local(p.name)) {
                        "title-info" -> inTitleInfo = true
                        "publish-info" -> inPublish = true
                        "author" -> if (inTitleInfo) { inAuthor = true; authorParts.clear() }
                        "annotation" -> if (inTitleInfo) inAnnotation = true
                        "sequence" -> if (inTitleInfo && series == null) {
                            series = p.getAttributeValue(null, "name")
                            index = p.getAttributeValue(null, "number")
                        }
                        "image" -> if (inTitleInfo) {
                            val href = (0 until p.attributeCount).firstNotNullOfOrNull { i -> p.getAttributeValue(i).takeIf { local(p.getAttributeName(i)) == "href" } }
                            if (coverId == null) coverId = href?.removePrefix("#")
                        }
                        "binary" -> binaryId = p.getAttributeValue(null, "id")
                    }
                }
                XmlPullParser.TEXT -> {
                    text.append(p.text)
                    if (inAnnotation) annotation.append(p.text)
                }
                XmlPullParser.END_TAG -> {
                    val v = text.toString().trim()
                    when (local(p.name)) {
                        "title-info" -> inTitleInfo = false
                        "publish-info" -> inPublish = false
                        "first-name", "middle-name", "last-name" -> if (inAuthor && v.isNotEmpty()) authorParts += v
                        "author" -> if (inAuthor) { inAuthor = false; authorParts.joinToString(" ").takeIf { it.isNotBlank() }?.let(authors::add) }
                        "book-title" -> if (inTitleInfo) title = v
                        "lang" -> if (inTitleInfo) lang = v
                        "date" -> if (inTitleInfo && date == null) date = v
                        "publisher" -> if (inPublish) publisher = v
                        "annotation" -> inAnnotation = false
                        "p" -> if (inAnnotation) annotation.append("\n")
                        "binary" -> {
                            if (binaryId != null && binaryId == coverId && cover == null) {
                                cover = runCatching { android.util.Base64.decode(v, android.util.Base64.DEFAULT) }.getOrNull()
                            }
                            binaryId = null
                        }
                    }
                    text.setLength(0)
                }
            }
        }
        return BookInfo(
            title = title, authors = authors, series = series?.takeIf { it.isNotBlank() }, seriesIndex = index,
            description = annotation.toString().trim().takeIf { it.isNotEmpty() }, publisher = publisher,
            year = date?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }, language = lang, cover = cover,
        )
    }
}

/**
 * A book's cover as a one-page "comic", so the library's cover and metadata pipeline works for
 * books too. Without a picture, a cover is drawn from the title and author.
 */
internal class BookCoverSource(private val context: Context, private val info: BookInfo, private val fallbackTitle: String) : ComicSource {
    override val pageCount: Int get() = 1

    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        info.cover?.let { bytes -> ImageDecoding.decode({ bytes.inputStream() }, maxWidth, maxHeight) }
            ?: GeneratedCover.draw(context, info.title ?: fallbackTitle, info.authors.firstOrNull(), maxWidth, maxHeight)

    override fun readComicInfo(): ByteArray = info.copy(title = info.title ?: fallbackTitle).toComicInfo()

    override fun close() {}
}

/** Typeset covers for books that come without one: the title large, the author small, a colour of its own. */
object GeneratedCover {
    private val palette = intArrayOf(
        0xFF2F4858.toInt(), 0xFF6B2D3C.toInt(), 0xFF33658A.toInt(), 0xFF5B4E3A.toInt(),
        0xFF3D5A3A.toInt(), 0xFF4A3B6B.toInt(), 0xFF7A4B24.toInt(), 0xFF283B59.toInt(),
    )

    fun colorFor(title: String): Int = palette[(title.hashCode() and 0x7fffffff) % palette.size]

    fun draw(context: Context, title: String, author: String?, maxWidth: Int, maxHeight: Int): Bitmap {
        // Book proportions (2:3) inside the box asked for.
        val h = maxHeight.coerceAtLeast(300)
        val w = minOf(maxWidth.coerceAtLeast(200), h * 2 / 3)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val base = colorFor(title)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), lighten(base, 0.12f), darken(base, 0.18f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        // A spine shadow and a thin frame, like a cloth-bound book.
        paint.color = Color.argb(60, 0, 0, 0)
        c.drawRect(0f, 0f, w * 0.045f, h.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.006f
        paint.color = Color.argb(150, 236, 225, 196)
        val m = w * 0.09f
        c.drawRect(m, m, w - m * 0.8f, h - m, paint)
        paint.style = Paint.Style.FILL

        val serif = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, io.github.christiandonatti.fwip.R.font.fraunces_700) }.getOrNull()
            ?: android.graphics.Typeface.SERIF
        val sans = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, io.github.christiandonatti.fwip.R.font.manrope_600) }.getOrNull()
            ?: android.graphics.Typeface.SANS_SERIF
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 236, 216)
            typeface = serif
            textSize = w * 0.11f
        }
        val textWidth = (w - m * 3).toInt()
        var layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()
        // Long titles get smaller type rather than running off the cover.
        while (layout.height > h * 0.5f && tp.textSize > w * 0.05f) {
            tp.textSize *= 0.88f
            layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()
        }
        c.save()
        c.translate(m * 1.6f, h * 0.24f)
        layout.draw(c)
        c.restore()
        // A small rule under the title.
        paint.color = Color.argb(170, 236, 225, 196)
        val ruleY = h * 0.24f + layout.height + h * 0.04f
        c.drawRect(w / 2f - w * 0.08f, ruleY, w / 2f + w * 0.08f, ruleY + w * 0.008f, paint)
        if (!author.isNullOrBlank()) {
            val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 236, 225, 196)
                typeface = sans
                textSize = w * 0.055f
                letterSpacing = 0.08f
            }
            val a = author.uppercase()
            val al = StaticLayout.Builder.obtain(a, 0, a.length, ap, textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build()
            c.save()
            c.translate(m * 1.6f, h * 0.8f - al.height)
            al.draw(c)
            c.restore()
        }
        return bmp
    }

    private fun lighten(c: Int, k: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * k).toInt(), (Color.green(c) + (255 - Color.green(c)) * k).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * k).toInt(),
    )

    private fun darken(c: Int, k: Float) = Color.rgb((Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt())
}

/** Small helpers for book files kept by the app. */
object BookFiles {
    /** Where books converted to EPUB (from MOBI, FB2, TXT) and network books are kept. */
    fun dir(context: Context): File = File(context.filesDir, "books").apply { mkdirs() }

    fun key(comic: Comic): String =
        java.security.MessageDigest.getInstance("SHA-1").digest((comic.id + "|" + comic.size).toByteArray()).joinToString("") { "%02x".format(it) }
}
