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
 * Genuine moving grains. There are no fixed local coordinates, springs to a
 * prescribed silhouette, particle emitters or implicit mergers in this solver.
 * Every grain carries the same mass before and after a collision.
 *
 * Gravity: softened inverse-square, approximated with a Barnes-Hut quadtree.
 * Contact: pairwise repulsive potential + inelastic normal impulse. Kinetic
 * energy dissipated by an impulse is explicitly moved to internal heat.
 * Integrator: kick-drift-kick, subdivided to resolve short range contacts.
 *
 * The integer owner is a display/selection label only. It NEVER exerts forces
 * and is never used to move or position a particle.
 */
internal data class SandGrain(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val mass: Float,
    var heat: Double,
    var material: MatterMaterial,
    var owner: Int = -1
) {
    // Physical mass is immutable, so both radii are immutable as well.
    // Recomputing sqrt(mass) for EVERY neighboring pair was a major cost
    // in dense 4600+ clouds; now the square roots run only at grain creation.
    val collisionRadius: Double =
        (.00135 * sqrt(mass.coerceAtLeast(.00001f) / .01f))
            .coerceIn(.00042, .0028)
    val visualRadius: Float =
        (.75f + sqrt(mass) * 4f).coerceIn(.65f, 1.55f)
}

internal data class GrainTotals(
    val mass: Double, val momentumX: Double, val momentumY: Double,
    val kinetic: Double, val internal: Double,
    val chemical: Double = 0.0
)

internal class GranularUniverse {
    val grains: MutableList<SandGrain> = ArrayList()
    var mode: MatterMode = MatterMode.BALANCED
        private set

    private val gravityConstant = 0.000028
    private val softeningSquared = .0032 * .0032
    private val contactStiffness = 3.2
    // No per-contact trig. When exact coordinates coincide, pick one of a
    // fixed set of equal-and-opposite normal directions by particle IDs.
    private val overlapNormalX = DoubleArray(32) {
        cos(2.0 * PI * (it + .5) / 32.0)
    }
    private val overlapNormalY = DoubleArray(32) {
        sin(2.0 * PI * (it + .5) / 32.0)
    }
    var lastGravityMs = 0.0
        private set
    var lastContactMs = 0.0
        private set
    private var gravityTimeNs = 0L
    private var contactTimeNs = 0L
    private val specificHeat = 0.000001
    private var currentEvent = CosmicEpisode()
    private val thermoInterval = 8
    private var nextGrainId = 1
    private var pulseSerial = -1
    private var tick = 0
    private var ax = DoubleArray(0)
    private var ay = DoubleArray(0)
    private var gx = DoubleArray(0)
    private var gy = DoubleArray(0)
    var gravityRebuilds: Long = 0
        private set
    private var gridNext = IntArray(0)
    private var cellX = IntArray(0)
    private var cellY = IntArray(0)
    // Hashing UNBOUNDED signed cell coordinates avoids piling offscreen
    // particles into a single clamped edge bucket (formerly an O(N²) freeze).
    private val gridCellSize = .0064
    private val gridHashMask = 16383
    private val heads = IntArray(gridHashMask + 1)
    private var contactChecks = 0L
    private var gravityNodesVisited = 0L
    val counters: Pair<Long, Long> get() = contactChecks to gravityNodesVisited

    // Primitive quadtree buffers, reused across physics steps.
    private var maxNodes = 0
    private var nodeCount = 0
    private var nodeCx = DoubleArray(0)
    private var nodeCy = DoubleArray(0)
    private var nodeHalf = DoubleArray(0)
    private var nodeMass = DoubleArray(0)
    private var nodeMx = DoubleArray(0)
    private var nodeMy = DoubleArray(0)
    private var nodeComX = DoubleArray(0)
    private var nodeComY = DoubleArray(0)
    private val traversalStack = IntArray(128)
    private var nodeIndex = IntArray(0)
    private var child = IntArray(0)
    private var root = 0

    fun updateMode(value: MatterMode) { mode = value }

    // Stored history is packed primitives instead of hundreds of thousands
    // of SandGrain copies retained across the rolling replay window.
    data class Snapshot(
        val values: FloatArray, val heat: DoubleArray, val metadata: IntArray
    )
    fun snapshot(): Snapshot {
        val values = FloatArray(grains.size * 5)
        val heat = DoubleArray(grains.size)
        val meta = IntArray(grains.size * 3)
        for (i in grains.indices) {
            val p = grains[i]; val v = i * 5; val m = i * 3
            values[v] = p.x; values[v+1] = p.y
            values[v+2] = p.vx; values[v+3] = p.vy; values[v+4] = p.mass
            heat[i] = p.heat
            meta[m] = p.id; meta[m+1] = p.owner
            meta[m+2] = p.material.ordinal
        }
        return Snapshot(values, heat, meta)
    }
    fun restore(snapshot: Snapshot) {
        grains.clear()
        val n = snapshot.heat.size
        (grains as ArrayList<SandGrain>).ensureCapacity(n)
        for (i in 0 until n) {
            val v = i * 5; val m = i * 3
            grains.add(SandGrain(
                snapshot.metadata[m], snapshot.values[v], snapshot.values[v+1],
                snapshot.values[v+2], snapshot.values[v+3], snapshot.values[v+4],
                snapshot.heat[i], MatterMaterial.entries[snapshot.metadata[m+2]],
                snapshot.metadata[m+1]
            ))
        }
        nextGrainId = (grains.maxOfOrNull { it.id } ?: 0) + 1
    }

