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

private val White get() = AX_WHITE
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
    // Automatically respect Android's power saver and a critically low battery.
    val context = LocalContext.current
    val reducedPower = remember(context, interacting) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        pm.isPowerSaveMode || capacity in 0..15
    }
    // Paint at display refresh rate, but integrate physics at stable 60/30 Hz.
    // Interpolation makes motion continuous on 90/120-Hz displays.
    var frameMix by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(world, interacting, reducedPower, world.maxBodies, world.matterMode) {
        val slow = interacting || reducedPower || world.maxBodies >= 40 ||
            world.matterMode == MatterMode.DETAILED
        val targetNanos = if (slow) 33_333_333L else 16_666_667L
        var previous = withFrameNanos { it }
        var accumulator = 0L
        while (coroutineContext.isActive) {
            val now = withFrameNanos { it }
            accumulator = (accumulator + (now - previous).coerceIn(0L, 70_000_000L))
                .coerceAtMost(targetNanos * 2L)
            previous = now
            val stepped = accumulator >= targetNanos
            if (stepped) {
                accumulator -= targetNanos
                world.step(if (slow) 1f / 30f else 1f / 60f)
            }
            // Avoid extra display-rate redraws when Android requests battery saving.
            if (!reducedPower || stepped) {
                frameMix = (accumulator.toFloat() / targetNanos.toFloat())
                    .coerceIn(0f, 1f)
            }
        }
    }

    Canvas(modifier) {
        paint.color = if (AxiomColors.palette.light) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
        val t = world.elapsed
        val unit = min(size.width, size.height)
        val perspective = world.depthShift

        // Very rare full-sky background appears only behind all UI controls.
        drawRareBackdrop(world.episode)
        drawUltraRareBackdrop(world.episode)

        // 3-layer star volume. Greater z means further / dimmer / slower motion.
        starLayers.forEachIndexed { i, star ->
            // White daytime mode keeps the same physics but a cleaner sky.
            if (AxiomColors.palette.light && i % 3 == 0) return@forEachIndexed
            val z = star.z
            var px = ((star.x + .005f * t / z + perspective * .027f / z) % 1.04f)
            var py = star.y + .009f * sin(t * .055f + i) / z
            if (world.episode.kind in listOf(CosmicEventKind.LENS,
                    CosmicEventKind.SINGULARITY)) {
                val ep = world.episode
                val dx = px-ep.x
                val dy = py-ep.y
                val d2 = dx*dx+dy*dy+.035f
                val lens = .0009f*ep.alpha/d2
                px += lens*dx
                py += lens*dy
            }
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
        // Keep the scene readable: no decorative object or equation may cover
        // a mathematical lesson, and search suppresses lessons entirely.
        val lesson = world.mathMoment.takeIf { !interacting && it.active }
        // Temporally coherent procedural events, blending with ordinary space.
        if (scene.kind != CosmicEventKind.QUIET && scene.alpha > .005f) {
            drawEpisode(scene, t, dim, unit, paint)
        }

        world.remnants.forEach { remnant ->
            val hidden = lesson != null &&
                remnant.x in (lesson.x - .17f)..(lesson.x + .52f) &&
                remnant.y in (lesson.y - .17f)..(lesson.y + .40f)
            if (hidden) return@forEach
            val life = (1f - remnant.age / remnant.lifetime).coerceIn(0f, 1f)
            val c = Offset(remnant.x * size.width, remnant.y * size.height)
            when (remnant.kind) {
                CosmicEventKind.NOVA -> {
                    drawCircle(White.copy(alpha = dim * life * .24f),
                        unit * (.09f + .19f * remnant.age / remnant.lifetime),
                        c, style = Stroke(.7.dp.toPx()))
                }
CosmicEventKind.SINGULARITY -> {
                    drawCircle(White.copy(alpha = dim * life * .19f),
                        unit * .055f, c, style = Stroke(.65.dp.toPx()))
                    drawCircle(White.copy(alpha = dim * life * .08f),
                        unit * .11f, c, style = Stroke(.65.dp.toPx()))
                }
                CosmicEventKind.WORMHOLE, CosmicEventKind.PULSAR -> {
                    drawCircle(White.copy(alpha = dim * life * .13f),
                        unit * (.028f + .11f * (1f-life)), c,
                        style = Stroke(.65.dp.toPx()))
                }
                else -> Unit
            }
        }

        world.ripples.forEach { ripple ->
            val hidden = lesson != null &&
                ripple.x in (lesson.x - .16f)..(lesson.x + .50f) &&
                ripple.y in (lesson.y - .16f)..(lesson.y + .38f)
            if (hidden) return@forEach
            val life = (1f - ripple.age / 1.7f).coerceIn(0f, 1f)
            drawCircle(
                White.copy(alpha = life * life * dim * .27f),
                radius = unit * (.015f + .22f * (1f - life)),
                center = Offset(ripple.x * size.width, ripple.y * size.height),
                style = Stroke(1.dp.toPx())
            )
        }
        world.brushPoint?.let { brush ->
            val c=Offset(brush.x*size.width,brush.y*size.height)
            val radius=unit*(.07f+world.brushMass*.02f)
            drawCircle(White.copy(alpha=dim*.30f),radius,c,
                style=Stroke(1.dp.toPx()))
            drawCircle(White.copy(alpha=dim*.14f),radius*1.7f,c,
                style=Stroke(.65.dp.toPx()))
            drawCircle(White.copy(alpha=dim*.76f),3.dp.toPx(),c)
        }

        // Mathematical information belongs to the atmospheric layer.
        // Even a giant cosmic body is drawn above the ML/maths backdrop.
        if (!interacting && scene.kind == CosmicEventKind.QUIET) {
            drawMathMoment(world.mathMoment, dim*.60f, paint)
        }

        // Bright, massless impact dust; capped independently of real physics.
        world.dust.forEach { p ->
            val life=(1f-p.age/p.lifetime).coerceIn(0f,1f)
            drawCircle(White.copy(alpha=dim*life*life*.65f),
                radius=(.6f+life*1.3f).dp.toPx(),
                center=Offset(p.x*size.width,p.y*size.height))
        }

        // Mass-bearing material packets: physical clusters, not cosmetic sprites.
        // One circle per packet; the linked-cell engine handles interactions.
        val lightBackdrop = AxiomColors.palette.light
        world.matter.particles.forEach { p ->
            val raw = Color(p.material.argb)
            val pigment = if (lightBackdrop) Color(
                red = raw.red * .58f, green = raw.green * .58f,
                blue = raw.blue * .58f, alpha = 1f) else raw
            val center = Offset(p.x * size.width, p.y * size.height)
            val radius = (1.05f + kotlin.math.sqrt(p.mass) * 7f).dp.toPx()
            val alpha = dim * (if (p.material.gas) .58f else .83f)
            if (p.material.gas && world.matterMode == MatterMode.DETAILED &&
                p.id % 4 == 0) {
                drawCircle(pigment.copy(alpha = alpha * .12f),
                    radius * 3.5f, center)
            }
            drawCircle(pigment.copy(alpha = alpha), radius, center)
        }

        // Actual material bodies always own the highest COSMIC depth layer.
        val mix = frameMix
        val oldBodies = world.previousBodies
        world.bodies.sortedByDescending { it.depth }.forEach { b ->
        val old = oldBodies.firstOrNull { it.id == b.id } ?: b
        val visualX = old.x + (b.x - old.x) * mix
        val visualY = old.y + (b.y - old.y) * mix
        val factor = (1f / b.depth).coerceIn(.38f, 1.45f)
        val fadeIn = (b.age / 1.8f).coerceIn(0f, 1f)
        val fadeOut = ((b.lifetime - b.age) / 3f).coerceIn(0f, 1f)
        val opacity = dim * (.20f + .57f * factor) * fadeIn * fadeOut
        val drawX = visualX + perspective * .026f * factor
        val origin = Offset(drawX * size.width, visualY * size.height)
        drawBody(b, origin, unit * factor, opacity, t)
        val rawTint = Color(b.material.argb)
        val rim = if (lightBackdrop) Color(
            red = rawTint.red * .56f, green = rawTint.green * .56f,
            blue = rawTint.blue * .56f) else rawTint
        drawCircle(rim.copy(alpha = opacity * .8f),
            radius = (unit * contactSize(b) * factor).coerceAtLeast(2.dp.toPx()),
            center = origin, style = Stroke(.85.dp.toPx()))
        }


    }
}

