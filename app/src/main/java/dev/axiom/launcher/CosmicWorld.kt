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
internal enum class CosmicEventKind { QUIET, METEORS, NEURAL, HARMONICS, LENS, SINGULARITY, NOVA, PULSAR, WORMHOLE, NEBULA, ENCOUNTER, FILAMENT, RADIATION, RING_WAVE, REACTION }

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

    // Only world simulation time changes. The app picker/keyboard remain at full speed.
    var timeScale by mutableStateOf(1f)
        private set
    var brushPoint by mutableStateOf<SpaceRipple?>(null)
        private set
    var brushMass by mutableStateOf(0f)
        private set
    private var brushStartX = 0f
    private var brushStartY = 0f
    private var brushPrevX = 0f
    private var brushPrevY = 0f
    private var brushGestureDistance = 0f
    private var brushInitialBodyCount = 0
    private val replayBuffer = ArrayDeque<List<CelestialBody>>(100)
    private var replayTimer = 0f
    private var cascadeBudget = 0
    var lastEncounter by mutableStateOf("none")
        private set

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
        if (bodies.size >= 23) return
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
            kind = if (burst) 1 else 1 + random.nextInt(22),
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
        // Mathematical diagrams and cinematic events occupy separate time slots.
        // An event replaces any math lesson rather than rendering across its text.
        if (kind != CosmicEventKind.QUIET) {
            mathMoment = MathMoment()
            mathPinned = false
            pinnedFor = 0f
            nextMathAt = elapsed + 7f
        }
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
                CosmicEventKind.ENCOUNTER -> 14f
                CosmicEventKind.FILAMENT -> 16f
                CosmicEventKind.RADIATION -> 12f
                CosmicEventKind.RING_WAVE -> 14f
                CosmicEventKind.REACTION -> 11f
                else -> 1f
            },
            x = .25f + random.nextFloat() * .5f,
            y = .20f + random.nextFloat() * .50f,
            serial = eventSerial
        )
        if (kind == CosmicEventKind.ENCOUNTER) {
            spawnBinary()
        }
        if (kind == CosmicEventKind.REACTION || kind == CosmicEventKind.RADIATION) {
            cascadeBudget = 2
        }
        if (kind == CosmicEventKind.METEORS) {
            repeat(3) { spawnFromCorner(burst = true) }
            lastBurstAt = elapsed
        }
    }

    /** Visual math/ML is now an independent ambient layer, not a cinematic event. */
    private fun chooseEvent(): CosmicEventKind = when (random.nextInt(300)) {
        in 0..54 -> CosmicEventKind.METEORS
        in 55..94 -> CosmicEventKind.LENS
        in 95..131 -> CosmicEventKind.NOVA
        in 132..164 -> CosmicEventKind.PULSAR
        in 165..191 -> CosmicEventKind.WORMHOLE
        in 192..218 -> CosmicEventKind.NEBULA
        in 219..244 -> CosmicEventKind.ENCOUNTER
        in 245..266 -> CosmicEventKind.FILAMENT
        in 267..281 -> CosmicEventKind.RING_WAVE
        in 282..291 -> CosmicEventKind.RADIATION
        in 292..297 -> CosmicEventKind.REACTION
        else -> CosmicEventKind.SINGULARITY
    }

    fun summonMath(requested: MathKind? = null) {
        // An explicitly requested lesson takes priority over ongoing scenery.
        if (episode.kind != CosmicEventKind.QUIET) {
            episode = CosmicEpisode()
            eventAt = elapsed + 22f
        }
        mathPinned = false
        pinnedFor = 0f
        nextMathAt = elapsed + 23f
        mathMoment = MathMoment(
            kind = requested ?: MathKind.entries[random.nextInt(MathKind.entries.size)],
            age = 0f,
            duration = 9.2f + random.nextFloat() * 3f,
            x = if (random.nextBoolean()) .08f else .51f,
            y = if (random.nextBoolean()) .15f else .65f,
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

    fun setTimeScale(next: Float) {
        require(next.isFinite())
        timeScale = when {
            next <= .01f -> 0f
            next <= .4f -> .25f
            next <= .75f -> .5f
            next <= 1.5f -> 1f
            else -> 2f
        }
    }

    fun startBrush(x: Float, y: Float) {
        brushStartX = x.coerceIn(0f, 1f)
        brushStartY = y.coerceIn(0f, 1f)
        brushPrevX = brushStartX
        brushPrevY = brushStartY
        brushGestureDistance = 0f
        brushInitialBodyCount = bodies.size
        brushMass = 1.2f
        brushPoint = SpaceRipple(brushStartX, brushStartY)
    }

    fun moveBrush(x: Float, y: Float) {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        brushGestureDistance += hypot(px - brushPrevX, py - brushPrevY)
        brushPrevX = px
        brushPrevY = py
        brushPoint = SpaceRipple(px, py)
        brushMass = (brushMass + .008f).coerceAtMost(4f)
    }

    /** The release vector injects a new orbit candidate. Pure taps create a
     * temporary gravity well instead; this is one unified long-press gesture.
     */
    fun endBrush() {
        val point = brushPoint ?: return
        if (brushGestureDistance > .065f && bodies.size < 23) {
            val dx = point.x - brushStartX
            val dy = point.y - brushStartY
            val norm = max(hypot(dx, dy), .005f)
            val speed = (norm * .65f).coerceIn(.04f, .24f)
            val nearby = bodies.minByOrNull {
                hypot(it.x - point.x, it.y - point.y)
            }
            // Tangential injection around an existing heavy body when nearby.
            val tangentX: Float
            val tangentY: Float
            if (nearby != null && hypot(nearby.x-point.x,nearby.y-point.y) < .35f) {
                val rx = point.x-nearby.x
                val ry = point.y-nearby.y
                val radius = max(hypot(rx, ry), .04f)
                tangentX = -.55f * ry / radius + .45f * dx / norm
                tangentY = .55f * rx / radius + .45f * dy / norm
            } else {
                tangentX = dx/norm
                tangentY = dy/norm
            }
            bodies = bodies + CelestialBody(idSequence++, 3 + random.nextInt(20),
                .5f + random.nextFloat() * 1.5f, 1.1f,
                point.x, point.y, tangentX*speed, tangentY*speed,
                lifetime = 100f)
        } else {
            disturb(point.x, point.y)
        }
        brushPoint = null
        brushMass = 0f
    }

    fun spawnBinary() {
        if (bodies.size > 20) return
        val x = .20f + random.nextFloat() * .56f
        val y = .19f + random.nextFloat() * .56f
        val vx = .008f + random.nextFloat() * .018f
        val id = idSequence++
        val left = CelestialBody(id, 6, 1.4f, 1.1f, x-.048f, y, 0f, -vx,
            lifetime = 125f)
        val right = CelestialBody(idSequence++, 6, 1.4f, 1.1f,
            x+.048f, y, 0f, vx, lifetime = 125f)
        bodies = bodies + left + right
        lastEncounter = "binary"
    }

    /** Deterministic rewind of stored physical frames; excludes app UI state.
     * The small bounded buffer intentionally does not persist user history.
     */
    fun rewind(seconds: Int = 3): Boolean {
        if (replayBuffer.isEmpty()) return false
        val index = (replayBuffer.size - 1 - seconds.coerceIn(0, 8) * 10)
            .coerceAtLeast(0)
        val snapshot = replayBuffer.elementAt(index)
        bodies = snapshot
        previousBodies = snapshot
        return true
    }

    fun clearReplay() {
        replayBuffer.clear()
        replayTimer = 0f
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
        timeScale = 1f
        brushPoint = null
        brushMass = 0f
        cascadeBudget = 0
        lastEncounter = "none"
        clearReplay()
    }

    /** Symplectic-style velocity integration, soft gravity, no mandatory center. */
    fun step(dt: Float) {
        if (timeScale == 0f) return
        val h = (dt.coerceIn(.004f, .034f) * timeScale).coerceAtMost(.034f)
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
        if (mathMoment.active) {
            if (!mathPinned) {
                val age = mathMoment.age + h
                mathMoment = if (age > mathMoment.duration) MathMoment()
                             else mathMoment.copy(age = age)
            }
        } else if (episode.kind == CosmicEventKind.QUIET && elapsed >= nextMathAt) {
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
        } else if (!mathMoment.active && elapsed >= eventAt) {
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
            accumulateForces(a, b, gravity, i, j, ax, ay)
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

        // Touch-controlled gravity: one bounded softened acceleration field.
        val well = brushPoint
        if (well != null) {
            for (i in current.indices) {
                val b = current[i]
                val dx = well.x - b.x
                val dy = well.y - b.y
                val r2 = dx*dx + dy*dy + .01f
                val influence = (.0024f * brushMass / (r2 * sqrt(r2)))
                    .coerceAtMost(1.3f)
                ax[i] += dx * influence
                ay[i] += dy * influence
            }
        }

        // Mild exterior forces for the special events.
        if (e.kind in listOf(CosmicEventKind.ENCOUNTER, CosmicEventKind.FILAMENT,
                CosmicEventKind.RADIATION, CosmicEventKind.RING_WAVE,
                CosmicEventKind.REACTION)) {
            for (i in current.indices) {
                val b = current[i]
                val dx = b.x - e.x
                val dy = b.y - e.y
                val d = max(hypot(dx, dy), .03f)
                when (e.kind) {
                    CosmicEventKind.RING_WAVE, CosmicEventKind.RADIATION -> {
                        val waveRadius = e.progress*.65f
                        val shell = (1f - kotlin.math.abs(d-waveRadius)/.08f)
                            .coerceIn(0f,1f)
                        ax[i] += .024f*shell*dx/d
                        ay[i] += .024f*shell*dy/d
                    }
                    CosmicEventKind.FILAMENT -> {
                        ay[i] += .0015f*sin(b.x*18f+e.age*.8f)
                    }
                    CosmicEventKind.ENCOUNTER -> {
                        ax[i] += -.002f*dy/d
                        ay[i] += .002f*dx/d
                    }
                    CosmicEventKind.REACTION -> {
                        ax[i] += .003f*sin(e.age*2f+b.rotation)
                        ay[i] += .003f*cos(e.age*2f+b.rotation)
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
                val evolved = if(b.age < b.lifetime*.63f &&
                    age >= b.lifetime*.63f) when(b.kind){
                        2 -> 7    // rubble crystallizes
                        3 -> 10   // satellite orbit becomes gyro-ring
                        4 -> 11   // neural microcluster forms lattice
                        8 -> 22   // spiral becomes epicyclic remnant
                        9 -> 16   // pulsar turns into photon filament
                        else -> b.kind
                    } else b.kind
                next += b.copy(x = nx, y = ny, vx = vx, vy = vy,
                    kind = evolved,
                    rotation = b.rotation + h * (.10f + .25f / b.depth), age = age)
            }
        }
        // Resolve touching, approaching pairs with a momentum-conserving impulse.
        // Slow encounters may subsequently coalesce; fast ones scatter.
        for (i in next.indices) for (j in i + 1 until next.size) {
            val a = next[i]
            val b = next[j]
            val velocities = collide(a, b)
            next[i] = a.copy(vx = velocities.avx, vy = velocities.avy)
            next[j] = b.copy(vx = velocities.bvx, vy = velocities.bvy)
        }
        // A fast impact can split one rocky object into two lighter shards.
        // Total mass of the affected asteroid is conserved, and creation is capped.
        var shard: CelestialBody? = null
        if(next.size < 23) {
            collision@ for(i in next.indices) for(j in i+1 until next.size) {
                val a=next[i]
                val b=next[j]
                val d=hypot(a.x-b.x,a.y-b.y)
                val dv=hypot(a.vx-b.vx,a.vy-b.vy)
                if(d < .027f && dv > .13f &&
                    kotlin.math.abs(a.depth-b.depth)<.48f) {
                    val target=if(a.kind in listOf(2,7,21)) i
                        else if(b.kind in listOf(2,7,21)) j else -1
                    if(target >= 0 && next[target].mass > .8f) {
                        val rock=next[target]
                        val piece=rock.mass*.18f
                        next[target]=rock.copy(mass=rock.mass-piece)
                        shard=rock.copy(
                            id=idSequence++,
                            kind=21,
                            mass=piece,
                            x=(rock.x+.012f).coerceIn(-.23f,1.23f),
                            y=(rock.y-.008f).coerceIn(-.23f,1.23f),
                            vx=(rock.vx+.055f).coerceIn(-.38f,.38f),
                            vy=(rock.vy-.045f).coerceIn(-.38f,.38f),
                            age=0f,
                            lifetime=28f
                        )
                        break@collision
                    }
                }
            }
        }
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
                hypot(a.vx-b.vx,a.vy-b.vy) < .018f &&
                a.mass + b.mass <= 7f
            }
            if (partner == null) compact += a
            else {
                val b = next[partner]
                val combinedMass = a.mass + b.mass
                compact += a.copy(
                    mass = combinedMass,
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
        val chain = cascadeBudget > 0 && e.kind == CosmicEventKind.REACTION &&
            e.age > 2f && e.age < 7f && random.nextFloat() < .0018f
        replayTimer += h
        if (replayTimer >= .1f) {
            replayBuffer.addLast(current)
            if (replayBuffer.size > 100) replayBuffer.removeFirst()
            replayTimer = 0f
        }
        previousBodies = current
        if(shard != null && compact.size < 23) compact.add(shard)
        bodies = compact
        if (chain) {
            if (bodies.size < 23) {
                spawnFromCorner(burst = true)
                ripples = (ripples + SpaceRipple(e.x, e.y)).takeLast(4)
            }
            cascadeBudget--
        }
    }
}
