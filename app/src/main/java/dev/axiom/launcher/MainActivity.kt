package dev.axiom.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black, surface = Color.Black,
                    onBackground = Color.White, onSurface = Color.White,
                    primary = Color.White, onPrimary = Color.Black
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
