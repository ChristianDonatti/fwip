package io.github.christiandonatti.fwip.remote

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

enum class ServerKind(val label: String) { Komga("Komga"), Kavita("Kavita"), Opds("OPDS") }

/** A comic server the user added. Credentials stay in the app's private storage. */
data class Server(
    val id: String,
    val name: String,
    val kind: ServerKind,
    /** Komga: the server address. Kavita and OPDS: the full OPDS address (Kavita's includes the API key). */
    val url: String,
    val user: String = "",
    val password: String = "",
) {
    /** Where the OPDS catalog starts. */
    val catalogUrl: String
        get() {
            val base = url.trim().trimEnd('/')
            return when {
                kind == ServerKind.Komga && !base.contains("/opds") -> "$base/opds/v1.2/catalog"
                else -> base
            }
        }

    /** Komga's REST API root, used to sync reading progress. */
    val komgaApi: String?
        get() = if (kind != ServerKind.Komga) null else url.trim().trimEnd('/').substringBefore("/opds") + "/api/v1"

    val authHeader: String?
        get() = if (user.isBlank()) null
        else "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
}

data class OpdsLink(
    val href: String,
    val rel: String?,
    val type: String?,
    /** OPDS Page Streaming Extension: number of pages and last page read. */
    val pseCount: Int? = null,
    val pseLastRead: Int? = null,
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String?,
    val authors: List<String>,
    val links: List<OpdsLink>,
) {
    private fun rel(vararg names: String) = links.firstOrNull { l -> names.any { l.rel == it } }

    /** A sub-catalog to open (series, library, collection...). */
    val navigation: OpdsLink?
        get() = links.firstOrNull { it.type?.contains("atom+xml") == true && it.rel?.contains("acquisition") != true && it.rel?.contains("image") != true }

    val stream: OpdsLink? get() = rel(PSE_STREAM)
    val acquisition: OpdsLink?
        get() = links.firstOrNull { it.rel?.startsWith("http://opds-spec.org/acquisition") == true }
    val thumbnail: String? get() = (rel("http://opds-spec.org/image/thumbnail", "x-stanza-cover-image-thumbnail") ?: rel("http://opds-spec.org/image"))?.href
    val cover: String? get() = (rel("http://opds-spec.org/image", "x-stanza-cover-image") ?: rel("http://opds-spec.org/image/thumbnail"))?.href

    val isBook: Boolean get() = stream != null || acquisition != null
}

data class OpdsFeed(
    val url: String,
    val title: String,
    val entries: List<OpdsEntry>,
    val next: String?,
    val search: String?,
)

const val PSE_STREAM = "http://vaemendis.net/opds-pse/stream"
private const val PSE_NS = "http://vaemendis.net/opds-pse/ns"

class HttpException(val code: Int, message: String) : IOException(message)

/** Minimal HTTP for catalogs, pages and downloads, with the server's credentials. */
object Http {
    fun open(server: Server, url: String, accept: String? = null): HttpURLConnection {
        var current = url
        repeat(5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            server.authHeader?.let { c.setRequestProperty("Authorization", it) }
            accept?.let { c.setRequestProperty("Accept", it) }
            c.setRequestProperty("User-Agent", "Fwip")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: throw HttpException(code, str(R.string.error_redirect))
                current = resolve(current, location)
                c.disconnect()
                return@repeat
            }
            if (code == 401 || code == 403) throw HttpException(code, str(R.string.error_auth))
            if (code !in 200..299) throw HttpException(code, str(R.string.error_http, code))
            return c
        }
        throw IOException(str(R.string.error_redirects))
    }

    suspend fun bytes(server: Server, url: String, accept: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val c = open(server, url, accept)
        try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
    }

    /** Resolves [href] against [base], keeping URL templates like {pageNumber} intact. */
    fun resolve(base: String, href: String): String = runCatching {
        val safe = href.replace(" ", "%20").replace("{", "%7B").replace("}", "%7D")
        URI(base).resolve(safe).toString().replace("%7B", "{").replace("%7D", "}")
    }.getOrDefault(href)
}

object Opds {
    suspend fun feed(server: Server, url: String): OpdsFeed {
        val bytes = Http.bytes(server, url, "application/atom+xml, application/xml;q=0.9, */*;q=0.5")
        return withContext(Dispatchers.Default) { parse(url, bytes) }
    }

    fun parse(url: String, bytes: ByteArray): OpdsFeed {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(bytes.inputStream(), null)

        var feedTitle = ""
        var next: String? = null
        var search: String? = null
        val entries = mutableListOf<OpdsEntry>()

        var inEntry = false
        var inAuthor = false
        var id = ""
        var title = ""
        var summary: String? = null
        val authors = mutableListOf<String>()
        val links = mutableListOf<OpdsLink>()
        val text = StringBuilder()

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (p.name) {
                        "entry" -> {
                            inEntry = true
                            id = ""; title = ""; summary = null
                            authors.clear(); links.clear()
                        }
                        "author" -> inAuthor = true
                        "link" -> {
                            val href = p.getAttributeValue(null, "href")
                            if (href != null) {
                                val link = OpdsLink(
                                    href = Http.resolve(url, href),
                                    rel = p.getAttributeValue(null, "rel"),
                                    type = p.getAttributeValue(null, "type"),
                                    pseCount = p.getAttributeValue(PSE_NS, "count")?.toIntOrNull(),
                                    pseLastRead = p.getAttributeValue(PSE_NS, "lastRead")?.toIntOrNull(),
                                )
                                if (inEntry) {
                                    links += link
                                } else when (link.rel) {
                                    "next" -> next = link.href
                                    "search" -> search = link.href
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> text.append(p.text)
                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    when (p.name) {
                        "entry" -> {
                            entries += OpdsEntry(id.ifBlank { title }, title, summary, authors.toList(), links.toList())
                            inEntry = false
                        }
                        "author" -> inAuthor = false
                        "name" -> if (inAuthor && inEntry && value.isNotBlank()) authors += value
                        "title" -> if (inEntry) title = value else if (feedTitle.isBlank()) feedTitle = value
                        "id" -> if (inEntry) id = value
                        "content", "summary" -> if (inEntry && value.isNotBlank()) summary = cleanSummary(value)
                    }
                    text.setLength(0)
                }
            }
            event = p.next()
        }
        return OpdsFeed(url, feedTitle, entries, next, search)
    }

    /** Servers often put HTML in summaries; keep plain text with line breaks. */
    private fun cleanSummary(raw: String): String? = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifBlank { null }

    /**
     * Turns the feed's search link into a URL for [terms]. Servers either give a template with
     * {searchTerms} directly or an OpenSearch description that holds it.
     */
    suspend fun searchUrl(server: Server, searchLink: String, terms: String): String? {
        val encoded = java.net.URLEncoder.encode(terms, "UTF-8").replace("+", "%20")
        if (searchLink.contains("{searchTerms}")) return searchLink.replace("{searchTerms}", encoded)
        val bytes = runCatching { Http.bytes(server, searchLink) }.getOrNull() ?: return null
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(bytes.inputStream(), null)
        var event = p.eventType
        var fallback: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name.equals("Url", ignoreCase = true)) {
                val template = p.getAttributeValue(null, "template")
                val type = p.getAttributeValue(null, "type").orEmpty()
                if (template != null) {
                    val full = Http.resolve(searchLink, template).replace("{searchTerms}", encoded)
                        .replace(Regex("\\{[^}]*\\?}"), "")
                    if (type.contains("atom")) return full
                    if (fallback == null) fallback = full
                }
            }
            event = p.next()
        }
        return fallback
    }
}
