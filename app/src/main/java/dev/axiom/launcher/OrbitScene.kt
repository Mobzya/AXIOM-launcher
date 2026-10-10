package dev.axiom.launcher

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Fixed coordinates, no random per frame. All motion is a slow periodic function.
private val Stars = listOf(
    .11f to .17f, .72f to .14f, .38f to .21f, .84f to .33f,
    .23f to .40f, .62f to .51f, .15f to .66f, .78f to .59f,
    .50f to .10f, .06f to .49f, .93f to .72f, .31f to .75f,
    .58f to .80f, .88f to .16f, .48f to .64f
)
private const val TAU = (2.0 * PI).toFloat()

/**
 * Intensity 0 draws a still scene; 1 = quiet ambient; 2 = slightly richer motion.
 * During search/catalog/settings the scene freezes to avoid competing animations.
 *
 * Two drawOval calls replace the old 480-segment path rebuilt every frame.
 * No blur, bitmap decoding, allocations of large paths, network or sensors.
 */
@Composable
fun AmbientSpace(intensity: Int, paused: Boolean, modifier: Modifier = Modifier) {
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
    }
    if (intensity > 0 && !paused) {
        val transition = rememberInfiniteTransition(label = "ambient phase")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (intensity == 1) 102_000 else 76_000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "slow astronomy phase"
        )
        SpaceFrame(phase, intensity, textPaint, modifier)
    } else {
        SpaceFrame(0f, intensity, textPaint, modifier)
    }
}

@Composable
private fun SpaceFrame(phase: Float, intensity: Int, textPaint: Paint, modifier: Modifier) {
    Canvas(modifier) {
        val tau = phase * TAU
        val extent = min(size.width, size.height)
        val center = Offset(size.width * .52f, size.height * .34f)
        val gray = Color.White
        val base = when (intensity) {
            0 -> .17f
            1 -> .23f
            else -> .32f
        }
        val stroke = .70.dp.toPx()

        // Static elliptical geometry. GPU canvas rotates ellipses directly.
        for (ring in 0..1) {
            val rx = extent * (.21f + ring * .087f)
            val ry = extent * (.077f + ring * .033f)
            withTransform({
                rotate(
                    degrees = -24f + ring * 17f,
                    pivot = center
                )
            }) {
                drawOval(
                    color = gray.copy(alpha = base * (1f - ring * .24f)),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2, ry * 2),
                    style = Stroke(width = stroke)
                )
            }
        }
        drawCircle(
            color = gray.copy(alpha = .57f), radius = 2.1.dp.toPx(),
            center = center
        )
        drawCircle(
            color = gray.copy(alpha = .065f), radius = extent * .023f,
            center = center
        )

        val orbitAngle = tau
        drawCircle(
            color = gray.copy(alpha = .78f),
            radius = 2.65.dp.toPx(),
            center = Offset(
                center.x + extent * .205f * cos(orbitAngle),
                center.y + extent * .077f * sin(orbitAngle)
            )
        )
        Stars.forEachIndexed { i, (x, y) ->
            val flicker = if (intensity == 0) 0f
                else .10f * sin(tau * (i % 3 + 1) + i.toFloat())
            drawCircle(
                color = gray.copy(alpha = (base + .18f + flicker).coerceIn(.07f, .68f)),
                radius = if (i % 5 == 0) 1.25.dp.toPx() else .75.dp.toPx(),
                center = Offset(size.width * x, size.height * y)
            )
        }

        // Short and infrequent fly-by; it never overlaps touch targets.
        if (intensity > 0) {
            val fly = (phase - .14f) / .125f
            if (fly in 0f..1f) {
                val fade = sin(PI.toFloat() * fly).coerceAtLeast(0f)
                val head = Offset(
                    size.width * (-.12f + 1.24f * fly),
                    size.height * (.18f + .23f * fly)
                )
                drawLine(
                    color = gray.copy(alpha = fade * .27f),
                    start = Offset(head.x - extent * .12f, head.y + extent * .026f),
                    end = head,
                    strokeWidth = .8.dp.toPx()
                )
                drawCircle(
                    color = gray.copy(alpha = fade * .85f),
                    radius = 1.8.dp.toPx(), center = head
                )
            }

            val formula = (phase - .39f) / .16f
            if (formula in 0f..1f) {
                val visibility = sin(PI.toFloat() * formula).coerceAtLeast(0f)
                textPaint.textSize = 10.sp.toPx()
                textPaint.alpha = (visibility * if (intensity == 1) 70 else 108)
                    .toInt().coerceIn(0, 255)
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        "∇L(θ)   ·   λ = 0.001",
                        size.width * .16f, size.height * .56f, textPaint
                    )
                }
            }

            // A tiny changing graph, suggesting an ML model / constellation.
            val graph = (phase - .64f) / .18f
            if (graph in 0f..1f) {
                val visibility = sin(PI.toFloat() * graph).coerceAtLeast(0f)
                val a = base * visibility * .85f
                val nodes = listOf(
                    Offset(.25f, .55f), Offset(.32f, .49f), Offset(.38f, .60f),
                    Offset(.44f, .53f), Offset(.50f, .58f)
                )
                val edges = listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3, 3 to 4)
                edges.forEach { (from, to) ->
                    drawLine(
                        color = gray.copy(alpha = a * .65f),
                        start = Offset(size.width * nodes[from].x, size.height * nodes[from].y),
                        end = Offset(size.width * nodes[to].x, size.height * nodes[to].y),
                        strokeWidth = .65.dp.toPx()
                    )
                }
                nodes.forEach { node ->
                    drawCircle(
                        color = gray.copy(alpha = a * 1.4f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(size.width * node.x, size.height * node.y)
                    )
                }
            }
        }
    }
}
