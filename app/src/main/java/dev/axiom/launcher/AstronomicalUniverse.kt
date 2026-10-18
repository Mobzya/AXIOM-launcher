package dev.axiom.launcher

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AXIOM 0.21: a fresh, two-scale astrophysical solver.
 *
 * Old all-grains Barnes-Hut dynamics made little clouds gravitationally
 * explosive while 4500 independently moving grains produced noisy orbits.
 * Here every particle is still real matter. Its exact mass contributes to a
 * coarse gravitational MONOPOLE for its gravitationally coherent cloud.
 * Centers are recalculated from particles and are NOT extra hidden masses.
 *
 * At small scales individual grains collide, exchange heat, react, rotate,
 * recoil and can escape/recapture. A smooth reciprocal restoring potential
 * approximates pressure/self-gravity in a resolved but finite-size planet.
 * No grain is snapped to a rigid transform, fixed circle or visual sprite.
 *
 * At orbital scales the mass-weighted centers produce smooth, long-lived
 * inverse-square attraction. This is intentionally a toy 2-D model rather
 * than an exact molecular N-body integrator or general relativity.
 */
internal class AstronomicalUniverse {
    val grains: MutableList<SandGrain> = ArrayList()
    /** A capacity limit, NOT a request to allocate this many particles. */
    var maxParticles: Int = MatterMode.BALANCED.maxParticles
        private set
    var mode: MatterMode = MatterMode.BALANCED
        private set

    private val gConstant = .000052
    private val orbitalSoft2 = .008 * .008
    private val contactSize = .0072
    private val maxContactRange = .0060
    private val cellMask = 16383
    private val cellHeads = IntArray(cellMask + 1)
    private var next = IntArray(0)
    private var cxCell = IntArray(0)
    private var cyCell = IntArray(0)
    private var fx = DoubleArray(0)
    private var fy = DoubleArray(0)
    private val centralRadius = HashMap<Int, Double>()
    private var nextId = 1
    private var tick = 0
    private var pulseKey = -1
    private var activeEpisode = CosmicEpisode()
    private var gravityVisits = 0L
    private var contactVisits = 0L
    var gravityRebuilds: Long = 0
        private set
    var lastGravityMs = .0
        private set
    var lastContactMs = .0
        private set
    var reactions = 0
        private set
    var phaseTransitions = 0
        private set
    var radiatedHeat = 0.0
        private set
    val counters: Pair<Long, Long> get() = contactVisits to gravityVisits

    private class Center(val id: Int) {
        var mass = 0.0
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var r = .012
    }

    private val centers = ArrayList<Center>()
    private val centersById = HashMap<Int, Center>()

    private var explicitlyConfiguredLimit = false
    fun updateMode(value: MatterMode) {
        mode = value
        // Direct physics tests and old integrations still inherit the mode
        // default, unless the user explicitly set a separate capacity.
        if (!explicitlyConfiguredLimit) maxParticles = value.maxParticles
    }
    fun updateParticleLimit(value: Int) {
        explicitlyConfiguredLimit = true
        maxParticles = value.coerceIn(100, 1_000_000)
    }
    /** Preserve unique grain IDs when freely seeded dust reaches 1M grains. */
    fun allocateGrainId(): Int = nextId++

    data class Snapshot(
        val values: FloatArray,
        val heat: DoubleArray,
        val metadata: IntArray
    )

    fun snapshot(): Snapshot {
        val v = FloatArray(grains.size * 5)
        val h = DoubleArray(grains.size)
        val m = IntArray(grains.size * 3)
        for (i in grains.indices) {
            val p = grains[i]
            val a = i * 5
            val b = i * 3
            v[a] = p.x; v[a + 1] = p.y
            v[a + 2] = p.vx; v[a + 3] = p.vy; v[a + 4] = p.mass
            h[i] = p.heat
            m[b] = p.id; m[b + 1] = p.owner
            m[b + 2] = p.material.ordinal
        }
        return Snapshot(v, h, m)
    }

    fun restore(frame: Snapshot) {
        grains.clear()
        centralRadius.clear()
        for (i in frame.heat.indices) {
            val a = i * 5
            val b = i * 3
            val p = SandGrain(frame.metadata[b],
                frame.values[a], frame.values[a + 1],
                frame.values[a + 2], frame.values[a + 3],
                frame.values[a + 4], frame.heat[i],
                MatterMaterial.entries[frame.metadata[b + 2]],
                frame.metadata[b + 1])
            grains.add(p)
        }
        nextId = (grains.maxOfOrNull { it.id } ?: 0) + 1
        buildCenters()
        for (c in centers) {
            var spread = 0.0
            for (p in grains) if (p.owner == c.id) {
                val dx = p.x - c.x; val dy = p.y - c.y
                spread += p.mass * (dx * dx + dy * dy)
            }
            val radius = sqrt(spread / c.mass) * 1.55
            centralRadius[c.id] = radius.coerceIn(.003, .065)
        }
    }

    /**
     * Screen pixels are finite: render a deterministic stratified sampling
     * of AT MOST 24k grains. All others still exist and are integrated.
     * Thus a 1,000,000 ceiling does not allocate 16MB each UI frame.
     */
    fun renderFrame(maxVisible: Int = 24_000): FloatArray {
        val n = grains.size
        val rendered = min(n, maxVisible.coerceAtLeast(1))
        val out = FloatArray(rendered * 4)
        for (i in 0 until rendered) {
            val source = if (n <= rendered) i
                else ((i.toLong() * n) / rendered).toInt()
            val p = grains[source]
            val k = i*4
            out[k] = p.x
            out[k+1] = p.y
            out[k+2] = p.visualRadius
            out[k+3] = p.material.ordinal.toFloat()
        }
        return out
    }

