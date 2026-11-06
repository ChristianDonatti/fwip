package io.github.christiandonatti.fwip.data

import android.content.Context
import android.util.Xml
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetUris
import org.apache.commons.compress.archivers.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A minimal, valid EPUB 3 (with an EPUB 2 table of contents too) built from chapters of XHTML. */
class EpubWriter(
    private val title: String,
    private val authors: List<String> = emptyList(),
    private val language: String = "und",
) {
    data class Chapter(val title: String, val body: String)
    data class Image(val name: String, val mediaType: String, val bytes: ByteArray)

    val chapters = mutableListOf<Chapter>()
    val images = mutableListOf<Image>()
    var coverImage: String? = null
    var series: String? = null
    var seriesIndex: String? = null
    var description: String? = null

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun write(target: File) {
        val tmp = File(target.path + ".part")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            // The mimetype comes first and uncompressed, as the format asks.
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime)
            zip.closeEntry()
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""",
            )
            val css = "body{margin:0;padding:0}h1,h2,h3{text-align:center;margin:1.2em 0 .8em}p{margin:0;text-indent:1.4em}p.first,h1+p,h2+p,h3+p{text-indent:0}.blank{height:1em}img{max-width:100%}blockquote{margin:1em 1.5em;font-style:italic}"
            put("OEBPS/style.css", css)
            chapters.forEachIndexed { i, ch ->
                put(
                    "OEBPS/ch${i + 1}.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="${esc(language)}" xml:lang="${esc(language)}">
<head><meta charset="UTF-8"/><title>${esc(ch.title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
${ch.body}
</body>
</html>""",
                )
            }
            images.forEach { img ->
                zip.putNextEntry(ZipEntry("OEBPS/images/${img.name}"))
                zip.write(img.bytes)
                zip.closeEntry()
            }
            val nav = StringBuilder()
            chapters.forEachIndexed { i, ch -> nav.append("      <li><a href=\"ch${i + 1}.xhtml\">${esc(ch.title)}</a></li>\n") }
            put(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="UTF-8"/><title>${esc(title)}</title></head>
<body><nav epub:type="toc" id="toc"><ol>
$nav    </ol></nav></body>
</html>""",
            )
            val ncx = StringBuilder()
            chapters.forEachIndexed { i, ch ->
                ncx.append("<navPoint id=\"n${i + 1}\" playOrder=\"${i + 1}\"><navLabel><text>${esc(ch.title)}</text></navLabel><content src=\"ch${i + 1}.xhtml\"/></navPoint>\n")
            }
            val id = "urn:fwip:" + java.util.UUID.nameUUIDFromBytes((title + authors).toByteArray())
            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="$id"/></head>
<docTitle><text>${esc(title)}</text></docTitle>
<navMap>
$ncx</navMap>
</ncx>""",
            )
            val manifest = StringBuilder()
            val spine = StringBuilder()
            manifest.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            manifest.append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            manifest.append("    <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
            chapters.indices.forEach { i ->
                manifest.append("    <item id=\"ch${i + 1}\" href=\"ch${i + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                spine.append("    <itemref idref=\"ch${i + 1}\"/>\n")
            }
            images.forEachIndexed { i, img ->
                val props = if (img.name == coverImage) " properties=\"cover-image\"" else ""
                manifest.append("    <item id=\"img${i + 1}\" href=\"images/${esc(img.name)}\" media-type=\"${img.mediaType}\"$props/>\n")
            }
            val coverMeta = images.indexOfFirst { it.name == coverImage }.takeIf { it >= 0 }?.let { "    <meta name=\"cover\" content=\"img${it + 1}\"/>\n" } ?: ""
            val seriesMeta = series?.let {
                "    <meta name=\"calibre:series\" content=\"${esc(it)}\"/>\n" +
                    (seriesIndex?.let { n -> "    <meta name=\"calibre:series_index\" content=\"${esc(n)}\"/>\n" } ?: "")
            } ?: ""
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">$id</dc:identifier>
    <dc:title>${esc(title)}</dc:title>
${authors.joinToString("") { "    <dc:creator>${esc(it)}</dc:creator>\n" }}    <dc:language>${esc(language)}</dc:language>
${description?.let { "    <dc:description>${esc(it)}</dc:description>\n" } ?: ""}    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
$coverMeta$seriesMeta  </metadata>
  <manifest>
$manifest  </manifest>
  <spine toc="ncx">
$spine  </spine>
</package>""",
            )
        }
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    companion object {
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}

/** Plain text into chapters: headings like "Chapter 3" or "Capítulo III" start new ones; blank lines split paragraphs. */
object TxtToEpub {
    private val heading = Regex(
        """^\s*((chapter|cap[ií]tulo|cap\.|parte|part|livro|book|pr[oó]logo|prologue|ep[ií]logo|epilogue)\b.{0,60}|[IVXLC]{1,7}\.?|\d{1,3}\.?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Reads bytes as UTF-8, or as Windows-1252 (old Brazilian files) when they are not valid UTF-8. */
    fun decode(bytes: ByteArray): String {
        val utf8 = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
        return try {
            utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: java.nio.charset.CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }

    fun convert(bytes: ByteArray, title: String, target: File) {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val w = EpubWriter(title)
        val body = StringBuilder()
        var chapterTitle = title
        var para = StringBuilder()
        var count = 0
        fun flushPara() {
            if (para.isNotBlank()) { body.append("<p>").append(EpubWriter.escape(para.toString().trim())).append("</p>\n") }
            para = StringBuilder()
        }
        fun flushChapter() {
            flushPara()
            if (body.isNotBlank()) w.chapters += EpubWriter.Chapter(chapterTitle, body.toString())
            body.setLength(0)
            count = 0
        }
        // Files where every line is a paragraph (no blank lines) vs. wrapped lines with blank-line paragraphs.
        val blankSeparated = text.contains("\n\n")
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty()) { flushPara(); continue }
            if (t.length < 80 && heading.matches(t) && (count > 0 || body.isNotEmpty() || w.chapters.isEmpty())) {
                flushChapter()
                chapterTitle = t
                body.append("<h2>").append(EpubWriter.escape(t)).append("</h2>\n")
                continue
            }
            if (!blankSeparated) { para.append(t); flushPara() } else { if (para.isNotEmpty()) para.append(' '); para.append(t) }
            count += t.length
            // Very long stretches without headings are cut into parts, so pages stay quick.
            if (count > 60_000 && para.isEmpty()) { flushChapter(); chapterTitle = "$title (${w.chapters.size + 1})" }
        }
        flushChapter()
        if (w.chapters.isEmpty()) w.chapters += EpubWriter.Chapter(title, "<p></p>")
        w.write(target)
    }
}

