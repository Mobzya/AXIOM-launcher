package dev.axiom.launcher

import android.os.Bundle
import android.os.PowerManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private var resumeVersion by mutableIntStateOf(0)
    private val frameMeter by lazy { FrameMeter(window) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            val colors = AxiomColors.palette
            MaterialTheme(
                colorScheme = if (colors.light) lightColorScheme(
                    background = colors.background, surface = colors.surface,
                    onBackground = colors.ink, onSurface = colors.ink,
                    primary = colors.ink, onPrimary = colors.background
                ) else darkColorScheme(
                    background = colors.background, surface = colors.surface,
                    onBackground = colors.ink, onSurface = colors.ink,
                    primary = colors.ink, onPrimary = colors.background
                )
            ) { AxiomScreen(resumeVersion, frameMeter) }
        }
    }

    override fun onDestroy() {
        frameMeter.stop()
        super.onDestroy()
    }

    /**
     * A 120Hz-capable display may otherwise select 60Hz for the launcher.
     * Request (do not force) its fastest mode at the current resolution.
     * Android retains authority over frame pacing, battery and thermals.
     */
    private fun requestFastDisplayMode() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isPowerSaveMode) return
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val active = display.mode
        val fast = display.supportedModes
            .filter { it.physicalWidth == active.physicalWidth &&
                it.physicalHeight == active.physicalHeight &&
                it.refreshRate >= 90f }
            .maxByOrNull { it.refreshRate } ?: return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != fast.modeId) {
            attributes.preferredDisplayModeId = fast.modeId
            window.attributes = attributes
        }
    }

    override fun onResume() {
        super.onResume()
        requestFastDisplayMode()
        resumeVersion++
    }
}
