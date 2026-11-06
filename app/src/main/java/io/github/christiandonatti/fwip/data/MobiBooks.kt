package io.github.christiandonatti.fwip.data

import java.io.File

/** Kindle books without DRM (MOBI, AZW3): read their details and turn them into EPUB. */
object MobiBooks {
    fun info(file: File): BookInfo = BookInfo()

    fun toEpub(source: File, target: File) {
        error(io.github.christiandonatti.fwip.str(io.github.christiandonatti.fwip.R.string.mobi_unsupported))
    }
}
