package io.github.christiandonatti.fwip.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.data.ComicFormat
import io.github.christiandonatti.fwip.data.ComicSource
import io.github.christiandonatti.fwip.data.ImageDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A book on a server, read page by page over OPDS Page Streaming. */
data class RemoteBook(
    val server: Server,
    val bookId: String,
    /** Page URL template with {pageNumber} (0-based) and maybe {maxWidth}. */
    val pageTemplate: String,
    val pageCount: Int,
    /** 0-based page to start at, from the server's own progress. */
    val startPage: Int,
)

/** Saved servers, in the app's private storage. */
class ServerStore(context: Context) {
    private val file = File(context.filesDir, "servers.json")

    fun load(): List<Server> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Server(
                id = o.getString("id"),
                name = o.getString("name"),
                kind = runCatching { ServerKind.valueOf(o.getString("kind")) }.getOrDefault(ServerKind.Opds),
                url = o.getString("url"),
                user = o.optString("user"),
                password = o.optString("password"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("kind", s.kind.name)
                    .put("url", s.url).put("user", s.user).put("password", s.password)
            )
        }
        file.writeText(arr.toString())
    }
}

/** Streams pages from the server as they are needed, keeping recent ones on disk for this session. */
class StreamComicSource(context: Context, private val book: RemoteBook) : ComicSource {
    private val dir = File(context.cacheDir, "stream/" + book.server.id + "_" + book.bookId.hashCode().toUInt()).apply { mkdirs() }

    override val pageCount: Int get() = book.pageCount

    private fun url(index: Int, maxWidth: Int) = book.pageTemplate
        .replace("{pageNumber}", index.toString())
        .replace("{maxWidth}", maxWidth.toString())

    private val locks = ConcurrentHashMap<Int, Any>()

    private fun bytes(index: Int, maxWidth: Int): ByteArray {
        val f = File(dir, "p$index")
        synchronized(locks.getOrPut(index) { Any() }) {
            if (f.length() > 0) return f.readBytes()
            val c = Http.open(book.server, url(index, maxWidth), "image/*")
            val data = try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
            runCatching { f.writeBytes(data) }
            return data
        }
    }

    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        ImageDecoding.decodeBytes(bytes(index, maxWidth.coerceAtLeast(1600)), maxWidth, maxHeight)

    override fun pageSize(index: Int): android.util.Size? {
        val data = runCatching { bytes(index, 1600) }.getOrNull() ?: return null
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, o)
        return if (o.outWidth > 0) android.util.Size(o.outWidth, o.outHeight) else null
    }

    override fun close() {}
}

object RemoteComics {
    /** How a remote book is opened by the reader: an id of its own, and the streaming source. */
    fun comic(book: RemoteBook, title: String): Comic = Comic(
        id = "remote:${book.server.id}:${book.bookId}",
        uri = Uri.parse(book.pageTemplate),
        fileName = title,
        title = title,
        folder = book.server.name,
        format = ComicFormat.ZIP,
        size = 0L,
        lastModified = 0L,
        remote = book,
    )

    /** Tells Komga how far you got, so other devices continue from the same page. */
    suspend fun syncProgress(book: RemoteBook, page: Int, pageCount: Int) {
        val api = book.server.komgaApi ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val c = java.net.URL("$api/books/${book.bookId}/read-progress").openConnection() as java.net.HttpURLConnection
                // Android's HttpURLConnection (OkHttp inside) accepts PATCH; plain Java ones do not.
                try {
                    c.requestMethod = "PATCH"
                } catch (e: java.net.ProtocolException) {
                    c.requestMethod = "POST"
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                }
                book.server.authHeader?.let { c.setRequestProperty("Authorization", it) }
                c.setRequestProperty("Content-Type", "application/json")
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.doOutput = true
                val completed = page >= pageCount - 1
                c.outputStream.use { it.write("""{"page":${page + 1},"completed":$completed}""".toByteArray()) }
                val code = c.responseCode
                c.disconnect()
                if (code !in 200..299) Log.w("Fwip", "Komga progress sync answered $code")
            }.onFailure { Log.w("Fwip", "Komga progress sync failed", it) }
        }
    }

    /** Downloads the whole file into the app's "Baixadas" folder for offline reading. */
    suspend fun download(context: Context, server: Server, url: String, name: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = downloadsDir(context)
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
            val target = File(dir, safe)
            val tmp = File(dir, "$safe.part")
            val c = Http.open(server, url)
            try {
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done / total.toFloat())
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            tmp.renameTo(target)
            target
        }

    fun downloadsDir(context: Context) = File(context.filesDir, "Baixadas").apply { mkdirs() }

    /** File name for a download, keeping the extension the server gave the file. */
    fun fileNameFor(title: String, acquisitionUrl: String, type: String?): String {
        val fromUrl = Uri.decode(acquisitionUrl.substringBefore('?').substringAfterLast('/'))
        if (ComicFormat.fromFileName(fromUrl) != null) return fromUrl
        val ext = when {
            type == null -> "cbz"
            type.contains("rar") -> "cbr"
            type.contains("7z") -> "cb7"
            type.contains("pdf") -> "pdf"
            else -> "cbz"
        }
        return "$title.$ext"
    }
}