    /** Immutable primitive frame for safe rendering while physics runs off UI. */
    fun renderFrame(): FloatArray {
        val frame = FloatArray(grains.size * 4)
        for (i in grains.indices) {
            val p = grains[i]; val j = i * 4
            frame[j] = p.x
            frame[j+1] = p.y
            frame[j+2] = p.visualRadius
            frame[j+3] = p.material.ordinal.toFloat()
        }
        return frame
    }

    /** Explicit finger force; particles are never emitted by gestures. */
    fun perturb(x: Float, y: Float, strength: Float) {
        for (p in grains) {
            val dx = (p.x - x).toDouble()
            val dy = (p.y - y).toDouble()
            val dist = sqrt(dx*dx + dy*dy)
            // A fingertip's gravitational pulse has a soft falloff. A larger
            // influence radius lets taps steer nearby planets even when their
            // visible grains are clustered away from the exact touch point.
            if (dist > .38 || dist < 1e-8) continue
            val kick = strength * (1.0 - dist / .38)
            p.vx = (p.vx + kick * dx / dist).toFloat()
            p.vy = (p.vy + kick * dy / dist).toFloat()
        }
    }
    fun clear() {
        grains.clear()
        nextGrainId = 1
        tick = 0
        pulseSerial = -1
    }

    /** Explicit initial or user-created objects only; never called by an impact. */
    fun seed(body: CelestialBody, requested: Int = if (mode ==
        MatterMode.DETAILED) 300 else 76) {
        if (grains.size >= mode.maxParticles) return
        val n = min(requested, mode.maxParticles - grains.size)
        if (n <= 0) return
        val radius = structureRadius(body) * 1.35
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        for (i in 0 until n) {
            // Deterministic disc packing. No egg shape or pinned lattice:
            // every point is integrated independently from the first frame.
            val angle = goldenAngle * i
            val r = radius * sqrt((i + .5) / n.toDouble())
            val rx = r * cos(angle)
            val ry = r * sin(angle)
            val type = when (body.stellarClass) {
                StellarClass.STAR, StellarClass.QUASAR ->
                    if (i % 5 == 0) MatterMaterial.HELIUM
                    else if (i % 7 == 0) MatterMaterial.PLASMA
                    else MatterMaterial.HYDROGEN
                StellarClass.CLOUD -> if (i % 4 == 0) MatterMaterial.ICE
                    else body.material
                StellarClass.PLANET -> when (i % 13) {
                    0 -> MatterMaterial.IRON
                    1 -> MatterMaterial.SILICATE
                    2 -> MatterMaterial.CARBON
                    else -> body.material
                }
            }
            grains.add(SandGrain(
                nextGrainId++, body.x + rx.toFloat(), body.y + ry.toFloat(),
                body.vx, body.vy, body.mass / n,
                body.mass.toDouble() / n * specificHeat *
                    body.temperature.coerceAtLeast(1f), type, body.id
            ))
        }
    }

    /** Mass, momentum and internal energy of actual grains, no proxy bodies. */
    fun totals(): GrainTotals {
        var mass = 0.0; var px = 0.0; var py = 0.0
        var kinetic = 0.0; var internal = 0.0; var chemical = 0.0
        for (p in grains) {
            val m = p.mass.toDouble()
            mass += m
            px += m * p.vx; py += m * p.vy
            kinetic += .5 * m * (p.vx * p.vx + p.vy * p.vy)
            internal += p.heat
            chemical += chemicalPotential(p.material) * m
        }
        return GrainTotals(mass, px, py, kinetic, internal, chemical)
    }

    /** Expensive exact energy diagnostic, intentionally outside the frame loop. */
    fun totalEnergy(gravity: Float): Double {
        val t = totals()
        var energy = t.kinetic + t.internal + t.chemical
        val g = gravityConstant * gravity
        for (i in grains.indices) for (j in i + 1 until grains.size) {
            val a = grains[i]; val b = grains[j]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val dist = sqrt(dx*dx + dy*dy + softeningSquared)
            energy -= g * a.mass.toDouble() * b.mass / dist
            val overlap = grainRadius(a) + grainRadius(b) - hypot(dx, dy)
            if (overlap > 0) energy += .5 * contactStiffness * overlap * overlap
        }
        return energy
    }

