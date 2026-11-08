package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.Calendar

/** Quiet observatory palette. A single snapshot update repaints Compose and Canvas. */
internal enum class AxiomThemeMode { DARK, LIGHT, AUTO }

/** User-settable daylight interval in LOCAL device minutes. Wraps midnight. */
internal data class ThemeSchedule(val sunrise: Int = 7 * 60,
                                  val sunset: Int = 19 * 60) {
    val start: Int get() = sunrise.coerceIn(0, 1439)
    val end: Int get() = sunset.coerceIn(0, 1439)
}
internal fun daylightAtMinutes(minute: Int, schedule: ThemeSchedule): Boolean {
    val t = ((minute % 1440) + 1440) % 1440
    val from = schedule.start
    val until = schedule.end
    return when {
        from == until -> true // Continuous day; user can choose DARK for constant night.
        from < until -> t in from until until
        else -> t >= from || t < until
    }
}
internal fun daylightAt(hour: Int): Boolean =
    daylightAtMinutes(hour * 60, ThemeSchedule())
internal fun resolvedLight(
    mode: AxiomThemeMode, hour: Int,
    schedule: ThemeSchedule = ThemeSchedule(),
    minute: Int = 0
): Boolean = when (mode) {
    AxiomThemeMode.DARK -> false
    AxiomThemeMode.LIGHT -> true
    AxiomThemeMode.AUTO -> daylightAtMinutes(hour * 60 + minute, schedule)
}
internal fun scheduleLabel(minutes: Int): String =
    "%02d:%02d".format(java.util.Locale.ROOT,
        minutes.coerceIn(0,1439) / 60,minutes.coerceIn(0,1439) % 60)

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
            background = Color(0xFF0B101A),
            ink = Color(0xFFECEEF4),
            muted = Color(0xFFA3ADBE),
            faint = Color(0xFF78869C),
            border = Color(0xFF303D50),
            glass = Color(0xE016202F),
            panel = Color(0xFF111A28),
            surface = Color(0xFF182334),
            tile = Color(0xFF25344A),
            hint = Color(0xFFCBD5E2),
            divider = Color(0xFF243247),
            light = false
        )
        val DAY = AxiomPalette(
            background = Color(0xFFF3F6F8),
            ink = Color(0xFF24364A),
            muted = Color(0xFF53657B),
            faint = Color(0xFF63758C),
            border = Color(0xFFBCCBD6),
            glass = Color(0xEDF2F5F6),
            panel = Color(0xFFF3F6F8),
            surface = Color(0xFFE7EEF2),
            tile = Color(0xFFDCE6ED),
            hint = Color(0xFF4F647A),
            divider = Color(0xFFCFDCE5),
            light = true
        )
    }
}

internal object AxiomColors {
    var palette by mutableStateOf(AxiomPalette.NIGHT)
        private set

    fun apply(
        mode: AxiomThemeMode,
        hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        schedule: ThemeSchedule = ThemeSchedule(),
        minute: Int = Calendar.getInstance().get(Calendar.MINUTE)
    ) {
        val next = if (resolvedLight(mode, hour, schedule, minute))
            AxiomPalette.DAY else AxiomPalette.NIGHT
        if (palette != next) palette = next
    }
}
