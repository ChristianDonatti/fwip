package io.github.christiandonatti.fwip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NasUploadTest {
    private fun f(rel: String, size: Long) = LocalFile(rel, size, "content://$rel")

    @Test
    fun sendsNewFilesKeepingFolders() {
        val plan = NasUpload.plan(listOf(f("Batman/Batman 001.cbz", 10), f("Loose.cbr", 5)), emptySet(), emptySet()) { null }
        assertEquals(listOf("Batman" to "Batman 001.cbz", "" to "Loose.cbr"), plan.steps.map { it.dir to it.name })
        assertEquals(0, plan.alreadyThere.size)
    }

    @Test
    fun skipsWhatWasSentOrIsThere() {
        val sent = f("A.cbz", 1)
        val listed = f("Sub/B.cbz", 2)
        val known = f("C.cbz", 3)
        val plan = NasUpload.plan(
            listOf(sent, listed, known),
            sent = setOf(sent.key),
            onNas = setOf(known.syncKey),
        ) { dir -> if (dir == "Sub") listOf("b.CBZ" to 2L) else emptyList() }
        assertEquals(emptyList<UploadStep>(), plan.steps)
        assertEquals(listOf(listed, known), plan.alreadyThere)
    }

    @Test
    fun differentFileWithTheSameNameGetsANewName() {
        val plan = NasUpload.plan(listOf(f("X.cbz", 9), f("Y.cbz", 4)), emptySet(), emptySet()) { listOf("X.cbz" to 8L, "X (2).cbz" to 7L) }
        assertEquals(listOf("X (3).cbz", "Y.cbz"), plan.steps.map { it.name })
    }
}