    // Specific phase enthalpies, measured in the same toy energy units as
    // kinetic energy. Heat + phase enthalpy remains invariant on transitions.
    private fun chemicalPotential(m: MatterMaterial): Double = when (m) {
        MatterMaterial.WATER -> .000030
        MatterMaterial.STEAM -> .000150
        MatterMaterial.PLASMA -> .000220
        MatterMaterial.GLASS -> .000090
        else -> 0.0
    }

    private fun phaseChange(p: SandGrain) {
        val temperature = p.heat / (p.mass * specificHeat)
        val next = when (p.material) {
            MatterMaterial.ICE -> if (temperature >= 290.0)
                MatterMaterial.WATER else null
            MatterMaterial.WATER -> when {
                temperature > 420.0 -> MatterMaterial.STEAM
                temperature < 260.0 -> MatterMaterial.ICE
                else -> null
            }
            MatterMaterial.STEAM -> if (temperature < 295.0)
                MatterMaterial.WATER else null
            MatterMaterial.HYDROGEN -> if (temperature > 4000.0)
                MatterMaterial.PLASMA else null
            MatterMaterial.PLASMA -> if (temperature < 2500.0)
                MatterMaterial.HYDROGEN else null
            MatterMaterial.SILICATE -> if (temperature > 1700.0)
                MatterMaterial.GLASS else null
            MatterMaterial.GLASS -> if (temperature < 1400.0)
                MatterMaterial.SILICATE else null
            else -> null
        } ?: return
        val delta = (chemicalPotential(next) -
            chemicalPotential(p.material)) * p.mass
        if (delta > p.heat) return // Cannot spend energy we do not have.
        p.heat = (p.heat - delta).coerceAtLeast(0.0)
        p.material = next
    }

    private fun grainRadius(p: SandGrain): Double = p.collisionRadius

    private fun capacity() {
        val n = grains.size
        if (ax.size < n) {
            ax = DoubleArray(n); ay = DoubleArray(n)
            gx = DoubleArray(n); gy = DoubleArray(n)
            gridNext = IntArray(n)
            cellX = IntArray(n)
            cellY = IntArray(n)
        }
        val required = max(128, n * 10)
        if (maxNodes < required) {
            maxNodes = required
            nodeCx = DoubleArray(maxNodes); nodeCy = DoubleArray(maxNodes)
            nodeHalf = DoubleArray(maxNodes)
            nodeMass = DoubleArray(maxNodes)
            nodeMx = DoubleArray(maxNodes); nodeMy = DoubleArray(maxNodes)
            nodeComX = DoubleArray(maxNodes); nodeComY = DoubleArray(maxNodes)
            nodeIndex = IntArray(maxNodes)
            child = IntArray(maxNodes * 4)
        }
    }

    private fun newNode(x: Double, y: Double, half: Double): Int {
        val id = nodeCount++
        check(id < maxNodes) { "Quadtree capacity exceeded" }
        nodeCx[id] = x; nodeCy[id] = y; nodeHalf[id] = half
        nodeMass[id] = 0.0; nodeMx[id] = 0.0; nodeMy[id] = 0.0
        nodeIndex[id] = -1
        val base = id * 4
        for (k in 0..3) child[base + k] = -1
        return id
    }

    private fun quadrant(node: Int, p: SandGrain): Int =
        (if (p.x.toDouble() >= nodeCx[node]) 1 else 0) or
            (if (p.y.toDouble() >= nodeCy[node]) 2 else 0)

    private fun insertInto(node: Int, index: Int, depth: Int) {
        val p = grains[index]
        val mass = p.mass.toDouble()
        nodeMass[node] += mass
        nodeMx[node] += mass * p.x
        nodeMy[node] += mass * p.y
        if (nodeIndex[node] == -1 && child[node*4] == -1 &&
            child[node*4+1] == -1 && child[node*4+2] == -1 &&
            child[node*4+3] == -1) {
            nodeIndex[node] = index
            return
        }
        if (depth >= 22 || nodeHalf[node] < 1e-9) {
            nodeIndex[node] = -2  // Degenerate aggregated bucket.
            return
        }
        val previous = nodeIndex[node]
        if (previous >= 0) {
            nodeIndex[node] = -1
            insertChild(node, previous, depth)
        }
        insertChild(node, index, depth)
    }

