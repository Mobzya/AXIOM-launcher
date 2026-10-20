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
    // Build-type specific tuning; only the separate Honor APK enables it.
    // It is safe to install on another Android device but is optimized
    // for an 11.5-inch 2508x1504 Snapdragon 685 tablet.
    private val honorTabletProfile by lazy {
        resources.getBoolean(R.bool.honor_tablet_tuned)
    }

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
            ) { AxiomScreen(resumeVersion, frameMeter, honorTabletProfile) }
        }
    }

    override fun onDestroy() {
        frameMeter.stop()
        super.onDestroy()
    }

    /**
     * Pixel / Samsung / OnePlus / Xiaomi use different refresh policies.
     * Never force an unsupported mode or overrule the user's 60 Hz / battery
     * preference. Observe the currently active mode; the animation
     * interpolator follows Choreographer's real display callbacks.
     */
    private fun configureDisplaySafely() {
        runCatching {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (power.isPowerSaveMode) return
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay ?: return
            val attributes = window.attributes
            // Clear earlier vendor-preferred overrides (if any). Android
            // chooses 60/90/120 Hz using system settings and thermal state.
            if (attributes.preferredDisplayModeId != 0) {
                attributes.preferredDisplayModeId = 0
                window.attributes = attributes
            }
            @Suppress("UNUSED_VARIABLE")
            val currentRefreshRate = display.refreshRate
        }
    }

    override fun onResume() {
        super.onResume()
        configureDisplaySafely()
        resumeVersion++
    }
}
