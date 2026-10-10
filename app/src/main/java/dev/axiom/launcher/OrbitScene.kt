package dev.axiom.launcher

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()
private val WHITE = Color.White
private val STARFIELD = listOf(
    .12f to .12f, .70f to .20f, .32f to .16f, .85f to .30f,
    .20f to .37f, .61f to .52f, .10f to .62f, .89f to .59f,
    .50f to .08f, .08f to .48f, .93f to .70f, .31f to .72f,
    .58f to .83f, .88f to .12f, .42f to .60f, .73f to .74f,
    .16f to .81f, .47f to .30f, .79f to .44f, .54f to .68f
)

private val NETWORK = listOf(
    .24f to .31f, .25f to .48f, .24f to .64f,
    .44f to .24f, .44f to .40f, .44f to .56f, .44f to .72f,
    .66f to .34f, .66f to .52f, .66f to .68f,
    .80f to .50f
)
private val EDGES = listOf(
    0 to 3, 0 to 4, 1 to 4, 1 to 5, 2 to 5, 2 to 6,
    3 to 7, 4 to 7, 4 to 8, 5 to 8, 5 to 9, 6 to 9,
    7 to 10, 8 to 10, 9 to 10
)

private fun wave01(value: Float) = (sin(value) + 1f) * .5f
private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun pulse(value: Float, from: Float, to: Float): Float {
    if (value < from || value > to) return 0f
    return sin(PI.toFloat() * (value - from) / (to - from)).coerceAtLeast(0f)
}

/** Precalculate the Lorenz trajectory exactly once, not on every frame. */
private fun lorenzPoints(): List<Pair<Float, Float>> {
    var x = .1
    var y = 0.0
    var z = .1
    val points = ArrayList<Pair<Float, Float>>(480)
    repeat(3400) { step ->
        val delta = .006
        val dx = 10.0 * (y - x)
        val dy = x * (28.0 - z) - y
        val dz = x * y - (8.0 / 3.0) * z
        x += dx * delta
        y += dy * delta
        z += dz * delta
        if (step > 600 && step % 7 == 0) {
            points.add((x / 25.0).toFloat().coerceIn(-1f, 1f) to
                ((z - 26.0) / 28.0).toFloat().coerceIn(-1f, 1f))
        }
    }
    return points
}

/**
 * 4 restrained mathematical environments:
 * 0 orbit mechanics, 1 Lorenz strange attractor, 2 neural graph, 3 harmonics.
 * The auto preset blends between them every 44s with smooth crossfades.
 * Spatial wander is smooth noise (sums of sines), never random jitter.
 * Geometry is strictly monochrome, and UI controls remain fixed.
 */
