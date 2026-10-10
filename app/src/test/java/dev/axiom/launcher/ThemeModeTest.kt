package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun automaticDaylightBoundariesAreClear() {
        assertFalse(daylightAt(0))
        assertFalse(daylightAt(6))
        assertTrue(daylightAt(7))
        assertTrue(daylightAt(12))
        assertTrue(daylightAt(18))
        assertFalse(daylightAt(19))
        assertFalse(daylightAt(23))
    }

    @Test fun manualModesOverrideClock() {
        assertTrue(resolvedLight(AxiomThemeMode.LIGHT, 23))
        assertFalse(resolvedLight(AxiomThemeMode.DARK, 12))
        assertTrue(resolvedLight(AxiomThemeMode.AUTO, 12))
        assertFalse(resolvedLight(AxiomThemeMode.AUTO, 3))
    }

    @Test fun invalidPreferenceNeverCrashesSettings() {
        assertEquals(AxiomThemeMode.DARK, modeFromPreference("NOT_A_MODE"))
        assertEquals(AxiomThemeMode.AUTO, modeFromPreference("AUTO"))
        assertEquals(AxiomThemeMode.LIGHT, modeFromPreference("LIGHT"))
    }

    @Test fun monochromePaletteHasHighContrast() {
        assertTrue(AxiomPalette.DAY.light)
        assertFalse(AxiomPalette.NIGHT.light)
        assertEquals(androidx.compose.ui.graphics.Color.White, AxiomPalette.DAY.background)
        assertEquals(androidx.compose.ui.graphics.Color.Black, AxiomPalette.NIGHT.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A0A0A), AxiomPalette.DAY.ink)
    }
}
