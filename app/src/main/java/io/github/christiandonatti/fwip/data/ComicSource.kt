package io.github.christiandonatti.fwip.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.min

/** A comic opened for reading: knows how many pages it has and how to decode each one. */
interface ComicSource : Closeable {
    val pageCount: Int

    /** Decodes [index] as a bitmap big enough to fill a [maxWidth] x [maxHeight] box without upscaling. */
    fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap?

    /** Raw ComicInfo.xml inside the archive, if there is one. */
    fun readComicInfo(): ByteArray? = null
}

object ComicSources {
    fun open(context: Context, comic: Comic): ComicSource = when (comic.format) {
        ComicFormat.ZIP -> ZipComicSource.open(context, comic)
        ComicFormat.SEVEN_ZIP -> SevenZipComicSource.open(context, comic)
        ComicFormat.RAR -> RarComicSource.open(context, comic)
        ComicFormat.PDF -> PdfComicSource.open(context, comic)
    }
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

internal fun isPageImage(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    if (normalized.contains("__MACOSX/")) return false
    val name = normalized.substringAfterLast('/')
    if (name.startsWith(".")) return false
    return name.substringAfterLast('.', "").lowercase() in imageExtensions
}

internal fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

internal object ImageDecoding {
    private const val MAX_BYTES = 64L * 1024 * 1024

    /** Reads the whole entry once and decodes from memory (archives are read a single time per page). */
    fun decode(open: () -> InputStream, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bytes = open().use { it.readBytes() }
        return decodeBytes(bytes, maxWidth, maxHeight)
    }

