package dev.axiom.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Visual colour is a material identifier, not a physically calibrated spectrum.
 * Chemistry below is intentionally a reduced, mass-conserving 2-D model.
 */
internal enum class MatterMaterial(
    val argb: Int, val gas: Boolean = false, val cohesion: Float = .045f
) {
    ROCK(0xffa7a5a3.toInt()),
    SILICATE(0xffb8a78c.toInt()),
    IRON(0xff858f9c.toInt()),
    CARBON(0xff636a71.toInt()),
    SULFUR(0xffe4c34b.toInt()),
    ICE(0xff9ed9ed.toInt()),
    WATER(0xff4a9bd6.toInt()),
    HYDROGEN(0xffefdc76.toInt(), true, .012f),
    HELIUM(0xffffc28c.toInt(), true, .009f),
    OXYGEN(0xff88d6ad.toInt(), true, .014f),
    METHANE(0xffbd8bd9.toInt(), true, .013f),
    STEAM(0xffd2e2ec.toInt(), true, .012f),
    PLASMA(0xffff7895.toInt(), true, .004f),
    OXIDE(0xffb86c56.toInt()),
    MINERAL(0xff77ba9b.toInt()),
    GLASS(0xffc6d9d6.toInt())
}

internal enum class MatterMode(val maxParticles: Int, val burstPerBody: Int) {
    BALANCED(850, 28),
    DETAILED(4800, 96)
}

internal fun matterModeFromPreference(value: String?): MatterMode =
    MatterMode.entries.firstOrNull { it.name == value } ?: MatterMode.BALANCED

internal fun materialForKind(kind: Int): MatterMaterial = when (kind % 13) {
    0 -> MatterMaterial.HYDROGEN
    1 -> MatterMaterial.ICE
    2 -> MatterMaterial.ROCK
    3 -> MatterMaterial.IRON
    4 -> MatterMaterial.SILICATE
    5 -> MatterMaterial.CARBON
    6 -> MatterMaterial.OXYGEN
    7 -> MatterMaterial.WATER
    8 -> MatterMaterial.METHANE
    9 -> MatterMaterial.PLASMA
    10 -> MatterMaterial.SULFUR
    11 -> MatterMaterial.HELIUM
    else -> MatterMaterial.MINERAL
}

internal data class MatterReaction(val product: MatterMaterial, val heat: Float)

/** Mass is preserved when two packets react; thermal chemistry is illustrative. */
internal fun reactionFor(
    a: MatterMaterial, b: MatterMaterial, temperature: Float
): MatterReaction? {
    fun isPair(x: MatterMaterial, y: MatterMaterial) =
        (a == x && b == y) || (a == y && b == x)
    return when {
        isPair(MatterMaterial.HYDROGEN, MatterMaterial.OXYGEN) && temperature >= 700f ->
            MatterReaction(MatterMaterial.STEAM, 560f)
        isPair(MatterMaterial.METHANE, MatterMaterial.OXYGEN) && temperature >= 850f ->
            MatterReaction(MatterMaterial.CARBON, 720f)
        isPair(MatterMaterial.IRON, MatterMaterial.OXYGEN) && temperature >= 470f ->
            MatterReaction(MatterMaterial.OXIDE, 170f)
        isPair(MatterMaterial.SILICATE, MatterMaterial.IRON) && temperature > 900f ->
            MatterReaction(MatterMaterial.MINERAL, -80f)
        isPair(MatterMaterial.SILICATE, MatterMaterial.OXYGEN) && temperature >= 1550f ->
            MatterReaction(MatterMaterial.GLASS, 120f)
        else -> null
    }
}

internal fun equilibrateMaterial(type: MatterMaterial, temperature: Float): MatterMaterial =
    when {
        type == MatterMaterial.ICE && temperature > 290f -> MatterMaterial.WATER
        type == MatterMaterial.WATER && temperature < 260f -> MatterMaterial.ICE
        type == MatterMaterial.WATER && temperature > 420f -> MatterMaterial.STEAM
        type == MatterMaterial.STEAM && temperature < 340f -> MatterMaterial.WATER
        type == MatterMaterial.HYDROGEN && temperature > 4000f -> MatterMaterial.PLASMA
        type == MatterMaterial.PLASMA && temperature < 2500f -> MatterMaterial.HYDROGEN
        type == MatterMaterial.SILICATE && temperature > 1700f -> MatterMaterial.GLASS
        else -> type
    }

