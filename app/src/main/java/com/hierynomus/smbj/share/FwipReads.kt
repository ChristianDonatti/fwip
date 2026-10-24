package com.hierynomus.smbj.share

import com.hierynomus.mssmb2.messages.SMB2ReadResponse
import java.util.concurrent.Future

/**
 * smbj keeps asynchronous reads to itself (package-private); this small door in the same
 * package lets Fwip ask for several pieces of a file at once.
 */
object FwipReads {
    fun readAsync(file: File, offset: Long, length: Int): Future<SMB2ReadResponse> = file.readAsync(offset, length)
}