    fun clear() {
        grains.clear()
        centralRadius.clear()
        centers.clear()
        centersById.clear()
        nextId = 1
        pulseKey = -1
        tick = 0
        reactions = 0
        phaseTransitions = 0
        radiatedHeat = 0.0
    }

    /**
     * Initial planets are FLAT circular layers of small real masses.
     * No gas/rock atom is emitted later by a mere collision.
     */
    fun seed(body: CelestialBody, requested: Int =
        if (mode == MatterMode.DETAILED) 300 else 76) {
        val n = min(requested, maxParticles - grains.size)
        if (n <= 0) return
        val radiusPerGrain = (.00135 *
            sqrt((body.mass / n).coerceAtLeast(.00001f) / .01f))
            .coerceIn(.00042, .0028)
        val radius = max(structureRadius(body).toDouble(),
            radiusPerGrain * sqrt(n / .72) * 1.08)
        val angleStep = PI * (3.0 - sqrt(5.0))
        centralRadius[body.id] = radius
        for (i in 0 until n) {
            val angle = angleStep * i
            val r = radius * sqrt((i + .5) / n.toDouble())
            val x = (r * cos(angle)).toFloat()
            val y = (r * sin(angle)).toFloat()
            val portion = (i * 37 + body.id * 23).mod(100)
            val radial = r / radius
            // Differentiated interiors and atmospheres: metallic cores,
            // rocky mantle, icy shell and a little volatile matter.
            // These are MASS packets, not stoichiometrically pure molecules.
            val material = when (body.stellarClass) {
                StellarClass.STAR -> when {
                    radial < .25 && portion < 24 -> MatterMaterial.PLASMA
                    portion < 70 -> MatterMaterial.HYDROGEN
                    portion < 93 -> MatterMaterial.HELIUM
                    else -> MatterMaterial.PLASMA
                }
                StellarClass.QUASAR -> when {
                    portion < 48 -> MatterMaterial.PLASMA
                    portion < 79 -> MatterMaterial.HYDROGEN
                    portion < 96 -> MatterMaterial.HELIUM
                    else -> MatterMaterial.NICKEL
                }
                StellarClass.CLOUD -> when {
                    portion < 36 -> MatterMaterial.HYDROGEN
                    portion < 53 -> MatterMaterial.HELIUM
                    portion < 68 -> MatterMaterial.METHANE
                    portion < 80 -> MatterMaterial.AMMONIA
                    portion < 92 -> MatterMaterial.ICE
                    else -> MatterMaterial.ORGANICS
                }
                StellarClass.PLANET -> when (body.material) {
                    MatterMaterial.METHANE, MatterMaterial.HYDROGEN,
                    MatterMaterial.HELIUM, MatterMaterial.AMMONIA -> when {
                        radial < .25 && portion < 75 -> MatterMaterial.ROCK
                        portion < 42 -> MatterMaterial.HYDROGEN
                        portion < 71 -> MatterMaterial.HELIUM
                        portion < 85 -> MatterMaterial.METHANE
                        else -> MatterMaterial.AMMONIA
                    }
                    MatterMaterial.ICE, MatterMaterial.WATER -> when {
                        radial < .26 -> if (portion < 56)
                            MatterMaterial.IRON else MatterMaterial.BASALT
                        portion < 51 -> MatterMaterial.ICE
                        portion < 69 -> MatterMaterial.WATER
                        portion < 82 -> MatterMaterial.SILICATE
                        portion < 91 -> MatterMaterial.AMMONIA
                        else -> MatterMaterial.ORGANICS
                    }
                    MatterMaterial.IRON, MatterMaterial.NICKEL -> when {
                        radial < .42 -> if (portion < 64)
                            MatterMaterial.IRON else MatterMaterial.NICKEL
                        portion < 60 -> MatterMaterial.BASALT
                        portion < 87 -> MatterMaterial.SILICATE
                        else -> MatterMaterial.CARBON
                    }
                    MatterMaterial.SULFUR, MatterMaterial.MAGMA -> when {
                        portion < 23 -> MatterMaterial.MAGMA
                        portion < 45 -> MatterMaterial.BASALT
                        portion < 64 -> MatterMaterial.SULFUR
                        portion < 78 -> MatterMaterial.ASH
                        else -> MatterMaterial.SILICATE
                    }
                    else -> when {
                        radial < .21 && portion < 78 -> MatterMaterial.IRON
                        portion < 36 -> body.material
                        portion < 64 -> MatterMaterial.BASALT
                        portion < 80 -> MatterMaterial.SILICATE
                        portion < 89 -> MatterMaterial.NICKEL
                        portion < 95 -> MatterMaterial.CARBON
                        else -> MatterMaterial.CRYSTAL
                    }
                }
            }
            val tangent = if (body.stellarClass == StellarClass.PLANET)
                .0025f else .0045f
            val spinX = (-sin(angle) * tangent * r/radius).toFloat()
            val spinY = (cos(angle) * tangent * r/radius).toFloat()
            val mass = body.mass / n
            grains.add(SandGrain(nextId++,
                body.x + x, body.y + y,
                body.vx + spinX, body.vy + spinY,
                mass, mass * .000001 * max(body.temperature, 1f),
                material, body.id))
        }
    }

    fun perturb(x: Float, y: Float, strength: Float) {
        // External energy from a finger, deliberately softer than the old
        // high-radius pulse which disrupted whole planets.
        for (p in grains) {
            val dx = p.x - x; val dy = p.y - y
            val r2 = dx*dx + dy*dy
            if (r2 < 1e-10f || r2 > .38f*.38f) continue
            val d = sqrt(r2)
            val kick = strength * .18f * (1f - d/.38f)
            p.vx += dx / d * kick
            p.vy += dy / d * kick
        }
    }

