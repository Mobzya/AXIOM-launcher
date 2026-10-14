package dev.axiom.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Collapsed representation of a self-gravitating object. These are REAL mass
 * samples of a body, not additional mass in the N-body solver.
 *
 * In a stable object samples keep local coordinates and share the velocity of
 * their centre of mass (rigid-body/continuum approximation). Released samples
 * move to MatterField.particles and start independent integration.
 */
internal enum class StellarClass { PLANET, STAR, QUASAR, CLOUD }

internal data class BoundMatter(
    var dx: Float,
    var dy: Float,
    var mass: Float,
    var material: MatterMaterial,
    var temperature: Float
)

/** Radius is shared by collision detection, tidal stress and particle rendering. */
internal fun structureRadius(body: CelestialBody): Float {
    val densityFactor = when (body.stellarClass) {
        StellarClass.PLANET -> 1f
        StellarClass.STAR -> 1.38f
        StellarClass.QUASAR -> 1.65f
        StellarClass.CLOUD -> 1.85f
    }
    return ((.005f + .008f * sqrt(body.mass.coerceAtLeast(.01f))) *
        body.sizeFactor * densityFactor).coerceIn(.003f, .16f)
}

internal fun stellarClassFromKind(kind: Int): StellarClass = when {
    kind in 97..104 -> StellarClass.QUASAR
    kind in 8..20 || kind in 60..70 -> StellarClass.STAR
    kind in 71..77 -> StellarClass.CLOUD
    else -> StellarClass.PLANET
}

/** Bounding number of individual bound samples is a visual LOD, not a mass limit. */
internal class BoundStructureField {
    private val structures = HashMap<Int, MutableList<BoundMatter>>()
    private var detail = MatterMode.BALANCED

    val sampleCount: Int get() = structures.values.sumOf { it.size }
    fun clear() = structures.clear()
    fun points(id: Int): List<BoundMatter> = structures[id] ?: emptyList()
    fun setMode(mode: MatterMode, bodies: List<CelestialBody>) {
        detail = mode
        // Rebuild only on deliberate quality changes; preserve existing mass
        // and composition by sampling the old structure rather than inventing
        // new material. A change in detail never alters the world's N-body mass.
        for (b in bodies) {
            val old = structures[b.id]
            if (old != null) {
                val existingMass = old.sumOf { it.mass.toDouble() }.toFloat()
                val newCount = preferredCount(b)
                val existingSize = old.size
                if (old.size > newCount) {
                    coarsen(old, newCount)
                } else if (old.size < newCount) {
                    while (old.size < newCount && old.isNotEmpty()) {
                        val p = old[(old.size - existingSize) % existingSize]
                        val piece = p.mass * .5f
                        p.mass -= piece
                        old.add(BoundMatter(p.dx, p.dy, piece, p.material, p.temperature))
                    }
                }
                normalize(b, old)
                check(kotlin.math.abs(existingMass -
                    old.sumOf { it.mass.toDouble() }.toFloat()) < 1e-3f)
            }
        }
    }

    private fun preferredCount(body: CelestialBody): Int {
        val base = if (detail == MatterMode.DETAILED) 340 else 100
        val massScale = (sqrt(body.mass.coerceAtLeast(.01f)) * .35f)
            .coerceIn(.35f, 1.65f)
        return (base * massScale).toInt().coerceIn(48,
            if (detail == MatterMode.DETAILED) 620 else 180)
    }

    fun ensure(body: CelestialBody): List<BoundMatter> {
        val existing = structures[body.id]
        if (existing != null) {
            normalize(body, existing)
            return existing
        }
        val count = preferredCount(body)
        val radius = structureRadius(body) * .95f
        val cells = ArrayList<BoundMatter>(count)
        val golden = PI * (3.0 - sqrt(5.0))
        for (i in 0 until count) {
            val theta = i * golden
            val radial = sqrt((i + .5) / count.toDouble()).toFloat()
            val stretch = when (body.stellarClass) {
                StellarClass.QUASAR -> if (i % 4 == 0) 1.22f else .7f
                StellarClass.CLOUD -> 1.05f
                else -> 1f
            }
            val dx = cos(theta).toFloat() * radius * radial * stretch
            val dy = sin(theta).toFloat() * radius * radial /
                max(.65f, stretch)
            val type = when (body.stellarClass) {
                StellarClass.STAR, StellarClass.QUASAR ->
                    when (i % 8) {
                        0, 1 -> MatterMaterial.PLASMA
                        2, 3 -> MatterMaterial.HELIUM
                        else -> MatterMaterial.HYDROGEN
                    }
                StellarClass.CLOUD -> when (i % 4) {
                    0 -> MatterMaterial.ICE
                    1 -> MatterMaterial.METHANE
                    2 -> MatterMaterial.HELIUM
                    else -> MatterMaterial.HYDROGEN
                }
                StellarClass.PLANET -> when (i % 12) {
                    0 -> MatterMaterial.IRON
                    1 -> MatterMaterial.SILICATE
                    2 -> MatterMaterial.CARBON
                    3 -> if (body.temperature < 260f) MatterMaterial.ICE
                        else MatterMaterial.ROCK
                    else -> body.material
                }
            }
            cells.add(BoundMatter(dx, dy, body.mass / count, type,
                body.temperature))
        }
        structures[body.id] = cells
        normalize(body, cells)
        return cells
    }

