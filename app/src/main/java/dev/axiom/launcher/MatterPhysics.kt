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
        isPair(MatterMaterial.HYDROGEN, MatterMaterial.OXYGEN) ->
            MatterReaction(MatterMaterial.STEAM, 560f)
        isPair(MatterMaterial.METHANE, MatterMaterial.OXYGEN) && temperature > 520f ->
            MatterReaction(MatterMaterial.CARBON, 720f)
        isPair(MatterMaterial.IRON, MatterMaterial.OXYGEN) ->
            MatterReaction(MatterMaterial.OXIDE, 170f)
        isPair(MatterMaterial.SILICATE, MatterMaterial.IRON) && temperature > 900f ->
            MatterReaction(MatterMaterial.MINERAL, -80f)
        isPair(MatterMaterial.SILICATE, MatterMaterial.OXYGEN) ->
            MatterReaction(MatterMaterial.GLASS, 120f)
        isPair(MatterMaterial.ROCK, MatterMaterial.ICE) && temperature < 360f ->
            MatterReaction(MatterMaterial.MINERAL, -50f)
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
        // Do not discard mass when switching to a smaller particle budget.
        // Existing packets finish coalescing, but no extra ones can spawn.
    }

    fun clear() {
        particles.clear()
        reactions = 0
        newPlanets = 0
        sequence = 0
    }

    fun totalMass(): Double = particles.sumOf { it.mass.toDouble() }

    /**
     * Transfer part of BOTH bodies' mass into symmetric particle ejecta.
     * Opposite-pair velocities sum to zero around each parent's velocity.
     * At the hard particle cap an impact remains an ordinary inelastic bounce.
     */
    fun emitImpact(
        first: CelestialBody, second: CelestialBody, speed: Float
    ): Pair<CelestialBody, CelestialBody> {
        val room = (mode.maxParticles - particles.size).coerceAtLeast(0)
        if (room < 4) return first to second
        val count = min(mode.burstPerBody, room / 2).let { it - it % 2 }
        if (count < 2) return first to second
        val fraction = (.24f + speed.coerceAtMost(.4f) * 1.1f)
            .coerceIn(.24f, .68f)
        val aMass = min(first.mass * fraction, (first.mass - .035f).coerceAtLeast(0f))
        val bMass = min(second.mass * fraction, (second.mass - .035f).coerceAtLeast(0f))
        if (aMass < .0001f || bMass < .0001f) return first to second

        fun spray(source: CelestialBody, ejected: Float, offset: Float) {
            val perParticle = ejected / count
            val angle = random.nextFloat() * (2 * PI).toFloat() + offset
            val kick = (.017f + speed * .36f).coerceAtMost(.14f)
            for (i in 0 until count) {
                val theta = angle + i * (2 * PI).toFloat() / count
                // Same speed for opposite directions: exact zero net impulse.
                val variation = 1f + (i % (count / 2)).rem(5) * .035f
                val dx = cos(theta)
                val dy = sin(theta)
                particles.add(MatterParticle(
                    sequence++,
                    source.x + dx * (contactSize(source) + .003f + i % 4 * .0004f),
                    source.y + dy * (contactSize(source) + .003f + i % 4 * .0004f),
                    source.vx + dx * kick * variation,
                    source.vy + dy * kick * variation,
                    perParticle,
                    (source.temperature + speed * 1700f).coerceIn(50f, 7000f),
                    debrisType(source.material, i + source.id)
                ))
            }
        }
        spray(first, aMass, 0f)
        spray(second, bMass, .35f)
        return first.copy(mass = first.mass - aMass) to
            second.copy(mass = second.mass - bMass)
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
        maxBodies: Int, nextBodyId: () -> Int, walls: Boolean
    ) {
        if (particles.isEmpty()) return
        val count = particles.size
        java.util.Arrays.fill(cellHeads, -1)
        java.util.Arrays.fill(dead, 0, count, false)

        for (i in 0 until count) {
            val p = particles[i]
            var ax = 0f
            var ay = 0f
            for (b in bodies) {
                val dx = b.x - p.x
                val dy = b.y - p.y
                val r2 = dx * dx + dy * dy
                if (r2 > .14f) continue
                val softened = r2 + .00025f
                val pull = .00022f * gravity * b.mass /
                    (softened * sqrt(softened))
                ax += dx * pull
                ay += dy * pull
            }
            val thermal = ((p.temperature - 250f) / 6000f).coerceIn(0f, 1f)
            val drag = if (p.material.gas) .17f else .045f
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
                newPlanets++
                landed = true
            }
            if (landed || (!walls && (p.x !in -.2f..1.2f ||
                    p.y !in -.2f..1.2f))) dead[i] = true
        }
        for (i in count - 1 downTo 0) if (dead[i]) particles.removeAt(i)
    }
}
