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
internal const val ASTRONOMICAL_G = .000052
private const val ORBITAL_SOFTENING_SQUARED = .008 * .008

internal fun circularOrbitSpeed(mass: Float, distance: Float, gravity: Float = 1f): Float {
    val r = distance.toDouble()
    val soft = r * r + ORBITAL_SOFTENING_SQUARED
    return sqrt(ASTRONOMICAL_G * gravity * mass * r * r / (soft * sqrt(soft))).toFloat()
}

internal class AstronomicalUniverse {
    val grains: MutableList<SandGrain> = ArrayList()
    var mode: MatterMode = MatterMode.BALANCED
        private set

    private val luminousOwners = HashSet<Int>()
    private val cometOwners = HashSet<Int>()
    private val contactSize = .0072
    private val maxContactRange = .0060
    private val cellMask = 16383
    private val cellHeads = IntArray(cellMask + 1)
    private var next = IntArray(0)
    private var cxCell = IntArray(0)
    private var cyCell = IntArray(0)
    private var fx = DoubleArray(0)
    private var fy = DoubleArray(0)
    private var ownCenters = arrayOfNulls<Center>(0)
    private var startX = FloatArray(0)
    private var startY = FloatArray(0)
    private val centerPool = ArrayList<Center>()
    private var speedSquared = 0f
    var lastImpact: ImpactFlash? = null
        private set
    private var impactX = 0f
    private var impactY = 0f
    private var impactEnergy = 0.0
    private var waveSerial = -1
    private var previousWaveRadius = -.001f
    private var waveSource: SandGrain? = null
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

    private class Center(var id: Int) {
        var mass = 0.0
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var r = .012
        var fieldMass = 0.0
    }

    private val centers = ArrayList<Center>()
    private val centersById = HashMap<Int, Center>()

    fun updateMode(value: MatterMode) { mode = value }

    data class Snapshot(
        val values: FloatArray,
        val heat: DoubleArray,
        val metadata: IntArray,
        val radii: Map<Int, Double>,
        val luminousOwners: Set<Int>,
        val cometOwners: Set<Int>
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
        return Snapshot(v, h, m, HashMap(centralRadius), HashSet(luminousOwners), HashSet(cometOwners))
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
        centralRadius.putAll(frame.radii)
        luminousOwners.clear()
        luminousOwners.addAll(frame.luminousOwners)
        cometOwners.clear()
        cometOwners.addAll(frame.cometOwners)
        pulseKey = -1
        waveSerial = -1
        waveSource = null
        speedSquared = 0f
    }

    fun releaseOwners(owners: Set<Int>) {
        for (p in grains) if (p.owner in owners) p.owner = -1
        for (owner in owners) centralRadius.remove(owner)
        luminousOwners.removeAll(owners)
        cometOwners.removeAll(owners)
    }

