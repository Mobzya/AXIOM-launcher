package dev.axiom.launcher

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val colorsByMaterial = MatterMaterial.entries
private val skyCyan = Color(0xff71bee2)
private val skyViolet = Color(0xffad8ad8)
private val skyAmber = Color(0xfff6a478)
private data class SkyStar(val x: Float, val y: Float, val brightness: Float)
private val distantStars = with(Random(824)) {
    List(120) { SkyStar(nextFloat(), nextFloat(), .10f + nextFloat() * .31f) }
}

@Composable
internal fun CosmicEnvironment(
    world: CosmicWorld,
    running: Boolean,
    lifecycle: Lifecycle,
    searching: Boolean,
    simulationDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier
) {
    val dim by animateFloatAsState(if (searching) .26f else 1f, tween(300), label = "sky intensity")
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val battery = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager }
    val light = AxiomColors.palette.light
    val playing = world.timeScale > 0f
    val formulaPaint = remember(light) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            color = if (light) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val haloPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val groupCount = colorsByMaterial.size * 2
    val counts = remember { IntArray(groupCount) }
    val offsets = remember { IntArray(groupCount) }
    val cursors = remember { IntArray(groupCount) }
    val capacity = MatterMode.DETAILED.maxParticles
    val points = remember { FloatArray(capacity * 2) }
    val packedPoints = remember { FloatArray(capacity * 2) }
    val groups = remember { IntArray(capacity) }
    val nativePaints = remember(light) {
        Array(groupCount) { index ->
            val c = colorsByMaterial[index / 2].argb
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (light) android.graphics.Color.rgb(
                    (android.graphics.Color.red(c) * .55f).toInt(),
                    (android.graphics.Color.green(c) * .55f).toInt(),
                    (android.graphics.Color.blue(c) * .55f).toInt()
                ) else c
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        }
    }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(world, running, playing, lifecycle) {
        if (!running || !playing) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val saver = power.isPowerSaveMode ||
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0..15
            launch(simulationDispatcher) {
                var averageCost = 0.0
                var highLoad = false
                while (isActive) {
                    val started = System.nanoTime()
                    val period = if (saver || world.particleCount >= 1_650 || highLoad)
                        33_333_333L else 16_666_667L
                    world.step(period / 1_000_000_000f)
                    val cost = System.nanoTime() - started
                    averageCost = if (averageCost == 0.0) cost.toDouble() else .92 * averageCost + .08 * cost
                    highLoad = if (highLoad) averageCost > 6_000_000 else averageCost > 11_000_000
                    delay(((period - cost + 500_000L) / 1_000_000L).coerceAtLeast(1L))
                }
            }
            var lastFrame = 0L
            var shown: GrainRenderPair? = null
            while (isActive) {
                val now = withFrameNanos { it }
                if (saver && now - lastFrame < 15_500_000L) continue
                val current = world.grainFrames
                if (current !== shown || current.blendAt(lastFrame) < 1f) {
                    frameNanos = now
                    lastFrame = now
                    shown = current
                }
            }
        }
    }

    val sky = world.preset.sky
    val background = AX_BLACK
    Box(modifier) {
        Box(Modifier.matchParentSize().drawWithCache {
            val (left, right) = when (sky) {
                SkyProfile.STARS -> skyCyan to skyViolet
                SkyProfile.DUST -> skyAmber to skyViolet
                SkyProfile.NEBULA -> skyViolet to skyCyan
                SkyProfile.GALAXY -> skyAmber to skyCyan
                SkyProfile.ANOMALY -> skyViolet to Color(0xff7a91ff)
            }
            val opacity = if (light) .24f else if (sky == SkyProfile.STARS) .16f else .31f
            val first = Brush.radialGradient(
                listOf(left.copy(alpha = opacity), left.copy(alpha = opacity * .32f), Color.Transparent),
                center = Offset(size.width * .17f, size.height * .28f), radius = size.maxDimension * .52f
            )
            val second = Brush.radialGradient(
                listOf(right.copy(alpha = opacity * .8f), right.copy(alpha = opacity * .22f), Color.Transparent),
                center = Offset(size.width * .81f, size.height * .73f), radius = size.maxDimension * .54f
            )
            val shade = Brush.verticalGradient(listOf(
                background.copy(alpha = .45f), Color.Transparent, background.copy(alpha = .67f)
            ))
            onDrawBehind {
                drawRect(first)
                drawRect(second)
                drawRect(shade)
            }
        })
        Canvas(Modifier.matchParentSize()) {
            val brightness = dim
            val depth = world.depthShift
            val starInk = if (light) Color(0xff344765) else Color(0xffd5eaff)
            for (star in distantStars) {
                drawCircle(starInk, (.4f + star.brightness) * density,
                    Offset((star.x + depth * star.brightness * .19f) * size.width,
                        star.y * size.height), alpha = star.brightness * brightness)
            }
            val episode = world.episode
            val materialFrame = world.grainFrames
            val frame = materialFrame.current
            val previous = materialFrame.previous
            val blend = if (running && playing) materialFrame.blendAt(frameNanos) else 1f
            val width = size.width
            val height = size.height

            if (!searching && episode.kind == CosmicEventKind.QUIET && world.mathMoment.active) {
                drawMathMoment(world.mathMoment, brightness * .63f, formulaPaint)
            }
            if (!searching) {
                drawPhenomenon(episode, brightness)
                for (flash in world.impactFlashes) {
                    val alpha = (1f - flash.age / 1.25f) * flash.energy
                    val radius = .014f + flash.age * (.09f + .12f * flash.energy)
                    val point = Offset(flash.x, flash.y)
                    drawSkyRing(point, radius, skyAmber, alpha * .83f, 1.65f)
                    drawSkyRing(point, radius * .67f, skyViolet, alpha * .40f, .9f)
                    if (flash.age < .24f) {
                        drawCircle(skyAmber, (6f + flash.age * 38f) * density,
                            Offset(flash.x * width, flash.y * height),
                            alpha = (1f - flash.age / .24f) * .78f)
                    }
                }
                for (ripple in world.ripples) {
                    drawSkyRing(Offset(ripple.x, ripple.y), .012f + ripple.age * .23f, skyCyan,
                        (1f - ripple.age / .8f) * .58f, 1.1f)
                }
            }
            java.util.Arrays.fill(counts, 0)
            var i = 0
            var visible = 0
            while (i < frame.size && visible < capacity) {
                val x = previous[i] + (frame[i] - previous[i]) * blend
                val y = previous[i + 1] + (frame[i + 1] - previous[i + 1]) * blend
                if (x in -.02f..1.02f && y in -.02f..1.02f) {
                    val k = frame[i + 3].toInt() * 2 + if (frame[i + 2] > 1.06f) 1 else 0
                    points[visible * 2] = x * width
                    points[visible * 2 + 1] = y * height
                    groups[visible] = k
                    counts[k] += 2
                    visible++
                }
                i += 4
            }
            var position = 0
            for (k in counts.indices) {
                offsets[k] = position
                cursors[k] = position
                position += counts[k]
            }
            for (point in 0 until visible) {
                val group = groups[point]
                val at = cursors[group]
                packedPoints[at] = points[point * 2]
                packedPoints[at + 1] = points[point * 2 + 1]
                cursors[group] = at + 2
            }
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (!searching) for (body in world.bodies) {
                    if (body.stellarClass == StellarClass.PLANET) continue
                    val radius = structureRadius(body) * size.minDimension
                    haloPaint.color = when {
                        body.stellarClass == StellarClass.CLOUD -> 0xff76b9e9.toInt()
                        body.stellarClass == StellarClass.QUASAR -> 0xffaa8cf1.toInt()
                        body.temperature >= 6400f -> 0xff91bcff.toInt()
                        else -> 0xffffbb71.toInt()
                    }
                    haloPaint.alpha = if (light) 13 else 19
                    native.drawCircle(body.x * width, body.y * height, radius * 2.4f, haloPaint)
                    haloPaint.alpha = if (light) 23 else 30
                    native.drawCircle(body.x * width, body.y * height, radius * 1.43f, haloPaint)
                }
                for (k in counts.indices) {
                    if (counts[k] == 0) continue
                    val gas = colorsByMaterial[k / 2].gas
                    val paint = nativePaints[k]
                    paint.strokeWidth = ((if (gas) 1.35f else 1.95f) + (k % 2) * .76f) * density
                    paint.alpha = ((if (gas) .75f else .96f) * brightness * 255).toInt()
                    native.drawPoints(packedPoints, offsets[k], counts[k], paint)
                }
            }
            world.brushPoint?.let {
                drawSkyRing(Offset(it.x, it.y), .015f, skyCyan, .80f, 1.2f)
            }
        }
    }
}