    fun decodeBytes(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        // Largest power-of-two downsample that still keeps at least the size shown on screen.
        val fit = min(maxWidth / w.toFloat(), maxHeight / h.toFloat())
        var sample = 1
        if (fit < 1f) {
            while (sample * 2 <= 1f / fit) sample *= 2
        }
        while ((w / sample).toLong() * (h / sample).toLong() * 4 > MAX_BYTES) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

/** Opens a document as a seekable channel when the provider allows it (local storage does). */
internal class SeekableHandle private constructor(
    private val pfd: ParcelFileDescriptor,
    private val stream: FileInputStream,
) : Closeable {
    val channel: FileChannel = stream.channel

    override fun close() {
        runCatching { stream.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): SeekableHandle? {
            val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
            return try {
                val stream = FileInputStream(pfd.fileDescriptor)
                val handle = SeekableHandle(pfd, stream)
                handle.channel.position(0)
                handle.channel.size()
                handle
            } catch (e: Exception) {
                runCatching { pfd.close() }
                null
            }
        }
    }
}

/** Copies a document into the app cache so libraries that need a real file can open it. */
internal object ArchiveCache {
    private const val KEEP = 4

    fun fileFor(context: Context, comic: Comic): File {
        val dir = File(context.cacheDir, "archives").apply { mkdirs() }
        val ext = comic.fileName.substringAfterLast('.', "bin")
        val target = File(dir, sha1(comic.id) + "." + ext)
        if (!target.exists() || target.length() == 0L || target.length() != comic.size && comic.size > 0) {
            val tmp = File(dir, target.name + ".part")
            context.contentResolver.openInputStream(comic.uri).use { input ->
                requireNotNull(input) { "Não foi possível ler o arquivo" }
                tmp.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            tmp.renameTo(target)
        }
        target.setLastModified(System.currentTimeMillis())
        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        return target
    }
}

private class ZipComicSource(
    private val zip: ZipFile,
    private val handle: Closeable?,
) : ComicSource {
    private val pages: List<ZipArchiveEntry> = zip.entries.toList()
        .filter { !it.isDirectory && isPageImage(it.name) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

    override val pageCount: Int get() = pages.size

    @Synchronized
    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        ImageDecoding.decode({ zip.getInputStream(pages[index]) }, maxWidth, maxHeight)

    @Synchronized
    override fun readComicInfo(): ByteArray? = zip.entries.toList()
        .filter { !it.isDirectory && isComicInfo(it.name) }
        .minByOrNull { it.name.count { c -> c == '/' } }
        ?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    override fun close() {
        runCatching { zip.close() }
        runCatching { handle?.close() }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun open(context: Context, comic: Comic): ComicSource {
            val handle = SeekableHandle.open(context, comic.uri)
            if (handle != null) {
                try {
                    return ZipComicSource(ZipFile(handle.channel), handle)
                } catch (e: Exception) {
                    handle.close()
                }
            }
            return ZipComicSource(ZipFile(ArchiveCache.fileFor(context, comic)), null)
        }
    }
}

private class SevenZipComicSource(
    private val archive: SevenZFile,
    private val handle: Closeable?,
) : ComicSource {
    private val pages: List<SevenZArchiveEntry> = archive.entries.toList()
        .filter { !it.isDirectory && it.hasStream() && isPageImage(it.name) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

    override val pageCount: Int get() = pages.size

    @Synchronized
    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        ImageDecoding.decode({ archive.getInputStream(pages[index]) }, maxWidth, maxHeight)

    @Synchronized
    override fun readComicInfo(): ByteArray? = archive.entries.toList()
        .filter { !it.isDirectory && it.hasStream() && isComicInfo(it.name) }
        .minByOrNull { it.name.count { c -> c == '/' } }
        ?.let { entry -> archive.getInputStream(entry).use { it.readBytes() } }

    override fun close() {
        runCatching { archive.close() }
        runCatching { handle?.close() }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun open(context: Context, comic: Comic): ComicSource {
            val handle = SeekableHandle.open(context, comic.uri)
            if (handle != null) {
                try {
                    return SevenZipComicSource(SevenZFile(handle.channel), handle)
                } catch (e: Exception) {
                    handle.close()
                }
            }
            return SevenZipComicSource(SevenZFile(ArchiveCache.fileFor(context, comic)), null)
        }
    }
}

/**
 * RAR (CBR) pages are extracted in archive order on a background thread into a per-comic cache folder.
 * Many CBRs are "solid" archives, where a file can only be decompressed after every file before it,
 * so pages cannot be pulled out one at a time in reading order. Pages become readable as soon as
 * their file is written, and a fully extracted comic opens instantly the next time.
 */
private class RarComicSource(
    private val archive: Archive,
    private val dir: File,
) : ComicSource {
    private val pages: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory && isPageImage(it.fileName) }
        .sortedWith(compareBy(NaturalOrder) { it.fileName })

    private val indexOf: Map<FileHeader, Int> = pages.withIndex().associate { (i, h) -> h to i }
    private val ready = BooleanArray(pages.size)
    private val lock = Object()
    private val complete = File(dir, ".complete")
    private val infoFile = File(dir, "comicinfo.xml")
    private val infoHeader: FileHeader? = archive.fileHeaders
        .filter { !it.isDirectory && isComicInfo(it.fileName) }
        .minByOrNull { it.fileName.count { c -> c == '/' || c == '\\' } }
    @Volatile private var comicInfo: ByteArray? = null

    @Volatile private var closed = false
    @Volatile private var finished = false
    @Volatile private var failure: Throwable? = null

    override val pageCount: Int get() = pages.size

    init {
        dir.mkdirs()
        if (complete.exists() && pages.indices.all { pageFile(it).length() > 0 }) {
            ready.fill(true)
            if (infoFile.exists()) comicInfo = runCatching { infoFile.readBytes() }.getOrNull()
            finished = true
            runCatching { archive.close() }
        } else {
            Thread(::extractAll, "fwip-rar").apply { isDaemon = true }.start()
        }
    }

    private fun pageFile(i: Int) = File(dir, "p$i")

    private fun extractInfo(header: FileHeader) {
        val out = ByteArrayOutputStream()
        archive.extractFile(header, out)
        val bytes = out.toByteArray()
        runCatching { infoFile.writeBytes(bytes) }
        synchronized(lock) {
            comicInfo = bytes
            lock.notifyAll()
        }
    }

    private fun extractAll() {
        try {
            // Outside solid archives any file can be read on its own, so grab the metadata first.
            val solid = runCatching { archive.mainHeader.isSolid }.getOrDefault(true)
            if (!solid) infoHeader?.let { runCatching { extractInfo(it) } }
            for (header in archive.fileHeaders) {
                if (closed) break
                if (header.isDirectory) continue
                val i = indexOf[header]
                if (i == null) {
                    if (header === infoHeader && comicInfo == null) {
                        extractInfo(header)
                    } else if (solid) {
                        // Not a page, but solid archives still need it decompressed to keep going.
                        archive.extractFile(header, NullSink)
                    }
                    continue
                }
                val tmp = File(dir, "p$i.${System.nanoTime()}.part")
                tmp.outputStream().buffered().use { archive.extractFile(header, it) }
                tmp.renameTo(pageFile(i))
                synchronized(lock) {
                    ready[i] = true
                    lock.notifyAll()
                }
            }
            if (!closed) complete.createNewFile()
        } catch (t: Throwable) {
            failure = t
        } finally {
            synchronized(lock) {
                finished = true
                lock.notifyAll()
            }
            runCatching { archive.close() }
        }
    }

    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        synchronized(lock) {
            while (!ready[index] && !finished && !closed) lock.wait(250)
        }
        if (!ready[index]) {
            throw failure ?: java.io.IOException("Página não encontrada no arquivo")
        }
        return ImageDecoding.decodeBytes(pageFile(index).readBytes(), maxWidth, maxHeight)
    }

    override fun readComicInfo(): ByteArray? {
        if (infoHeader == null) return null
        synchronized(lock) {
            while (comicInfo == null && !finished && !closed) lock.wait(250)
        }
        return comicInfo
    }

    override fun close() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
    }

    private object NullSink : java.io.OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }

    companion object {
        private const val KEEP = 5

        fun open(context: Context, comic: Comic): ComicSource {
            val root = File(context.cacheDir, "pages").apply { mkdirs() }
            val dir = File(root, sha1(comic.id))
            dir.setLastModified(System.currentTimeMillis())
            root.listFiles()
                ?.filter { it.isDirectory && it != dir }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP - 1)
                ?.forEach { it.deleteRecursively() }
            return RarComicSource(Archive(ArchiveCache.fileFor(context, comic)), dir)
        }
    }
}

private class PdfComicSource(
    private val pfd: ParcelFileDescriptor,
) : ComicSource {
    private val renderer = PdfRenderer(pfd)

    override val pageCount: Int get() = renderer.pageCount

    @Synchronized
    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val page = renderer.openPage(index)
        try {
            val scale = min(maxWidth / page.width.toFloat(), maxHeight / page.height.toFloat())
            val w = (page.width * scale).toInt().coerceIn(1, 4096)
            val h = (page.height * scale).toInt().coerceIn(1, 4096)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, comic: Comic): ComicSource {
            val direct = runCatching { context.contentResolver.openFileDescriptor(comic.uri, "r") }.getOrNull()
            if (direct != null) {
                try {
                    return PdfComicSource(direct)
                } catch (e: Exception) {
                    runCatching { direct.close() }
                }
            }
            val file = ArchiveCache.fileFor(context, comic)
            return PdfComicSource(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }
}
