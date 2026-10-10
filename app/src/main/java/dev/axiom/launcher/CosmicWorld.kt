package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Single cinematic world; scenarios are temporary events, not user-facing modes. */
internal enum class CosmicEventKind { QUIET, METEORS, NEURAL, HARMONICS, LENS, SINGULARITY, NOVA, PULSAR, WORMHOLE, NEBULA }

internal data class CosmicEpisode(
    val kind: CosmicEventKind = CosmicEventKind.QUIET,
    val age: Float = 0f,
    val duration: Float = 1f,
    val x: Float = .5f,
    val y: Float = .37f,
    val serial: Int = 0
) {
    val progress: Float get() = (age / duration).coerceIn(0f, 1f)
    val alpha: Float get() {
        val t = progress
        return (min(1f, t * 5f) * min(1f, (1f - t) * 5f)).coerceIn(0f, 1f)
    }
}

internal data class CelestialBody(
    val id: Int,
    val kind: Int,
    val mass: Float,
    val depth: Float,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float = 0f,
    val age: Float = 0f,
    val lifetime: Float = 58f
)

internal data class SpaceRipple(val x: Float, val y: Float, val age: Float = 0f)

internal data class CosmicRemnant(
    val kind: CosmicEventKind, val x: Float, val y: Float,
    val age: Float = 0f, val lifetime: Float = 38f
)

internal class CosmicWorld(seed: Int = 42) {
    private val random = Random(seed)
    var bodies by mutableStateOf<List<CelestialBody>>(initialBodies())
        private set
    var ripples by mutableStateOf<List<SpaceRipple>>(emptyList())
        private set
    var previousBodies by mutableStateOf(bodies)
        private set
    var remnants by mutableStateOf<List<CosmicRemnant>>(emptyList())
        private set
    var mathPinned by mutableStateOf(false)
        private set
    var elapsed by mutableStateOf(0f)
        private set
    var gravity by mutableStateOf(1f)
        private set
    var episode by mutableStateOf(CosmicEpisode())
        private set
    var depthShift by mutableStateOf(0f)
        private set
    private var idSequence = 50
    private var eventSerial = 0
    private var spawnAt = 10f
    private var eventAt = 38f
    private var lastBurstAt = -100f
    private val tunneled = mutableSetOf<Int>()
    private var nextMathAt = 13f
    var mathMoment by mutableStateOf(MathMoment())
        private set
    var selectedBody by mutableStateOf<CelestialBody?>(null)
        private set
    var eventHistory by mutableStateOf<List<String>>(emptyList())
        private set
    private var inspectAge = 0f
    private var pinnedFor = 0f

    private fun initialBodies(): List<CelestialBody> {
        // Deliberately no permanent massive object in the center.
        return listOf(
            CelestialBody(1, 2, 1.0f, 1.35f, .18f, .19f, .006f, .012f, .1f, lifetime = 95f),
            CelestialBody(2, 3, .7f, 2.2f, .83f, .28f, -.008f, .003f, .7f, lifetime = 96f),
            CelestialBody(3, 4, 1.2f, .95f, .76f, .77f, -.015f, -.007f, 1.5f, lifetime = 92f),
            CelestialBody(4, 5, .4f, 2.7f, .23f, .63f, .013f, -.005f, 2.4f, lifetime = 90f),
            CelestialBody(5, 6, 2.1f, 1.12f, .38f, .80f, .015f, -.009f, .3f, lifetime = 120f),
            CelestialBody(6, 8, 1.7f, 1.50f, .73f, .44f, -.014f, -.005f, 2f, lifetime = 110f)
        )
    }

    fun updateGravity(value: Float) { gravity = value.coerceIn(.25f, 2.2f) }

    /** Spawns just outside one screen corner, with a velocity toward the scene. */
    fun spawnFromCorner(burst: Boolean = false) {
        if (bodies.size >= 21) return
        val corner = random.nextInt(4)
        val spread = random.nextFloat() * .17f - .085f
        val (x, y) = when (corner) {
            0 -> -.09f to -.07f
            1 -> 1.09f to -.07f
            2 -> -.09f to 1.07f
            else -> 1.09f to 1.07f
        }
        val targetX = .26f + random.nextFloat() * .48f + spread
        val targetY = .19f + random.nextFloat() * .59f
        val dx = targetX - x
        val dy = targetY - y
        val length = max(hypot(dx, dy), .05f)
        val speed = if (burst) .17f + random.nextFloat() * .12f
                    else .048f + random.nextFloat() * .085f
        val depth = .65f + random.nextFloat() * 2.6f
        val body = CelestialBody(
            id = idSequence++,
            kind = if (burst) 1 else 1 + random.nextInt(12),
            mass = .30f + random.nextFloat() * 1.35f,
            depth = depth,
            x = x, y = y, vx = dx / length * speed,
            vy = dy / length * speed, rotation = random.nextFloat() * 6.28f,
            lifetime = if (burst) 20f + random.nextFloat() * 15f
                       else 39f + random.nextFloat() * 37f
        )
        bodies = bodies + body
    }