private fun DrawScope.drawSkyRing(
    origin: Offset, radius: Float, color: Color, alpha: Float, stroke: Float
) {
    if (alpha <= 0f || radius <= 0f) return
    drawOval(color,
        topLeft = Offset((origin.x - radius) * size.width, (origin.y - radius) * size.height),
        size = Size(2f * radius * size.width, 2f * radius * size.height),
        alpha = alpha, style = Stroke(stroke.dp.toPx()))
}

private fun DrawScope.drawPhenomenon(ep: CosmicEpisode, brightness: Float) {
    val opacity = ep.alpha * brightness
    if (opacity <= 0f) return
    val origin = Offset(ep.x, ep.y)
    val progress = ep.progress
    when (ep.kind) {
        CosmicEventKind.COSMIC_SHOCK, CosmicEventKind.SUPERNOVA, CosmicEventKind.NOVA,
        CosmicEventKind.RING_WAVE, CosmicEventKind.THERMAL_SPIKE, CosmicEventKind.CRYO_TIDE,
        CosmicEventKind.SOLAR_WIND, CosmicEventKind.IONIZATION_FRONT, CosmicEventKind.TIDAL_PULSE -> {
            val color = when (ep.kind) {
                CosmicEventKind.CRYO_TIDE, CosmicEventKind.IONIZATION_FRONT -> skyCyan
                CosmicEventKind.TIDAL_PULSE -> skyViolet
                else -> skyAmber
            }
            val radius = when (ep.kind) {
                CosmicEventKind.COSMIC_SHOCK, CosmicEventKind.SUPERNOVA -> ep.pressureRadius
                else -> ep.intensity.radius * (.12f + .88f * progress)
            }
            drawSkyRing(origin, radius, color, opacity * .7f, 1.5f)
            drawSkyRing(origin, radius * .90f, color, opacity * .26f, 1f)
            if (ep.kind == CosmicEventKind.SUPERNOVA) {
                drawSkyRing(origin, radius * .66f, skyViolet, opacity * .39f, 2.1f)
            }
        }
        CosmicEventKind.SINGULARITY, CosmicEventKind.TIDAL_DISRUPTION,
        CosmicEventKind.GRAVITATIONAL_CAUSTIC -> {
            for (index in 0..3) {
                val radius = (.03f + index * .018f) * (1f + .16f * sin(ep.age * 1.8f + index))
                drawSkyRing(origin, radius, skyViolet, opacity * (.73f - index * .15f), 1.2f)
            }
        }
        CosmicEventKind.PULSAR, CosmicEventKind.QUASAR_JET, CosmicEventKind.POLAR_JET,
        CosmicEventKind.MAGNETAR_FLARE, CosmicEventKind.MAGNETIC_RECONNECTION -> {
            val center = Offset(ep.x * size.width, ep.y * size.height)
            val angle = if (ep.kind == CosmicEventKind.QUASAR_JET ||
                ep.kind == CosmicEventKind.MAGNETAR_FLARE) 1.39f else ep.age * 3.6f
            val length = size.minDimension * (.17f + .24f * ep.intensity.ordinal / 3f)
            val color = if (ep.kind == CosmicEventKind.PULSAR) skyCyan else skyViolet
            for (side in -1..1 step 2) {
                for (arm in -1..1) {
                    val direction = angle + (if (side == 1) 0f else PI.toFloat()) + arm * .10f
                    val end = center + Offset(cos(direction), sin(direction)) * length
                    drawLine(color, center, end, (if (arm == 0) 2f else 1f).dp.toPx(),
                        alpha = opacity * if (arm == 0) .56f else .23f)
                }
            }
        }
        CosmicEventKind.ACCRETION_BURST, CosmicEventKind.STELLAR_NURSERY,
        CosmicEventKind.CRYSTALLIZATION, CosmicEventKind.PLASMA_STORM -> {
            val outward = ep.kind == CosmicEventKind.PLASMA_STORM
            val color = when (ep.kind) {
                CosmicEventKind.CRYSTALLIZATION -> skyCyan
                CosmicEventKind.ACCRETION_BURST -> skyAmber
                else -> skyViolet
            }
            for (band in 0..2) {
                val phase = (progress * 2.3f + band / 3f) % 1f
                val r = ep.intensity.radius * (.1f + .68f * if (outward) phase else 1f - phase)
                val envelope = 4f * phase * (1f - phase)
                drawSkyRing(origin, r, color, opacity * .5f * envelope, 1.1f)
            }
        }
        else -> Unit
    }
}
