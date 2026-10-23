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

    private fun run(share: String, label: String, pipelined: Boolean, buffer: Int, data: ByteArray) {
        SmbTuning.pipelined = pipelined
        SmbTuning.bufferSize = buffer
        try {
            val c = client(share)
            c.list("")
            val start = System.nanoTime()
            c.upload("speed.bin", data.size.toLong(), data.inputStream())
            val seconds = (System.nanoTime() - start) / 1e9
            println("SPEED $tag $share $label: %.1f MB/s (%.1f s for %d MB)".format(data.size / 1e6 / seconds, seconds, data.size shr 20))
            val back = BlockReader(c.open("speed.bin"), blockSize = 1 shl 20, maxBlocks = 4).use { it.inputStream().readBytes() }
            assertArrayEquals(data, back)
            c.delete("speed.bin")
        } catch (e: Exception) {
            println("SPEED $tag $share $label: failed: $e")
            if (share == "Comics") throw e
        } finally {
            SmbTuning.pipelined = true
            SmbTuning.bufferSize = 4 shl 20
        }
    }

    @Test
    fun uploadSpeed() {
        assumeTrue(address != null && System.getenv("FWIP_SPEED") != null)
        val data = ByteArray(48 shl 20) { (it * 31 % 251).toByte() }
        for (share in listOf("Comics")) {
            run(share, "old 1MB one at a time", pipelined = false, buffer = 1 shl 20, data = data)
            run(share, "4MB one at a time", pipelined = false, buffer = 4 shl 20, data = data)
            run(share, "1MB x8 in flight", pipelined = true, buffer = 4 shl 20, data = data)
        }
    }
}