@Composable
fun CosmicEnvironment(
    intensity: Int,
    preset: String,
    entropy: Boolean,
    interacting: Boolean,
    modifier: Modifier = Modifier
) {
    val lorenz = remember { lorenzPoints() }
    val formulaPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            color = android.graphics.Color.WHITE
            textSize = 28f
        }
    }
    val sceneDim by animateFloatAsState(
        targetValue = if (interacting) .19f else 1f,
        animationSpec = tween(durationMillis = 610),
        label = "environment recedes when interacting"
    )
    val transition = rememberInfiniteTransition(label = "celestial chronology")
    val position by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (intensity >= 2) 156_000 else 192_000,
                easing = LinearEasing)
        ),
        label = "global scene progression"
    )
    val time = if (intensity == 0) 0f else position
    Canvas(modifier) {
        val alpha = sceneDim * if (intensity == 0) .42f
            else if (intensity == 1) .75f else 1f

        // Quiet stationary stars are independent of the four moving structures.
        STARFIELD.forEachIndexed { index, (x, y) ->
            val wander = if (entropy && intensity > 0) {
                .0025f * sin(time * TAU * (index % 4 + 1) + index)
            } else 0f
            val shimmer = if (intensity > 0) {
                .12f * sin(time * TAU * (index % 3 + 1) + index)
            } else 0f
            drawCircle(
                color = WHITE.copy(alpha = ((.15f + shimmer) * alpha)
                    .coerceIn(0f, .32f)),
                radius = if (index % 5 == 0) 1.1.dp.toPx() else .65.dp.toPx(),
                center = Offset(size.width * (x + wander), size.height * (y + wander))
            )
        }

        val preferred = when (preset) {
            "ORBIT" -> 0
            "ATTRACTOR" -> 1
            "NEURAL" -> 2
            "WAVE" -> 3
            else -> -1
        }
        if (preferred >= 0 || intensity == 0) {
            drawStructure(
                index = if (preferred == -1) 0 else preferred,
                phase = time,
                alpha = alpha,
                entropy = entropy,
                lorenz = lorenz,
                text = formulaPaint
            )
        } else {
            val progress = time * 4f
            val index = floor(progress).toInt().coerceIn(0, 3)
            val within = progress - index
            val blend = smoothstep((within - .83f) / .17f)
            drawStructure(index, time, alpha * (1f - blend),
                entropy, lorenz, formulaPaint)
            if (blend > .001f) {
                drawStructure((index + 1) % 4, time, alpha * blend,
                    entropy, lorenz, formulaPaint)
            }
        }
    }
}

private fun DrawScope.drawStructure(
    index: Int, phase: Float, alpha: Float,
    entropy: Boolean,
    lorenz: List<Pair<Float, Float>>,
    text: Paint
) {
    if (alpha <= .001f) return
    when (index) {
        0 -> orbitSystem(phase, alpha, entropy, text)
        1 -> lorenzSystem(phase, alpha, entropy, lorenz, text)
        2 -> neuralSystem(phase, alpha, entropy, text)
        else -> fourierSystem(phase, alpha, entropy, text)
    }
}

private fun DrawScope.orbitSystem(
    t: Float, alpha: Float, entropy: Boolean, paint: Paint
) {
    val unit = min(size.width, size.height)
    val wander = if (entropy) sin(t * TAU * 2f) * unit * .022f else 0f
    val center = Offset(size.width * .5f + wander, size.height * .36f)
    val orbitRotation = -24f + 8f * sin(t * TAU)
    for (i in 0..3) {
        val rx = unit * (.20f + i * .067f)
        val ry = rx * (.33f + i * .045f)
        withTransform({ rotate(orbitRotation + i * 13f, pivot = center) }) {
            drawOval(
                WHITE.copy(alpha = alpha * (.28f - i * .035f)),
                topLeft = Offset(center.x - rx, center.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = .85.dp.toPx())
            )
        }
    }
    val angle = t * TAU * 3f
    for (n in 0..2) {
        val r = unit * (.20f + n * .067f)
        val a = angle * (if (n % 2 == 0) 1f else -.75f) + n * 2f
        val x = center.x + r * cos(a)
        val y = center.y + r * .38f * sin(a)
        drawCircle(WHITE.copy(alpha = alpha * (.57f + n * .1f)),
            radius = (1.6f + n * .38f).dp.toPx(), center = Offset(x, y))
    }
    drawCircle(WHITE.copy(alpha = alpha * .80f), radius = 3.3.dp.toPx(), center)
    drawCircle(WHITE.copy(alpha = alpha * .09f), radius = unit * .028f, center)
    drawFormula("r(θ)  /  ORBITAL MECHANICS",
        Offset(size.width * .18f, size.height * .64f),
        alpha * .20f, paint)
}

