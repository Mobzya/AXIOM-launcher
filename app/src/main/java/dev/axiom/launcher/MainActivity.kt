package dev.axiom.launcher

import android.os.Bundle
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

    override fun onResume() {
        super.onResume()
        resumeVersion++
    }
}