    fun renderFrame(): FloatArray {
        val out = FloatArray(grains.size * 4)
        for (i in grains.indices) {
            val p = grains[i]; val k = i * 4
            out[k] = p.x; out[k+1] = p.y
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
        luminousOwners.clear()
        cometOwners.clear()
        nextId = 1
        pulseKey = -1
        waveSerial = -1
        waveSource = null
        speedSquared = 0f
        lastImpact = null
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
        val n = min(requested, mode.maxParticles - grains.size)
        if (n <= 0) return
        val radiusPerGrain = (.00135 *
            sqrt((body.mass / n).coerceAtLeast(.00001f) / .01f))
            .coerceIn(.00042, .0028)
        val radius = max(structureRadius(body).toDouble(),
            radiusPerGrain * sqrt(n / .72) * 1.08)
        val angleStep = PI * (3.0 - sqrt(5.0))
        centralRadius[body.id] = radius
        if (body.stellarClass == StellarClass.STAR || body.stellarClass == StellarClass.QUASAR)
            luminousOwners.add(body.id)
        if (body.isComet) cometOwners.add(body.id)
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
                        body.material == MatterMaterial.AMMONIA && portion < 43 -> MatterMaterial.AMMONIA
                        portion < 44 -> MatterMaterial.HYDROGEN
                        portion < 68 -> MatterMaterial.HELIUM
                        portion < 85 -> MatterMaterial.METHANE
                        else -> MatterMaterial.AMMONIA
                    }
                    MatterMaterial.WATER -> when {
                        radial < .29 -> if (portion < 54) MatterMaterial.IRON else MatterMaterial.BASALT
                        radial > .83 && portion < 26 -> MatterMaterial.STEAM
                        portion < 72 -> MatterMaterial.WATER
                        portion < 87 -> MatterMaterial.ICE
                        else -> MatterMaterial.MINERAL
                    }
                    MatterMaterial.CARBON -> when {
                        radial < .36 -> MatterMaterial.IRON
                        portion < 55 -> MatterMaterial.CARBON
                        portion < 78 -> MatterMaterial.CRYSTAL
                        else -> MatterMaterial.SILICATE
                    }
                    MatterMaterial.CRYSTAL -> when {
                        radial < .28 -> MatterMaterial.NICKEL
                        portion < 67 -> MatterMaterial.CRYSTAL
                        portion < 85 -> MatterMaterial.MINERAL
                        else -> MatterMaterial.ICE
                    }
                    MatterMaterial.ICE -> when {
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
            val capacity = max(n, fx.size + 256)
            fx = DoubleArray(capacity); fy = DoubleArray(capacity)
            next = IntArray(capacity); cxCell = IntArray(capacity); cyCell = IntArray(capacity)
            ownCenters = arrayOfNulls(capacity)
            startX = FloatArray(capacity); startY = FloatArray(capacity)
        }
    }

