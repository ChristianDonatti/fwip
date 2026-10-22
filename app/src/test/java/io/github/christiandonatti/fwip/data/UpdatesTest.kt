package io.github.christiandonatti.fwip.data

import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun versionsCompareByNumber() {
        assertTrue(Updates.compareVersions("0.3.1", "0.3.0") > 0)
        assertTrue(Updates.compareVersions("0.10.0", "0.9.2") > 0)
        assertTrue(Updates.compareVersions("1.0", "1.0.0") == 0)
        assertTrue(Updates.compareVersions("0.3.0", "0.4.0") < 0)
    }
}

class IssueNumberTest {
    @Test
    fun placeholdersAreNoNumber() {
        org.junit.Assert.assertNull(issueOrNull("None"))
        org.junit.Assert.assertNull(issueOrNull(" null "))
        org.junit.Assert.assertNull(issueOrNull(""))
        org.junit.Assert.assertEquals("12", issueOrNull("#12"))
        org.junit.Assert.assertEquals("0", issueOrNull("0"))
        org.junit.Assert.assertNull(ComicMeta(series = "Saga", number = "None").numberValue)
        org.junit.Assert.assertEquals("Saga", ComicMeta(series = "Saga", number = "None").displayTitle)
    }
}