private fun DrawScope.lorenzSystem(
    t: Float, alpha: Float, entropy: Boolean,
    points: List<Pair<Float, Float>>, paint: Paint
) {
    val unit = min(size.width, size.height)
    val center = Offset(size.width * .5f, size.height * .39f)
    val drift = if (entropy) sin(t * TAU * 2f) * unit * .016f else 0f
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        val px = center.x + x * unit * .34f + drift
        val py = center.y + y * unit * .205f
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, WHITE.copy(alpha = alpha * .39f),
        style = Stroke(width = .85.dp.toPx()))
    val point = points[(t * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)]
    drawCircle(WHITE.copy(alpha = alpha * .84f),
        radius = 2.dp.toPx(),
        center = Offset(center.x + point.first * unit * .34f + drift,
            center.y + point.second * unit * .205f))
    drawFormula("dx/dt = σ(y−x)  /  CHAOS",
        Offset(size.width * .16f, size.height * .67f),
        alpha * .27f, paint)
}

private fun DrawScope.neuralSystem(
    t: Float, alpha: Float, entropy: Boolean, paint: Paint
) {
    val wander = if (entropy) sin(t * TAU) * .011f else 0f
    val nodes = NETWORK.mapIndexed { index, pair ->
        Offset(size.width * (pair.first + wander),
            size.height * (pair.second +
                if (entropy) .003f * sin(t * TAU * 3f + index) else 0f))
    }
    EDGES.forEachIndexed { i, (from, to) ->
        val a = nodes[from]
        val b = nodes[to]
        drawLine(WHITE.copy(alpha = alpha * .20f),
            start = a, end = b, strokeWidth = .7.dp.toPx())
        if (i % 4 == 0) {
            val progress = (t * 6f + i * .22f) % 1f
            drawCircle(WHITE.copy(alpha = alpha * .72f),
                radius = 1.6.dp.toPx(),
                center = Offset(
                    a.x + (b.x - a.x) * progress,
                    a.y + (b.y - a.y) * progress
                ))
        }
    }
    nodes.forEachIndexed { i, p ->
        val shimmer = .70f + .28f * wave01(t * TAU * 4f + i)
        drawCircle(WHITE.copy(alpha = alpha * .66f * shimmer),
            radius = if (i < 3) 2.2.dp.toPx() else 2.8.dp.toPx(), center = p)
    }
    drawFormula("∇L(θ)   λ = 0.001",
        Offset(size.width * .24f, size.height * .79f),
        alpha * .32f, paint)
}

private fun DrawScope.fourierSystem(
    t: Float, alpha: Float, entropy: Boolean, paint: Paint
) {
    val unit = min(size.width, size.height)
    val phase = t * TAU * 2f
    val centerY = size.height * .41f
    for (wave in 0 until 3) {
        val path = Path()
        val amplitude = unit * (.035f + wave * .028f)
        val start = size.width * .1f
        val width = size.width * .8f
        for (i in 0..90) {
            val f = i / 90f
            val x = start + width * f
            val envelope = sin(f * PI.toFloat()).coerceAtLeast(0f)
            val harmonic = sin((f * (2.5f + wave) * TAU) + phase +
                wave * 1.6f) + .35f * sin(f * TAU * 7f - phase * 1.7f)
            val y = centerY + (wave - 1) * unit * .09f +
                amplitude * harmonic * envelope
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path,
            WHITE.copy(alpha = alpha * (.16f + wave * .065f)),
            style = Stroke(width = .85.dp.toPx()))
    }
    drawCircle(WHITE.copy(alpha = alpha * .42f),
        radius = 2.dp.toPx(),
        center = Offset(
            size.width * (.15f + .70f * wave01(phase * .15f)),
            centerY + unit * .04f * sin(phase)
        ))
    drawFormula("Σ aₙ sin(nωt + φₙ)  /  FOURIER",
        Offset(size.width * .16f, size.height * .70f),
        alpha * .25f, paint)
}

private fun DrawScope.drawFormula(value: String, at: Offset,
                                   alpha: Float, paint: Paint) {
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    paint.textSize = 10.sp.toPx()
    drawIntoCanvas { it.nativeCanvas.drawText(value, at.x, at.y, paint) }
}
