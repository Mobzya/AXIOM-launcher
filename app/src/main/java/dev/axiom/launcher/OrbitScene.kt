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
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val White = Color.White
private const val TAU = (2.0 * PI).toFloat()
private data class DeepStar(val x: Float, val y: Float, val z: Float, val radius: Float)
private val starLayers = Random(824).let { rng ->
    List(76) {
        DeepStar(rng.nextFloat(), rng.nextFloat(), .6f + rng.nextFloat() * 3.5f,
            .5f + rng.nextFloat() * .65f)
    }
}
private val neuralNodes = listOf(
    Offset(.10f,.28f), Offset(.10f,.52f), Offset(.10f,.75f),
    Offset(.38f,.14f), Offset(.38f,.38f), Offset(.38f,.61f), Offset(.38f,.84f),
    Offset(.67f,.27f), Offset(.67f,.50f), Offset(.67f,.75f),
    Offset(.91f,.49f)
)
private val neuralEdges = listOf(
    0 to 3, 0 to 4, 1 to 4, 1 to 5, 2 to 5, 2 to 6,
    3 to 7, 4 to 7, 4 to 8, 5 to 8, 5 to 9, 6 to 9,
    7 to 10, 8 to 10, 9 to 10
)

@Composable
internal fun CosmicEnvironment(
    world: CosmicWorld,
    interacting: Boolean,
    modifier: Modifier = Modifier
) {
    val dim by animateFloatAsState(
        targetValue = if (interacting) .23f else 1f,
        animationSpec = tween(560), label = "quiet space"
    )
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
    }
    // Fixed stepping, independent of UI animations and drawing.
    LaunchedEffect(world, interacting) {
        val targetNanos = if (interacting) 33_333_333L else 16_666_667L
        var previous = withFrameNanos { it }
        var accumulator = 0L
        while (coroutineContext.isActive) {
            val now = withFrameNanos { it }
            accumulator = (accumulator + (now - previous).coerceIn(0L, 70_000_000L))
                .coerceAtMost(targetNanos * 2L)
            previous = now
            if (accumulator >= targetNanos) {
                accumulator -= targetNanos
                world.step(if (interacting) 1f / 30f else 1f / 60f)
            }
        }
    }

    Canvas(modifier) {
        val t = world.elapsed
        val unit = min(size.width, size.height)
        val perspective = world.depthShift

        // 3-layer star volume. Greater z means further / dimmer / slower motion.
        starLayers.forEachIndexed { i, star ->
            val z = star.z
            val px = ((star.x + .005f * t / z + perspective * .027f / z) % 1.04f)
            val py = star.y + .009f * sin(t * .055f + i) / z
            val shimmer = .82f + .18f * sin(t * (.18f + i % 3 * .025f) + i)
            val opacity = ((.17f + .24f / z) * shimmer * dim).coerceIn(0f, .54f)
            drawCircle(White.copy(alpha = opacity),
                radius = star.radius.dp.toPx() / (z * .55f).coerceAtLeast(.8f),
                center = Offset(px * size.width, py * size.height))
        }

        // A few vector-depth "dust" planes, not blue gradients or fullscreen blur.
        for (layer in 1..3) {
            val z = layer.toFloat() + .7f
            val x = (.24f + .28f * layer + perspective * .025f / z) * size.width
            val y = (.20f + .16f * (layer % 3)) * size.height
            drawCircle(White.copy(alpha = .012f * dim),
                radius = unit * (.16f / z),
                center = Offset(x, y),
                style = Stroke(.7.dp.toPx()))
        }

        val scene = world.episode
        world.bodies.sortedByDescending { it.depth }.forEach { b ->
            val factor = (1f / b.depth).coerceIn(.38f, 1.45f)
            val fadeIn = (b.age / 1.8f).coerceIn(0f, 1f)
            val fadeOut = ((b.lifetime - b.age) / 3f).coerceIn(0f, 1f)
            val opacity = dim * (.20f + .57f * factor) * fadeIn * fadeOut
            val origin = Offset(
                (b.x + perspective * .026f * factor) * size.width,
                b.y * size.height)
            drawBody(b, origin, unit * factor, opacity, t)
        }

        // Temporally coherent procedural events, blending with ordinary space.
        if (scene.kind != CosmicEventKind.QUIET && scene.alpha > .005f) {
            drawEpisode(scene, t, dim, unit, paint)
        }

        world.ripples.forEach { ripple ->
            val life = (1f - ripple.age / 1.7f).coerceIn(0f, 1f)
            drawCircle(
                White.copy(alpha = life * life * dim * .27f),
                radius = unit * (.015f + .22f * (1f - life)),
                center = Offset(ripple.x * size.width, ripple.y * size.height),
                style = Stroke(1.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawBody(
    b: CelestialBody, c: Offset, unit: Float, a: Float, time: Float
) {
    if (a < .002f) return
    val radius = (unit * (.0055f + b.mass * .0014f)).coerceIn(1.3.dp.toPx(), 7.dp.toPx())
    when (b.kind) {
        1 -> { // Comet: perspective-scaled velocity trail and a thin shock halo.
            val v = Offset(b.vx, b.vy)
            val speed = max(hypot(v.x, v.y), .01f)
            val length = (speed * unit * .68f).coerceIn(3f * radius, 18f * radius)
            drawLine(White.copy(alpha = a * .28f),
                c - v / speed * length, c, .75.dp.toPx())
            drawLine(White.copy(alpha = a * .10f),
                c - v / speed * length * 1.55f, c, 2.dp.toPx())
            drawCircle(White.copy(alpha = a * .16f), radius * 2.9f, c)
            drawCircle(White.copy(alpha = a), radius * .62f, c)
        }
        2 -> { // Wireframe asteroid, not a UI icon.
            withTransform({ rotate(b.rotation * 32f, c) }) {
                val p = Path().apply {
                    moveTo(c.x - radius * 2f, c.y)
                    lineTo(c.x - radius * .2f, c.y - radius * 2.1f)
                    lineTo(c.x + radius * 1.7f, c.y - radius * .5f)
                    lineTo(c.x + radius * 1.0f, c.y + radius * 1.4f)
                    close()
                }
                drawPath(p, White.copy(alpha = a * .67f),
                    style = Stroke(.7.dp.toPx()))
                drawLine(White.copy(alpha = a * .28f),
                    c + Offset(-radius * 2f,0f),
                    c + Offset(radius * 1.0f,radius * 1.4f),
                    .6.dp.toPx())
            }
        }
        3 -> { // Orbiting satellite and distant faint elliptical ring.
            drawOval(White.copy(alpha = a * .32f),
                topLeft = c - Offset(radius * 3.0f, radius * 1.1f),
                size = Size(radius * 6f, radius * 2.2f),
                style = Stroke(.75.dp.toPx()))
            val pos = time * (.22f + .09f / b.depth) + b.rotation
            drawCircle(White.copy(alpha = a),
                radius = radius * .37f,
                center = c + Offset(cos(pos) * radius * 3f,
                    sin(pos) * radius * 1.1f))
            drawCircle(White.copy(alpha = a * .65f),
                radius = radius * .65f, center = c)
        }
        4 -> { // Four-signal neural microcluster.
            val points = listOf(Offset(-2f,-.8f), Offset(.7f,-2f),
                Offset(2f,.7f), Offset(-.4f,2f))
            points.forEachIndexed { i, p ->
                val end = c + p * radius
                drawLine(White.copy(alpha = a * .30f), c, end, .65.dp.toPx())
                drawCircle(White.copy(alpha = a * (.48f + .18f * sin(time + i))),
                    radius * .26f, end)
            }
            drawCircle(White.copy(alpha = a * .70f), radius * .43f, c)
        }
        else -> { // Slowly rotating mathematical glyph, a travelling sine fragment.
            val path = Path()
            for (i in 0..19) {
                val xx = (i / 19f - .5f) * radius * 5f
                val yy = sin(i / 19f * 2.5f * TAU + b.rotation) * radius * .7f
                if (i == 0) path.moveTo(c.x + xx,c.y + yy)
                else path.lineTo(c.x + xx,c.y + yy)
            }
            drawPath(path, White.copy(alpha = a * .72f),
                style = Stroke(.7.dp.toPx()))
        }
    }
}

private fun DrawScope.drawEpisode(
    ep: CosmicEpisode, t: Float, dim: Float, unit: Float, paint: Paint
) {
    val a = ep.alpha * dim
    val center = Offset(ep.x * size.width, ep.y * size.height)
    when (ep.kind) {
        CosmicEventKind.METEORS -> {
            // A transient meteor window: trails are the actual moving corner entrants.
            // A sparse constellation triangulates their travel corridor.
            for (i in 0..3) {
                val f = ((ep.age * .15f + i * .22f) % 1f)
                val x = (-.17f + f * 1.36f) * size.width
                val y = (ep.y + (f - .5f) * .18f + i * .02f) * size.height
                val c = Offset(x,y)
                drawLine(White.copy(alpha = .14f * a),
                    c - Offset(unit * .1f, unit * .025f),
                    c, .7.dp.toPx())
            }
            formula("v = dr/dt   // TRANSIT", Offset(size.width * .16f,
                size.height * .70f), a * .31f, paint)
        }
        CosmicEventKind.NEURAL -> {
            val width = unit * .72f
            val height = unit * .61f
            val topLeft = center - Offset(width * .5f,height * .52f)
            val positions = neuralNodes.mapIndexed { i, pos ->
                topLeft + Offset(pos.x * width,
                    (pos.y + .015f * sin(t * .39f + i)) * height)
            }
            neuralEdges.forEachIndexed { i, (left, right) ->
                val from = positions[left]
                val to = positions[right]
                drawLine(White.copy(alpha = .15f * a), from, to, .7.dp.toPx())
                val u = (ep.age * (.16f + .012f * (i % 3)) +
                    (i * .13f)) % 1f
                drawCircle(White.copy(alpha = .64f * a),
                    1.7.dp.toPx(),
                    from + (to - from) * u)
            }
            positions.forEachIndexed { i, point ->
                drawCircle(White.copy(alpha = (.40f + .25f *
                    sin(ep.age * .45f + i)) * a),
                    if (i % 3 == 0) 2.5.dp.toPx() else 1.6.dp.toPx(), point)
            }
            formula("∇L(θ) = 0    ∂y/∂x", Offset(size.width * .23f,
                size.height * .75f), a * .34f, paint)
        }
        CosmicEventKind.HARMONICS -> {
            // Coherent constructive and destructive interference, not drifting text.
            for (wave in 0..3) {
                val path = Path()
                for (i in 0..90) {
                    val x = i / 90f
                    val envelope = sin(PI.toFloat() * x)
                    val harmonic = sin(TAU * x * (2f + wave * .7f) -
                        ep.age * (.46f + wave * .07f))
                    val secondary = .32f * sin(TAU * x * (5f + wave) +
                        ep.age * .23f)
                    val xx = size.width * (.08f + x * .84f)
                    val yy = center.y + (wave - 1.5f) * unit * .065f +
                        (harmonic + secondary) * unit * .035f * envelope
                    if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                drawPath(path, White.copy(alpha = a * (.12f + wave * .07f)),
                    style = Stroke(.85.dp.toPx()))
            }
            formula("Σ aₙ sin(nωt + φₙ)", Offset(size.width * .20f,
                size.height * .69f), a * .35f, paint)
        }
        CosmicEventKind.LENS -> {
            // Momentary curved spacetime grid, with trajectories deflected by lens.
            for (row in -3..3) {
                val path = Path()
                for (step in 0..32) {
                    val xx = center.x + (step / 32f - .5f) * unit * .84f
                    val yy0 = center.y + row * unit * .065f
                    val dx = (xx - center.x) / unit
                    val influence = .014f / (dx * dx + row * row * .01f + .09f)
                    val yy = yy0 + unit * influence * (if (row < 0) -1f else 1f)
                    if (step == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                drawPath(path, White.copy(alpha = a * .085f),
                    style = Stroke(.55.dp.toPx()))
            }
            for (ring in 0..2) {
                val radius = unit * (.064f + ring * .041f)
                drawCircle(White.copy(alpha = a * (.29f - ring * .06f)),
                    radius, center, style = Stroke(.8.dp.toPx()))
            }
            formula("κ = (1/2) ∇² ψ", Offset(size.width * .22f,
                size.height * .77f), a * .34f, paint)
        }
        CosmicEventKind.SINGULARITY -> {
            // Extremely rare gravitational lensing: dark center, light photon ring,
            // two accretion ellipses and spiralling polar tracer particles.
            val radius = unit * (.037f + .004f * sin(ep.age * .36f))
            drawCircle(White.copy(alpha = .035f * a),
                radius * 3.8f, center)
            withTransform({ rotate(ep.age * 6f, center) }) {
                drawOval(White.copy(alpha = a * .35f),
                    topLeft = center - Offset(radius * 3.9f, radius * 1.2f),
                    size = Size(radius * 7.8f,radius * 2.4f),
                    style = Stroke(.9.dp.toPx()))
                drawOval(White.copy(alpha = a * .16f),
                    topLeft = center - Offset(radius * 5.2f,radius * 1.65f),
                    size = Size(radius * 10.4f,radius * 3.3f),
                    style = Stroke(.65.dp.toPx()))
            }
            for (i in 0..17) {
                val theta = i * .62f + ep.age * (.12f + (i % 4) * .045f)
                val r = radius * (1.72f + .30f * sin(ep.age * .15f + i))
                val p = center + Offset(cos(theta) * r, sin(theta) * r * .70f)
                drawCircle(White.copy(alpha = a * (.18f + .22f *
                    max(0f, sin(theta)))),
                    if (i % 4 == 0) 1.7.dp.toPx() else .95.dp.toPx(), p)
            }
            drawCircle(Color.Black, radius, center)
            drawCircle(White.copy(alpha = a * .82f),
                radius * 1.08f, center, style = Stroke(1.1.dp.toPx()))
            drawCircle(Color.Black, radius * .97f, center)
            formula("rₛ = 2GM/c²   // EVENT HORIZON",
                Offset(size.width * .13f,size.height * .74f),
                a * .39f, paint)
        }
        else -> Unit
    }
}

private fun DrawScope.formula(text: String, point: Offset, a: Float, paint: Paint) {
    if (a < .009f) return
    paint.alpha = (a.coerceIn(0f,1f) * 255f).toInt()
    paint.textSize = 10.sp.toPx()
    drawIntoCanvas {
        it.nativeCanvas.drawText(text, point.x, point.y, paint)
    }
}