    private fun ensureCapacity() {
        val n = grains.size
        if (fx.size < n) {
            val capacity = max(n, fx.size + fx.size/2 + 128)
            fx = DoubleArray(capacity); fy = DoubleArray(capacity)
            next = IntArray(capacity)
            cxCell = IntArray(capacity); cyCell = IntArray(capacity)
        }
    }

    /** Recalculate each gravitational source from REAL particle masses. */
    private fun buildCenters() {
        centers.clear()
        centersById.clear()
        for (p in grains) if (p.owner >= 0) {
            val c = centersById.getOrPut(p.owner) {
                Center(p.owner).also { centers.add(it) }
            }
            val m = p.mass.toDouble()
            c.mass += m
            c.x += p.x*m; c.y += p.y*m
            c.vx += p.vx*m; c.vy += p.vy*m
        }
        for (c in centers) {
            if (c.mass <= 0) continue
            c.x /= c.mass; c.y /= c.mass
            c.vx /= c.mass; c.vy /= c.mass
            c.r = centralRadius[c.id] ?: .014
        }
    }

    private fun cell(value: Float): Int =
        kotlin.math.floor(value.toDouble() / contactSize).toInt()
    private fun hash(x: Int, y: Int): Int =
        ((x * 73856093) xor (y * 19349663)) and cellMask
    private fun indexContacts() {
        java.util.Arrays.fill(cellHeads, -1)
        for (i in grains.indices) {
            val p = grains[i]
            val x = cell(p.x); val y = cell(p.y)
            cxCell[i] = x; cyCell[i] = y
            val bucket = hash(x, y)
            next[i] = cellHeads[bucket]; cellHeads[bucket] = i
        }
    }

    private fun thermalPotential(type: MatterMaterial): Double = when (type) {
        MatterMaterial.IRON -> .000310
        MatterMaterial.OXYGEN -> .000160
        MatterMaterial.HYDROGEN -> .000080
        MatterMaterial.METHANE -> .000250
        MatterMaterial.WATER -> .000030
        MatterMaterial.STEAM -> .000150
        MatterMaterial.PLASMA -> .000220
        MatterMaterial.OXIDE -> .000070
        MatterMaterial.GLASS -> .000090
        MatterMaterial.NICKEL -> .000280
        MatterMaterial.BASALT -> .000035
        MatterMaterial.AMMONIA -> .000175
        MatterMaterial.ORGANICS -> .000240
        MatterMaterial.MAGMA -> .000240
        MatterMaterial.ASH -> .000025
        MatterMaterial.CRYSTAL -> .000005
        else -> 0.0
    }

    internal fun reactPair(a: SandGrain, b: SandGrain): Boolean {
        val t = .5 * (a.heat/a.mass + b.heat/b.mass) / .000001
        val ma = a.material; val mb = b.material
        fun pair(x: MatterMaterial, y: MatterMaterial) =
            (ma == x && mb == y) || (ma == y && mb == x)
        val product = when {
            pair(MatterMaterial.IRON, MatterMaterial.OXYGEN) && t > 470 ->
                MatterMaterial.OXIDE to MatterMaterial.OXIDE
            pair(MatterMaterial.HYDROGEN, MatterMaterial.OXYGEN) && t > 700 ->
                MatterMaterial.STEAM to MatterMaterial.STEAM
            pair(MatterMaterial.METHANE, MatterMaterial.OXYGEN) && t > 850 ->
                MatterMaterial.CARBON to MatterMaterial.STEAM
            pair(MatterMaterial.IRON, MatterMaterial.SILICATE) && t > 950 ->
                MatterMaterial.IRON to MatterMaterial.MINERAL
            pair(MatterMaterial.NICKEL, MatterMaterial.OXYGEN) && t > 650 ->
                MatterMaterial.OXIDE to MatterMaterial.OXIDE
            pair(MatterMaterial.CARBON, MatterMaterial.OXYGEN) && t > 1050 ->
                MatterMaterial.ASH to MatterMaterial.ASH
            pair(MatterMaterial.ORGANICS, MatterMaterial.OXYGEN) && t > 760 ->
                MatterMaterial.CARBON to MatterMaterial.STEAM
            pair(MatterMaterial.METHANE, MatterMaterial.PLASMA) && t > 1800 ->
                MatterMaterial.CARBON to MatterMaterial.HYDROGEN
            pair(MatterMaterial.AMMONIA, MatterMaterial.PLASMA) && t > 2000 ->
                MatterMaterial.HYDROGEN to MatterMaterial.HELIUM
            else -> return false
        }
        val delta = a.mass * (thermalPotential(ma) -
            thermalPotential(product.first)) +
            b.mass * (thermalPotential(mb) -
                thermalPotential(product.second))
        val heat = a.heat + b.heat
        if (delta < -heat) return false
        val ratio = if (heat > 0) a.heat / heat else .5
        a.heat = max(0.0, a.heat + delta * ratio)
        b.heat = max(0.0, b.heat + delta * (1.0 - ratio))
        a.material = product.first; b.material = product.second
        reactions++
        return true
    }