internal data class MatterParticle(
    val id: Int,
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var mass: Float, var temperature: Float, var material: MatterMaterial,
    var age: Float = 0f
)

private fun debrisType(type: MatterMaterial, index: Int): MatterMaterial {
    val alternatives = when (type) {
        MatterMaterial.ROCK, MatterMaterial.MINERAL, MatterMaterial.GLASS ->
            arrayOf(MatterMaterial.ROCK, MatterMaterial.SILICATE,
                MatterMaterial.IRON, MatterMaterial.CARBON, MatterMaterial.SULFUR)
        MatterMaterial.HYDROGEN, MatterMaterial.HELIUM, MatterMaterial.METHANE ->
            arrayOf(MatterMaterial.HYDROGEN, MatterMaterial.HELIUM,
                MatterMaterial.OXYGEN, MatterMaterial.METHANE)
        MatterMaterial.ICE, MatterMaterial.WATER, MatterMaterial.STEAM ->
            arrayOf(MatterMaterial.ICE, MatterMaterial.WATER,
                MatterMaterial.OXYGEN, MatterMaterial.HYDROGEN)
        MatterMaterial.PLASMA ->
            arrayOf(MatterMaterial.PLASMA, MatterMaterial.HYDROGEN, MatterMaterial.HELIUM)
        else -> arrayOf(type, type, type, type)
    }
    return alternatives[index % alternatives.size]
}

/**
 * Bounded physical debris. Primitive linked-cell grid avoids an O(particle^2)
 * scan and avoids allocating a collection for every spatial cell each frame.
 * Particles are mutable only inside the world's frame loop (UI thread).
 */
internal class MatterField(private val random: Random = Random(91)) {
    val particles: MutableList<MatterParticle> = ArrayList()
    val structures = BoundStructureField()
    var mode: MatterMode = MatterMode.BALANCED
        private set
    var reactions: Int = 0
        private set
    var newPlanets: Int = 0
        private set

    private var sequence = 0
    private val gridSize = 52
    private val cellHeads = IntArray(gridSize * gridSize)
    private val links = IntArray(MatterMode.DETAILED.maxParticles)
    private val dead = BooleanArray(MatterMode.DETAILED.maxParticles)

    fun setMode(next: MatterMode) {
        mode = next
        coarsenTo(next.maxParticles)
    }

    /** Resolution-only LOD: preserve packet mass and weighted momentum exactly. */
    private fun coarsenTo(targetCount: Int) {
        if (particles.size <= targetCount) return
        val batchSize = (particles.size + targetCount - 1) / targetCount
        val ordered = particles.sortedWith(compareBy<MatterParticle> {
            cell(it.y)
        }.thenBy { cell(it.x) }.thenBy { it.material.ordinal }.thenBy { it.x })
        val compact = ArrayList<MatterParticle>(targetCount)
        for (start in ordered.indices step batchSize) {
            val end = min(start + batchSize, ordered.size)
            var mass = 0.0
            var x = 0.0; var y = 0.0
            var vx = 0.0; var vy = 0.0; var temp = 0.0
            for (i in start until end) {
                val p = ordered[i]
                val m = p.mass.toDouble()
                mass += m
                x += p.x * m; y += p.y * m
                vx += p.vx * m; vy += p.vy * m
                temp += p.temperature * m
            }
            val source = ordered[start]
            compact.add(MatterParticle(source.id,
                (x / mass).toFloat(), (y / mass).toFloat(),
                (vx / mass).toFloat(), (vy / mass).toFloat(),
                mass.toFloat(), (temp / mass).toFloat(), source.material,
                source.age))
        }
        particles.clear()
        particles.addAll(compact)
    }

    fun clear() {
        particles.clear()
        structures.clear()
        episodeHits.clear()
        reactions = 0
        newPlanets = 0
        sequence = 0
    }

    fun totalMass(): Double = particles.sumOf { it.mass.toDouble() }