    private fun insertChild(node: Int, index: Int, depth: Int) {
        val quad = quadrant(node, grains[index])
        val offset = node * 4 + quad
        var c = child[offset]
        if (c < 0) {
            val half = nodeHalf[node] * .5
            c = newNode(
                nodeCx[node] + (if (quad and 1 != 0) half else -half),
                nodeCy[node] + (if (quad and 2 != 0) half else -half),
                half
            )
            child[offset] = c
        }
        insertInto(c, index, depth + 1)
    }

    private fun buildTree() {
        nodeCount = 0
        if (grains.isEmpty()) return
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (p in grains) {
            minX = min(minX, p.x.toDouble()); maxX = max(maxX, p.x.toDouble())
            minY = min(minY, p.y.toDouble()); maxY = max(maxY, p.y.toDouble())
        }
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(.02) * .501
        root = newNode((minX + maxX) * .5, (minY + maxY) * .5, span)
        for (i in grains.indices) insertInto(root, i, 0)
        // Predivide once per node, not on every visit from every grain.
        for (i in 0 until nodeCount) {
            if (nodeMass[i] > 0.0) {
                nodeComX[i] = nodeMx[i] / nodeMass[i]
                nodeComY[i] = nodeMy[i] / nodeMass[i]
            }
        }
    }

    /**
     * Iterative Barnes-Hut walk with a cached centre of mass. Squared
     * opening-angle test avoids an extra sqrt at every internal node.
     * A single sqrt is evaluated only when a multipole contributes force.
     */
    private fun addTreeGravity(index: Int, g: Double, theta2: Double) {
        val p = grains[index]
        var count = 1
        traversalStack[0] = root
        while (count > 0) {
            val node = traversalStack[--count]
            gravityNodesVisited++
            val mass = nodeMass[node]
            if (mass <= 0.0 || nodeIndex[node] == index) continue
            val dx = nodeComX[node] - p.x
            val dy = nodeComY[node] - p.y
            val r2 = dx*dx + dy*dy + softeningSquared
            val leaf = nodeIndex[node] != -1
            val half = nodeHalf[node]
            val inside = !leaf &&
                abs(p.x.toDouble() - nodeCx[node]) <= half &&
                abs(p.y.toDouble() - nodeCy[node]) <= half
            if (leaf || (!inside && half*half*4 < theta2*r2)) {
                val inv = 1.0 / (r2 * sqrt(r2))
                ax[index] += g * mass * dx * inv
                ay[index] += g * mass * dy * inv
            } else {
                for (q in 0..3) {
                    val c = child[node*4+q]
                    if (c >= 0) {
                        check(count < traversalStack.size)
                        traversalStack[count++] = c
                    }
                }
            }
        }
    }

    private fun cellCoord(value: Float): Int =
        kotlin.math.floor(value.toDouble() / gridCellSize).toInt()

    private fun bucket(cx: Int, cy: Int): Int =
        ((cx * 73856093) xor (cy * 19349663)) and gridHashMask

    private fun indexCells() {
        java.util.Arrays.fill(heads, -1)
        for (i in grains.indices) {
            val p = grains[i]
            val x = cellCoord(p.x)
            val y = cellCoord(p.y)
            cellX[i] = x
            cellY[i] = y
            val slot = bucket(x, y)
            gridNext[i] = heads[slot]
            heads[slot] = i
        }
    }

    private inline fun forNearby(
        action: (Int, Int, Double, Double, Double) -> Unit
    ) {
        forNearbyParticle@ for (i in grains.indices) {
            val a = grains[i]
            val cx = cellX[i]
            val cy = cellY[i]
            var contacts = 0
            for (y in cy - 1..cy + 1) {
                for (x in cx - 1..cx + 1) {
                    var j = heads[bucket(x, y)]
                    while (j >= 0) {
                        val other = j
                        j = gridNext[other]
                        // Every linked bucket is ordered by DESCENDING
                        // insertion index. Once <= i, no later pair is valid.
                        // This prevents the collapsed-cloud O(N²) tail scan.
                        if (other <= i) break
                        if (cellX[other] != x || cellY[other] != y) continue
                        val b = grains[other]
                        val dx = (b.x - a.x).toDouble()
                        if (abs(dx) > .0056) continue
                        val dy = (b.y - a.y).toDouble()
                        if (abs(dy) > .0056) continue
                        contactChecks++
                        val limit = grainRadius(a) + grainRadius(b)
                        val distance2 = dx * dx + dy * dy
                        if (distance2 < limit * limit) {
                            action(i, other, dx, dy, sqrt(distance2))
                            // Prevent unbounded work in a pathological fully
                            // collapsed cell. Each accepted pair is symmetric;
                            // skipping excess contacts cannot create momentum.
                            if (++contacts >= 32) continue@forNearbyParticle
                        }
                    }
                }
            }
        }
    }

