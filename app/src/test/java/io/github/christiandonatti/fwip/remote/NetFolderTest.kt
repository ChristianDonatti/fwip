package io.github.christiandonatti.fwip.remote

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads comics from a real SMB share. CI starts Samba with Test.cbz, Plain.cbr and Sub/Solid.cbr
 * (pages p1..p3.png, also kept in FWIP_PAGES to compare). Skipped when FWIP_SMB is not set.
 */
class NetFolderTest {
    private val address = System.getenv("FWIP_SMB")
    private val pages = System.getenv("FWIP_PAGES")?.let(::File)

    private fun client(): NetClient {
        val server = Server(
            id = "test",
            name = "Test",
            kind = ServerKind.Smb,
            url = "smb://$address",
            user = System.getenv("FWIP_SMB_USER").orEmpty(),
            password = System.getenv("FWIP_SMB_PASS").orEmpty(),
        )
        return NetClients.forServer(server)
    }

    private fun expected(name: String) = File(pages, name).readBytes()

    @Test
    fun listsTheShare() {
        assumeTrue(address != null)
        val root = client().list("")
        val cbz = root.single { it.name == "Test.cbz" }
        assertFalse(cbz.isDir)
        assertEquals(File(pages!!.parentFile, "share/Test.cbz").length(), cbz.size)
        assertTrue(root.single { it.name == "Sub" }.isDir)
        assertEquals("Sub/Solid.cbr", client().list("Sub").single { it.name == "Solid.cbr" }.path)
    }

    @Test
    fun readsCbzInPlace() {
        assumeTrue(address != null)
        BlockReader(client().open("Test.cbz"), blockSize = 4096, maxBlocks = 4).use { reader ->
            @Suppress("DEPRECATION")
            val zip = ZipFile(BlockChannel(reader))
            val names = zip.entries.toList().map { it.name }.sorted()
            assertEquals(listOf("p1.png", "p2.png", "p3.png"), names)
            for (n in names) {
                assertArrayEquals(n, expected(n), zip.getInputStream(zip.getEntry(n)).use { it.readBytes() })
            }
        }
    }

    /** Everything in a RAR, read straight from disk with junrar: the reference. */
    private fun extractLocal(path: String): Map<String, ByteArray> {
        val archive = Archive(File(System.getenv("FWIP_SHARE"), path))
        return archive.fileHeaders.filter { !it.isDirectory }.associate { h ->
            h.fileName to ByteArrayOutputStream().also { archive.extractFile(h, it) }.toByteArray()
        }.also { archive.close() }
    }

    private fun checkRar(path: String) {
        val reference = extractLocal(path)
        assertTrue(reference.size >= 2)
        BlockReader(client().open(path), blockSize = 4096, maxBlocks = 4).use { reader ->
            val archive = Archive(NetRarVolumes(reader), null, null)
            val headers = archive.fileHeaders.filter { !it.isDirectory }
            assertEquals(reference.keys, headers.map { it.fileName }.toSet())
            for (h in headers) {
                val out = ByteArrayOutputStream()
                archive.extractFile(h, out)
                assertArrayEquals(h.fileName, reference.getValue(h.fileName), out.toByteArray())
            }
            archive.close()
        }
    }

    @Test
    fun readsCbrInPlace() {
        assumeTrue(address != null)
        checkRar("Plain.cbr")
    }

    @Test
    fun readsSolidCbrInPlace() {
        assumeTrue(address != null)
        checkRar("Sub/Solid.cbr")
    }

    /** RAR5 is left to 7-Zip on the device; junrar must refuse it in a way the app can catch. */
    @Test(expected = com.github.junrar.exception.UnsupportedRarV5Exception::class)
    fun recognisesRar5() {
        assumeTrue(address != null)
        BlockReader(client().open("New.cbr")).use { reader -> Archive(NetRarVolumes(reader), null, null) }
    }

    @Test
    fun uploadsAndDeletes() {
        assumeTrue(address != null)
        val c = client()
        val data = ByteArray(3_000_000) { (it % 251).toByte() }
        c.upload("Moved.cbz", data.size.toLong(), data.inputStream())
        assertEquals(data.size.toLong(), c.list("").single { it.name == "Moved.cbz" }.size)
        val back = BlockReader(c.open("Moved.cbz")).use { it.inputStream().readBytes() }
        assertArrayEquals(data, back)
        c.delete("Moved.cbz")
        assertTrue(c.list("").none { it.name == "Moved.cbz" })
    }

    @Test
    fun writesSyncFiles() {
        assumeTrue(address != null)
        val c = client()
        c.mkdirs(".fwip")
        c.mkdirs(".fwip")
        val text = """{"device":"test","n":${System.nanoTime()}}"""
        c.write(".fwip/test.json", text.toByteArray())
        c.write(".fwip/test.json", text.toByteArray())
        val back = BlockReader(c.open(".fwip/test.json")).use { it.inputStream().readBytes().decodeToString() }
        assertEquals(text, back)
        assertTrue(c.list(".fwip").any { it.name == "test.json" })
        // Hidden folders stay out of listings (and of library scans).
        assertTrue(c.list("").none { it.name == ".fwip" })
    }
}
