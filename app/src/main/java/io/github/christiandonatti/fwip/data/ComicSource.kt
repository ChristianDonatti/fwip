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

    fun decode(open: () -> InputStream, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it.buffered(), null, bounds) }
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
        return open().use { BitmapFactory.decodeStream(it.buffered(), null, options) }
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

private class RarComicSource(private val archive: Archive) : ComicSource {
    private val pages: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory && isPageImage(it.fileName) }
        .sortedWith(compareBy(NaturalOrder) { it.fileName })

    override val pageCount: Int get() = pages.size

    @Synchronized
    override fun decodePage(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? =
        ImageDecoding.decode({ archive.getInputStream(pages[index]) }, maxWidth, maxHeight)

    override fun close() {
        runCatching { archive.close() }
    }

    companion object {
        fun open(context: Context, comic: Comic): ComicSource =
            RarComicSource(Archive(ArchiveCache.fileFor(context, comic)))
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