    /**
     * A transient black-hole analogue: a handful of EXISTING central grains
     * create a stronger compact, pairwise and reciprocal gravitational field.
     * No external static attractor or new invisible mass is instantiated.
     * The changing event coupling does external work at its boundaries:
     * global energy is not exactly conserved while this anomaly is active.
     */
    private fun compactCoreGravity(event: CosmicEpisode, g: Double) {
        if (grains.size < 3) return
        val nearest = IntArray(8) { -1 }
        val distance = DoubleArray(8) { Double.POSITIVE_INFINITY }
        for (i in grains.indices) {
            val p = grains[i]
            val dx = p.x - event.x; val dy = p.y - event.y
            val r2 = (dx*dx + dy*dy).toDouble()
            if (r2 >= distance[7]) continue
            var k = 7
            while (k > 0 && r2 < distance[k-1]) {
                distance[k] = distance[k-1]
                nearest[k] = nearest[k-1]; k--
            }
            distance[k] = r2; nearest[k] = i
        }
        for (i in grains.indices) {
            val p = grains[i]
            val rr = hypot(p.x-event.x,p.y-event.y)
            if (rr > .30f) continue
            for (core in nearest) {
                if (core < 0 || core == i) continue
                val q = grains[core]
                val dx = (q.x-p.x).toDouble()
                val dy = (q.y-p.y).toDouble()
                val d2 = dx*dx+dy*dy+.0000008
                val force = g * 5.0 * p.mass * q.mass /
                    (d2 * sqrt(d2))
                val fx = force * dx; val fy = force * dy
                ax[i] += fx / p.mass
                ay[i] += fy / p.mass
                ax[core] -= fx / q.mass
                ay[core] -= fy / q.mass
            }
        }
    }

    private fun accelerations(gravity: Float, refreshGravity: Boolean) {
        if (refreshGravity) {
            val gravityStart = System.nanoTime()
            gravityRebuilds++
            gravityNodesVisited = 0
            java.util.Arrays.fill(ax, 0, grains.size, 0.0)
            java.util.Arrays.fill(ay, 0, grains.size, 0.0)
            buildTree()
            val theta = if (grains.size > 3500) .90 else
                if (grains.size > 2400) .78 else
                if (mode == MatterMode.DETAILED) .48 else .56
            val g = gravityConstant * gravity
            val theta2 = theta * theta
            for (i in grains.indices) addTreeGravity(i, g, theta2)
            if (currentEvent.kind == CosmicEventKind.SINGULARITY)
                compactCoreGravity(currentEvent, g)
            var mx = 0.0; var my = 0.0; var total = 0.0
            for (i in grains.indices) {
                val m = grains[i].mass.toDouble()
                mx += ax[i] * m; my += ay[i] * m; total += m
            }
            val cx = if (total > 0) mx / total else 0.0
            val cy = if (total > 0) my / total else 0.0
            for (i in grains.indices) {
                gx[i] = ax[i] - cx
                gy[i] = ay[i] - cy
            }
            gravityTimeNs += System.nanoTime() - gravityStart
        }
        java.lang.System.arraycopy(gx, 0, ax, 0, grains.size)
        java.lang.System.arraycopy(gy, 0, ay, 0, grains.size)
        val contactStart = System.nanoTime()
        contactChecks = 0
        indexCells()
        forNearby { i, j, dx, dy, d ->
            val a = grains[i]; val b = grains[j]
            val nx: Double
            val ny: Double
            if (d > 1e-9) { nx = dx/d; ny = dy/d }
            else {
                val slot = (a.id xor b.id) and 31
                nx = overlapNormalX[slot]
                ny = overlapNormalY[slot]
            }
            val overlap = grainRadius(a) + grainRadius(b) - d
            val force = contactStiffness * overlap
            ax[i] -= force * nx / a.mass
            ay[i] -= force * ny / a.mass
            ax[j] += force * nx / b.mass
            ay[j] += force * ny / b.mass

            // Resolve the impulse on THIS SAME broad-phase visit. This
            // removes an extra grid traversal and catches fast grains before
            // they cross through each other between substeps.
            val approaching = (b.vx-a.vx)*nx + (b.vy-a.vy)*ny
            if (approaching < 0) {
                val invA = 1.0/a.mass
                val invB = 1.0/b.mass
                val restitution = if (a.material.gas && b.material.gas)
                    .72 else .45
                val impulse = -(1.0+restitution)*approaching/(invA+invB)
                a.vx = (a.vx - impulse*invA*nx).toFloat()
                a.vy = (a.vy - impulse*invA*ny).toFloat()
                b.vx = (b.vx + impulse*invB*nx).toFloat()
                b.vy = (b.vy + impulse*invB*ny).toFloat()
                val lost = .5*(1.0-restitution*restitution) *
                    approaching*approaching/(invA+invB)
                a.heat += lost * .5
                b.heat += lost * .5
                if (abs(approaching) > .065 && a.owner != b.owner) {
                    a.owner = -1; b.owner = -1
                }
            }
            // Thermal conduction is likewise pairwise: moving heat between
            // two existing grains changes no energy or particle count.
            val amount = (a.heat/a.mass - b.heat/b.mass) *
                min(a.mass,b.mass) * .035
            val transfer = amount.coerceIn(-b.heat*.25, a.heat*.25)
            a.heat -= transfer
            b.heat += transfer
        }
        contactTimeNs += System.nanoTime() - contactStart
    }

