package com.hierynomus.smbj.share

import com.hierynomus.mssmb2.messages.SMB2ReadResponse
import java.util.concurrent.Future

/**
 * smbj keeps asynchronous reads and the negotiated request sizes to itself (package-private);
 * this small door in the same package lets Fwip ask for several pieces of a file at once.
 */
object FwipReads {
    fun readAsync(file: File, offset: Long, length: Int): Future<SMB2ReadResponse> = file.readAsync(offset, length)

    /** Largest read the NAS accepts in one request (64 KB on old SMB 2.0, often 1 to 8 MB). */
    fun readSize(file: File): Int = file.diskShare.readBufferSize

    fun writeSize(file: File): Int = file.diskShare.writeBufferSize
}
