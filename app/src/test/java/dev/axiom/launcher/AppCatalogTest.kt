package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    private val apps = listOf(
        InstalledApp("Telegram", "org.telegram", "Main", false),
        InstalledApp("Termux", "com.termux", "Main", false),
        InstalledApp("Калькулятор", "com.calc", "Main", true)
    )

    @Test fun prefixResultsBeforeSubstring() {
        val result = searchApps(apps, "te", emptySet())
        assertEquals(listOf("Telegram", "Termux"), result.map { it.label })
    }

    @Test fun russianTransliterationFindsEnglishApp() {
        val result = searchApps(apps, "тер", emptySet())
        assertEquals("Termux", result.first().label)
    }

    @Test fun favoritesWinTies() {
        val result = searchApps(apps, "te", setOf("com.termux"))
        assertEquals("Termux", result.first().label)
    }

    @Test fun unmatchedSearchReturnsEmpty() {
        assertTrue(searchApps(apps, "qqqqqq", emptySet()).isEmpty())
    }

    @Test fun englishTransliterationFindsRussianApp() {
        val result = searchApps(apps, "kalk", emptySet())
        assertTrue(result.any { it.packageName == "com.calc" })
    }
}
