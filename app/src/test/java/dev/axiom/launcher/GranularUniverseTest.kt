package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GranularUniverseTest {
    private fun grain(
        id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f,
        m: Float = .01f, heat: Double = .00002
    ) = SandGrain(id, x, y, vx, vy, m, heat, MatterMaterial.ROCK, 1)

    @Test fun gravityIsSymmetricAndPreservesMomentum() {
        val engine = GranularUniverse()
        engine.grains.add(grain(1, .3f, .48f, m = .02f))
        engine.grains.add(grain(2, .7f, .52f, m = .07f))
        val before = engine.totals()
        engine.step(1f / 60f, 1f)
        val after = engine.totals()
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-7)
        assertEquals(before.momentumY, after.momentumY, 1e-7)
        assertTrue(engine.grains[0].vx > 0f && engine.grains[1].vx < 0f)
    }

    @Test fun inelasticContactConvertsKineticEnergyIntoHeat() {
        val engine = GranularUniverse()
        engine.grains.add(grain(1, .4988f, .5f, .15f))
        engine.grains.add(grain(2, .5012f, .5f, -.15f))
        val before = engine.totalEnergy(0f)
        val px = engine.totals().momentumX
        repeat(8) { engine.step(1f / 120f, 0f) }
        val after = engine.totalEnergy(0f)
        assertEquals(2, engine.grains.size)
        assertEquals(px, engine.totals().momentumX, 2e-6)
        assertTrue(engine.totals().internal > .00004)
        assertTrue(abs(after - before) < .00012)
    }

    @Test fun spontaneousAndTriggeredEventsCannotCreateParticlesOrMass() {
        val engine = GranularUniverse()
        val initial = CelestialBody(1, 2, 1f, 1f,
            .5f, .5f, .02f, -.01f, stellarClass = StellarClass.PLANET)
        engine.seed(initial, 54)
        val initialIds = engine.grains.map { it.id }.toSet()
        val initialMass = engine.totals().mass
        val shock = CosmicEpisode(CosmicEventKind.NOVA, age = 4f,
            duration = 13f, x = .5f, y = .5f, serial = 1)
        repeat(120) {
            engine.step(1f / 60f, 1f, if (it == 3) shock else CosmicEpisode())
        }
        assertEquals(initialIds, engine.grains.map { it.id }.toSet())
        assertEquals(initialMass, engine.totals().mass, 1e-6)
        assertTrue(engine.grains.all { it.x.isFinite() && it.y.isFinite()
            && it.vx.isFinite() && it.vy.isFinite() && it.heat >= 0 })
    }

    @Test fun granularMatterDoesNotRetainAnyPrescribedEggOrSphereCoordinates() {
        val engine = GranularUniverse()
        val b = CelestialBody(1, 2, 1.5f, 1f, .48f, .48f, 0f, 0f)
        engine.seed(b, 70)
        val first = engine.grains.map { it.x to it.y }
        repeat(25) { engine.step(1f/60f, 1f) }
        assertTrue(engine.grains.indices.any { i ->
            val (oldX, oldY) = first[i]
            hypot(engine.grains[i].x - oldX, engine.grains[i].y - oldY) > .00001f
        })
        val initialTotal = engine.totals().mass
        assertEquals(b.mass.toDouble(), initialTotal, 1e-5)
    }

    @Test fun detailModeDoesNotSynthesizeExtraMassOrGrains() {
        val engine = GranularUniverse()
        engine.seed(CelestialBody(9, 4, 2f, 1f, .5f, .5f, 0f, 0f), 75)
        val before = engine.totals()
        engine.updateMode(MatterMode.DETAILED)
        engine.step(1f / 60f, 1f)
        engine.updateMode(MatterMode.BALANCED)
        assertEquals(75, engine.grains.size)
        assertEquals(before.mass, engine.totals().mass, 1e-5)
    }

    @Test fun worldWithActiveEventHasConstantMassAndCount() {
        val world = CosmicWorld(321)
        val before = world.sand.totals()
        val count = world.sand.grains.size
        world.trigger(CosmicEventKind.NOVA)
        repeat(65) { world.step(1f / 60f) }
        assertEquals(count, world.sand.grains.size)
        assertEquals(before.mass, world.sand.totals().mass, 1e-5)
    }
}