    fun trigger(kind: CosmicEventKind) {
        if (kind == CosmicEventKind.QUIET) {
            episode = CosmicEpisode()
            eventAt = elapsed + 32f
            return
        }
        eventSerial++
        tunneled.clear()
        eventHistory = (eventHistory + kind.name).takeLast(10)
        episode = CosmicEpisode(
            kind = kind, age = 0f,
            duration = when (kind) {
                CosmicEventKind.SINGULARITY -> 17f
                CosmicEventKind.NEURAL -> 16f
                CosmicEventKind.HARMONICS -> 17f
                CosmicEventKind.LENS -> 13f
                CosmicEventKind.METEORS -> 10f
                CosmicEventKind.NOVA -> 13f
                CosmicEventKind.PULSAR -> 17f
                CosmicEventKind.WORMHOLE -> 16f
                CosmicEventKind.NEBULA -> 18f
                else -> 1f
            },
            x = .25f + random.nextFloat() * .5f,
            y = .20f + random.nextFloat() * .50f,
            serial = eventSerial
        )
        if (kind == CosmicEventKind.METEORS) {
            repeat(3) { spawnFromCorner(burst = true) }
            lastBurstAt = elapsed
        }
    }

    /** Visual math/ML is now an independent ambient layer, not a cinematic event. */
    private fun chooseEvent(): CosmicEventKind = when (random.nextInt(200)) {
        in 0..49 -> CosmicEventKind.METEORS
        in 50..83 -> CosmicEventKind.LENS
        in 84..117 -> CosmicEventKind.NOVA
        in 118..151 -> CosmicEventKind.PULSAR
        in 152..176 -> CosmicEventKind.WORMHOLE
        in 177..197 -> CosmicEventKind.NEBULA
        else -> CosmicEventKind.SINGULARITY
    }

    fun summonMath(requested: MathKind? = null) {
        mathPinned = false
        pinnedFor = 0f
        nextMathAt = elapsed + 23f
        mathMoment = MathMoment(
            kind = requested ?: MathKind.entries[random.nextInt(MathKind.entries.size)],
            age = 0f,
            duration = 9.2f + random.nextFloat() * 3f,
            x = .10f + random.nextFloat() * .27f,
            y = if (random.nextBoolean()) .18f else .63f,
            seed = random.nextInt()
        )
    }

    /**
     * Touching a live mathematical construction advances its derivation.
     * Touching elsewhere produces a local impulse.
     */
    fun interact(x: Float, y: Float) {
        val m = mathMoment
        if (m.active && x in (m.x - .04f)..(m.x + .43f) &&
            y in (m.y - .04f)..(m.y + .32f)) {
            mathPinned = true
            pinnedFor = 0f
            val next = ((m.progress * 3f).toInt() + 1).coerceAtMost(2)
            mathMoment = m.copy(age = (next / 3f + .04f) * m.duration)
            return
        }
        disturb(x, y)
    }

    fun disturb(x: Float, y: Float) {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        ripples = (ripples + SpaceRipple(px, py)).takeLast(4)
        val closest = bodies.minByOrNull { hypot(it.x - px, it.y - py) }
        selectedBody = closest?.takeIf {
            hypot(it.x - px, it.y - py) < .072f
        }
        inspectAge = 0f
        bodies = bodies.map { b ->
            val dx = b.x - px
            val dy = b.y - py
            val distance = max(hypot(dx, dy), .04f)
            val kick = (.022f / (distance + .065f)).coerceAtMost(.14f)
            b.copy(vx = b.vx + dx / distance * kick / sqrt(b.mass),
                vy = b.vy + dy / distance * kick / sqrt(b.mass))
        }
    }

    fun shiftPerspective(dx: Float) {
        depthShift = (depthShift + dx).coerceIn(-1f, 1f)
    }

    fun reset() {
        bodies = initialBodies()
        previousBodies = bodies
        ripples = emptyList()
        remnants = emptyList()
        mathPinned = false
        pinnedFor = 0f
        elapsed = 0f
        episode = CosmicEpisode()
        depthShift = 0f
        spawnAt = 10f
        eventAt = 38f
        lastBurstAt = -100f
        tunneled.clear()
        mathMoment = MathMoment()
        selectedBody = null
        eventHistory = emptyList()
        inspectAge = 0f
        nextMathAt = 13f
    }