    private fun phaseChange(p: SandGrain) {
        val t = p.heat / (p.mass * .000001)
        val new = when (p.material) {
            MatterMaterial.ICE -> if (t >= 290) MatterMaterial.WATER else null
            MatterMaterial.WATER -> when {
                t > 420 -> MatterMaterial.STEAM
                t < 260 -> MatterMaterial.ICE
                else -> null
            }
            MatterMaterial.STEAM -> if (t < 295) MatterMaterial.WATER else null
            MatterMaterial.HYDROGEN -> if (t > 4000) MatterMaterial.PLASMA else null
            MatterMaterial.PLASMA -> if (t < 2500) MatterMaterial.HYDROGEN else null
            MatterMaterial.SILICATE -> if (t > 1700) MatterMaterial.GLASS else null
            MatterMaterial.GLASS -> if (t < 1400) MatterMaterial.SILICATE else null
            MatterMaterial.BASALT -> if (t > 1900) MatterMaterial.MAGMA else null
            MatterMaterial.MAGMA -> if (t < 1100) MatterMaterial.BASALT else null
            MatterMaterial.ORGANICS -> if (t > 1100) MatterMaterial.CARBON else null
            MatterMaterial.MINERAL -> if (t < 240) MatterMaterial.CRYSTAL else null
            MatterMaterial.CRYSTAL -> if (t > 550) MatterMaterial.MINERAL else null
            else -> null
        } ?: return
        val delta = (thermalPotential(new) -
            thermalPotential(p.material)) * p.mass
        if (delta > p.heat) return
        p.heat = max(0.0, p.heat - delta)
        p.material = new
        phaseTransitions++
    }

    /**
     * Optically simple stellar radiation. Hot plasma/gas loses EXACTLY the
     * energy gained by cooler material within one scene radius. The world
     * doesn't create free heat, spawn fictional photons or change mass.
     *
     * O(N), executed only every 16 physics steps (not every display frame).
     */
    private fun exchangeStellarRadiation() {
        val emitter = centers.maxByOrNull { it.mass } ?: return
        var source = 0.0
        var receiverWeight = 0.0
        val radius2 = .45*.45
        for (p in grains) {
            val m = p.mass.toDouble()
            val temperature = p.heat / (m * .000001)
            if (p.owner == emitter.id && p.material.gas &&
                temperature > 2800.0) {
                source += p.heat * .003
            } else if (p.owner != emitter.id && temperature < 2600.0) {
                val dx = p.x - emitter.x
                val dy = p.y - emitter.y
                val r2 = dx*dx + dy*dy
                if (r2 < radius2)
                    receiverWeight += m / (.025 + r2)
            }
        }
        if (source <= 0 || receiverWeight <= 0) return
        var deposited = 0.0
        for (p in grains) {
            val m = p.mass.toDouble()
            val temp = p.heat / (m * .000001)
            if (p.owner == emitter.id && p.material.gas &&
                temp > 2800.0) {
                p.heat -= p.heat*.003
            } else if (p.owner != emitter.id && temp < 2600.0) {
                val dx = p.x - emitter.x
                val dy = p.y - emitter.y
                val r2 = dx*dx + dy*dy
                if (r2 < radius2) {
                    val absorbed = source *
                        (m / (.025 + r2)) / receiverWeight
                    p.heat += absorbed
                    deposited += absorbed
                }
            }
        }
        // Floating-point residue is negligible (<1e-12 relative) and
        // intentionally not injected into any random particle.
    }

