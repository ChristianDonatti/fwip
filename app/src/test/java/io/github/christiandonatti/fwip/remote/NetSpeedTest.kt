package io.github.christiandonatti.fwip.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How fast comics go to an SMB share, the old way (one piece at a time) and the new one (several
 * pieces in flight). CI runs it with a delay on the network, like Wi-Fi. Only when FWIP_SPEED is set.
 */
class NetSpeedTest {
    private val address = System.getenv("FWIP_SMB")
    private val tag = System.getenv("FWIP_SPEED").orEmpty()

    private fun client(share: String): NetClient = NetClients.forServer(
        Server(
            id = "speed-$share-${System.nanoTime()}",
            name = "Speed",
            kind = ServerKind.Smb,
            url = "smb://" + address!!.substringBefore('/') + "/" + share,
            user = System.getenv("FWIP_SMB_USER").orEmpty(),
            password = System.getenv("FWIP_SMB_PASS").orEmpty(),
        )
    )

    private fun run(share: String, label: String, pipelined: Boolean, buffer: Int, data: ByteArray, fast: Boolean = true) {
        SmbTuning.pipelined = pipelined
        SmbTuning.fastCrypto = fast
        SmbTuning.bufferSize = buffer
        try {
            val c = client(share)
            c.list("")
            val start = System.nanoTime()
            c.upload("speed.bin", data.size.toLong(), data.inputStream())
            val seconds = (System.nanoTime() - start) / 1e9
            println("SPEED $tag $share $label: %.1f MB/s (%.1f s for %d MB)".format(data.size / 1e6 / seconds, seconds, data.size shr 20))
            val out = java.io.ByteArrayOutputStream(data.size)
            val t = System.nanoTime()
            c.download("speed.bin", out) { _, _ -> }
            val down = (System.nanoTime() - t) / 1e9
            println("SPEED $tag $share $label download: %.1f MB/s".format(data.size / 1e6 / down))
            assertArrayEquals(data, out.toByteArray())
            if (label == "1MB x8 in flight") {
                for (n in listOf(1, 2, 3)) {
                    SmbTuning.connections = n
                    val file = java.io.File.createTempFile("speed", ".bin")
                    val t2 = System.nanoTime()
                    c.downloadTo("speed.bin", file) { _, _ -> }
                    val s2 = (System.nanoTime() - t2) / 1e9
                    println("SPEED $tag $share to file, $n streams: %.1f MB/s".format(data.size / 1e6 / s2))
                    assertArrayEquals(data, file.readBytes())
                    file.delete()
                }
                SmbTuning.connections = 2
            }
            c.delete("speed.bin")
        } catch (e: Exception) {
            println("SPEED $tag $share $label: failed: $e")
            if (share == "Comics") throw e
        } finally {
            SmbTuning.pipelined = true
            SmbTuning.fastCrypto = true
            SmbTuning.bufferSize = 4 shl 20
        }
    }

    @Test
    fun uploadSpeed() {
        assumeTrue(address != null && System.getenv("FWIP_SPEED") != null)
        val data = ByteArray(48 shl 20) { (it * 31 % 251).toByte() }
        for (share in listOf("Comics")) {
            run(share, "old 1MB one at a time", pipelined = false, buffer = 1 shl 20, data = data)
            run(share, "1MB x8 in flight, Bouncy Castle", pipelined = true, buffer = 4 shl 20, data = data, fast = false)
            run(share, "1MB x8 in flight", pipelined = true, buffer = 4 shl 20, data = data)
        }
    }
}