    fun reconcile(bodies: List<CelestialBody>) {
        val alive = HashSet<Int>(bodies.size)
        for (body in bodies) {
            alive.add(body.id)
            ensure(body)
        }
        structures.keys.retainAll(alive)
    }

    fun remove(bodyId: Int) { structures.remove(bodyId) }

    fun merge(a: CelestialBody, b: CelestialBody, merged: CelestialBody) {
        val first = ensure(a).map { it.copy(dx = it.dx + a.x - merged.x,
            dy = it.dy + a.y - merged.y) }
        val second = ensure(b).map { it.copy(dx = it.dx + b.x - merged.x,
            dy = it.dy + b.y - merged.y) }
        val together = ArrayList<BoundMatter>(first.size + second.size)
        together.addAll(first)
        together.addAll(second)
        structures.remove(a.id)
        structures.remove(b.id)
        if (together.size > preferredCount(merged)) {
            coarsen(together, preferredCount(merged))
        }
        structures[merged.id] = together
        normalize(merged, together)
    }

    fun capture(body: CelestialBody, particle: MatterParticle) {
        val samples = ensure(body) as MutableList<BoundMatter>
        // Body mass is updated by caller only AFTER this transfer.
        samples.add(BoundMatter(particle.x - body.x,
            particle.y - body.y, particle.mass, particle.material,
            particle.temperature))
        if (samples.size > preferredCount(body) + 50) {
            coarsen(samples, preferredCount(body))
        }
    }

    fun normalize(body: CelestialBody, samples: List<BoundMatter>) {
        val total = samples.sumOf { it.mass.toDouble() }
        if (total <= 0.0) return
        val factor = body.mass.toDouble() / total
        if (kotlin.math.abs(factor - 1.0) > 1e-6)
            samples.forEach { it.mass = (it.mass * factor).toFloat() }
    }

    /**
     * A bonded body can shed matter while keeping COM momentum. Material is
     * removed from its true constituents rather than manufactured as sparks.
     * Old bound samples stay bound, the freed fraction becomes independent.
     */
    fun release(
        body: CelestialBody,
        requested: Int,
        fraction: Float,
        impulse: Float,
        nextParticleId: () -> Int
    ): Pair<CelestialBody, List<MatterParticle>> {
        val samples = ensure(body)
        val amount = (body.mass * fraction.coerceIn(0f, .7f))
            .coerceAtMost((body.mass - .035f).coerceAtLeast(0f))
        val count = min(requested, samples.size).let { it - it % 2 }
        if (amount <= 0f || count < 2) return body to emptyList()
        val fractionActual = amount / body.mass
        // Fractionally eject each sample, pooling samples of similar material
        // into bounded visual packets, without altering elemental inventory.
        val emitted = ArrayList<MatterParticle>(count)
        val buckets = Array(count) { ArrayList<BoundMatter>() }
        samples.forEachIndexed { i, p -> buckets[i % count].add(p) }
        val angleOffset = (body.id * 0.6180339887f)
        var emittedTotal = 0f
        for (i in 0 until count) {
            val bucket = buckets[i]
            val bucketMass = bucket.sumOf { it.mass.toDouble() }.toFloat() * fractionActual
            if (bucketMass <= 0f) continue
            val theta = angleOffset + i * (2 * PI / count).toFloat()
            val directionX = cos(theta)
            val directionY = sin(theta)
            val source = bucket.maxBy { it.mass }
            val dist = structureRadius(body) * (1.05f + .09f * (i % 3))
            emitted.add(MatterParticle(nextParticleId(),
                body.x + directionX * dist, body.y + directionY * dist,
                body.vx + directionX * impulse,
                body.vy + directionY * impulse, bucketMass,
                body.temperature + impulse * 1800f,
                source.material, age = 0f))
            emittedTotal += bucketMass
        }
        // Balance the exact ejection momentum; recoil is unnecessary because
        // released particles share their source's COM momentum.
        val ejectPx = emitted.sumOf { (it.mass * it.vx).toDouble() }.toFloat()
        val ejectPy = emitted.sumOf { (it.mass * it.vy).toDouble() }.toFloat()
        val newMass = body.mass - emittedTotal
        val remaining = body.copy(mass = newMass,
            vx = (body.mass * body.vx - ejectPx) / newMass,
            vy = (body.mass * body.vy - ejectPy) / newMass)
        for (p in samples) p.mass *= 1f - fractionActual
        normalize(remaining, samples)
        return remaining to emitted
    }

    private fun coarsen(samples: MutableList<BoundMatter>, target: Int) {
        if (samples.size <= target) return
        val ordered = samples.sortedWith(compareBy<BoundMatter> {
            it.material.ordinal
        }.thenBy { it.dx }.thenBy { it.dy })
        val compact = ArrayList<BoundMatter>(target)
        val batch = (ordered.size + target - 1) / target
        for (start in ordered.indices step batch) {
            val subset = ordered.subList(start, min(start + batch, ordered.size))
            val mass = subset.sumOf { it.mass.toDouble() }
            if (mass == 0.0) continue
            compact.add(BoundMatter(
                (subset.sumOf { it.dx.toDouble() * it.mass.toDouble() } / mass).toFloat(),
                (subset.sumOf { it.dy.toDouble() * it.mass.toDouble() } / mass).toFloat(),
                mass.toFloat(), subset.maxBy { it.mass }.material,
                (subset.sumOf { it.temperature.toDouble() * it.mass.toDouble() } / mass).toFloat()
            ))
        }
        samples.clear()
        samples.addAll(compact)
    }
}