    private fun releaseEventHeat(event: CosmicEpisode) {
        if (event.kind == CosmicEventKind.QUIET ||
            event.kind == CosmicEventKind.METEORS) return
        val repeat = event.kind == CosmicEventKind.PULSAR ||
            event.kind == CosmicEventKind.QUASAR_JET ||
            event.kind == CosmicEventKind.MAGNETAR_FLARE ||
            event.kind == CosmicEventKind.SOLAR_WIND
        val k = event.serial * 128 +
            if (repeat) (event.age / .7f).toInt() else 0
        if (pulseKey == k) return
        pulseKey = k
        val radius2 = event.intensity.radius * event.intensity.radius
        if (event.kind == CosmicEventKind.IONIZATION_FRONT) {
            // Reallocate real gas heat before phase changes; each
            // ionization spends its chemical potential. No new mass.
            var donorHeat = 0.0
            var donorCount = 0
            for (p in grains) {
                val dx = p.x - event.x; val dy = p.y - event.y
                if (dx*dx + dy*dy > radius2) continue
                if (p.material != MatterMaterial.PLASMA ||
                    donorCount >= 120) continue
                val released = p.heat *
                    (.012*event.intensity.heatFactor).coerceIn(.01,.09)
                p.heat -= released
                donorHeat += released
                donorCount++
            }
            val targets = grains.asSequence()
                .filter { p ->
                    val dx=p.x-event.x; val dy=p.y-event.y
                    dx*dx+dy*dy <= radius2 &&
                        p.material == MatterMaterial.HYDROGEN
                }.take(150).toList()
            if (targets.isNotEmpty()) {
                val per = donorHeat/targets.size
                targets.forEach { p ->
                    p.heat += per
                    phaseChange(p)
                }
            } else {
                // Return untransferred heat locally; the event cannot
                // conjure a sink if no physical hydrogen is present.
                if (donorCount>0) {
                    val donors=grains.filter { p ->
                        val dx=p.x-event.x; val dy=p.y-event.y
                        dx*dx+dy*dy <= radius2 &&
                            p.material==MatterMaterial.PLASMA
                    }.take(donorCount)
                    donors.forEach { it.heat += donorHeat/donorCount }
                }
            }
            return
        }
        if (event.kind == CosmicEventKind.CRYO_TIDE ||
            event.kind == CosmicEventKind.CRYSTALLIZATION ||
            event.kind == CosmicEventKind.STELLAR_NURSERY) {
            // Radiative cooling into the external environment: no fictitious
            // new ice or matter, and escaped heat is tracked explicitly.
            val fraction = (if (event.kind == CosmicEventKind.CRYO_TIDE)
                .07 else .024) * event.intensity.heatFactor
            val cooling = fraction.coerceIn(.015, .50)
            for (p in grains) {
                val dx = p.x - event.x; val dy = p.y - event.y
                if (dx*dx + dy*dy > radius2) continue
                val emitted = p.heat * cooling
                p.heat -= emitted
                radiatedHeat += emitted
                phaseChange(p)
            }
            return
        }
        if (event.kind == CosmicEventKind.THERMAL_SPIKE) {
            // A shock converts RELATIVE mechanical motion into heat.
            // Momentum of each pair is preserved; no energy is added.
            var first: SandGrain? = null
            var handled = 0
            for (p in grains) {
                val dx = p.x - event.x; val dy = p.y - event.y
                if (dx*dx + dy*dy > radius2) continue
                val a = first
                if (a == null) { first = p; continue }
                first = null
                val invA = 1.0 / a.mass; val invB = 1.0 / p.mass
                val mu = 1.0 / (invA + invB)
                val relativeX = (p.vx-a.vx).toDouble()
                val relativeY = (p.vy-a.vy).toDouble()
                val damp = (.08 * event.intensity.heatFactor)
                    .coerceIn(.015, .65)
                val jx = mu*damp*relativeX
                val jy = mu*damp*relativeY
                a.vx = (a.vx+jx*invA).toFloat()
                a.vy = (a.vy+jy*invA).toFloat()
                p.vx = (p.vx-jx*invB).toFloat()
                p.vy = (p.vy-jy*invB).toFloat()
                val lost = .5*mu*(relativeX*relativeX+
                    relativeY*relativeY)*(2*damp-damp*damp)
                a.heat += lost * .5
                p.heat += lost * .5
                if (++handled >= 512) break
            }
            return
        }
        if (event.kind !in listOf(
            CosmicEventKind.PULSAR, CosmicEventKind.QUASAR_JET,
            CosmicEventKind.NOVA, CosmicEventKind.PLASMA_STORM,
            CosmicEventKind.SOLAR_FLARE, CosmicEventKind.COMET_SWARM,
            CosmicEventKind.GALAXY_COLLISION,
            CosmicEventKind.PARTICLE_CASCADE,
            CosmicEventKind.REACTION, CosmicEventKind.NEBULA,
            CosmicEventKind.SINGULARITY, CosmicEventKind.COSMIC_SHOCK,
            CosmicEventKind.MAGNETAR_FLARE, CosmicEventKind.SOLAR_WIND
        )) return
        var previous = -1
        var activated = 0
        val ang = event.age * 3.6
        val nx = if (event.kind == CosmicEventKind.QUASAR_JET ||
            event.kind == CosmicEventKind.MAGNETAR_FLARE)
            .18 else cos(ang.toDouble())
        val ny = if (event.kind == CosmicEventKind.QUASAR_JET ||
            event.kind == CosmicEventKind.MAGNETAR_FLARE)
            .9836666102 else sin(ang.toDouble())
        for (i in grains.indices) {
            val p = grains[i]
            val dx = p.x - event.x; val dy = p.y - event.y
            if (dx*dx + dy*dy > radius2 || p.heat <= 0.0) continue
            if (repeat && !p.material.gas) continue
            if (previous < 0) { previous = i; continue }
            val a = grains[previous]; val b = p; previous = -1
            // Power classes modulate EXISTING thermal energy; a giant
            // flare can spend more heat than a faint flash, never create it.
            val energy = (a.heat + b.heat) *
                ((if (repeat) .018 else .035) *
                    event.intensity.heatFactor).coerceAtMost(.23)
            if (energy <= 0) continue
            val inv = 1.0/a.mass + 1.0/b.mass
            val rv = (b.vx-a.vx)*nx + (b.vy-a.vy)*ny
            val dir = if (rv < 0) -1 else 1
            val speed = abs(rv)
            val impulse = (sqrt(speed*speed+2*inv*energy) - speed) / inv
            if (!impulse.isFinite() || impulse <= 0) continue
            val lost = speed*impulse + .5*inv*impulse*impulse
            val fraction = a.heat / (a.heat + b.heat)
            a.heat = max(0.0, a.heat - lost*fraction)
            b.heat = max(0.0, b.heat - lost*(1-fraction))
            a.vx = (a.vx - impulse/a.mass*nx*dir).toFloat()
            a.vy = (a.vy - impulse/a.mass*ny*dir).toFloat()
            b.vx = (b.vx + impulse/b.mass*nx*dir).toFloat()
            b.vy = (b.vy + impulse/b.mass*ny*dir).toFloat()
            if (++activated >= if (repeat) 48 else 128) break
        }
    }