    /**
     * A stellar nursery is a real cooling/dissipative gas encounter.
     * Existing neighboring gas packets exchange opposite velocity changes;
     * their lost relative kinetic energy becomes heat, while gravity can
     * subsequently assemble the slower cloud. No mass is created.
     */
    private fun settleNurseryGas(event: CosmicEpisode) {
        var previous = -1
        for (i in grains.indices) {
            val a = grains[i]
            if (!a.material.gas ||
                hypot(a.x-event.x, a.y-event.y) > .45f) continue
            if (previous < 0) { previous = i; continue }
            val b = grains[previous]
            previous = -1
            if (hypot(a.x-b.x,a.y-b.y) > .09f) continue
            val mx = (b.vx-a.vx).toDouble()
            val my = (b.vy-a.vy).toDouble()
            val totalMass = a.mass.toDouble() + b.mass
            val mu = a.mass.toDouble()*b.mass/totalMass
            val fraction = .28
            val lost = .5*mu*(mx*mx+my*my) *
                (2*fraction - fraction*fraction)
            // Both grains move toward their shared centre-of-mass velocity.
            a.vx = (a.vx + fraction*mx*b.mass/totalMass).toFloat()
            a.vy = (a.vy + fraction*my*b.mass/totalMass).toFloat()
            b.vx = (b.vx - fraction*mx*a.mass/totalMass).toFloat()
            b.vy = (b.vy - fraction*my*a.mass/totalMass).toFloat()
            a.heat += lost * a.mass/totalMass
            b.heat += lost * b.mass/totalMass
        }
    }

    /** Draw explosion energy from local material heat; never add energy. */
    private fun releaseHeatAsMotion(episode: CosmicEpisode) {
        if (episode.kind == CosmicEventKind.QUIET ||
            episode.serial == pulseSerial) return
        pulseSerial = episode.serial
        if (episode.kind == CosmicEventKind.STELLAR_NURSERY) {
            settleNurseryGas(episode)
            return
        }
        if (episode.kind !in listOf(CosmicEventKind.NEBULA,
            CosmicEventKind.METEORS,
            CosmicEventKind.NOVA,
            CosmicEventKind.SOLAR_FLARE, CosmicEventKind.PARTICLE_CASCADE,
            CosmicEventKind.GALAXY_COLLISION, CosmicEventKind.REACTION,
            CosmicEventKind.POLAR_JET, CosmicEventKind.RING_WAVE)) return
        val candidates = grains.indices.filter { i ->
            val p = grains[i]
            (episode.kind == CosmicEventKind.METEORS &&
                p.y < .48f ||
                hypot(p.x - episode.x, p.y - episode.y) < .40f) &&
                (episode.kind != CosmicEventKind.NEBULA ||
                    p.material.gas) && p.heat > 0
        }
        for (k in 0 until candidates.size - 1 step 2) {
            val a = grains[candidates[k]]
            val b = grains[candidates[k+1]]
            val rx = (b.x - a.x).toDouble()
            val ry = (b.y - a.y).toDouble()
            val d = sqrt(rx*rx+ry*ry).coerceAtLeast(1e-8)
            var nx = rx/d; var ny = ry/d
            if (episode.kind == CosmicEventKind.METEORS) {
                // Opposite impulses on existing dust: incoming meteors plus
                // physical recoil elsewhere. The pair keeps total momentum.
                nx = .24; ny = .970772
            }
            val vrel = (b.vx-a.vx)*nx+(b.vy-a.vy)*ny
            if (vrel < 0) { nx = -nx; ny = -ny }
            val available = (a.heat + b.heat) *
                (if (episode.kind == CosmicEventKind.NEBULA) .025 else .12)
            if (available <= 0) continue
            val inv = 1.0/a.mass + 1.0/b.mass
            val speedAlong = abs(vrel)
            val dp = (sqrt(speedAlong*speedAlong + 2*inv*available) -
                speedAlong) / inv
            val applied = speedAlong*dp + .5*inv*dp*dp
            if (!applied.isFinite() || applied <= 0) continue
            val ha = a.heat / (a.heat+b.heat)
            a.heat -= applied * ha
            b.heat -= applied * (1-ha)
            a.vx = (a.vx - dp/a.mass*nx).toFloat()
            a.vy = (a.vy - dp/a.mass*ny).toFloat()
            b.vx = (b.vx + dp/b.mass*nx).toFloat()
            b.vy = (b.vy + dp/b.mass*ny).toFloat()
            a.owner = -1; b.owner = -1
        }
    }

