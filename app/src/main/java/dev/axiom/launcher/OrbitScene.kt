package dev.axiom.launcher

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val WHITE = Color.White
private const val TAU = (2 * PI).toFloat()
private val ASPECT_STARS = Random(7).let { rng ->
    List(39) { Offset(.025f + rng.nextFloat() * .95f,
        .035f + rng.nextFloat() * .85f) }
}

/** One persistent n-body world, drawn in custom monochrome vector sprites. */
@Composable
internal fun CosmicEnvironment(
    world: CosmicWorld,
    interacting: Boolean,
    modifier: Modifier = Modifier
) {
    val sceneAlpha by animateFloatAsState(
        targetValue = if (interacting) .22f else 1f,
        animationSpec = tween(580),
        label = "ambient recession"
    )
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 27f
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
    }

    // Fixed-timestep 60Hz at home / 30Hz while user interacts with the UI.
    // We never allocate sprites or calculate forces during Canvas drawing.
    LaunchedEffect(world, interacting) {
        val interval = if (interacting) 33_333_333L else 16_666_666L
        var previous = withFrameNanos { it }
        var accumulated = 0L
        while (coroutineContext.isActive) {
            val now = withFrameNanos { it }
            accumulated = (accumulated + (now - previous).coerceIn(0L, 100_000_000L))
                .coerceAtMost(interval * 2L)
            previous = now
            if (accumulated >= interval) {
                accumulated -= interval
                world.step(if (interacting) 1f / 30f else 1f / 60f)
            }
        }
    }

    Canvas(modifier) {
        val bodies = world.bodies
        val time = world.elapsed
        val unit = min(size.width, size.height)

        ASPECT_STARS.forEachIndexed { i, p ->
            val flicker = .18f + .07f * sin(time * (.15f + i % 5 * .05f) + i)
            drawCircle(
                WHITE.copy(alpha = sceneAlpha * flicker.coerceIn(.04f, .27f)),
                radius = if (i % 7 == 0) 1.1.dp.toPx() else .65.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }

        // The "constellation": elastic visual connections between nearby masses.
        bodies.forEachIndexed { i, a ->
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                val distance = hypot(a.x - b.x, a.y - b.y)
                if (distance < .31f) {
                    drawLine(
                        WHITE.copy(alpha = (sceneAlpha * (.18f - distance * .42f))
                            .coerceIn(0f, .12f)),
                        start = Offset(a.x * size.width, a.y * size.height),
                        end = Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = .55.dp.toPx()
                    )
                }
            }
        }

        bodies.forEach { b ->
            val origin = Offset(b.x * size.width, b.y * size.height)
            val a = sceneAlpha * (
                if (b.lifetime - b.age < 8f) ((b.lifetime - b.age) / 8f)
                    .coerceIn(0f, 1f)
                else (b.age / 2.5f).coerceIn(.12f, 1f)
            )
            val r = (3.0f + b.mass * .62f).dp.toPx()
            when (b.kind) {
                0 -> {
                    // Gravitational nucleus: concentric accretion disks,
                    // angled orbital geometry and an event-horizon core.
                    withTransform({ rotate(time * 1.8f, origin) }) {
                        drawOval(WHITE.copy(alpha = a * .32f),
                            topLeft = origin - Offset(unit * .083f, unit * .026f),
                            size = Size(unit * .166f, unit * .052f),
                            style = Stroke(width = .72.dp.toPx()))
                        drawOval(WHITE.copy(alpha = a * .22f),
                            topLeft = origin - Offset(unit * .125f, unit * .045f),
                            size = Size(unit * .25f, unit * .09f),
                            style = Stroke(width = .68.dp.toPx()))
                    }
                    drawCircle(WHITE.copy(alpha = a * .10f), radius = r * 3.5f, center = origin)
                    drawCircle(WHITE.copy(alpha = a * .8f), radius = r, center = origin)
                    drawCircle(Color.Black, radius = r * .42f, center = origin)
                }
                1 -> {
                    // Comet with speed-sensitive tail.
                    val vel = Offset(b.vx, b.vy)
                    val length = (hypot(b.vx, b.vy) * unit * 1.0f)
                        .coerceIn(r * 3f, r * 13f)
                    val speed = max(hypot(b.vx, b.vy), .001f)
                    drawLine(WHITE.copy(alpha = a * .21f),
                        start = origin - vel / speed * length, end = origin,
                        strokeWidth = 1.2.dp.toPx())
                    drawCircle(WHITE.copy(alpha = a * .26f),
                        radius = r * 2.25f, center = origin)
                    drawCircle(WHITE.copy(alpha = a * .87f), radius = r * .67f, center = origin)
                }
                2 -> {
                    // Rotating crystal tetrahedron.
                    withTransform({ rotate(b.rotation * 47f, origin) }) {
                        val top = origin + Offset(0f, -r * 2f)
                        val left = origin + Offset(-r * 1.65f, r * 1.12f)
                        val right = origin + Offset(r * 1.65f, r * 1.12f)
                        val bottom = origin + Offset(0f, r * .35f)
                        listOf(top to left, left to right, right to top, top to bottom,
                            left to bottom, right to bottom).forEach { (p, q) ->
                            drawLine(WHITE.copy(alpha = a * .55f),
                                p, q, strokeWidth = .75.dp.toPx())
                        }
                    }
                }
                3 -> {
                    // Oscillating wave sprite: physics body carries a tiny glyph.
                    val path = Path()
                    for (i in 0..20) {
                        val x = (i / 20f - .5f) * r * 5f
                        val y = sin(i / 20f * TAU * 1.5f + b.rotation) * r * .58f
                        if (i == 0) path.moveTo(origin.x + x, origin.y + y)
                        else path.lineTo(origin.x + x, origin.y + y)
                    }
                    drawPath(path, WHITE.copy(alpha = a * .62f),
                        style = Stroke(width = .8.dp.toPx()))
                }
                4 -> {
                    // Binary moon orbit and central point.
                    val phase = time * .37f + b.rotation
                    val moon = origin + Offset(cos(phase) * r * 2.8f,
                        sin(phase) * r * 1.35f)
                    drawOval(WHITE.copy(alpha = a * .33f),
                        topLeft = origin - Offset(r * 2.8f, r * 1.35f),
                        size = Size(r * 5.6f, r * 2.7f),
                        style = Stroke(.7.dp.toPx()))
                    drawCircle(WHITE.copy(alpha = a * .77f), radius = r * .55f, center = origin)
                    drawCircle(WHITE.copy(alpha = a * .9f), radius = r * .26f, center = moon)
                }
                else -> {
                    // Oscillating four-point "neuron" sprite.
                    val offsets = listOf(
                        Offset(-r * 2f, 0f), Offset(0f, -r * 2f),
                        Offset(r * 2f, 0f), Offset(0f, r * 2f)
                    )
                    offsets.forEachIndexed { i, off ->
                        val p = origin + off
                        drawLine(WHITE.copy(alpha = a * .27f), origin, p,
                            strokeWidth = .75.dp.toPx())
                        drawCircle(WHITE.copy(alpha = a * (.5f + .3f *
                            sin(time + i).coerceIn(0f, 1f))),
                            radius = r * .38f, center = p)
                    }
                    drawCircle(WHITE.copy(alpha = a * .78f),
                        radius = r * .62f, center = origin)
                }
            }
        }

        // Actual formulas become transient annotations of interacting bodies.
        if (bodies.isNotEmpty()) {
            val active = bodies[(time / 7f).toInt().coerceAtLeast(0) % bodies.size]
            val phase = (time % 7f) / 7f
            val fade = (sin(phase * PI.toFloat()).coerceAtLeast(0f) * .29f * sceneAlpha)
            if (fade > .015f) {
                val phrase = when (active.kind) {
                    0 -> "F = Gm₁m₂/r²"
                    1 -> "a = Δv / Δt"
                    2 -> "ψ(x) = Σwᵢxᵢ"
                    3 -> "Σ aₙsin(nωt)"
                    4 -> "r = r₀ + δr"
                    else -> "∇L(θ) → 0"
                }
                labelPaint.textSize = 10.sp.toPx()
                labelPaint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
                val x = (active.x * size.width + 13.dp.toPx())
                    .coerceIn(12.dp.toPx(), size.width * .69f)
                val y = (active.y * size.height - 17.dp.toPx())
                    .coerceIn(24.dp.toPx(), size.height * .83f)
                drawIntoCanvas { it.nativeCanvas.drawText(phrase, x, y, labelPaint) }
            }
        }
        if (world.interactionSignal > .01f) {
            // Local echo of a tap, tied to real momentum impulse rather than random flash.
            val nucleus = bodies.firstOrNull()
            if (nucleus != null) {
                drawCircle(
                    WHITE.copy(alpha = world.interactionSignal * .10f * sceneAlpha),
                    radius = unit * (.05f + .14f * (1f - world.interactionSignal)),
                    center = Offset(nucleus.x * size.width, nucleus.y * size.height),
                    style = Stroke(1.dp.toPx())
                )
            }
        }
    }
}
