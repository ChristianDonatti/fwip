package io.github.christiandonatti.fwip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingListsTest {
    private val cbl = """<?xml version="1.0"?>
<ReadingList xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <Name>Civil War</Name>
  <Books>
    <Book Series="Civil War" Number="1" Volume="2006" Year="2006"><Id>a</Id></Book>
    <Book Series="The Amazing Spider-Man" Number="532" Volume="1963" Year="2006" />
    <Book Series="Civil War" Number="2" Volume="2006" Year="2006" />
    <Book Series="Iron Man" Number="13" Volume="2005" Year="2006" />
    <Book Series="Fantastic Four" Number="538" Volume="1998" Year="2006" />
  </Books>
</ReadingList>""".toByteArray()

    @Test
    fun parsesNameAndBooks() {
        val list = ReadingLists.parse(cbl)
        assertEquals("Civil War", list.name)
        assertEquals(5, list.books.size)
        assertEquals(CblBook("The Amazing Spider-Man", "532", 1963, 2006), list.books[1])
    }

    @Test
    fun matchesInOrderAndPicksTheRightRun() {
        val library = listOf(
            CblCandidate("cw2", "Civil War", "002", 2006, 2006),
            CblCandidate("cw1", "Civil War (2006)", "1", 2006, 2006),
            CblCandidate("asm", "Amazing Spider-Man", "#532", null, 2006),
            CblCandidate("im-old", "Iron Man", "13", 1968, 1969),
            CblCandidate("im-new", "Iron Man", "13", 2005, 2006),
        )
        val m = ReadingLists.match(ReadingLists.parse(cbl), library)
        assertEquals(listOf("cw1", "asm", "cw2", "im-new"), m.ids)
        assertEquals(listOf("Fantastic Four"), m.missing.map { it.series })
        assertEquals(5, m.total)
    }

    @Test
    fun numbers() {
        assertEquals("1", ReadingLists.numberNorm("001"))
        assertEquals("1", ReadingLists.numberNorm("#1.0"))
        assertEquals("0.5", ReadingLists.numberNorm("½"))
        assertEquals("1a", ReadingLists.numberNorm("1A"))
    }
}