    /** Low-mass, pre-existing protoplanetary discs make matter visible at launch. */
    fun seedOrbit(source: CelestialBody, requested: Int = 24): CelestialBody {
        val room = (mode.maxParticles - particles.size).coerceAtLeast(0)
        val count = min(requested, room).let { it - it % 2 }
        if (count < 2 || source.mass <= .10f) return source
        val mass = source.mass * .055f
        val per = mass / count
        for (i in 0 until count) {
            val angle = i * (2 * PI).toFloat() / count
            val radius = .022f + (i % 3) * .003f
            val r2 = radius * radius + .00025f
            // Circular speed for the SAME softened potential used below.
            val orbitalSpeed = sqrt(.00022f * source.mass * radius * radius /
                (r2 * sqrt(r2)))
            particles.add(MatterParticle(sequence++,
                source.x + cos(angle) * radius,
                source.y + sin(angle) * radius,
                source.vx - sin(angle) * orbitalSpeed,
                source.vy + cos(angle) * orbitalSpeed,
                per, source.temperature,
                debrisType(source.material, i)))
        }
        return source.copy(mass = source.mass - mass)
    }

    private val episodeHits = HashMap<Long, Float>()

    /** Collision fragmentation: bonded source mass is released, never cloned. */
    fun emitImpact(
        first: CelestialBody, second: CelestialBody, speed: Float
    ): Pair<CelestialBody, CelestialBody> {
        reserveParticles(mode.burstPerBody * 2)
        val room = (mode.maxParticles - particles.size).coerceAtLeast(0)
        val count = min(mode.burstPerBody, room / 2).let { it - it % 2 }
        if (count < 2) return first to second
        val frac = (.075f + (speed - .07f).coerceAtLeast(0f) * 1.1f)
            .coerceIn(.045f, .36f)
        val (a, emittedA) = structures.release(first, count, frac,
            .02f + speed * .65f, ::nextId)
        val (b, emittedB) = structures.release(second, count, frac,
            .02f + speed * .65f, ::nextId)
        particles.addAll(emittedA)
        particles.addAll(emittedB)
        return a to b
    }

    private fun nextId(): Int = sequence++

    private fun reserveParticles(incoming: Int) {
        if (particles.size + incoming > mode.maxParticles) {
            coarsenTo((mode.maxParticles * .65f).toInt())
        }
    }

    /**
     * Roche-like stripping in close encounters and episodic particle outflows.
     * A stable structure remains rigid until the external gradient is material.
     */
    fun releaseFromTidesAndEvents(
        bodies: MutableList<CelestialBody>, episode: CosmicEpisode, elapsed: Float
    ) {
        if (bodies.isEmpty()) return
        for (i in bodies.indices) {
            val source = bodies[i]
            val r = structureRadius(source)
            var tidal = 0f
            for (j in bodies.indices) {
                if (i == j) continue
                val other = bodies[j]
                val d = hypot(other.x - source.x, other.y - source.y)
                if (d <= r + structureRadius(other)) continue // impact handles contact
                val ratio = (other.mass / source.mass.coerceAtLeast(.02f)) *
                    (2f * r / d.coerceAtLeast(.003f)).let { it * it * it }
                tidal = max(tidal, ratio)
            }
            val dx = source.x - episode.x
            val dy = source.y - episode.y
            val dist = hypot(dx, dy)
            val activity = episode.alpha
            val shock = episode.kind in listOf(
                CosmicEventKind.NOVA, CosmicEventKind.SOLAR_FLARE,
                CosmicEventKind.RADIATION, CosmicEventKind.PARTICLE_CASCADE,
                CosmicEventKind.METEORS, CosmicEventKind.GALAXY_COLLISION
            ) && dist < .37f && activity > .22f
            val rip = episode.kind in listOf(
                CosmicEventKind.SINGULARITY, CosmicEventKind.GRAVITATIONAL_CAUSTIC,
                CosmicEventKind.PHASE_TIDE, CosmicEventKind.TIDAL_CAPTURE
            ) && dist < .27f && activity > .30f
            val strip = tidal > 1.8f || shock || rip
            if (!strip || source.mass < .12f) continue
            val key = (source.id.toLong() shl 32) or episode.serial.toLong()
            val cooldown = if (tidal > 1.8f) 1.8f else 4.5f
            if (elapsed - (episodeHits[key] ?: -999f) < cooldown) continue
            reserveParticles(32)
            val room = (mode.maxParticles - particles.size).coerceAtLeast(0)
            val count = min(if (mode == MatterMode.DETAILED) 60 else 20, room)
                .let { it - it % 2 }
            if (count < 2) continue
            val intensity = when {
                tidal > 1.8f -> (tidal * .025f).coerceIn(.018f, .18f)
                rip -> .065f
                else -> .04f
            }
            val (remaining, shards) = structures.release(source, count,
                intensity, .025f + intensity * .4f, ::nextId)
            if (shards.isNotEmpty()) {
                bodies[i] = remaining
                particles.addAll(shards)
                episodeHits[key] = elapsed
            }
        }
        if (episodeHits.size > 300)
            episodeHits.entries.removeAll { elapsed - it.value > 9f }
    }

