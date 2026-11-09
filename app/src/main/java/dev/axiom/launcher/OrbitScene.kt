package dev.axiom.launcher

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val White get() = AX_WHITE
private data class DeepStar(val x: Float, val y: Float, val z: Float, val radius: Float)
private val starLayers = Random(824).let { rng ->
    List(76) {
        DeepStar(rng.nextFloat(), rng.nextFloat(), .6f + rng.nextFloat() * 3.5f,
            .5f + rng.nextFloat() * .65f)
    }
}
/**
 * Unified particle renderer. Legacy orbit/sprite artwork is deliberately not
 * called: every visible cosmic object is a separately simulated grain.
 * Positions come from a compact immutable frame published by the worker.
 */
@Composable
internal fun CosmicEnvironment(
    world: CosmicWorld,
    interacting: Boolean,
    modifier: Modifier = Modifier
) {
    val dim by animateFloatAsState(
        targetValue = if (interacting) .45f else 1f,
        animationSpec = tween(450), label = "particle background"
    )
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val displayDensity=LocalDensity.current.density
    val sceneFade=remember {Animatable(1f)}
    LaunchedEffect(world.sceneRevision,world.reducedMotion) {
        if(world.reducedMotion)sceneFade.snapTo(1f)
        else {sceneFade.snapTo(.24f);sceneFade.animateTo(1f,tween(650))}
    }
    val reducedPower = remember(context, interacting) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        power.isPowerSaveMode || capacity in 0..15
    }
    val formulaPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
    }
    // Cheap, physical-summary-based stellar corona. Only the grains define
    // the visible star; these two translucent circles are light, not matter.
    val coronaPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }
    // Reuse point buffers and Android's drawPoints() batching rather than
    // allocating / submitting 4,500 individual Compose drawCircle operations.
    val materials = MatterMaterial.entries
    val buffers = remember { Array(materials.size) { FloatArray(9600) } }
    val counts = remember { IntArray(materials.size) }
    val lightPalette = AxiomColors.palette.light
    var interpolatedFrameFraction by remember { mutableFloatStateOf(1f) }
    val nativePaints = remember(lightPalette) {
        materials.map { material ->
            val c = material.argb
            val color = if (lightPalette) android.graphics.Color.rgb(
                (android.graphics.Color.red(c)*.57f).toInt(),
                (android.graphics.Color.green(c)*.57f).toInt(),
                (android.graphics.Color.blue(c)*.57f).toInt()
            ) else c
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        }
    }
    val trailPath = remember { Path() }
    LaunchedEffect(world, interacting, reducedPower, world.maxBodies, world.matterMode,
        world.reducedMotion, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val heavy = interacting || reducedPower || world.maxBodies >= 40 ||
            world.matterMode == MatterMode.DETAILED
        // Heavy physics is 30Hz, regular physics is 60Hz. Screen refresh is
        // independent: phones may redraw interpolated particles at 120Hz
        // without evaluating a 4600-particle Barnes-Hut tree 120 times.
        val physicsPeriod = if (heavy) 33_333_333L else 16_666_667L
        val worker = launch(Dispatchers.Default) {
            var lastPhysics = System.nanoTime()
            while (coroutineContext.isActive) {
                val start = System.nanoTime()
                val dt = ((start-lastPhysics)/1e9).toFloat().coerceIn(0f,.1f)
                lastPhysics = start
                world.step(dt)
                val spent = System.nanoTime() - start
                val remaining = physicsPeriod - spent
                // Never spin or queue up overdue expensive physics steps.
                // Slow devices run at lower *simulation* throughput, while
                // the display/input thread can still render native 120Hz.
                delay((remaining / 1_000_000L).coerceAtLeast(1L))
            }
        }
        try {
            var lastDisplay = 0L
            val renderPeriod = if (world.reducedMotion) 33_333_333L
                else if (reducedPower || interacting) 16_666_667L else 8_333_333L
            while (coroutineContext.isActive) {
                val now = withFrameNanos { it }
                if (now - lastDisplay < renderPeriod) continue
                lastDisplay = now
                val frames = world.grainFrames
                val diff = (now - frames.committedAtNanos)
                    .coerceAtLeast(0L)
                interpolatedFrameFraction =
                    (diff.toFloat() / frames.intervalNanos).coerceIn(0f, 1f)
            }
        } finally {
            worker.cancel()
        }
        }
    }

    Canvas(modifier.onGloballyPositioned {coordinates ->
        val p=coordinates.positionInRoot()
        world.updateViewport(coordinates.size.width.toFloat(),coordinates.size.height.toFloat(),p.x,p.y,displayDensity)
    }) {
        val dim=dim*sceneFade.value
        val snapshot=world.grainFrames
        val renderedBodies=interpolateBodies(snapshot,interpolatedFrameFraction)
        val viewport = UniverseViewport(size.width,size.height)
        val unit = viewport.side
        val ink = if (AxiomColors.palette.light) Color(0xff526f86) else Color(0xffa4bad1)
        val lenses=renderedBodies.filter {it.nature==CelestialNature.BLACK_HOLE}.take(3).toMutableList()
        if(world.episode.kind==CosmicEventKind.MICROLENSING)
            renderedBodies.maxByOrNull {it.mass}?.let {if(it !in lenses)lenses.add(it)}
        for (star in starLayers) {
            var point=Offset(star.x*size.width,star.y*size.height)
            for(lens in lenses) {
                val center=Offset(viewport.x(lens.x),viewport.y(lens.y))
                val delta=point-center;val distance=delta.getDistance().coerceAtLeast(1f)
                if(distance<unit*.28f) {
                    val strength=if(lens.nature==CelestialNature.BLACK_HOLE).018f else .036f*world.episode.alpha
                    val deflection=(unit*unit*strength*strength/distance).coerceAtMost(unit*.04f)
                    point+=delta/distance*deflection
                }
            }
            drawCircle(ink.copy(alpha=(.13f+.12f/star.z)*dim),
                star.radius*density*.55f,point)
        }
        if (world.showTrails) {
            for ((_, points) in world.trails) {
                if (points.size < 2) continue
                trailPath.reset()
                trailPath.moveTo(viewport.x(points[0].x),viewport.y(points[0].y))
                for (index in 1 until points.size) {
                    val point=points[index]
                    trailPath.lineTo(viewport.x(point.x),viewport.y(point.y))
                }
                drawPath(trailPath,ink.copy(alpha=.24f*dim),style=Stroke(.8f*density))
            }
        }
        if (world.showVectors) for (body in renderedBodies) {
            val start = Offset(viewport.x(body.x),viewport.y(body.y))
            val end = start+Offset(body.vx*unit*1.5f,body.vy*unit*1.5f)
            drawLine(Color(0xff83cfc5).copy(alpha=.65f*dim),start,end,1.dp.toPx())
            val angle = kotlin.math.atan2(end.y-start.y,end.x-start.x)
            for (sign in listOf(-1f,1f)) {
                val a = angle+sign*.45f
                drawLine(Color(0xff83cfc5).copy(alpha=.65f*dim),end,
                    end-Offset(cos(a)*5.dp.toPx(),sin(a)*5.dp.toPx()),1.dp.toPx())
            }
        }
        world.selectedBody?.let { selected ->
            val body = renderedBodies.firstOrNull { it.id == selected.id }
            if (body != null) drawCircle(ink.copy(alpha=.65f*dim),
                (structureRadius(body)*unit+8.dp.toPx()),
                Offset(viewport.x(body.x),viewport.y(body.y)),style=Stroke(1.dp.toPx()))
        }
        // A low-cost, soft volumetric light falloff derived from the position
        // of a PHYSICAL star. This is illumination, never fake particle mass.
        if (!interacting && !AxiomColors.palette.light) {
            val luminous = renderedBodies.firstOrNull {
                it.stellarClass == StellarClass.STAR ||
                    it.stellarClass == StellarClass.QUASAR
            }
            if (luminous != null) {
                val glow = if (luminous.stellarClass == StellarClass.QUASAR)
                    Color(0xff776db5) else Color(0xff4d7294)
                drawRect(brush = Brush.radialGradient(
                    colors = listOf(
                        glow.copy(alpha=.21f*dim),
                        glow.copy(alpha=.085f*dim),
                        Color.Transparent),
                    center = Offset(viewport.x(luminous.x),viewport.y(luminous.y)),
                    radius = min(size.width,size.height)*.53f))
            }
        }
        // Educational math remains available; only cosmic silhouettes are removed.
        if (!interacting && world.mathMoment.active && world.episode.kind == CosmicEventKind.QUIET) {
            formulaPaint.color = if (AxiomColors.palette.light)
                android.graphics.Color.BLACK else android.graphics.Color.WHITE
            inset(viewport.left,viewport.top,viewport.left,viewport.top) {
                drawMathMoment(world.mathMoment,dim*.55f,formulaPaint)
            }
        }
        // All grains are actual independent masses. Batching by material
        // uses one native drawPoints per material, even at 4800 grains.
        val frame = snapshot.current
        val old = snapshot.previous
        val blend = interpolatedFrameFraction
        val canBlend = old.size == frame.size
        java.util.Arrays.fill(counts, 0)
        var i = 0
        while (i + 3 < frame.size) {
            val x = if (canBlend)
                old[i] + (frame[i] - old[i]) * blend else frame[i]
            val y = if (canBlend)
                old[i+1] + (frame[i+1] - old[i+1]) * blend
                else frame[i+1]
            val material = frame[i+3].toInt()
            val px=viewport.x(x);val py=viewport.y(y)
            if (px >= -8f && px <= size.width+8f &&
                py >= -8f && py <= size.height+8f &&
                material in buffers.indices) {
                val k = counts[material]
                val buffer = buffers[material]
                if (k + 1 < buffer.size) {
                    buffer[k] = viewport.x(x)
                    buffer[k+1] = viewport.y(y)
                    counts[material] = k + 2
                }
            }
            i += 4
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // Faint fronts denote the *radius and strength* of genuine
            // thermodynamic/gravitational events. This ring has no mass.
            val ep = world.episode
            if (ep.kind == CosmicEventKind.COSMIC_SHOCK ||
                ep.kind == CosmicEventKind.CRYO_TIDE ||
                ep.kind == CosmicEventKind.THERMAL_SPIKE ||
                ep.kind == CosmicEventKind.MAGNETAR_FLARE ||
                ep.kind == CosmicEventKind.SOLAR_WIND ||
                ep.kind == CosmicEventKind.IONIZATION_FRONT ||
                ep.kind == CosmicEventKind.TIDAL_PULSE ||
                ep.kind == CosmicEventKind.NOVA ||
                ep.kind == CosmicEventKind.SINGULARITY) {
                val color = when (ep.kind) {
                    CosmicEventKind.CRYO_TIDE ->
                        android.graphics.Color.rgb(113, 210, 239)
                    CosmicEventKind.MAGNETAR_FLARE ->
                        android.graphics.Color.rgb(182, 143, 255)
                    CosmicEventKind.SINGULARITY ->
                        android.graphics.Color.rgb(157, 172, 198)
                    else -> android.graphics.Color.rgb(255, 131, 89)
                }
                coronaPaint.color = color
                coronaPaint.style = Paint.Style.STROKE
                coronaPaint.strokeWidth =
                    (1.2f + ep.intensity.heatFactor.toFloat()*.42f)*density
                coronaPaint.alpha = (ep.alpha * dim * 92f)
                    .toInt().coerceIn(0, 150)
                val radius = ep.intensity.radius *
                    min(size.width, size.height) *
                    (.20f + .80f*ep.progress)
                native.drawCircle(viewport.x(ep.x),viewport.y(ep.y),
                    radius, coronaPaint)
                coronaPaint.style = Paint.Style.FILL
            }
            for (body in renderedBodies) {
                if(body.nature==CelestialNature.BLACK_HOLE)continue
                if (body.stellarClass != StellarClass.STAR &&
                    body.stellarClass != StellarClass.QUASAR &&
                    !(world.preset == CosmicPreset.SATURN_RINGS &&
                        body.id == 1)) continue
                val color = when {
                    body.nature==CelestialNature.NEUTRON_STAR || body.nature==CelestialNature.WHITE_DWARF ->
                        android.graphics.Color.rgb(181,207,255)
                    body.stellarClass == StellarClass.QUASAR ->
                        android.graphics.Color.rgb(125, 167, 255)
                    body.stellarClass == StellarClass.STAR ->
                        android.graphics.Color.rgb(255, 201, 100)
                    else -> android.graphics.Color.rgb(173, 197, 239)
                }
                val radius = structureRadius(body) *
                    min(size.width, size.height)
                coronaPaint.color = color
                coronaPaint.alpha = (dim * 19f).toInt().coerceIn(0,255)
                native.drawCircle(viewport.x(body.x),viewport.y(body.y),
                    radius*3.2f, coronaPaint)
                coronaPaint.alpha = (dim * 31f).toInt().coerceIn(0,255)
                native.drawCircle(viewport.x(body.x),viewport.y(body.y),
                    radius*1.75f, coronaPaint)
            }
            for (k in materials.indices) {
                val count = counts[k]
                if (count == 0) continue
                val paint = nativePaints[k]
                paint.strokeWidth = (if (materials[k].gas) 1.9f else 2.6f) *
                    density
                paint.alpha = ((if (materials[k].gas) .75f else .94f) *
                    dim * 255f).toInt().coerceIn(0,255)
                native.drawPoints(buffers[k], 0, count, paint)
            }
        }
        drawCosmicSystems(world,viewport,renderedBodies,dim,System.nanoTime())
        world.brushPoint?.let { brush ->
            drawCircle(White.copy(alpha = .55f * dim), 2.dp.toPx(),
                Offset(viewport.x(brush.x),viewport.y(brush.y)))
        }
    }
}