    /** Symplectic-style velocity integration, soft gravity, no mandatory center. */
    fun step(dt: Float) {
        val h = dt.coerceIn(.004f, .034f)
        elapsed += h
        inspectAge += h
        if (mathPinned) {
            pinnedFor += h
            if (pinnedFor > 14f) mathPinned = false
        }
        if (inspectAge > 4.5f && selectedBody != null) selectedBody = null
        depthShift *= (1f - .5f * h)
        ripples = ripples.map { it.copy(age = it.age + h) }.filter { it.age < 1.7f }
        remnants = remnants.map { it.copy(age = it.age + h) }
            .filter { it.age < it.lifetime }
        if (mathMoment.active && !mathPinned) {
            val age = mathMoment.age + h
            mathMoment = if (age > mathMoment.duration) MathMoment()
                         else mathMoment.copy(age = age)
        } else if (elapsed >= nextMathAt) {
            summonMath()
        }

        val oldEpisode = episode
        if (oldEpisode.kind != CosmicEventKind.QUIET) {
            if (oldEpisode.age + h >= oldEpisode.duration) {
                if (oldEpisode.kind in listOf(
                    CosmicEventKind.SINGULARITY, CosmicEventKind.NOVA,
                    CosmicEventKind.PULSAR, CosmicEventKind.WORMHOLE)) {
                    remnants = (remnants + CosmicRemnant(
                        oldEpisode.kind, oldEpisode.x, oldEpisode.y,
                        lifetime = if (oldEpisode.kind == CosmicEventKind.SINGULARITY)
                            65f else 32f
                    )).takeLast(4)
                    // Scattering is a lasting physical consequence of the episode.
                    repeat(2) { spawnFromCorner(burst = true) }
                }
                episode = CosmicEpisode()
                eventAt = elapsed + 24f + random.nextFloat() * 48f
            } else episode = oldEpisode.copy(age = oldEpisode.age + h)
        } else if (elapsed >= eventAt) {
            trigger(chooseEvent())
            eventAt = elapsed + 110f
        }

        if (elapsed >= spawnAt) {
            spawnFromCorner()
            spawnAt = elapsed + 4.5f + random.nextFloat() * 9f
        }
        if (episode.kind == CosmicEventKind.METEORS &&
            elapsed - lastBurstAt > 1.8f && episode.age < 6.5f) {
            spawnFromCorner(burst = true)
            lastBurstAt = elapsed
        }

        val current = bodies
        val n = current.size
        val ax = FloatArray(n)
        val ay = FloatArray(n)

        // Softened inverse-square attraction, scaled by real normalized depth.
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = current[i]
            val b = current[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val radius2 = dx * dx + dy * dy + .0035f
            val grav = (.00026f * gravity /
                (radius2 * sqrt(radius2))).coerceAtMost(1.15f)
            val planeCoupling = 1f / (1f + .55f * kotlin.math.abs(a.depth - b.depth))
            ax[i] += grav * b.mass * dx * planeCoupling
            ay[i] += grav * b.mass * dy * planeCoupling
            ax[j] -= grav * a.mass * dx * planeCoupling
            ay[j] -= grav * a.mass * dy * planeCoupling
            val separation = sqrt(dx * dx + dy * dy)
            if (separation > .001f && separation < .023f &&
                kotlin.math.abs(a.depth - b.depth) < .55f) {
                val repel = (.023f - separation) * .8f
                ax[i] -= dx / separation * repel
                ay[i] -= dy / separation * repel
                ax[j] += dx / separation * repel
                ay[j] += dy / separation * repel
            }
        }

        // Singularity bends trajectories only during the exceptional episode.
        val e = episode
        if (e.kind == CosmicEventKind.SINGULARITY) {
            for (i in current.indices) {
                val b = current[i]
                val dx = e.x - b.x
                val dy = e.y - b.y
                val d2 = dx * dx + dy * dy + .009f
                val strength = (.0019f * e.alpha / (d2 * sqrt(d2))).coerceAtMost(.65f)
                ax[i] += strength * (dx - .33f * dy)
                ay[i] += strength * (dy + .33f * dx)
            }
        } else if (e.kind == CosmicEventKind.LENS) {
            for (i in current.indices) {
                val b = current[i]
                val dx = e.x - b.x
                val dy = e.y - b.y
                val d2 = dx * dx + dy * dy + .08f
                ax[i] += -.0034f * e.alpha * dy / d2
                ay[i] += .0034f * e.alpha * dx / d2
            }
        } else if (e.kind == CosmicEventKind.NOVA ||
                   e.kind == CosmicEventKind.PULSAR ||
                   e.kind == CosmicEventKind.WORMHOLE ||
                   e.kind == CosmicEventKind.NEBULA) {
            for (i in current.indices) {
                val b = current[i]
                val dx = b.x - e.x
                val dy = b.y - e.y
                val d2 = dx * dx + dy * dy + .026f
                when (e.kind) {
                    CosmicEventKind.NOVA -> {
                        // Shock front interacts with a body when the expanding ring reaches it.
                        val ringRadius = .03f + .38f * e.progress
                        val separation = sqrt(dx*dx + dy*dy)
                        val shell = (1f - kotlin.math.abs(separation-ringRadius) / .07f)
                            .coerceIn(0f, 1f)
                        val impulse = .035f * shell * e.alpha / (separation + .08f)
                        ax[i] += impulse * dx / (separation + .02f)
                        ay[i] += impulse * dy / (separation + .02f)
                    }
                    CosmicEventKind.PULSAR -> {
                        val pulse = .5f + .5f * sin(e.age * 5f)
                        ax[i] += .012f * e.alpha * pulse * dx / d2
                        ay[i] += .012f * e.alpha * pulse * dy / d2
                    }
                    CosmicEventKind.WORMHOLE -> {
                        ax[i] += -.006f * e.alpha * dy / d2
                        ay[i] += .006f * e.alpha * dx / d2
                    }
                    CosmicEventKind.NEBULA -> {
                        ax[i] += .0035f * e.alpha * sin(b.rotation + e.age) / d2
                        ay[i] += .0035f * e.alpha * cos(b.rotation + e.age) / d2
                    }
                    else -> Unit
                }
            }
        }

        // Rare singularity remnants continue to bend paths after the event ends.
        for (remnant in remnants) {
            if (remnant.kind != CosmicEventKind.SINGULARITY) continue
            val strength = (1f - remnant.age/remnant.lifetime).coerceIn(0f, 1f)
            for (i in current.indices) {
                val dx = remnant.x-current[i].x
                val dy = remnant.y-current[i].y
                val d2 = dx*dx + dy*dy + .022f
                val lens = .00045f * strength / (d2 * sqrt(d2))
                ax[i] += lens*(dx-.21f*dy)
                ay[i] += lens*(dy+.21f*dx)
            }
        }

        val next = ArrayList<CelestialBody>(n)
        for (i in current.indices) {
            val b = current[i]
            var vx = (b.vx + ax[i].coerceIn(-.8f, .8f) * h) * (1f - .0009f * h)
            var vy = (b.vy + ay[i].coerceIn(-.8f, .8f) * h) * (1f - .0009f * h)
            val speed = hypot(vx, vy)
            if (speed > .38f) {
                vx *= .38f / speed; vy *= .38f / speed
            }
            var nx = b.x + vx * h
            var ny = b.y + vy * h
            if (e.kind == CosmicEventKind.WORMHOLE &&
                e.alpha > .30f && b.id !in tunneled) {
                val left = e.x - .17f
                val right = e.x + .17f
                val fromLeft = hypot(nx-left, ny-e.y) < .043f
                val fromRight = hypot(nx-right, ny-e.y) < .043f
                if (fromLeft || fromRight) {
                    // Conserves speed but changes location and direction.
                    // Each body may transit once per episode.
                    nx = if (fromLeft) right + .057f else left - .057f
                    ny = e.y + (ny-e.y) * .7f
                    vx = -vx
                    tunneled += b.id
                    ripples = (ripples + SpaceRipple(nx,ny)).takeLast(4)
                }
            }
            val age = b.age + h
            // Corner-launched sprites cross the screen and leave. No hard wall bouncing.
            if (age < b.lifetime && nx in -.24f..1.24f && ny in -.24f..1.24f) {
                next += b.copy(x = nx, y = ny, vx = vx, vy = vy,
                    rotation = b.rotation + h * (.10f + .25f / b.depth), age = age)
            }
        }
        // Very slow close encounters may coalesce instead of indefinitely colliding.
        val compact = ArrayList<CelestialBody>(next.size)
        val consumed = HashSet<Int>()
        for (i in next.indices) {
            if (next[i].id in consumed) continue
            val a = next[i]
            val partner = ((i+1) until next.size).firstOrNull { j ->
                val b = next[j]
                b.id !in consumed &&
                kotlin.math.abs(a.depth-b.depth) < .4f &&
                hypot(a.x-b.x,a.y-b.y) < .012f &&
                hypot(a.vx-b.vx,a.vy-b.vy) < .018f
            }
            if (partner == null) compact += a
            else {
                val b = next[partner]
                val combinedMass = a.mass + b.mass
                compact += a.copy(
                    mass = combinedMass.coerceAtMost(7f),
                    x = (a.x*a.mass+b.x*b.mass)/combinedMass,
                    y = (a.y*a.mass+b.y*b.mass)/combinedMass,
                    vx = (a.vx*a.mass+b.vx*b.mass)/combinedMass,
                    vy = (a.vy*a.mass+b.vy*b.mass)/combinedMass,
                    lifetime = maxOf(a.lifetime,b.lifetime)+12f
                )
                consumed += b.id
                ripples = (ripples + SpaceRipple(a.x,a.y)).takeLast(4)
            }
        }
        previousBodies = current
        bodies = compact
    }
}
