package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.Calendar

/** Monochrome palette. A single snapshot update repaints Compose and Canvas. */
internal enum class AxiomThemeMode { DARK, LIGHT, AUTO }

internal fun daylightAt(hour: Int): Boolean = hour in 7..18

internal fun resolvedLight(mode: AxiomThemeMode, hour: Int): Boolean =
    when (mode) {
        AxiomThemeMode.DARK -> false
        AxiomThemeMode.LIGHT -> true
        AxiomThemeMode.AUTO -> daylightAt(hour)
    }

internal fun modeFromPreference(value: String?): AxiomThemeMode =
    AxiomThemeMode.entries.firstOrNull { it.name == value } ?: AxiomThemeMode.DARK

internal data class AxiomPalette(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val glass: Color,
    val panel: Color,
    val surface: Color,
    val tile: Color,
    val hint: Color,
    val divider: Color,
    val light: Boolean
) {
    companion object {
        val NIGHT = AxiomPalette(
            background = Color(0xFF000000),
            ink = Color(0xFFF8F8F8),
            muted = Color(0xFF989898),
            faint = Color(0xFF575757),
            border = Color(0xFF494949),
            glass = Color(0xB3181818),
            panel = Color(0xF5080808),
            surface = Color(0xFF161616),
            tile = Color(0xFF282828),
            hint = Color(0xFFD3D3D3),
            divider = Color(0xFF292929),
            light = false
        )
        val DAY = AxiomPalette(
            background = Color.White,
            ink = Color(0xFF0A0A0A),
            muted = Color(0xFF525252),
            faint = Color(0xFF777777),
            border = Color(0xFFC1C1C1),
            glass = Color(0xDDF7F7F7),
            panel = Color(0xFCFFFFFF),
            surface = Color(0xFFF5F5F5),
            tile = Color(0xFFEEEEEE),
            hint = Color(0xFF4D4D4D),
            divider = Color(0xFFE1E1E1),
            light = true
        )
    }
}

internal object AxiomColors {
    var palette by mutableStateOf(AxiomPalette.NIGHT)
        private set

    fun apply(mode: AxiomThemeMode, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        val next = if (resolvedLight(mode, hour)) AxiomPalette.DAY else AxiomPalette.NIGHT
        if (palette != next) palette = next
    }
}
