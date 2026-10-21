package io.github.christiandonatti.fwip.data

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.christiandonatti.fwip.remote.BlockReader
import io.github.christiandonatti.fwip.remote.NetClients
import io.github.christiandonatti.fwip.remote.NetUris
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Where a comic can be moved: a library folder, or the app's own downloads. */
data class MoveTarget(val uri: Uri, val label: String)

/** Deleting and moving comic files, wherever they live: device folders, app downloads, network folders. */
object FileOps {

    private fun isAppFile(uri: Uri) = uri.scheme == "file"

    fun delete(context: Context, comic: Comic) {
        val uri = comic.uri
        when {
            isAppFile(uri) -> if (!File(uri.path!!).delete()) throw IOException(str(R.string.error_delete))
            NetUris.isNet(uri) -> NetClients.forUri(context, uri).delete(NetUris.path(uri))
            else -> try {
                if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) throw IOException(str(R.string.error_delete))
            } catch (e: SecurityException) {
                throw IOException(str(R.string.error_delete_permission))
            }
        }
    }

    private fun open(context: Context, comic: Comic): InputStream {
        val uri = comic.uri
        return when {
            isAppFile(uri) -> File(uri.path!!).inputStream()
            NetUris.isNet(uri) -> BlockReader(NetClients.forUri(context, uri).open(NetUris.path(uri)), blockSize = 1 shl 20, maxBlocks = 2).let { r ->
                object : java.io.FilterInputStream(r.inputStream()) {
                    override fun close() { super.close(); r.close() }
                }
            }
            else -> context.contentResolver.openInputStream(uri) ?: throw IOException(str(R.string.error_read_file))
        }
    }

    /** Counts bytes as they go by, for a progress bar. */
    private class Counting(input: InputStream, private val total: Long, private val onProgress: (Float) -> Unit) : java.io.FilterInputStream(input) {
        private var done = 0L
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
            if (n > 0) { done += n; onProgress((done / total.coerceAtLeast(1).toFloat()).coerceAtMost(1f)) }
        }
    }

    /** "Batman 001.cbz", or "Batman 001 (2).cbz" when that name is taken. */
    private fun freeName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val candidate = "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""
            if (candidate !in taken) return candidate
            n++
        }
    }

    /** Copies [comic] into [target], then deletes the original. Returns the new file's name. */
    fun move(context: Context, comic: Comic, target: Uri, onProgress: (Float) -> Unit): String {
        val cr = context.contentResolver
        val name: String
        Counting(open(context, comic), comic.size, onProgress).use { input ->
            when {
                isAppFile(target) -> {
                    val dir = File(target.path!!).apply { mkdirs() }
                    name = freeName(comic.fileName, dir.list()?.toSet().orEmpty())
                    val tmp = File(dir, "$name.part")
                    tmp.outputStream().use { input.copyTo(it, 1 shl 20) }
                    if (!tmp.renameTo(File(dir, name))) throw IOException(str(R.string.error_read_file))
                }
                NetUris.isNet(target) -> {
                    val client = NetClients.forUri(context, target)
                    val dir = NetUris.path(target)
                    name = freeName(comic.fileName, runCatching { client.list(dir) }.getOrDefault(emptyList()).map { it.name }.toSet())
                    client.upload(listOf(dir, name).filter { it.isNotEmpty() }.joinToString("/"), comic.size, input)
                }
                else -> {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(target, DocumentsContract.getTreeDocumentId(target))
                    val taken = mutableSetOf<String>()
                    cr.query(
                        DocumentsContract.buildChildDocumentsUriUsingTree(target, DocumentsContract.getTreeDocumentId(target)),
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
                    )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(taken::add) }
                    name = freeName(comic.fileName, taken)
                    val doc = try {
                        DocumentsContract.createDocument(cr, parent, "application/octet-stream", name)
                    } catch (e: SecurityException) {
                        throw IOException(str(R.string.error_delete_permission))
                    } ?: throw IOException(str(R.string.error_read_file))
                    try {
                        cr.openOutputStream(doc, "w")!!.use { input.copyTo(it, 1 shl 20) }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(cr, doc) }
                        throw e
                    }
                }
            }
        }
        delete(context, comic)
        return name
    }
}