private fun DrawScope.drawBody(
    b: CelestialBody, c: Offset, unit: Float, a: Float, time: Float
) {
    if (a < .002f) return
    val radius = (unit*(.0050f + .0052f*
        kotlin.math.sqrt(b.mass.coerceAtLeast(.05f)))*
        b.sizeFactor).coerceIn(1.0.dp.toPx(),11.dp.toPx())
    when (b.kind) {
        in 35..104 -> drawExpandedBody(b,c,radius,a,time)
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
        6 -> { // Rotating binary with a visible barycenter.
            val phase = b.rotation * 3f + time * .35f
            val dx = cos(phase) * radius * 2.3f
            val dy = sin(phase) * radius * 1.3f
            drawLine(White.copy(alpha = a * .3f),
                c - Offset(dx,dy),c + Offset(dx,dy),.7.dp.toPx())
            drawCircle(White.copy(alpha = a * .9f),
                radius*.8f,c+Offset(dx,dy))
            drawCircle(White.copy(alpha = a * .58f),
                radius*.58f,c-Offset(dx,dy))
        }
        7 -> { // Procedural thin tetrahedral cage.
            withTransform({ rotate(b.rotation * 36f, c) }) {
                val t = c + Offset(0f,-radius*2.6f)
                val l = c + Offset(-radius*1.7f,radius)
                val r = c + Offset(radius*1.7f,radius)
                val m = c + Offset(0f,radius*.35f)
                listOf(t to l,t to r,l to r,t to m,l to m,r to m)
                    .forEach { (p,q) ->
                        drawLine(White.copy(alpha = a*.47f),
                            p,q,.7.dp.toPx())
                    }
            }
        }
        8 -> { // Galaxy-like spiral arms.
            repeat(2) { arm ->
                val path = Path()
                for(i in 0..28){
                    val u=i/28f
                    val theta=b.rotation + arm*PI.toFloat() + u*6f
                    val rr=radius*(.3f+u*3.5f)
                    val point=c+Offset(cos(theta)*rr,sin(theta)*rr*.68f)
                    if(i==0)path.moveTo(point.x,point.y)
                    else path.lineTo(point.x,point.y)
                }
                drawPath(path,White.copy(alpha=a*.40f),
                    style=Stroke(.7.dp.toPx()))
            }
            drawCircle(White.copy(alpha=a*.75f),radius*.4f,c)
        }
        9 -> { // Pulsar beams, rotating without screen-filling glow.
            val theta=b.rotation*2f
            val ray=Offset(cos(theta),sin(theta))*radius*5f
            drawLine(White.copy(alpha=a*.52f),c-ray,c+ray,.85.dp.toPx())
            drawCircle(White.copy(alpha=a*.6f),
                radius*1.15f,c,style=Stroke(.75.dp.toPx()))
        }
        10 -> { // Gyroscope of eccentric rings.
            for(j in 0..2) {
                withTransform({ rotate(b.rotation*29f+j*58f,c) }) {
                    drawOval(White.copy(alpha=a*(.24f+j*.05f)),
                        topLeft=c-Offset(radius*2.5f,radius*.7f),
                        size=Size(radius*5f,radius*1.4f),
                        style=Stroke(.65.dp.toPx()))
                }
            }
        }
        11 -> { // Short molecular/lattice polyhedron.
            val nodes=listOf(Offset(-2f,-1f),Offset(0f,-2f),
                Offset(2f,-1f),Offset(-2f,1f),Offset(0f,2f),Offset(2f,1f))
            for(j in 0..5) {
                val p=c+nodes[j]*radius
                drawCircle(White.copy(alpha=a*.65f),
                    radius*.27f,p)
                if(j<5)drawLine(White.copy(alpha=a*.25f),p,
                    c+nodes[j+1]*radius,.65.dp.toPx())
            }
        }
        in 13..22 -> {
            // Additional procedurally drawn astro/math entities. No textures.
            val family = b.kind - 13
            when (family) {
                0 -> { // Planet transit and atmospheric ring
                    drawCircle(White.copy(alpha=a*.17f),radius*3.2f,c,
                        style=Stroke(.8.dp.toPx()))
                    drawCircle(White.copy(alpha=a*.68f),radius*.95f,c)
                    drawCircle(AX_BLACK.copy(alpha=.98f),radius*.43f,
                        c+Offset(cos(time*.15f)*radius,sin(time*.15f)*radius*.3f))
                }
                1,2 -> { // Radial stellar flare / sixfold snowflake
                    val rays=if(family==1)8 else 6
                    for(i in 0 until rays) {
                        val theta=i*TAU/rays+b.rotation
                        val ray=Offset(cos(theta),sin(theta))*radius*
                            (if(family==1)3.8f else 2.3f)
                        drawLine(White.copy(alpha=a*.45f),c,c+ray,.7.dp.toPx())
                        if(family==2)
                            drawCircle(White.copy(alpha=a*.45f),radius*.25f,c+ray)
                    }
                }
                3 -> { // Photon's dashed path
                    for(i in 0..7){
                        val u=i/8f
                        val p=c+Offset((u-.5f)*radius*8f,
                            sin(u*TAU*3+b.rotation)*radius*.75f)
                        drawCircle(White.copy(alpha=a*(.18f+u*.6f)),
                            radius*.13f,p)
                    }
                }
                4 -> { // Double helix
                    for(chain in 0..1) {
                        val curve=Path()
                        for(i in 0..28) {
                            val u=i/28f
                            val p=c+Offset((u-.5f)*radius*6f,
                                sin(u*TAU*1.5f+b.rotation+chain*PI.toFloat())*
                                radius*.9f)
                            if(i==0)curve.moveTo(p.x,p.y)
                            else curve.lineTo(p.x,p.y)
                        }
                        drawPath(curve,White.copy(alpha=a*.5f),
                            style=Stroke(.7.dp.toPx()))
                    }
                }
                5 -> { // Turbine / rotating satellite
                    for(i in 0..3) {
                        val phi=i*TAU/4+b.rotation*1.8f
                        val start=c+Offset(cos(phi),sin(phi))*radius
                        val end=c+Offset(cos(phi+.25f),sin(phi+.25f))*radius*3.1f
                        drawLine(White.copy(alpha=a*.54f),start,end,.7.dp.toPx())
                    }
                }
                6,7 -> { // Crystalline hex grid / orbital flower
                    val petals=if(family==6)6 else 9
                    for(i in 0 until petals){
                        val phi=i*TAU/petals+b.rotation*.35f
                        val point=c+Offset(cos(phi),sin(phi))*radius*2.5f
                        drawLine(White.copy(alpha=a*.28f),c,point,.65.dp.toPx())
                        drawCircle(White.copy(alpha=a*.5f),
                            radius*(if(family==6).3f else .55f),point,
                            style=Stroke(.6.dp.toPx()))
                    }
                }
                8 -> { // Tumbling rubble cluster
                    for(i in 0..5){
                        val phi=i*2.4f+b.rotation
                        val r=radius*(.65f+i*.32f)
                        drawCircle(White.copy(alpha=a*(.28f+i*.07f)),
                            radius*(.16f+(i%3)*.07f),
                            c+Offset(cos(phi)*r,sin(phi)*r))
                    }
                }
                else -> { // Epicycloid orbit
                    val path=Path()
                    for(i in 0..38) {
                        val u=i/38f*TAU
                        val p=c+Offset(
                            (cos(u)*2f+.45f*cos(u*5f+b.rotation))*radius,
                            (sin(u)*2f-.45f*sin(u*5f+b.rotation))*radius)
                        if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)
                    }
                    drawPath(path,White.copy(alpha=a*.5f),
                        style=Stroke(.7.dp.toPx()))
                }
            }
        }
        in 23..34 -> {
            // Twelve additional thin, monochrome physical/scientific silhouettes.
            val family = b.kind - 23
            when (family) {
                0 -> { // Quasar polar jets
                    val theta = b.rotation*.7f
                    val axis=Offset(cos(theta),sin(theta))
                    drawLine(White.copy(alpha=a*.75f),
                        c-axis*radius*5.2f,c+axis*radius*5.2f,
                        1.dp.toPx())
                    drawCircle(White.copy(alpha=a*.6f),radius*.7f,c)
                    drawCircle(White.copy(alpha=a*.24f),radius*1.9f,c,
                        style=Stroke(.8.dp.toPx()))
                }
                1 -> { // Star map / connected constellation
                    val pts=Array(6){i->
                        val phi=i*TAU/6f+b.rotation
                        c+Offset(cos(phi)*(1f+(i%2)*.6f),
                            sin(phi)*(1f+(i%3)*.18f))*radius*2f
                    }
                    for(i in 0..4){
                        drawLine(White.copy(alpha=a*.42f),
                            pts[i],pts[i+1],.65.dp.toPx())
                    }
                    pts.forEach {p->drawCircle(White.copy(alpha=a*.8f),
                        1.2.dp.toPx(),p)}
                }
                2 -> { // Star forming accretion disk
                    for(k in 0..2){
                        val r=radius*(1.4f+k*.8f)
                        withTransform({rotate(b.rotation*20f+k*33f,c)}){
                            drawOval(White.copy(alpha=a*(.5f-k*.10f)),
                                c-Offset(r,r*.28f),Size(r*2f,r*.56f),
                                style=Stroke(.7.dp.toPx()))
                        }
                    }
                    drawCircle(White.copy(alpha=a*.82f),radius*.46f,c)
                }
                3 -> { // Interlaced orbital inclinations
                    for(k in 0..3){
                        withTransform({rotate(k*46f+b.rotation*11f,c)}){
                            drawOval(White.copy(alpha=a*.36f),
                                c-Offset(radius*2.8f,radius*.60f),
                                Size(radius*5.6f,radius*1.2f),
                                style=Stroke(.65.dp.toPx()))
                        }
                    }
                }
                4 -> { // Seismology-like radial oscillations
                    for(k in 0..5){
                        val r=radius*(.7f+k*.50f+
                            .14f*sin(time*.8f+k*.75f))
                        drawCircle(White.copy(alpha=a*(.55f-k*.075f)),
                            r,c,style=Stroke(.65.dp.toPx()))
                    }
                }
                5 -> { // Rotating geometric hypercube
                    val theta=b.rotation
                    val side=radius*1.3f
                    val p=Offset(cos(theta),sin(theta))*radius*.8f
                    for(k in 0..3){
                        val angle=(k+.5f)*TAU/4f
                        val v=Offset(cos(angle),sin(angle))*side
                        val w=Offset(cos(angle+TAU/4f),
                            sin(angle+TAU/4f))*side
                        drawLine(White.copy(alpha=a*.5f),
                            c+v,c+w,.7.dp.toPx())
                        drawLine(White.copy(alpha=a*.22f),
                            c+p+v,c+p+w,.7.dp.toPx())
                        drawLine(White.copy(alpha=a*.24f),
                            c+v,c+p+v,.6.dp.toPx())
                    }
                }
                6 -> { // Magnetic field loop
                    for(k in -2..2){
                        val curve=Path()
                        for(j in 0..32){
                            val u=j/32f
                            val x=(u-.5f)*radius*6f
                            val y=(.5f*sin(u*TAU)+k*.26f)*
                                radius*(1f+.2f*sin(time*.2f))
                            if(j==0)curve.moveTo(c.x+x,c.y+y)
                            else curve.lineTo(c.x+x,c.y+y)
                        }
                        drawPath(curve,White.copy(alpha=a*.3f),
                            style=Stroke(.65.dp.toPx()))
                    }
                }
                7 -> { // Pulsing dark nebula filament
                    val path=Path()
                    for(j in 0..35){
                        val u=j/35f
                        val r=radius*(.3f+3f*u)
                        val theta=u*TAU*2f+b.rotation
                        val p=c+Offset(cos(theta)*r,
                            sin(theta)*r*.66f)
                        if(j==0)path.moveTo(p.x,p.y)
                        else path.lineTo(p.x,p.y)
                    }
                    drawPath(path,White.copy(alpha=a*.67f),
                        style=Stroke(.65.dp.toPx()))
                }
                8 -> { // Three-body resonance marker
                    for(j in 0..2){
                        val phi=b.rotation+j*TAU/3f
                        val p=c+Offset(cos(phi),sin(phi))*radius*2.2f
                        drawLine(White.copy(alpha=a*.25f),
                            c,p,.6.dp.toPx())
                        drawCircle(White.copy(alpha=a*.6f),
                            radius*.38f,p)
                    }
                }
                9 -> { // Diffraction grating
                    for(j in -4..4){
                        val x=c.x+j*radius*.55f
                        drawLine(White.copy(alpha=a*(.20f+
                            .34f*(1f-kotlin.math.abs(j)/5f))),
                            Offset(x,c.y-radius*1.6f),
                            Offset(x,c.y+radius*1.6f),.7.dp.toPx())
                    }
                }
                10 -> { // Helical rotating filament
                    for(k in 0..1){
                        val path=Path()
                        for(j in 0..26){
                            val u=j/26f
                            val x=(u-.5f)*radius*7f
                            val y=sin(u*TAU*2f+b.rotation+k*PI.toFloat())*
                                radius*.88f
                            if(j==0)path.moveTo(c.x+x,c.y+y)
                            else path.lineTo(c.x+x,c.y+y)
                        }
                        drawPath(path,White.copy(alpha=a*.52f),
                            style=Stroke(.65.dp.toPx()))
                    }
                }
                else -> { // Molecular orbit cloud
                    for(j in 0..13){
                        val phi=j*2.39f+b.rotation*.55f
                        val rad=radius*(.5f+2.5f*j/14f)
                        drawCircle(White.copy(alpha=a*(.20f+j*.045f)),
                            radius*.16f,c+Offset(cos(phi)*rad,sin(phi)*rad))
                    }
                }
            }
        }
        12 -> { // Double shock ring with opposite phase.
            val wave=.5f+.5f*sin(time*.6f+b.rotation)
            drawCircle(White.copy(alpha=a*.22f),
                radius*(1.2f+wave*2.4f),c,style=Stroke(.75.dp.toPx()))
            drawCircle(White.copy(alpha=a*.4f),
                radius*(1.5f+(1f-wave)*1.6f),c,style=Stroke(.7.dp.toPx()))
            drawCircle(White.copy(alpha=a*.65f),radius*.42f,c)
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
    if(ep.kind in CosmicEventKind.ECHO_SHELL..
            CosmicEventKind.PARTICLE_CASCADE) {
        drawAdditionalEvent(ep,unit,a)
        return
    }
    // Ultra rare events own the full sky; do not paint another central label.
    if(ep.kind in CosmicEventKind.MEGASTRUCTURE..
            CosmicEventKind.GRAVITATIONAL_CAUSTIC)return
    when (ep.kind) {
        CosmicEventKind.ECLIPSE, CosmicEventKind.COSMIC_WEB,
        CosmicEventKind.PHASE_TIDE -> {
            // Full sky is already rendered behind the star field.
            // Centerpiece stays subtle enough to preserve search readability.
            val label=when(ep.kind){
                CosmicEventKind.ECLIPSE -> "TOTAL OCCULTATION"
                CosmicEventKind.COSMIC_WEB -> "DARK MATTER FILAMENTS"
                else -> "SPACETIME PHASE TIDE"
            }
            formula(label,Offset(size.width*.12f,size.height*.84f),
                a*.78f,paint)
        }
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
        CosmicEventKind.NOVA -> {
            // Expanding shock rings physically kick surrounding bodies.
            val f=ep.progress
            repeat(4) { i ->
                val rr=unit*(.045f+f*(.12f+i*.065f))
                drawCircle(White.copy(alpha=a*(.26f-i*.04f)),
                    rr,center,style=Stroke((.9f-i*.1f).dp.toPx()))
            }
            for (i in 0..11) {
                val theta=i*TAU/12f
                val r=unit*(.10f+ep.progress*.40f)
                val point=center+Offset(cos(theta)*r,sin(theta)*r)
                drawCircle(White.copy(alpha=a*.6f),1.3.dp.toPx(),point)
            }
            formula("E = Δmc²  /  SHOCK FRONT",
                Offset(size.width*.12f,size.height*.76f),a*.30f,paint)
        }
        CosmicEventKind.PULSAR -> {
            val rr=unit*.09f
            val theta=ep.age*1.5f
            val ray=Offset(cos(theta),sin(theta))*unit*.34f
            drawCircle(White.copy(alpha=a*.38f),rr,center,
                style=Stroke(.8.dp.toPx()))
            drawLine(White.copy(alpha=a*.44f),center-ray,center+ray,.9.dp.toPx())
            repeat(4) { k ->
                drawCircle(White.copy(alpha=a*(.17f-k*.027f)),
                    rr*(1.4f+k*.7f)+unit*.018f*sin(ep.age*4f),
                    center,style=Stroke(.8.dp.toPx()))
            }
            formula("ν = 1/T   /   PERIODICITY",
                Offset(size.width*.18f,size.height*.77f),a*.30f,paint)
        }
        CosmicEventKind.WORMHOLE -> {
            // Two separate portals with a curved spatial bridge.
            val left=center-Offset(unit*.21f,0f)
            val right=center+Offset(unit*.21f,0f)
            repeat(3) { k ->
                drawOval(White.copy(alpha=a*(.27f-k*.06f)),
                    topLeft=left-Offset(unit*(.028f+k*.012f),unit*.10f),
                    size=Size(unit*(.056f+k*.024f),unit*.2f),
                    style=Stroke(.75.dp.toPx()))
                drawOval(White.copy(alpha=a*(.27f-k*.06f)),
                    topLeft=right-Offset(unit*(.028f+k*.012f),unit*.10f),
                    size=Size(unit*(.056f+k*.024f),unit*.2f),
                    style=Stroke(.75.dp.toPx()))
            }
            val bridge=Path()
            for(i in 0..38){
                val t=i/38f
                val p=Offset(left.x+(right.x-left.x)*t,
                    left.y+sin(t*PI.toFloat()*2f+ep.age*.3f)*unit*.052f)
                if(i==0)bridge.moveTo(p.x,p.y) else bridge.lineTo(p.x,p.y)
            }
            drawPath(bridge,White.copy(alpha=a*.23f),
                style=Stroke(.75.dp.toPx()))
            formula("ds² = gᵤᵥ dxᵘdxᵛ",
                Offset(size.width*.22f,size.height*.75f),a*.28f,paint)
        }
        CosmicEventKind.NEBULA -> {
            // Anisotropic slow flow rather than expensive alpha-blurred textures.
            for (layer in 0..7) {
                val path=Path()
                for(i in 0..42){
                    val t=i/42f
                    val xx=center.x+unit*(t-.5f)*.95f
                    val yy=center.y+unit*((layer-3.5f)*.036f+
                        .047f*sin(t*TAU*2f+ep.age*.21f+layer*.43f))
                    if(i==0)path.moveTo(xx,yy) else path.lineTo(xx,yy)
                }
                drawPath(path,White.copy(alpha=a*(.07f+layer*.009f)),
                    style=Stroke(.65.dp.toPx()))
            }
        }
        CosmicEventKind.ENCOUNTER -> {
            for(i in 0..1){
                val shift=(if(i==0)-1f else 1f)*
                    unit*(.22f-.11f*ep.progress)
                val p=center+Offset(shift,unit*.04f*sin(ep.age*.65f+i))
                drawCircle(White.copy(alpha=a*.30f),
                    unit*.045f,p,style=Stroke(.75.dp.toPx()))
                drawCircle(White.copy(alpha=a*.5f),
                    2.dp.toPx(),p)
            }
        }
        CosmicEventKind.FILAMENT -> {
            for(k in 0..6){
                val path=Path()
                for(i in 0..36){
                    val u=i/36f
                    val x=center.x+(u-.5f)*unit*.92f
                    val y=center.y+(k-3f)*unit*.028f+
                        sin(u*TAU*2.4f+ep.age*.3f+k*.47f)*unit*.048f
                    if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
                }
                drawPath(path,White.copy(alpha=a*(.07f+k*.014f)),
                    style=Stroke(.65.dp.toPx()))
            }
        }
        CosmicEventKind.RADIATION, CosmicEventKind.RING_WAVE,
        CosmicEventKind.REACTION -> {
            val shells=if(ep.kind==CosmicEventKind.REACTION)8 else 4
            for(k in 0 until shells){
                val r=unit*(.03f+ep.progress*.38f+k*.028f)
                drawCircle(White.copy(alpha=(a*(.24f-k*.022f))
                    .coerceIn(0f,1f)),r,center,
                    style=Stroke(.75.dp.toPx()))
            }
            if(ep.kind==CosmicEventKind.REACTION){
                repeat(12){i->
                    val phi=i*TAU/12+ep.age*.25f
                    val rad=unit*(.06f+.19f*ep.progress)
                    drawCircle(White.copy(alpha=a*.5f),1.5.dp.toPx(),
                        center+Offset(cos(phi)*rad,sin(phi)*rad))
                }
            }
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
            drawCircle(AX_BLACK, radius, center)
            drawCircle(White.copy(alpha = a * .82f),
                radius * 1.08f, center, style = Stroke(1.1.dp.toPx()))
            drawCircle(AX_BLACK, radius * .97f, center)
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
