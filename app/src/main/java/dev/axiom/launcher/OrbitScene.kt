package dev.axiom.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()
private val Dust = listOf(
    .14f to .11f, .68f to .18f, .80f to .35f, .21f to .54f,
    .70f to .61f, .40f to .19f, .09f to .39f, .91f to .71f,
    .35f to .69f, .58f to .07f, .77f to .83f, .23f to .86f
)

@Composable
fun OrbitScene(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    if (motionEnabled) {
        val clock = rememberInfiniteTransition(label = "slow orbital clock")
        val phase by clock.animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(tween(75000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbital phase"
        )
        DrawOrbits(phase, modifier)
    } else {
        DrawOrbits(0f, modifier)
    }
}

@Composable
private fun DrawOrbits(phase: Float, modifier: Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width * .5f, size.height * .40f)
        val pen = Color(0xFF8190A0)

        // Low-amplitude deterministic drift: no random jumps.
        val drift = .024f * sin(phase * 2f)
        for (ring in 0..2) {
            val rx = unit * (.19f + ring * .07f)
            val ry = unit * (.072f + ring * .023f)
            val tilt = -.38f + ring * .18f + drift
            val path = Path()
            for (i in 0..160) {
                val a = i.toFloat() / 160f * TAU
                val x = rx * cos(a)
                val y = ry * sin(a)
                val px = center.x + x * cos(tilt) - y * sin(tilt)
                val py = center.y + x * sin(tilt) + y * cos(tilt)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color = pen.copy(alpha = .36f - ring * .06f),
                style = Stroke(width = .85.dp.toPx()))
        }
        drawCircle(Color(0xFFDCE2EA).copy(alpha = .90f),
            radius = 3.7.dp.toPx(), center = center)
        drawCircle(pen.copy(alpha = .12f), radius = unit * .032f, center = center)

        val satelliteAngle = phase + .4f
        drawCircle(
            Color(0xFFE0E4EB), 2.8.dp.toPx(),
            Offset(center.x + unit * .19f * cos(satelliteAngle),
                center.y + unit * .072f * sin(satelliteAngle))
        )
        Dust.forEachIndexed { index, (x, y) ->
            val pulse = .66f + .15f * sin(phase * (index % 3 + 1) + index)
            drawCircle(
                pen.copy(alpha = pulse),
                radius = if (index % 4 == 0) 1.3.dp.toPx() else .8.dp.toPx(),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}