    private fun accelerations(gravity: Float) {
        java.util.Arrays.fill(fx, 0, grains.size, 0.0)
        java.util.Arrays.fill(fy, 0, grains.size, 0.0)
        gravityVisits = 0
        val g = gConstant * gravity
        val compactEvent = activeEpisode.kind == CosmicEventKind.SINGULARITY ||
            activeEpisode.kind == CosmicEventKind.TIDAL_PULSE
        val beginning = System.nanoTime()
        // Every source is one MONOPOLE representing the mass of a genuine
        // connected particle cloud, never a second copy of that matter.
        for (i in grains.indices) {
            val p = grains[i]
            val own = centersById[p.owner]
            val m = p.mass.toDouble()
            if (own != null) {
                val dx = p.x - own.x; val dy = p.y - own.y
                // Self-gravity + pressure potential in a uniform-density
                // sphere. The restoring field is linear near the center.
                // At the surface its strength diminishes rather than
                // causing a singular kick.
                val gas = p.material.gas
                val k = when (p.material) {
                    MatterMaterial.IRON, MatterMaterial.NICKEL,
                    MatterMaterial.CRYSTAL -> 5.60
                    MatterMaterial.BASALT, MatterMaterial.ROCK,
                    MatterMaterial.SILICATE -> 4.50
                    MatterMaterial.ICE, MatterMaterial.MINERAL -> 3.35
                    MatterMaterial.WATER, MatterMaterial.MAGMA -> 2.05
                    else -> if (gas) 1.25 else 3.30
                }
                fx[i] -= k * dx
                fy[i] -= k * dy
                val damping = when (p.material) {
                    MatterMaterial.MAGMA, MatterMaterial.WATER -> .53
                    MatterMaterial.ICE -> .38
                    MatterMaterial.IRON, MatterMaterial.NICKEL -> .22
                    else -> if (gas) .08 else .28
                }
                fx[i] -= damping * (p.vx - own.vx)
                fy[i] -= damping * (p.vy - own.vy)
            }
            for (c in centers) {
                if (c.id == p.owner) continue
                val dx = c.x - p.x; val dy = c.y - p.y
                val r2 = dx*dx+dy*dy+orbitalSoft2
                val inv = 1.0 / (r2 * sqrt(r2))
                val linkedCore = compactEvent &&
                    hypot(c.x-activeEpisode.x, c.y-activeEpisode.y) < .055
                val fieldBoost = if (linkedCore)
                    1.0 + (if (activeEpisode.kind ==
                        CosmicEventKind.TIDAL_PULSE) .21 else .42) *
                        activeEpisode.intensity.heatFactor
                    else 1.0
                fx[i] += g * fieldBoost * c.mass * dx * inv
                fy[i] += g * fieldBoost * c.mass * dy * inv
                gravityVisits++
            }
        }
        // Correct the global numerical COM drift from the monopole
        // approximation. It does not change any relative acceleration.
        var mass = 0.0; var px = 0.0; var py = 0.0
        for (i in grains.indices) {
            val m = grains[i].mass.toDouble()
            mass += m
            px += m*fx[i]; py += m*fy[i]
        }
        if (mass > 0) {
            val ax = px / mass; val ay = py / mass
            for (i in grains.indices) { fx[i] -= ax; fy[i] -= ay }
        }
        lastGravityMs = (System.nanoTime() - beginning) / 1e6
    }

