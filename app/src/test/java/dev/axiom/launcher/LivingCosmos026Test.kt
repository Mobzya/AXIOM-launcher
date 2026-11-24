package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingCosmos026Test {
    @Test fun highSpeedContactInDenseWorldPreservesMomentumAndReleasesHeat() {
        val universe = AstronomicalUniverse()
        universe.updateMode(MatterMode.DETAILED)
        val a = SandGrain(1, .492f, .50f, .64f, 0f, .01f, .0001, MatterMaterial.IRON, 1)
        val b = SandGrain(2, .508f, .50f, -.64f, 0f, .01f, .0001, MatterMaterial.ROCK, 2)
        universe.grains.addAll(listOf(a, b))
        repeat(1800) { i ->
            universe.grains.add(SandGrain(i + 3, 1.2f + (i % 45) * .021f,
                1.2f + (i / 45) * .021f, 0f, 0f, .01f, .0001, MatterMaterial.ROCK))
        }
        val before = universe.totals()
        universe.step(1f / 60f, 0f)
        val after = universe.totals()
        assertTrue(a.x < b.x && a.vx < 0f && b.vx > 0f)
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-7)
        assertEquals(before.kinetic + before.internal, after.kinetic + after.internal, 1e-7)
        assertNotNull(universe.lastImpact)
    }

    @Test fun ordinaryCaptureCannotHideDustInsideCompactBodyLabel() {
        val universe = AstronomicalUniverse()
        val hole = CelestialBody(1, 99, .05f, 1f, .5f, .5f, 0f, 0f,
            stellarClass = StellarClass.QUASAR, nature = CelestialNature.BLACK_HOLE)
        universe.seed(hole, 1)
        val dust = SandGrain(290, .5048f, .5f, 0f, 0f, .005f, .0001, MatterMaterial.ICE)
        universe.grains.add(dust)
        repeat(12) { universe.step(1f / 60f, 0f) }
        universe.relabelCaptured(listOf(hole))
        assertEquals(-1, dust.owner)
        assertEquals(1, universe.grains.count { it.owner == hole.id })
    }

    @Test fun rewindResetsInterpolationEvenWhenParticleIdsStayTheSame() {
        val world = CosmicWorld()
        world.configureObservation(events = false)
        repeat(40) { world.step(1f / 60f) }
        assertTrue(world.rewind())
        assertSame(world.grainFrames.current, world.grainFrames.previous)
        assertEquals(1f, world.grainFrames.blendAt(0L), 0f)
    }

    @Test fun galaxyDisksCarryOppositeOrbitalMomentumAroundTheirOwnCores() {
        val world = CosmicWorld(initialPreset = CosmicPreset.GALAXY_PASSAGE)
        val cores = world.bodies.filter { it.nature == CelestialNature.BLACK_HOLE }
        assertEquals(2, cores.size)
        val disk = world.sand.grains.filter { it.owner < 0 }
        assertEquals(580, disk.size)
        val momentum = cores.map { core ->
            disk.filter { if (core.id == 1) it.x < .5f else it.x > .5f }
                .sumOf { p -> p.mass.toDouble() *
                    ((p.x - core.x) * (p.vy - core.vy) - (p.y - core.y) * (p.vx - core.vx)) }
        }
        assertTrue(momentum[0] > 0.0 && momentum[1] < 0.0)
    }

    @Test fun changingOrdinaryCadenceDoesNotChangeRareSettings() {
        val world = CosmicWorld()
        world.configureCosmos(rate = CatastropheRate.NATURAL)
        world.changeEventCadence(EventCadence.OFTEN)
        assertEquals(EventCadence.OFTEN, world.eventCadence)
        assertEquals(CatastropheRate.NATURAL, world.catastropheRate)
        assertTrue(EventCadence.OFTEN.wait(1f) < EventCadence.NORMAL.wait(0f))
    }
}