    /** Event forces act on actual detached material, not drawn textures. */
    private fun eventAcceleration(
        p: MatterParticle, episode: CosmicEpisode
    ): Pair<Float, Float> {
        if (episode.kind == CosmicEventKind.QUIET || episode.alpha <= 0f)
            return 0f to 0f
        val dx = p.x - episode.x
        val dy = p.y - episode.y
        val d = hypot(dx, dy).coerceAtLeast(.025f)
        val strength = episode.alpha
        return when (episode.kind) {
            CosmicEventKind.NOVA, CosmicEventKind.SOLAR_FLARE,
            CosmicEventKind.RADIATION, CosmicEventKind.PARTICLE_CASCADE ->
                if (d < .48f) (.014f * strength * dx / d) to
                    (.014f * strength * dy / d) else 0f to 0f
            CosmicEventKind.SINGULARITY, CosmicEventKind.GRAVITATIONAL_CAUSTIC ->
                (-.026f * strength * dx / (d * d + .015f)) to
                    (-.026f * strength * dy / (d * d + .015f))
            CosmicEventKind.WORMHOLE, CosmicEventKind.GALAXY_COLLISION ->
                (-.015f * strength * dy / d) to (.015f * strength * dx / d)
            CosmicEventKind.STELLAR_NURSERY, CosmicEventKind.NEBULA ->
                (-.007f * strength * dx / d) to (-.007f * strength * dy / d)
            else -> 0f to 0f
        }
    }

    private fun cell(value: Float): Int =
        (((value + .2f) / 1.4f) * gridSize).toInt().coerceIn(0, gridSize - 1)

    private fun planetKind(material: MatterMaterial): Int = when (material) {
        MatterMaterial.ICE, MatterMaterial.WATER, MatterMaterial.STEAM -> 7
        MatterMaterial.HYDROGEN, MatterMaterial.HELIUM, MatterMaterial.METHANE -> 15
        MatterMaterial.PLASMA -> 9
        MatterMaterial.IRON, MatterMaterial.OXIDE -> 21
        MatterMaterial.GLASS, MatterMaterial.MINERAL -> 34
        else -> 2
    }