    private fun collisions(h: Double) {
        val beginning = System.nanoTime()
        indexContacts()
        contactVisits = 0
        val maxVisits = when {
            grains.size > 100_000 -> 20
            grains.size > 10_000 -> 36
            grains.size > 2000 -> 60
            else -> 125
        }
        // Spatial cells still contain EVERY live particle. With large
        // populations, resolve a rotating fraction of contact candidates
        // per tick. Momentum and heat remain pairwise conserved.
        val stride = when {
            grains.size > 300_000 -> 16
            grains.size > 100_000 -> 8
            grains.size > 30_000 -> 4
            grains.size > 10_000 -> 2
            else -> 1
        }
        for (i in tick.mod(stride) until grains.size step stride) {
            val p = grains[i]
            val x = cxCell[i]; val y = cyCell[i]
            var visits = 0
            var hits = 0
            collisionCells@ for (cy in y - 1..y + 1) {
                for (cx in x - 1..x + 1) {
                    var j = cellHeads[hash(cx, cy)]
                    while (j >= 0) {
                        val other = j; j = next[other]
                        if (++visits > maxVisits) break@collisionCells
                        if (other <= i) continue
                        if (cxCell[other] != cx || cyCell[other] != cy) continue
                        val q = grains[other]
                        val dx = q.x-p.x; val dy = q.y-p.y
                        if (abs(dx) > maxContactRange ||
                            abs(dy) > maxContactRange) continue
                        contactVisits++
                        val r2 = dx*dx+dy*dy
                        val radii = p.collisionRadius+q.collisionRadius
                        val r = sqrt(max(r2.toDouble(), 1e-16))
                        // Dust that has escaped any gravitational core can
                        // accrete into new bodies via *mutual*, bounded
                        // attraction. This is never a pull toward a preset
                        // center and never creates a new grain or mass.
                        if (p.owner < 0 && q.owner < 0 &&
                            r > 1e-7 && r < .006 &&
                            hypot(p.vx-q.vx,p.vy-q.vy) < .065f) {
                            val nx = dx/r; val ny = dy/r
                            val invA = 1.0/p.mass
                            val invB = 1.0/q.mass
                            val mu = 1.0/(invA+invB)
                            val accretionBoost = if (activeEpisode.kind ==
                                CosmicEventKind.ACCRETION_BURST)
                                1.0 + activeEpisode.intensity.heatFactor
                                else 1.0
                            val dp = .035 * accretionBoost *
                                mu * h * (1.0-r/.006)
                            p.vx = (p.vx+dp*invA*nx).toFloat()
                            p.vy = (p.vy+dp*invA*ny).toFloat()
                            q.vx = (q.vx-dp*invB*nx).toFloat()
                            q.vy = (q.vy-dp*invB*ny).toFloat()
                            // Low-speed inelastic dust sticking: symmetric
                            // velocity relaxation with lost energy as heat.
                            val rvx = (q.vx-p.vx).toDouble()
                            val rvy = (q.vy-p.vy).toDouble()
                            val damping = .006
                            val jx = mu*damping*rvx
                            val jy = mu*damping*rvy
                            p.vx = (p.vx+jx*invA).toFloat()
                            p.vy = (p.vy+jy*invA).toFloat()
                            q.vx = (q.vx-jx*invB).toFloat()
                            q.vy = (q.vy-jy*invB).toFloat()
                            val lost = .5*mu*(rvx*rvx+rvy*rvy) *
                                (2*damping-damping*damping)
                            p.heat += lost*.5; q.heat += lost*.5
                        }
                        // Distinct short-range EM / liquid interactions:
                        // repulsive plasma pressure and cohesive liquid films.
                        // Every impulse is strictly equal-and-opposite, so
                        // they do not add net momentum or fictional matter.
                        if (r > 1e-7 && r < .006) {
                            val liquids = (p.material == MatterMaterial.WATER ||
                                p.material == MatterMaterial.MAGMA) &&
                                (q.material == MatterMaterial.WATER ||
                                    q.material == MatterMaterial.MAGMA)
                            val charged = p.material == MatterMaterial.PLASMA &&
                                q.material == MatterMaterial.PLASMA
                            if (liquids || charged) {
                                val invA=1.0/p.mass
                                val invB=1.0/q.mass
                                val mu=1.0/(invA+invB)
                                val sign=if (charged) -1.0 else 1.0
                                val impulse=sign*(if(charged) .035 else .045) *
                                    mu*h*(1-r/.006)
                                val nx=dx/r
                                val ny=dy/r
                                p.vx=(p.vx+impulse*invA*nx).toFloat()
                                p.vy=(p.vy+impulse*invA*ny).toFloat()
                                q.vx=(q.vx-impulse*invB*nx).toFloat()
                                q.vy=(q.vy-impulse*invB*ny).toFloat()
                            }
                        }
                        if (r2 >= radii*radii) continue
                        val nx = if (r > 1e-7) dx/r else 1.0
                        val ny = if (r > 1e-7) dy/r else .0
                        val invA = 1.0/p.mass; val invB = 1.0/q.mass
                        val closing = (q.vx-p.vx)*nx+(q.vy-p.vy)*ny
                        if (closing < 0) {
                            val e = when {
                                p.material == MatterMaterial.MAGMA ||
                                    q.material == MatterMaterial.MAGMA ||
                                    p.material == MatterMaterial.WATER ||
                                    q.material == MatterMaterial.WATER -> .08
                                p.material == MatterMaterial.ICE ||
                                    q.material == MatterMaterial.ICE -> .12
                                p.material == MatterMaterial.IRON ||
                                    q.material == MatterMaterial.IRON ||
                                    p.material == MatterMaterial.NICKEL ||
                                    q.material == MatterMaterial.NICKEL -> .31
                                p.material == MatterMaterial.PLASMA ||
                                    q.material == MatterMaterial.PLASMA -> .64
                                p.material.gas && q.material.gas -> .54
                                else -> .19
                            }
                            val impulse = -(1+e)*closing/(invA+invB)
                            p.vx = (p.vx-impulse*invA*nx).toFloat()
                            p.vy = (p.vy-impulse*invA*ny).toFloat()
                            q.vx = (q.vx+impulse*invB*nx).toFloat()
                            q.vy = (q.vy+impulse*invB*ny).toFloat()
                            val converted = .5*(1-e*e)*closing*closing/
                                (invA+invB)
                            p.heat += converted*.5
                            q.heat += converted*.5
                        }
                        // Positional overlap correction follows inverse
                        // masses and does not teleport the center of mass.
                        val overlap = radii-r
                        val shift = overlap*.18/(invA+invB)
                        p.x = (p.x-shift*invA*nx).toFloat()
                        p.y = (p.y-shift*invA*ny).toFloat()
                        q.x = (q.x+shift*invB*nx).toFloat()
                        q.y = (q.y+shift*invB*ny).toFloat()
                        val conduction = when {
                            p.material == MatterMaterial.IRON ||
                                p.material == MatterMaterial.NICKEL ||
                                q.material == MatterMaterial.IRON ||
                                q.material == MatterMaterial.NICKEL -> .075
                            p.material.gas && q.material.gas -> .008
                            else -> .024
                        }
                        val heat = (p.heat/p.mass-q.heat/q.mass) *
                            min(p.mass, q.mass)*conduction
                        val moved = heat.coerceIn(-q.heat*.2, p.heat*.2)
                        p.heat -= moved; q.heat += moved
                        val hot = p.heat/p.mass > .00065 ||
                            q.heat/q.mass > .00065
                        val chance = if (hot) 127 else 511
                        if ((p.id*31+q.id*17+tick).and(chance) == 0)
                            reactPair(p,q)
                        if (++hits >= 12) break@collisionCells
                    }
                }
            }
        }
        lastContactMs = (System.nanoTime()-beginning)/1e6
    }

    fun step(dt: Float, gravity: Float,
        episode: CosmicEpisode = CosmicEpisode(),
        walls: Boolean = false) {
        if (grains.isEmpty() || dt <= 0f) return
        ensureCapacity()
        activeEpisode = episode
        releaseEventHeat(episode)
        val h = min(dt, .034f).toDouble() /
            (if (grains.size < 1000) 2 else 1)
        val substeps = if (grains.size < 1000) 2 else 1
        var gm = .0; var cm = .0
        for (part in 0 until substeps) {
            buildCenters()
            accelerations(gravity)
            gm += lastGravityMs
            gravityRebuilds += if (tick % 2 == 0) 1 else 0
            for (i in grains.indices) {
                val p = grains[i]
                val ax = fx[i].coerceIn(-.75, .75)
                val ay = fy[i].coerceIn(-.75, .75)
                p.vx = (p.vx+ax*h).toFloat()
                p.vy = (p.vy+ay*h).toFloat()
                // Velocity cap is a numerical guardrail, not a hidden
                // nonconservative damping force. Normal bound orbits are
                // far below this scale.
                val speed2 = p.vx*p.vx+p.vy*p.vy
                if (speed2 > .8f*.8f) {
                    val s = .8f/sqrt(speed2)
                    p.vx *= s; p.vy *= s
                }
                p.x = (p.x+p.vx*h).toFloat()
                p.y = (p.y+p.vy*h).toFloat()
                if (walls) {
                    val r = p.collisionRadius.toFloat()
                    if (p.x < r && p.vx < 0f) {
                        p.x = 2*r-p.x; p.vx = -p.vx
                    }
                    if (p.x > 1f-r && p.vx > 0f) {
                        p.x = 2*(1f-r)-p.x; p.vx = -p.vx
                    }
                    if (p.y < r && p.vy < 0f) {
                        p.y = 2*r-p.y; p.vy = -p.vy
                    }
                    if (p.y > 1f-r && p.vy > 0f) {
                        p.y = 2*(1f-r)-p.y; p.vy = -p.vy
                    }
                }
            }
            collisions(h)
            cm += lastContactMs
        }
        lastGravityMs = gm; lastContactMs = cm
        tick++
        if (tick % 8 == 0) for (p in grains) phaseChange(p)
        if (tick % 16 == 0) {
            buildCenters()
            exchangeStellarRadiation()
        }
        if (tick % 20 == 0) {
            buildCenters()
            // Real ejecta are allowed but quiet material stays in clouds.
            for (p in grains) {
                val c = centersById[p.owner] ?: continue
                val distance = hypot(p.x-c.x, p.y-c.y)
                val speed = hypot(p.vx-c.vx, p.vy-c.vy)
                if (distance > c.r * 2.6 &&
                    speed > if (p.material.gas) .10 else .16)
                    p.owner = -1
            }
        }
    }