/** FictionBook 2 into EPUB: sections become chapters, emphasis and pictures kept. */
object Fb2ToEpub {
    fun convert(bytes: ByteArray, fallbackTitle: String, target: File) {
        val info = BookInfoReader.fb2(bytes)
        val w = EpubWriter(info.title ?: fallbackTitle, info.authors, info.language ?: "und")
        w.series = info.series
        w.seriesIndex = info.seriesIndex
        w.description = info.description
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(bytes.inputStream(), null)
        val body = StringBuilder()
        var chapterTitle = ""
        var inBody = false
        var bodyIsNotes = false
        var depth = 0
        var inTitle = false
        val titleText = StringBuilder()
        var binaryId: String? = null
        var binaryType = "image/jpeg"
        val binaryText = StringBuilder()
        var coverId: String? = null
        var inTitleInfo = false
        fun local(n: String) = n.substringAfter(':')
        fun href(): String? = (0 until p.attributeCount).firstNotNullOfOrNull { i -> p.getAttributeValue(i).takeIf { local(p.getAttributeName(i)) == "href" } }
        fun flushChapter() {
            if (body.isNotBlank()) w.chapters += EpubWriter.Chapter(chapterTitle.ifBlank { w.chapters.size.plus(1).toString() }, body.toString())
            body.setLength(0)
            chapterTitle = ""
        }
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "title-info" -> inTitleInfo = true
                    "image" -> {
                        val h = href()?.removePrefix("#")
                        if (inTitleInfo) { if (coverId == null) coverId = h }
                        else if (inBody && h != null) body.append("<div style=\"text-align:center\"><img src=\"images/").append(EpubWriter.escape(h)).append("\" alt=\"\"/></div>\n")
                    }
                    "body" -> { inBody = true; bodyIsNotes = p.getAttributeValue(null, "name") == "notes"; if (bodyIsNotes) { flushChapter(); chapterTitle = "Notes" } }
                    "section" -> if (inBody) { depth++; if (depth == 1 && !bodyIsNotes) flushChapter() }
                    "title" -> if (inBody) { inTitle = true; titleText.setLength(0); body.append(if (depth <= 1) "<h2>" else "<h3>") }
                    "p" -> if (inBody) { if (inTitle) titleText.append(if (titleText.isEmpty()) "" else " "); body.append(if (inTitle) "" else "<p>") }
                    "emphasis" -> if (inBody) body.append("<em>")
                    "strong" -> if (inBody) body.append("<strong>")
                    "empty-line" -> if (inBody) body.append("<div class=\"blank\"></div>\n")
                    "epigraph", "cite" -> if (inBody) body.append("<blockquote>")
                    "subtitle" -> if (inBody) body.append("<h4>")
                    "binary" -> { binaryId = p.getAttributeValue(null, "id"); binaryType = p.getAttributeValue(null, "content-type") ?: "image/jpeg"; binaryText.setLength(0) }
                }
                XmlPullParser.TEXT -> {
                    if (binaryId != null) binaryText.append(p.text)
                    else if (inBody) {
                        body.append(EpubWriter.escape(p.text))
                        if (inTitle) titleText.append(p.text.trim())
                    }
                }
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "title-info" -> inTitleInfo = false
                    "body" -> { inBody = false; flushChapter() }
                    "section" -> if (inBody) depth--
                    "title" -> if (inBody) {
                        inTitle = false
                        body.append(if (depth <= 1) "</h2>\n" else "</h3>\n")
                        if (chapterTitle.isBlank()) chapterTitle = titleText.toString().trim()
                    }
                    "p" -> if (inBody && !inTitle) body.append("</p>\n") else if (inTitle) body.append("<br/>")
                    "emphasis" -> if (inBody) body.append("</em>")
                    "strong" -> if (inBody) body.append("</strong>")
                    "epigraph", "cite" -> if (inBody) body.append("</blockquote>\n")
                    "subtitle" -> if (inBody) body.append("</h4>\n")
                    "binary" -> {
                        val id = binaryId
                        if (id != null) runCatching {
                            val data = android.util.Base64.decode(binaryText.toString(), android.util.Base64.DEFAULT)
                            w.images += EpubWriter.Image(id, binaryType, data)
                        }
                        binaryId = null
                    }
                }
            }
        }
        flushChapter()
        if (coverId != null && w.images.any { it.name == coverId }) {
            w.coverImage = coverId
            // The cover as the first page too.
            w.chapters.add(0, EpubWriter.Chapter(w.chapters.firstOrNull()?.let { info.title ?: fallbackTitle } ?: fallbackTitle, "<div style=\"text-align:center\"><img src=\"images/${EpubWriter.escape(coverId!!)}\" alt=\"\"/></div>"))
        }
        if (w.chapters.isEmpty()) w.chapters += EpubWriter.Chapter(fallbackTitle, "<p></p>")
        w.write(target)
    }
}

