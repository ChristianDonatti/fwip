package io.github.christiandonatti.fwip.data

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Kindle books without DRM (MOBI, AZW3, PRC): libmobi rebuilds their HTML, styles, images,
 * table of contents and OPF, and this packs them as an EPUB the reader opens like any other.
 */
object MobiBooks {
    /** Receives each rebuilt file from the native side. */
    fun interface PartSink { fun part(name: String, data: ByteArray) }

    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("fwipmobi") }.isSuccess }

    @JvmStatic private external fun nativeParts(path: String, sink: PartSink): Int

    fun toEpub(source: File, target: File) {
        check(loaded) { str(R.string.mobi_unsupported) }
        val tmp = File(target.path + ".part")
        var count = 0
        var hasOpf = false
        val code = ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""".toByteArray(),
            )
            zip.closeEntry()
            nativeParts(source.absolutePath) { name, data ->
                zip.putNextEntry(ZipEntry("OEBPS/$name"))
                zip.write(data)
                zip.closeEntry()
                count++
                if (name == "content.opf") hasOpf = true
            }
        }
        if (code != 0 || count == 0 || !hasOpf) {
            tmp.delete()
            error(str(if (code == 2) R.string.mobi_drm else R.string.mobi_unsupported))
        }
        tmp.renameTo(target)
    }
}