    /**
     * Update in world-coordinates (screen width and height normalized to 1).
     * Real particle-body attraction, near-neighbour pressure/chemistry,
     * accretion and nucleation share one mass accounting system.
     */
    fun step(
        dt: Float, gravity: Float, bodies: MutableList<CelestialBody>,
        maxBodies: Int, nextBodyId: () -> Int, walls: Boolean,
        episode: CosmicEpisode = CosmicEpisode()
    ) {
        if (particles.isEmpty()) return
        val count = particles.size
        val reactionX = FloatArray(bodies.size)
        val reactionY = FloatArray(bodies.size)
        java.util.Arrays.fill(cellHeads, -1)
        java.util.Arrays.fill(dead, 0, count, false)

        for (i in 0 until count) {
            val p = particles[i]
            var ax = 0f
            var ay = 0f
            for (bodyIndex in bodies.indices) {
                val b = bodies[bodyIndex]
                val dx = b.x - p.x
                val dy = b.y - p.y
                val r2 = dx * dx + dy * dy
                if (r2 > .14f) continue
                val softened = r2 + .00025f
                val pull = .00022f * gravity * b.mass /
                    (softened * sqrt(softened))
                val fx = (dx * pull).coerceIn(-.6f, .6f)
                val fy = (dy * pull).coerceIn(-.6f, .6f)
                ax += fx
                ay += fy
                // Newton's third law: particles pull on bodies as well.
                reactionX[bodyIndex] -= fx * p.mass / b.mass.coerceAtLeast(.01f)
                reactionY[bodyIndex] -= fy * p.mass / b.mass.coerceAtLeast(.01f)
            }
            val eventForce = eventAcceleration(p, episode)
            ax += eventForce.first
            ay += eventForce.second
            val thermal = ((p.temperature - 250f) / 6000f).coerceIn(0f, 1f)
            val drag = if (p.material.gas) .012f else .003f
            val vx = ((p.vx + ax.coerceIn(-1.8f, 1.8f) * dt) *
                (1f - drag * dt)).coerceIn(-.7f, .7f)
            val vy = ((p.vy + ay.coerceIn(-1.8f, 1.8f) * dt) *
                (1f - drag * dt)).coerceIn(-.7f, .7f)
            p.vx = vx
            p.vy = vy
            p.x += vx * dt
            p.y += vy * dt
            if (walls) {
                if (p.x !in 0f..1f) { p.x = p.x.coerceIn(0f, 1f); p.vx *= -.56f }
                if (p.y !in 0f..1f) { p.y = p.y.coerceIn(0f, 1f); p.vy *= -.56f }
            }
            p.temperature += (180f - p.temperature) *
                (if (p.material.gas) .016f else .008f) * dt
            // Hot gases cool and disperse; solid packets can stick together.
            if (p.material.gas && thermal > .2f) p.vy -= .001f * thermal * dt
            p.material = equilibrateMaterial(p.material, p.temperature)
            p.age += dt
            val index = cell(p.y) * gridSize + cell(p.x)
            links[i] = cellHeads[index]
            cellHeads[index] = i
        }

        for (i in 0 until count) {
            if (dead[i]) continue
            val a = particles[i]
            val cx = cell(a.x)
            val cy = cell(a.y)
            var interactions = 0
            for (gy in max(0, cy - 1)..min(gridSize - 1, cy + 1)) {
                for (gx in max(0, cx - 1)..min(gridSize - 1, cx + 1)) {
                    var j = cellHeads[gy * gridSize + gx]
                    while (j >= 0) {
                        val bIndex = j
                        j = links[bIndex]
                        if (bIndex <= i || dead[bIndex]) continue
                        val b = particles[bIndex]
                        val dx = b.x - a.x
                        val dy = b.y - a.y
                        val r2 = dx * dx + dy * dy
                        if (r2 > .00022f) continue
                        if (++interactions > 28) break
                        val dist = sqrt(r2.coerceAtLeast(.0000001f))
                        val relative = hypot(a.vx - b.vx, a.vy - b.vy)
                        val contact = (.0028f +
                            .002f * sqrt(a.mass + b.mass)).coerceAtMost(.009f)
                        if (dist > contact) {
                            // Bounded mutual gravitation between nearby packets.
                            val force = .000004f * dt /
                                ((r2 + .00006f) * sqrt(r2 + .00006f))
                            a.vx += dx * force * b.mass
                            a.vy += dy * force * b.mass
                            b.vx -= dx * force * a.mass
                            b.vy -= dy * force * a.mass
                            continue
                        }
                        val chemistry = reactionFor(
                            a.material, b.material,
                            (a.temperature + b.temperature) * .5f)
                        val threshold = min(a.material.cohesion, b.material.cohesion)
                        if (chemistry != null ||
                            (!a.material.gas && !b.material.gas &&
                                relative < threshold &&
                                max(a.temperature, b.temperature) < 1900f)) {
                            val mass = a.mass + b.mass
                            a.vx = (a.vx * a.mass + b.vx * b.mass) / mass
                            a.vy = (a.vy * a.mass + b.vy * b.mass) / mass
                            a.x = (a.x * a.mass + b.x * b.mass) / mass
                            a.y = (a.y * a.mass + b.y * b.mass) / mass
                            a.temperature = (a.temperature * a.mass +
                                b.temperature * b.mass) / mass +
                                (chemistry?.heat ?: 0f)
                            a.mass = mass
                            if (chemistry != null) {
                                a.material = chemistry.product
                                reactions++
                            } else if (a.material != b.material) {
                                a.material = if (a.mass >= b.mass * 2f)
                                    a.material else MatterMaterial.MINERAL
                            }
                            dead[bIndex] = true
                        } else {
                            // Equal-and-opposite impulse on a close contact.
                            val nx = dx / dist
                            val ny = dy / dist
                            val closing = (b.vx - a.vx) * nx +
                                (b.vy - a.vy) * ny
                            if (closing < 0f) {
                                val pressure = if (a.material.gas || b.material.gas)
                                    .55f else .30f
                                val impulse = -(1f + pressure) * closing /
                                    (1f / a.mass + 1f / b.mass)
                                a.vx -= impulse * nx / a.mass
                                a.vy -= impulse * ny / a.mass
                                b.vx += impulse * nx / b.mass
                                b.vy += impulse * ny / b.mass
                            }
                        }
                    }
                }
            }
        }

        for (i in bodies.indices) {
            val b = bodies[i]
            bodies[i] = b.copy(vx = b.vx + reactionX[i] * dt,
                vy = b.vy + reactionY[i] * dt)
        }

        // Merge packets into larger bodies; never delete a particle without
        // transferring its mass and momentum to the receiving body.
        for (i in 0 until count) {
            if (dead[i]) continue
            val p = particles[i]
            if (!p.x.isFinite() || !p.y.isFinite() ||
                !p.vx.isFinite() || !p.vy.isFinite() || !p.mass.isFinite()) {
                // Defensive numerical fallback: retain a finite, positive packet.
                p.x = .5f; p.y = .5f; p.vx = 0f; p.vy = 0f
                p.mass = p.mass.takeIf { it.isFinite() && it > 0f } ?: .0001f
            }
            var landed = false
            for (j in bodies.indices) {
                val body = bodies[j]
                val touch = contactSize(body) + .003f
                val dx = body.x - p.x
                val dy = body.y - p.y
                if (p.age < .6f || dx * dx + dy * dy > touch * touch) continue
                if (hypot(body.vx - p.vx, body.vy - p.vy) > .24f) continue
                val mass = body.mass + p.mass
                structures.capture(body, p)
                bodies[j] = body.copy(
                    mass = mass,
                    x = (body.x * body.mass + p.x * p.mass) / mass,
                    y = (body.y * body.mass + p.y * p.mass) / mass,
                    vx = (body.vx * body.mass + p.vx * p.mass) / mass,
                    vy = (body.vy * body.mass + p.vy * p.mass) / mass,
                    temperature = (body.temperature * body.mass +
                        p.temperature * p.mass) / mass
                )
                landed = true
                break
            }
            if (!landed && p.mass >= .11f && !p.material.gas &&
                p.temperature < 1900f && bodies.size < maxBodies) {
                bodies.add(CelestialBody(
                    id = nextBodyId(), kind = planetKind(p.material), mass = p.mass,
                    depth = 1.25f, x = p.x, y = p.y, vx = p.vx, vy = p.vy,
                    lifetime = 120f, sizeFactor = .7f,
                    material = p.material, temperature = p.temperature
                ))
                structures.ensure(bodies.last())
                newPlanets++
                landed = true
            }
            if (landed || (!walls && (p.x !in -.2f..1.2f ||
                    p.y !in -.2f..1.2f))) dead[i] = true
        }
        // Stable O(N) compaction: removeAt(i) from an ArrayList in a loop
        // would otherwise introduce large frame-time spikes after collisions.
        var destination = 0
        for (i in 0 until count) if (!dead[i]) {
            if (destination != i) particles[destination] = particles[i]
            destination++
        }
        while (particles.size > destination) particles.removeAt(particles.lastIndex)
        structures.reconcile(bodies)
    }
}
