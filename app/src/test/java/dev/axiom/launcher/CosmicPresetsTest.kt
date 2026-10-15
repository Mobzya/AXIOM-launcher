package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Presets are real, integrated granular initial conditions, not fixed sprites. */
class CosmicPresetsTest {
    @Test fun eachPresetSeedsUniqueMovingMassesWithinParticleBudget() {
        for (preset in CosmicPreset.entries) {
            for (mode in MatterMode.entries) {
                val world = CosmicWorld(21, mode, preset)
                assertEquals(preset, world.preset)
                assertTrue("no matter in $preset", world.sand.grains.isNotEmpty())
                assertTrue(world.sand.grains.size <= mode.maxParticles)
                assertTrue("duplicate grain ids in $preset",
                    world.sand.grains.map { it.id }.toSet().size ==
                        world.sand.grains.size)
                assertTrue(world.sand.totals().mass > 0.0)
                val count = world.sand.grains.size
                val mass = world.sand.totals().mass
                repeat(4) { world.step(1f / 60f) }
                assertEquals(count, world.sand.grains.size)
                assertEquals(mass, world.sand.totals().mass, 1e-5)
                assertTrue(world.sand.grains.all {
                    it.x.isFinite() && it.y.isFinite() &&
                        it.vx.isFinite() && it.vy.isFinite() &&
                        it.heat >= 0.0
                })
            }
        }
    }

    @Test fun saturnHasGenuineKeplerianIceAndRockRings() {
        val world = CosmicWorld(13, MatterMode.DETAILED,
            CosmicPreset.SATURN_RINGS)
        val ring = world.sand.grains.filter { it.owner < 0 }
        assertTrue(ring.size >= 1000)
        assertTrue(ring.count { it.material == MatterMaterial.ICE } >
            ring.size * .65)
        assertTrue(ring.all {
            hypot(it.x - .50f, it.y - .49f) in .062f.. .166f
        })
        val angular = ring.sumOf { p ->
            val dx = (p.x - .50f).toDouble()
            val dy = (p.y - .49f).toDouble()
            p.mass * (dx * p.vy - dy * p.vx)
        }
        assertTrue("rings need prograde orbital moment", angular > .0)
        val before = world.sand.totals()
        repeat(10) { world.step(1f / 60f) }
        assertEquals(before.mass, world.sand.totals().mass, 1e-5)
    }

    @Test fun solarPlanetsStartOnOrbitsNotChaoticStraightLines() {
        val world = CosmicWorld(11, MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM)
        assertEquals(6, world.bodies.size)
        assertEquals(.5f, world.timeScale, .0001f)
        val star = world.bodies.first()
        assertEquals(StellarClass.STAR, star.stellarClass)
        for (planet in world.bodies.drop(1)) {
            val dx = planet.x - star.x
            val dy = planet.y - star.y
            assertTrue(hypot(dx, dy) in .09f.. .42f)
            val dot = dx * planet.vx + dy * planet.vy
            assertTrue("near-tangential initial orbit", kotlin.math.abs(dot) < 1e-5f)
        }
        val snapshot = world.sand.snapshot()
        val firstPositions = world.sand.grains.map { it.x to it.y }
        repeat(45) { world.step(1f/60f) }
        assertEquals(snapshot.heat.size, world.sand.grains.size)
        assertTrue(world.sand.grains.indices.any { i ->
            val before = firstPositions[i]
            hypot(world.sand.grains[i].x - before.first,
                world.sand.grains[i].y - before.second) > .00005f
        })
    }

    @Test fun selectingSceneReplacesMatterAndResetKeepsSelectedTemplate() {
        val world = CosmicWorld(43)
        assertEquals(CosmicPreset.DEEP_SPACE, world.preset)
        world.loadPreset(CosmicPreset.PROTOPLANETARY)
        val mass = world.sand.totals().mass
        val size = world.sand.grains.size
        assertTrue(world.sand.grains.any { it.owner < 0 })
        assertTrue(size > 700)
        world.step(1f/60f)
        world.reset()
        assertEquals(CosmicPreset.PROTOPLANETARY, world.preset)
        assertEquals(.5f, world.timeScale, .0001f)
        assertEquals(size, world.sand.grains.size)
        assertEquals(mass, world.sand.totals().mass, 1e-5)
    }
}