/** Getting a book's bytes and an EPUB the reader can open, wherever the book lives. */
object BookSources {
    /** The whole file (books are small), from the device, a document folder or the NAS. */
    fun readAll(context: Context, comic: Comic): ByteArray =
        if (NetUris.isNet(comic.uri)) {
            val out = java.io.ByteArrayOutputStream()
            NetClients.forUri(context, comic.uri).download(NetUris.path(comic.uri), out) { _, _ -> }
            out.toByteArray()
        } else {
            context.contentResolver.openInputStream(comic.uri)?.use { it.readBytes() } ?: error("cannot read ${comic.fileName}")
        }

    /** A local copy (or the file itself) of the original. */
    fun localOriginal(context: Context, comic: Comic): File {
        if (comic.uri.scheme == "file") return File(comic.uri.path!!)
        val target = File(BookFiles.dir(context), BookFiles.key(comic) + "." + comic.fileName.substringAfterLast('.', "bin").lowercase())
        if (!target.exists() || target.length() == 0L) {
            val tmp = File(target.path + ".part")
            if (NetUris.isNet(comic.uri)) {
                NetClients.forUri(context, comic.uri).downloadTo(NetUris.path(comic.uri), tmp) { _, _ -> }
            } else {
                context.contentResolver.openInputStream(comic.uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
            }
            tmp.renameTo(target)
        }
        target.setLastModified(System.currentTimeMillis())
        tidy(context)
        return target
    }

    /** An EPUB for the reader: the book itself, or a converted copy made once and kept. */
    fun epubFor(context: Context, comic: Comic): File {
        if (comic.format == ComicFormat.EPUB) return localOriginal(context, comic)
        val target = File(BookFiles.dir(context), BookFiles.key(comic) + ".conv.epub")
        if (target.exists() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return target }
        val title = prettyTitle(comic.fileName)
        when (comic.format) {
            ComicFormat.TXT -> TxtToEpub.convert(readAll(context, comic), title, target)
            ComicFormat.FB2 -> Fb2ToEpub.convert(readAll(context, comic), title, target)
            ComicFormat.MOBI -> MobiBooks.toEpub(localOriginal(context, comic), target)
            else -> error("not a book")
        }
        tidy(context)
        return target
    }

    /** What the library shows for a book: title, author, series, cover. */
    fun info(context: Context, comic: Comic): BookInfo = when (comic.format) {
        ComicFormat.EPUB -> withZip(context, comic) { BookInfoReader.epub(it) }
        ComicFormat.FB2 -> BookInfoReader.fb2(readAll(context, comic))
        // Kindle books are rebuilt as EPUB once; their details come from that copy.
        ComicFormat.MOBI -> @Suppress("DEPRECATION") ZipFile(epubFor(context, comic)).use { BookInfoReader.epub(it) }
        else -> BookInfo()
    }

    private fun <T> withZip(context: Context, comic: Comic, block: (ZipFile) -> T): T {
        if (NetUris.isNet(comic.uri)) {
            val reader = io.github.christiandonatti.fwip.remote.BlockReader(NetClients.forUri(context, comic.uri).open(NetUris.path(comic.uri)))
            @Suppress("DEPRECATION")
            return reader.use { ZipFile(io.github.christiandonatti.fwip.remote.BlockChannel(reader)).use(block) }
        }
        if (comic.uri.scheme == "file") {
            @Suppress("DEPRECATION")
            return ZipFile(File(comic.uri.path!!)).use(block)
        }
        val handle = SeekableHandle.open(context, comic.uri)
        if (handle != null) {
            @Suppress("DEPRECATION")
            return handle.use { ZipFile(it.channel).use(block) }
        }
        @Suppress("DEPRECATION")
        return ZipFile(localOriginal(context, comic)).use(block)
    }

    /** Keeps the books folder from growing without end: copies and conversions not opened lately go. */
    private fun tidy(context: Context) {
        val files = BookFiles.dir(context).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        val limit = 400L shl 20
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit || files.size < 6) break
            total -= f.length()
            f.delete()
        }
    }

    /** For the library's analysis: a one-page source with the book's cover and metadata. */
    fun coverSource(context: Context, comic: Comic): ComicSource =
        BookCoverSource(context, runCatching { info(context, comic) }.getOrDefault(BookInfo()), prettyTitle(comic.fileName))
}