    fun totals(): GrainTotals {
        var mass = .0; var px = .0; var py = .0
        var kinetic = .0; var internal = .0; var chemical = .0
        for (p in grains) {
            val m = p.mass.toDouble()
            mass += m; px += m*p.vx; py += m*p.vy
            kinetic += .5*m*(p.vx*p.vx+p.vy*p.vy)
            internal += p.heat
            chemical += m*thermalPotential(p.material)
        }
        return GrainTotals(mass,px,py,kinetic,internal,chemical)
    }

    fun totalEnergy(gravity: Float): Double {
        val t = totals()
        var result = t.kinetic+t.internal+t.chemical
        val g = gConstant * gravity
        for (i in grains.indices) for (j in i+1 until grains.size) {
            val a = grains[i]; val b = grains[j]
            val dx = (a.x-b.x).toDouble()
            val dy = (a.y-b.y).toDouble()
            result -= g*a.mass*b.mass /
                sqrt(dx*dx+dy*dy+orbitalSoft2)
        }
        return result
    }

    fun summarizeBodies(previous: List<CelestialBody>): List<CelestialBody> {
        buildCenters()
        return previous.mapNotNull { b ->
            val c = centersById[b.id] ?: return@mapNotNull null
            b.copy(mass = c.mass.toFloat(),
                x = c.x.toFloat(), y = c.y.toFloat(),
                vx = c.vx.toFloat(), vy = c.vy.toFloat())
        }
    }

    fun relabelCaptured(bodies: List<CelestialBody>) {
        if (tick % 12 != 0) return
        buildCenters()
        for (p in grains) {
            if (p.owner >= 0) continue
            var candidate = -1
            var best = Double.POSITIVE_INFINITY
            for (c in centers) {
                val d = hypot(p.x-c.x,p.y-c.y)
                val dv = hypot(p.vx-c.vx,p.vy-c.vy)
                if (d < c.r*1.8 && dv < .052 && d < best) {
                    candidate = c.id; best = d
                }
            }
            if (candidate >= 0) p.owner = candidate
        }
    }

    fun mergeCaptureLabels(bodies: List<CelestialBody>): List<CelestialBody> {
        if (tick % 42 != 0) return bodies
        val retained = bodies.toMutableList()
        for (i in retained.indices) for (j in i+1 until retained.size) {
            val a = retained[i]; val b = retained[j]
            if (a.mass <= 0 || b.mass <= 0) continue
            val d = hypot(a.x-b.x,a.y-b.y)
            val dv = hypot(a.vx-b.vx,a.vy-b.vy)
            if (d > .010f || dv > .028f) continue
            val winner = if (a.mass >= b.mass) a.id else b.id
            val loser = if (winner == a.id) b.id else a.id
            for (p in grains) if (p.owner == loser) p.owner = winner
            if (winner == a.id) retained[j] = b.copy(mass=0f)
            else retained[i] = a.copy(mass=0f)
        }
        return retained.filter { it.mass > 0 }
    }

    /** Cloud discovery is merely naming of physically captured debris. */
    fun discoverClumps(
        bodies: List<CelestialBody>, maxBodies: Int, nextId: () -> Int
    ): List<CelestialBody> {
        if (tick % 100 != 0 || bodies.size >= maxBodies) return emptyList()
        val loose = grains.filter { it.owner < 0 }
        if (loose.size < 16) return emptyList()
        val discovered = ArrayList<CelestialBody>()
        val candidates = loose.take(160)
        for (seed in candidates) {
            if (seed.owner >= 0) continue
            val neighbor = loose.filter {
                it.owner < 0 &&
                    abs(it.x-seed.x) < .010f &&
                    abs(it.y-seed.y) < .010f &&
                    abs(it.vx-seed.vx) < .030f &&
                    abs(it.vy-seed.vy) < .030f
            }
            if (neighbor.size < 16) continue
            val total = neighbor.sumOf { it.mass.toDouble() }
            if (total < .05) continue
            val id = nextId()
            for (p in neighbor) p.owner = id
            val mx = neighbor.sumOf { it.x*it.mass.toDouble() }/total
            val my = neighbor.sumOf { it.y*it.mass.toDouble() }/total
            val vx = neighbor.sumOf { it.vx*it.mass.toDouble() }/total
            val vy = neighbor.sumOf { it.vy*it.mass.toDouble() }/total
            centralRadius[id] = .015
            discovered.add(CelestialBody(id, 2,total.toFloat(),1f,
                mx.toFloat(),my.toFloat(),vx.toFloat(),vy.toFloat(),
                material=seed.material,lifetime=3600f))
            if (bodies.size+discovered.size >= maxBodies) break
        }
        return discovered
    }
}