    fun step(
        dt: Float, gravity: Float,
        episode: CosmicEpisode = CosmicEpisode(), walls: Boolean = false
    ) {
        if (grains.isEmpty() || dt <= 0f) return
        currentEvent = episode
        gravityTimeNs = 0L
        contactTimeNs = 0L
        releaseHeatAsMotion(episode)
        capacity()
        // Soft contact springs remain stable at h <= .017 for normal grain
        // masses. A 4600-grain cloud no longer repeats contact broad-phase
        // four times per displayed frame.
        val substeps = when {
            grains.size > 2400 -> 2
            mode == MatterMode.DETAILED -> 3
            else -> 2
        }
        val h = min(dt.toDouble(), .034) / substeps
        // Position-dependent forces are identical at a substep boundary.
        // Reuse its acceleration for the next half-kick instead of rebuilding
        // the tree and neighbour grid twice at the same coordinates.
        // Gravity is smooth over two frames at high density. Spring
        // pressure AND reciprocal impulses are evaluated together each
        // substep; a second collision grid traversal is unnecessary.
        // This adaptive cadence removes several expensive tree rebuilds.
        val refresh = grains.size < 2800 || tick % 2 == 0
        accelerations(gravity, refresh)
        repeat(substeps) {
            for (i in grains.indices) {
                val p = grains[i]
                p.vx = (p.vx + (ax[i] * h * .5)).toFloat()
                p.vy = (p.vy + (ay[i] * h * .5)).toFloat()
                p.x = (p.x + p.vx * h).toFloat()
                p.y = (p.y + p.vy * h).toFloat()
                if (walls) {
                    val margin = grainRadius(p).toFloat()
                    // Perfect reflection conserves kinetic energy and mass;
                    // incoming grains may still enter from beyond the border.
                    if (p.x < margin && p.vx < 0f) {
                        p.x = margin + (margin - p.x)
                        p.vx = -p.vx
                    } else if (p.x > 1f-margin && p.vx > 0f) {
                        p.x = 2f*(1f-margin) - p.x
                        p.vx = -p.vx
                    }
                    if (p.y < margin && p.vy < 0f) {
                        p.y = margin + (margin - p.y)
                        p.vy = -p.vy
                    } else if (p.y > 1f-margin && p.vy > 0f) {
                        p.y = 2f*(1f-margin) - p.y
                        p.vy = -p.vy
                    }
                }
            }
            accelerations(gravity,
                refreshGravity = grains.size <= 850 && it == substeps - 1)
            for (i in grains.indices) {
                val p = grains[i]
                p.vx = (p.vx + (ax[i] * h * .5)).toFloat()
                p.vy = (p.vy + (ay[i] * h * .5)).toFloat()
            }
        }
        lastGravityMs = gravityTimeNs / 1_000_000.0
        lastContactMs = contactTimeNs / 1_000_000.0
        tick++
        if (tick % thermoInterval == 0) {
            for (p in grains) phaseChange(p)
        }
    }

    /** Display proxies, always calculated FROM grains, never used as forces. */
    fun summarizeBodies(previous: List<CelestialBody>): List<CelestialBody> {
        data class Sum(
            var mass: Double = 0.0, var x: Double = 0.0,
            var y: Double = 0.0, var vx: Double = 0.0,
            var vy: Double = 0.0
        )
        val sums = HashMap<Int, Sum>()
        for (p in grains) {
            if (p.owner < 0) continue
            val sum = sums.getOrPut(p.owner) { Sum() }
            sum.mass += p.mass
            sum.x += p.x * p.mass; sum.y += p.y * p.mass
            sum.vx += p.vx * p.mass; sum.vy += p.vy * p.mass
        }
        return previous.mapNotNull { b ->
            val s = sums[b.id] ?: return@mapNotNull null
            b.copy(mass = s.mass.toFloat(),
                x = (s.x/s.mass).toFloat(), y = (s.y/s.mass).toFloat(),
                vx = (s.vx/s.mass).toFloat(), vy = (s.vy/s.mass).toFloat())
        }
    }

