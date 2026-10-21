package io.github.christiandonatti.fwip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    private fun check(file: String, expected: ParsedName) = assertEquals(file, expected, parseFileName(file))

    @Test
    fun typicalNames() {
        check("Batman 001 (2016) (Digital) (Zone-Empire).cbr", ParsedName("Batman", "1", null, 2016, null, null))
        check("Amazing Spider-Man v5 #012 (2018).cbz", ParsedName("Amazing Spider-Man", "12", 5, 2018, null, null))
        check("Saga Vol. 1.cbz", ParsedName("Saga", null, 1, null, null, null))
        check("Saga 054 (2018) (digital) (Son of Ultron-Empire).cbr", ParsedName("Saga", "54", null, 2018, null, null))
        check("Batman 001 - The Court of Owls.cbr", ParsedName("Batman", "1", null, null, null, "The Court of Owls"))
        check("Batman - The Long Halloween 01 (of 13) (1996).cbz", ParsedName("Batman - The Long Halloween", "1", null, 1996, 13, null))
        check("X-Men 2099 001.cbz", ParsedName("X-Men 2099", "1", null, null, null, null))
        check("The.Walking.Dead.100.2012.cbr", ParsedName("The Walking Dead", "100", null, 2012, null, null))
        check("Invincible Vol. 3 - Perfect Strangers (2004).cbz", ParsedName("Invincible", null, 3, 2004, null, "Perfect Strangers"))
        check("Watchmen.cbz", ParsedName("Watchmen", null, null, null, null, null))
        check("Batman v2 001.cbz", ParsedName("Batman", "1", 2, null, null, null))
        check("Ms. Marvel 001 (2014).cbz", ParsedName("Ms. Marvel", "1", null, 2014, null, null))
        check("Batman_Vol.2_#07.cbz", ParsedName("Batman", "7", 2, null, null, null))
        check("Batman Annual 01 (2017).cbr", ParsedName("Batman Annual", "1", null, 2017, null, null))
        check("Spawn 300 (2019) [Digital-Empire].cbz", ParsedName("Spawn", "300", null, 2019, null, null))
        check("Hellboy 0.5.cbz", ParsedName("Hellboy", "0.5", null, null, null, null))
        check("Daredevil 01 of 05.cbz", ParsedName("Daredevil", "1", null, null, 5, null))
        check("Wonder Woman (2016-) 001.cbr", ParsedName("Wonder Woman", "1", null, 2016, null, null))
    }

    @Test
    fun display() {
        assertEquals("Batman #1", parseFileName("Batman 001 (2016).cbz").display)
        assertEquals("Saga Vol. 1", parseFileName("Saga Vol. 1.cbz").display)
        assertEquals("Watchmen", parseFileName("Watchmen.cbz").display)
    }
}