    /** Recalculate each gravitational source from REAL particle masses. */
    private fun buildCenters() {
        ensureCapacity()
        centers.clear()
        centersById.clear()
        for (i in grains.indices) {
            val p = grains[i]
            if (p.owner < 0) {
                ownCenters[i] = null
                continue
            }
            val c = centersById.getOrPut(p.owner) {
                val slot = centers.size
                val center = if (slot < centerPool.size) centerPool[slot]
                    else Center(p.owner).also { centerPool.add(it) }
                center.id = p.owner
                center.mass = 0.0
                center.x = 0.0; center.y = 0.0
                center.vx = 0.0; center.vy = 0.0
                centers.add(center)
                center
            }
            ownCenters[i] = c
            val m = p.mass.toDouble()
            c.mass += m
            c.x += p.x*m; c.y += p.y*m
            c.vx += p.vx*m; c.vy += p.vy*m
        }
        for (c in centers) {
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

    private fun releaseEventHeat(event: CosmicEpisode) {
        if (event.kind == CosmicEventKind.COSMIC_SHOCK || event.kind == CosmicEventKind.SUPERNOVA) {
            advancePressureFront(event)
            return
        }
        if (event.kind == CosmicEventKind.QUIET ||
            event.kind == CosmicEventKind.METEORS || event.kind == CosmicEventKind.COMET_FLYBY) return
        val repeat = event.kind == CosmicEventKind.PULSAR ||
            event.kind == CosmicEventKind.QUASAR_JET ||
            event.kind == CosmicEventKind.MAGNETAR_FLARE ||
            event.kind == CosmicEventKind.SOLAR_WIND ||
            event.kind == CosmicEventKind.MAGNETIC_RECONNECTION
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
            CosmicEventKind.MAGNETAR_FLARE, CosmicEventKind.SOLAR_WIND,
            CosmicEventKind.MAGNETIC_RECONNECTION
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

    // A shell acts only on the matter it has reached. Thermal energy pays
    // for the kick and the source takes the equal and opposite recoil.
    private fun advancePressureFront(event: CosmicEpisode) {
        if (event.serial != waveSerial) {
            waveSerial = event.serial
            previousWaveRadius = -.001f
            waveSource = if (event.sourceId != null)
                grains.filter { it.owner == event.sourceId }.maxByOrNull { it.heat } else null
            if (waveSource == null) waveSource = grains.minByOrNull {
                val dx = it.x - event.x; val dy = it.y - event.y
                dx * dx + dy * dy
            }
        }
        val radius = event.pressureRadius
        if (radius <= previousWaveRadius) return
        val core = waveSource ?: return
        for (p in grains) {
            if (p === core) continue
            val dx = p.x - event.x
            val dy = p.y - event.y
            val r2 = dx * dx + dy * dy
            if (r2 > radius * radius || r2 < 1e-10f ||
                previousWaveRadius > 0f && r2 <= previousWaveRadius * previousWaveRadius) continue
            val r = sqrt(r2)
            val nx = dx / r
            val ny = dy / r
            val relative = (p.vx - core.vx) * nx + (p.vy - core.vy) * ny
            val energy = (p.heat * .028 + core.heat * .012) * event.intensity.heatFactor /
                (1.0 + r * 9.0)
            val inverseMass = 1.0 / p.mass + 1.0 / core.mass
            val speed = abs(relative.toDouble())
            val impulse = 2.0 * energy / (sqrt(speed * speed + 2.0 * inverseMass * energy) + speed)
            if (!impulse.isFinite() || impulse <= 0.0) continue
            val work = relative * impulse + .5 * inverseMass * impulse * impulse
            if (work > 0) {
                val fraction = p.heat / (p.heat + core.heat)
                p.heat -= work * fraction
                core.heat -= work * (1 - fraction)
            } else {
                p.heat -= work * .5
                core.heat -= work * .5
            }
            p.vx += (impulse * nx / p.mass).toFloat()
            p.vy += (impulse * ny / p.mass).toFloat()
            core.vx -= (impulse * nx / core.mass).toFloat()
            core.vy -= (impulse * ny / core.mass).toFloat()
        }
        previousWaveRadius = radius
    }

    private fun accelerations(gravity: Float) {
        java.util.Arrays.fill(fx, 0, grains.size, 0.0)
        java.util.Arrays.fill(fy, 0, grains.size, 0.0)
        gravityVisits = 0
        val g = ASTRONOMICAL_G * gravity
        val event = activeEpisode
        val boost = when (event.kind) {
            CosmicEventKind.TIDAL_PULSE -> .21
            CosmicEventKind.SINGULARITY -> .42
            CosmicEventKind.TIDAL_DISRUPTION -> .92 * event.alpha
            else -> 0.0
        }
        for (c in centers) {
            val linked = if (event.sourceId != null) c.id == event.sourceId
                else (c.x-event.x)*(c.x-event.x) + (c.y-event.y)*(c.y-event.y) < .055*.055
            c.fieldMass = g * c.mass * if (boost > 0 && linked)
                1.0 + boost * event.intensity.heatFactor else 1.0
        }
        val beginning = System.nanoTime()
        // Every source is one MONOPOLE representing the mass of a genuine
        // connected particle cloud, never a second copy of that matter.
        for (i in grains.indices) {
            val p = grains[i]
            val own = ownCenters[i]
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
            for (j in centers.indices) {
                val c = centers[j]
                if (c === own) continue
                val dx = c.x - p.x; val dy = c.y - p.y
                val r2 = dx*dx+dy*dy+ORBITAL_SOFTENING_SQUARED
                val inv = 1.0 / (r2 * sqrt(r2))
                fx[i] += c.fieldMass * dx * inv
                fy[i] += c.fieldMass * dy * inv
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

    private fun collisions(h: Double, swept: Boolean) {
        val beginning = System.nanoTime()
        indexContacts()
        contactVisits = 0
        val maxVisits = if (grains.size > 2000) 60 else 125
        for (i in grains.indices) {
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
                        if (other <= i) break
                        if (cxCell[other] != cx || cyCell[other] != cy) continue
                        val q = grains[other]
                        var dx = q.x-p.x; var dy = q.y-p.y
                        val radii = p.collisionRadius + q.collisionRadius
                        val contact2 = radii * radii
                        val r2 = dx*dx + dy*dy
                        var impactFraction = 1.0
                        val close = abs(dx) <= maxContactRange && abs(dy) <= maxContactRange
                        val eligible = swept &&
                            ((p.vx-q.vx)*(p.vx-q.vx) + (p.vy-q.vy)*(p.vy-q.vy) > .115f*.115f)
                        if (!close && !eligible) continue
                        if (eligible) {
                            val priorX = (startX[other] - startX[i]).toDouble()
                            val priorY = (startY[other] - startY[i]).toDouble()
                            val moveX = dx - priorX
                            val moveY = dy - priorY
                            val span = moveX * moveX + moveY * moveY
                            val rate = priorX * moveX + priorY * moveY
                            val offset = priorX * priorX + priorY * priorY - contact2
                            if (rate < 0 && offset <= 0 && priorX * dx + priorY * dy <= 0) {
                                val normalLength = sqrt(offset + contact2)
                                if (normalLength > 1e-7) {
                                    impactFraction = 0.0
                                    dx = (radii * priorX / normalLength).toFloat()
                                    dy = (radii * priorY / normalLength).toFloat()
                                }
                            } else if (r2 >= contact2 && span > 1e-12 && rate < 0 && offset > 0) {
                                val discriminant = rate * rate - span * offset
                                if (discriminant >= 0) {
                                    val crossing = (-rate - sqrt(discriminant)) / span
                                    if (crossing in 0.0..<1.0) {
                                        impactFraction = crossing
                                        dx = (priorX + crossing * moveX).toFloat()
                                        dy = (priorY + crossing * moveY).toFloat()
                                    }
                                }
                            }
                        }
                        if (!close && impactFraction == 1.0) continue
                        val loosePair = p.owner < 0 && q.owner < 0
                        if (!loosePair && r2 >= contact2 && impactFraction == 1.0) continue
                        contactVisits++
                        val r = if (impactFraction < 1.0) radii else sqrt(max(r2.toDouble(), 1e-16))
                        // Dust that has escaped any gravitational core can
                        // accrete into new bodies via *mutual*, bounded
                        // attraction. This is never a pull toward a preset
                        // center and never creates a new grain or mass.
                        if (loosePair && r > 1e-7 && r < .006 &&
                            (p.vx-q.vx)*(p.vx-q.vx) + (p.vy-q.vy)*(p.vy-q.vy) < .065f*.065f) {
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
                        if (r2 >= contact2 && impactFraction == 1.0) continue
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
                            val inverseMass = invA + invB
                            val impulse = -(1+e)*closing/inverseMass
                            val tangentSpeed = -(q.vx-p.vx)*ny + (q.vy-p.vy)*nx
                            val friction = if (p.material.gas || q.material.gas) .035 else .24
                            val slide = (-tangentSpeed / inverseMass)
                                .coerceIn(-friction * impulse, friction * impulse)
                            val impulseX = impulse*nx - slide*ny
                            val impulseY = impulse*ny + slide*nx
                            p.vx = (p.vx-impulseX*invA).toFloat()
                            p.vy = (p.vy-impulseY*invA).toFloat()
                            q.vx = (q.vx+impulseX*invB).toFloat()
                            q.vy = (q.vy+impulseY*invB).toFloat()
                            val converted = (.5*(1-e*e)*closing*closing/inverseMass -
                                slide*tangentSpeed - .5*inverseMass*slide*slide).coerceAtLeast(0.0)
                            p.heat += converted*.5
                            q.heat += converted*.5
                            if (impactFraction < 1.0) {
                                val remainder = (1.0 - impactFraction) * h
                                p.x -= (impulseX * invA * remainder).toFloat()
                                p.y -= (impulseY * invA * remainder).toFloat()
                                q.x += (impulseX * invB * remainder).toFloat()
                                q.y += (impulseY * invB * remainder).toFloat()
                            }
                            if (p.owner != q.owner && abs(closing) > .075 &&
                                converted > impactEnergy) {
                                impactEnergy = converted
                                impactX = (p.x + q.x) * .5f
                                impactY = (p.y + q.y) * .5f
                            }
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
        lastImpact = null
        impactEnergy = 0.0
        val duration = min(dt, 1f / 15f)
        if (tick == 0) speedSquared = grains.maxOf { it.vx * it.vx + it.vy * it.vy }
        val fast = speedSquared > .14f * .14f
        val baseStep = if (fast && grains.size < 1600) 1f / 90f
            else if (fast || grains.size < 1000) 1f / 60f else 1f / 30f
        val substeps = kotlin.math.ceil(duration / baseStep).toInt().coerceIn(1, 5)
        val h = duration.toDouble() / substeps
        speedSquared = 0f
        var gm = .0; var cm = .0
        for (part in 0 until substeps) {
            buildCenters()
            accelerations(gravity)
            gm += lastGravityMs
            gravityRebuilds += if (tick % 2 == 0) 1 else 0
            for (i in grains.indices) {
                val p = grains[i]
                if (fast) {
                    startX[i] = p.x
                    startY[i] = p.y
                }
                val ax = fx[i].coerceIn(-.75, .75)
                val ay = fy[i].coerceIn(-.75, .75)
                p.vx = (p.vx+ax*h).toFloat()
                p.vy = (p.vy+ay*h).toFloat()
                // Velocity cap is a numerical guardrail, not a hidden
                // nonconservative damping force. Normal bound orbits are
                // far below this scale.
                val speed2 = p.vx*p.vx+p.vy*p.vy
                speedSquared = max(speedSquared, speed2)
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
            collisions(h, fast)
            cm += lastContactMs
        }
        lastGravityMs = gm; lastContactMs = cm
        if (impactEnergy > .000004) lastImpact = ImpactFlash(impactX, impactY,
            (sqrt(impactEnergy) * 80.0).toFloat().coerceIn(.22f, 1f))
        tick++
        if (tick % 8 == 0) for (p in grains) phaseChange(p)
        if (tick % 4 == 0 && cometOwners.isNotEmpty() && luminousOwners.isNotEmpty())
            sublimateComets()
        if (tick % 20 == 0 || episode.kind == CosmicEventKind.TIDAL_DISRUPTION && tick % 5 == 0) {
            buildCenters()
            // Real ejecta are allowed but quiet material stays in clouds.
            for (p in grains) {
                val c = centersById[p.owner] ?: continue
                val distance = hypot(p.x-c.x, p.y-c.y)
                val tidal = episode.kind == CosmicEventKind.TIDAL_DISRUPTION &&
                    c.id != episode.sourceId && hypot(c.x-episode.x, c.y-episode.y) < episode.intensity.radius
                if (distance <= c.r * (if (tidal) 1.35 else 2.6)) continue
                val speed = hypot(p.vx-c.vx, p.vy-c.vy)
                val escape = sqrt(2.0 * ASTRONOMICAL_G * gravity * c.mass / max(distance, .008))
                val minimum = if (tidal) .023 else if (p.material.gas) .10 else .16
                if (speed > max(minimum, escape)) p.owner = -1
            }
        }
    }

    // Irradiated surface ice can sublimate and leave a tail of actual
    // vapor. The luminous grains supply the latent heat and take the recoil.
    private fun sublimateComets() {
        buildCenters()
        val donor = grains.filter { it.owner in luminousOwners }.maxByOrNull { it.heat } ?: return
        var released = 0
        for (p in grains) {
            if (p.owner !in cometOwners || p.material != MatterMaterial.ICE) continue
            val dx = p.x - donor.x
            val dy = p.y - donor.y
            val d2 = dx * dx + dy * dy
            if (d2 !in .0001f.. .026f) continue
            val comet = centersById[p.owner] ?: continue
            if ((p.x - comet.x) * dx + (p.y - comet.y) * dy >= 0) continue
            if ((p.id + tick / 4) % 9 != 0) continue
            val latent = (thermalPotential(MatterMaterial.STEAM) - thermalPotential(p.material)) * p.mass
            val speed = .058f
            val inverseMass = 1.0 / p.mass + 1.0 / donor.mass
            val impulse = speed / inverseMass
            val distance = sqrt(d2)
            val nx = dx / distance; val ny = dy / distance
            val relative = (p.vx - donor.vx) * nx + (p.vy - donor.vy) * ny
            val work = relative * impulse + .5 * inverseMass * impulse * impulse
            // Keep newly released vapor above the condensation threshold.
            val heat = max(0.0, p.mass * .00032 - p.heat)
            if (donor.heat < max(latent + heat + work, latent + heat)) continue
            donor.heat -= latent + heat + work
            p.heat += heat
            p.material = MatterMaterial.STEAM
            p.owner = -1
            p.vx += (nx * impulse / p.mass).toFloat()
            p.vy += (ny * impulse / p.mass).toFloat()
            donor.vx -= (nx * impulse / donor.mass).toFloat()
            donor.vy -= (ny * impulse / donor.mass).toFloat()
            phaseTransitions++
            if (++released >= 4) break
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
        val g = ASTRONOMICAL_G * gravity
        for (i in grains.indices) for (j in i+1 until grains.size) {
            val a = grains[i]; val b = grains[j]
            val dx = (a.x-b.x).toDouble()
            val dy = (a.y-b.y).toDouble()
            result -= g*a.mass*b.mass /
                sqrt(dx*dx+dy*dy+ORBITAL_SOFTENING_SQUARED)
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

    fun updateOwnership(
        bodies: List<CelestialBody>, maxBodies: Int, nextId: () -> Int
    ): List<CelestialBody>? {
        if (tick % 12 != 0 && tick % 42 != 0 && tick % 100 != 0) return null
        if (tick % 12 == 0) relabelCaptured()
        val current = summarizeBodies(bodies)
        val newborn = if (tick % 100 == 0) discoverClumps(current, maxBodies, nextId) else emptyList()
        return if (tick % 42 == 0) mergeCaptureLabels(current + newborn) else current + newborn
    }

    private fun relabelCaptured() {
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

    private fun mergeCaptureLabels(bodies: List<CelestialBody>): List<CelestialBody> {
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
            centralRadius.remove(loser)
            luminousOwners.remove(loser)
            cometOwners.remove(loser)
            if (winner == a.id) retained[j] = b.copy(mass=0f)
            else retained[i] = a.copy(mass=0f)
        }
        return retained.filter { it.mass > 0 }
    }

    /** Cloud discovery is merely naming of physically captured debris. */
    private fun discoverClumps(
        bodies: List<CelestialBody>, maxBodies: Int, nextId: () -> Int
    ): List<CelestialBody> {
        if (bodies.size >= maxBodies) return emptyList()
        val loose = grains.filter { it.owner < 0 }
        if (loose.size < 16) return emptyList()
        val discovered = ArrayList<CelestialBody>()
        val neighbor = ArrayList<SandGrain>()
        for (i in 0 until min(loose.size, 160)) {
            val seed = loose[i]
            if (seed.owner >= 0) continue
            neighbor.clear()
            for (p in loose) if (p.owner < 0 && abs(p.x-seed.x) < .010f &&
                abs(p.y-seed.y) < .010f && abs(p.vx-seed.vx) < .030f &&
                abs(p.vy-seed.vy) < .030f) neighbor.add(p)
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
