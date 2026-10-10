package dev.axiom.launcher

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Opt-in frame meter. Processing and percentile sorting take place on a
 * dedicated thread, never on the launcher UI thread.
 *
 * The only UI work is one text update per 90 measured frames.
 */
internal class FrameMeter(private val window: Window) {
    var enabled by mutableStateOf(false)
        private set
    var summary by mutableStateOf("Сбор данных о кадрах…")
        private set

    private val main = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private val durations = LongArray(240)
    private var count = 0
    private var cursor = 0
    private var session = 0

    fun toggle(): Boolean {
        if (enabled) stop() else start()
        return enabled
    }

    private fun start() {
        stop()
        session++
        val currentSession = session
        count = 0
        cursor = 0
        summary = "Сбор данных о кадрах…"
        worker = HandlerThread("AXIOM-FrameMeter").also { it.start() }
        val capture = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            val duration = frame.getMetric(FrameMetrics.TOTAL_DURATION)
            if (duration <= 0L || currentSession != session) return@OnFrameMetricsAvailableListener
            durations[cursor] = duration
            cursor = (cursor + 1) % durations.size
            count++
            if (count % 90 == 0) {
                val values = durations.take(minOf(count, durations.size)).sorted()
                val last = values.lastIndex
                fun pct(q: Double): String {
                    val index = (last * q).toInt().coerceIn(0, last)
                    return "%.1f".format(
                        java.util.Locale.US, values[index] / 1_000_000.0
                    )
                }
                val value = "P50 ${pct(.5)} ms   P95 ${pct(.95)} ms   P99 ${pct(.99)} ms"
                main.post {
                    if (session == currentSession && enabled) summary = value
                }
            }
        }
        listener = capture
        enabled = true
        window.addOnFrameMetricsAvailableListener(capture, Handler(worker!!.looper))
    }

    fun stop() {
        enabled = false
        session++
        listener?.let { window.removeOnFrameMetricsAvailableListener(it) }
        listener = null
        worker?.quitSafely()
        worker = null
    }
}