    /**
     * Find physically coherent debris clouds without spawning particles or
     * imposing a solid shape. Union-find is used ONLY to name visible objects.
     */
    fun discoverClumps(
        bodies: List<CelestialBody>, maxBodies: Int, nextId: () -> Int
    ): List<CelestialBody> {
        if (tick % 24 != 0 || bodies.size >= maxBodies) return emptyList()
        val parent = IntArray(grains.size) { it }
        val sizes = IntArray(grains.size) { 1 }
        fun root(index: Int): Int {
            var i = index
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        fun unite(a: Int, b: Int) {
            val ra = root(a); val rb = root(b)
            if (ra == rb) return
            if (sizes[ra] < sizes[rb]) {
                parent[ra] = rb; sizes[rb] += sizes[ra]
            } else {
                parent[rb] = ra; sizes[ra] += sizes[rb]
            }
        }
        indexCells()
        for (i in grains.indices) {
            val a = grains[i]
            if (a.owner >= 0) continue
            val cx = cellX[i]; val cy = cellY[i]
            var visited = 0
            for (y in cy - 1..cy + 1)
                for (x in cx - 1..cx + 1) {
                    var j = heads[bucket(x, y)]
                    while (j >= 0 && visited++ < 180) {
                        val current = j
                        j = gridNext[current]
                        if (current >= i || cellX[current] != x ||
                            cellY[current] != y) continue
                        val b = grains[current]
                        if (b.owner >= 0) continue
                        val distance = hypot(a.x-b.x, a.y-b.y)
                        val relative = hypot(a.vx-b.vx, a.vy-b.vy)
                        if (distance < (grainRadius(a)+grainRadius(b))*2.5 &&
                            relative < .048f) unite(i, current)
                    }
                }
        }
        val components = HashMap<Int, MutableList<Int>>()
        for (i in grains.indices) {
            if (grains[i].owner >= 0) continue
            components.getOrPut(root(i)) { ArrayList() }.add(i)
        }
        val generated = ArrayList<CelestialBody>()
        for (indices in components.values.sortedByDescending { it.size }) {
            if (indices.size < 12 || bodies.size + generated.size >= maxBodies) continue
            var mass = 0.0; var mx = 0.0; var my = 0.0
            var mvx = 0.0; var mvy = 0.0
            var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
            for (i in indices) {
                val p = grains[i]
                val m = p.mass.toDouble()
                mass += m; mx += m*p.x; my += m*p.y
                mvx += m*p.vx; mvy += m*p.vy
                minX = min(minX,p.x.toDouble()); maxX = max(maxX,p.x.toDouble())
                minY = min(minY,p.y.toDouble()); maxY = max(maxY,p.y.toDouble())
            }
            if (mass < .075 || max(maxX-minX,maxY-minY) > .075) continue
            val id = nextId()
            for (i in indices) grains[i].owner = id
            val p = grains[indices[0]]
            val gas = indices.count { grains[it].material.gas } > indices.size*.75
            generated.add(CelestialBody(id, if (gas) 15 else 2,
                mass.toFloat(), 1.25f, (mx/mass).toFloat(), (my/mass).toFloat(),
                (mvx/mass).toFloat(), (mvy/mass).toFloat(),
                lifetime = 3600f, material = p.material,
                stellarClass = if (gas) StellarClass.CLOUD else StellarClass.PLANET))
        }
        return generated
    }

    /**
     * Gravitational capture changes only object ownership in the UI. It does
     * not glue the particles, alter accelerations, or destroy momentum.
     */
    fun mergeCaptureLabels(bodies: List<CelestialBody>): List<CelestialBody> {
        if (tick % 18 != 0) return bodies
        val retained = bodies.toMutableList()
        for (i in retained.indices) {
            val a = retained[i]
            if (a.mass <= 0f) continue
            for (j in i + 1 until retained.size) {
                val b = retained[j]
                if (b.mass <= 0f) continue
                val distance = hypot(a.x-b.x,a.y-b.y)
                val relative = hypot(a.vx-b.vx,a.vy-b.vy)
                if (distance > (structureRadius(a)+structureRadius(b))*.7f ||
                    relative > .037f) continue
                val winner = if (a.mass >= b.mass) a.id else b.id
                val loser = if (winner == a.id) b.id else a.id
                for (p in grains) if (p.owner == loser) p.owner = winner
                if (winner == a.id) retained[j] = b.copy(mass = 0f)
                else retained[i] = a.copy(mass = 0f)
            }
        }
        return retained.filter { it.mass > 0f }
    }

    /** Capture is classification only: every grain still follows its own forces. */
    fun relabelCaptured(bodies: List<CelestialBody>) {
        if (tick % 9 != 0) return
        for (p in grains) {
            if (p.owner >= 0) continue
            var nearest: CelestialBody? = null
            var score = Double.POSITIVE_INFINITY
            for (b in bodies) {
                val dx = p.x-b.x; val dy = p.y-b.y
                val dist = hypot(dx,dy)
                val relative = hypot(p.vx-b.vx,p.vy-b.vy)
                if (dist < structureRadius(b)*1.25f &&
                    relative < .05f && dist < score) {
                    nearest = b; score = dist.toDouble()
                }
            }
            if (nearest != null) p.owner = nearest.id
        }
    }
}
