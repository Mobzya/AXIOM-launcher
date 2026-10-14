package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BoundStructuresTest {
    private fun body(
        id: Int, mass: Float = 2f, x: Float = .5f,
        kind: StellarClass = StellarClass.PLANET
    ) = CelestialBody(id, 2, mass, 1f, x, .5f, .01f, .0f,
        lifetime = 3600f, stellarClass = kind)

    @Test fun stablePlanetsStarsAndQuasarsAreBoundParticleStructures() {
        val field = BoundStructureField()
        for (kind in StellarClass.entries) {
            val b = body(kind.ordinal + 1, kind = kind)
            val points = field.ensure(b)
            assertTrue(points.size >= 48)
            assertEquals(b.mass.toDouble(),
                points.sumOf { it.mass.toDouble() }, 1e-4)
            assertTrue(points.map { it.material }.distinct().size >= 2)
            val first = points.map { it.dx to it.dy }
            field.ensure(b.copy(x = .67f, y = .63f, vx = -.04f))
            assertEquals(first, field.points(b.id).map { it.dx to it.dy })
        }
    }

    @Test fun collisionReleasesRealMassAndConservesBothMomentumComponents() {
        val field = MatterField()
        val a = body(10, 3f).copy(vx = .2f, vy = -.04f)
        val b = body(11, 1.5f, .52f, StellarClass.STAR)
            .copy(vx = -.10f, vy = .05f)
        field.structures.ensure(a); field.structures.ensure(b)
        val beforePx = a.mass * a.vx + b.mass * b.vx
        val beforePy = a.mass * a.vy + b.mass * b.vy
        val outcome = field.emitImpact(a, b, .3f)
        assertTrue(field.particles.isNotEmpty())
        assertTrue(outcome.first.mass < a.mass)
        assertTrue(outcome.second.mass < b.mass)
        val finalMass = outcome.first.mass + outcome.second.mass +
            field.particles.sumOf { it.mass.toDouble() }.toFloat()
        assertEquals(a.mass + b.mass, finalMass, 1e-4f)
        val px = outcome.first.mass * outcome.first.vx +
            outcome.second.mass * outcome.second.vx +
            field.particles.sumOf { (it.mass * it.vx).toDouble() }.toFloat()
        val py = outcome.first.mass * outcome.first.vy +
            outcome.second.mass * outcome.second.vy +
            field.particles.sumOf { (it.mass * it.vy).toDouble() }.toFloat()
        assertEquals(beforePx, px, 1e-4f)
        assertEquals(beforePy, py, 1e-4f)
        assertEquals(outcome.first.mass.toDouble(),
            field.structures.points(a.id).sumOf { it.mass.toDouble() }, 2e-4)
    }

    @Test fun mergingEnlargesStructuresWithoutInventingMass() {
        val field = BoundStructureField()
        val a = body(1, 9f, x = .48f)
        val b = body(2, 18f, x = .52f, kind = StellarClass.STAR)
        field.ensure(a); field.ensure(b)
        val mass = a.mass + b.mass
        val merged = a.copy(mass = mass, x = (a.x*a.mass+b.x*b.mass)/mass,
            stellarClass = StellarClass.STAR)
        field.merge(a,b,merged)
        assertTrue(field.points(b.id).isEmpty())
        assertTrue(field.points(a.id).isNotEmpty())
        assertEquals(mass.toDouble(),
            field.points(a.id).sumOf { it.mass.toDouble() }, 1e-3)
        assertTrue(structureRadius(merged) > structureRadius(a))
    }

    @Test fun novaDismantlesLocalMatterInsteadOfSpawningCosmeticObjects() {
        val field = MatterField()
        val planet = body(42, 3f)
        val bodies = arrayListOf(planet)
        field.structures.ensure(planet)
        val episode = CosmicEpisode(kind = CosmicEventKind.NOVA,
            x = .5f, y = .5f, age = 2f, duration = 8f, serial = 9)
        field.releaseFromTidesAndEvents(bodies, episode, 12f)
        assertTrue(field.particles.size >= 2)
        assertTrue(bodies.single().mass < planet.mass)
        assertEquals(planet.mass,
            bodies.single().mass + field.totalMass().toFloat(), 1e-4f)
    }

    @Test fun changingDetailNeverChangesPhysicalBodyMassOrInventory() {
        val field = BoundStructureField()
        val b = body(5, 16f, kind = StellarClass.QUASAR)
        field.ensure(b)
        val low = field.sampleCount
        field.setMode(MatterMode.DETAILED, listOf(b))
        assertTrue(field.sampleCount > low)
        field.setMode(MatterMode.BALANCED, listOf(b))
        assertTrue(field.sampleCount <= low + 1)
        val sum = field.points(b.id).sumOf { it.mass.toDouble() }
        assertTrue(abs(sum - b.mass) < 1e-3)
    }
}
