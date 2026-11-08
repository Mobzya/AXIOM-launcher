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

    @Test fun observatoryPalettesKeepReadableTextContrast() {
        fun luminance(color: androidx.compose.ui.graphics.Color): Double {
            fun channel(v: Float): Double = if(v <= .04045f) v/12.92 else
                Math.pow((v+.055)/1.055,2.4)
            return .2126*channel(color.red)+.7152*channel(color.green)+.0722*channel(color.blue)
        }
        fun contrast(a: androidx.compose.ui.graphics.Color,b: androidx.compose.ui.graphics.Color): Double {
            val x=luminance(a);val y=luminance(b)
            return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
        }
        assertTrue(AxiomPalette.DAY.light)
        assertFalse(AxiomPalette.NIGHT.light)
        for(palette in listOf(AxiomPalette.DAY,AxiomPalette.NIGHT)) {
            assertTrue(contrast(palette.ink,palette.background)>7.0)
            assertTrue(contrast(palette.muted,palette.surface)>4.5)
        }
    }
}
