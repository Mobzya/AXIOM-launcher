package dev.axiom.launcher

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Optional in-app frame timing profiler (API 28+).
 * It gathers timings for AXIOM's own window only; does not need ADB, root,
 * Usage Access or overlay permissions.
 *
 * Runs only while explicitly enabled and publishes results every 90 frames.
 */
internal class FrameMeter(private val window: Window) {
    var enabled by mutableStateOf(false)
        private set
    var summary by mutableStateOf("Сбор данных о кадрах…")
        private set

    private val durations = LongArray(240)
    private var cursor = 0
    private var count = 0
    private val handler = Handler(Looper.getMainLooper())
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (!enabled) return@OnFrameMetricsAvailableListener
        val nanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (nanos <= 0L) return@OnFrameMetricsAvailableListener
        durations[cursor] = nanos
        cursor = (cursor + 1) % durations.size
        count++
        if (count % 90 == 0) {
            val n = minOf(count, durations.size)
            val sorted = durations.take(n).sorted()
            val median = sorted[(n * .5f).toInt().coerceIn(0, n-1)]
            val p95 = sorted[(n * .95f).toInt().coerceIn(0, n-1)]
            val p99 = sorted[(n * .99f).toInt().coerceIn(0, n-1)]
            summary = "P50 ${format(median)}   P95 ${format(p95)}   P99 ${format(p99)}"
        }
    }

    fun toggle(): Boolean {
        if (enabled) {
            enabled = false
            window.removeOnFrameMetricsAvailableListener(listener)
        } else {
            count = 0
            cursor = 0
            summary = "Сбор данных о кадрах…"
            enabled = true
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }
        return enabled
    }

    fun stop() {
        if (enabled) {
            enabled = false
            window.removeOnFrameMetricsAvailableListener(listener)
        }
    }

    private fun format(nanos: Long) = "%.1f ms".format(
        java.util.Locale.US, nanos / 1_000_000.0
    )
}
