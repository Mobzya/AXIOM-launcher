package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Observatory024Test {
    @Test fun timeScaleStaysAccurateAtThirtyHertz() {
        val world = CosmicWorld(initialFrequency = EventFrequency.OFF)
        world.updateTimeScale(2f)
        repeat(30) { world.step(1f / 30f) }
        assertEquals(2f, world.elapsed, 1e-5f)
    }

    @Test fun eventRateCanBeDisabledWithoutDisablingManualEvents() {
        val world = CosmicWorld(initialFrequency = EventFrequency.OFF)
        world.updateTimeScale(2f)
        repeat(500) { world.step(1f / 30f) }
        assertTrue(world.eventHistory.isEmpty())
        world.trigger(CosmicEventKind.SUPERNOVA)
        assertEquals(CosmicEventKind.SUPERNOVA, world.episode.kind)
        world.trigger(CosmicEventKind.QUIET)
        world.updateEventFrequency(EventFrequency.OFTEN)
        repeat(275) { world.step(1f / 30f) }
        assertTrue(world.eventHistory.size > 1)
    }

    @Test fun pressureFrontTransfersHeatOnlyAfterReachingMatter() {
        val universe = AstronomicalUniverse()
        val source = SandGrain(1, .50f, .50f, 0f, 0f, .2f, .002, MatterMaterial.PLASMA)
        val target = SandGrain(2, .70f, .50f, 0f, 0f, .04f, .001, MatterMaterial.IRON)
        universe.grains.addAll(listOf(source, target))
        val event = CosmicEpisode(CosmicEventKind.SUPERNOVA,
            age = 2f, duration = 8f, x = .50f, y = .50f, serial = 2)
        val before = universe.totals()
        universe.step(1f / 60f, 0f, event)
        assertEquals(0f, target.vx, 1e-7f)
        universe.step(1f / 60f, 0f, event.copy(age = 4f))
        val after = universe.totals()
        assertTrue(target.vx > 0f && source.vx < 0f)
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-7)
        assertEquals(before.kinetic + before.internal, after.kinetic + after.internal, 1e-7)
    }

    @Test fun fastGlancingContactReboundsAndHeatsWithoutCreatingMass() {
        val universe = AstronomicalUniverse()
        val a = SandGrain(1, .492f, .50f, .48f, .011f, .03f, .001,
            MatterMaterial.IRON, 1)
        val b = SandGrain(2, .508f, .501f, -.48f, -.011f, .03f, .001,
            MatterMaterial.ROCK, 2)
        universe.grains.addAll(listOf(a, b))
        val before = universe.totals()
        universe.step(1f / 30f, 0f)
        val after = universe.totals()
        assertTrue(a.x < b.x)
        assertTrue(abs(a.vx - b.vx) < .96f)
        assertTrue(after.internal > before.internal)
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-7)
        assertNotNull(universe.lastImpact)
    }

    @Test fun denseWorldDetectsFastCrossingWithinOneGravityPass() {
        val universe = AstronomicalUniverse()
        universe.updateMode(MatterMode.DETAILED)
        val a = SandGrain(1, .498f, .50f, .24f, 0f, .03f, .001,
            MatterMaterial.IRON, 1)
        val b = SandGrain(2, .506f, .50f, -.24f, 0f, .03f, .001,
            MatterMaterial.ROCK, 2)
        universe.grains.addAll(listOf(a, b))
        repeat(1800) { index ->
            universe.grains.add(SandGrain(index + 10,
                1.4f + (index % 45) * .02f, 1.4f + (index / 45) * .02f,
                0f, 0f, .01f, .001, MatterMaterial.ROCK))
        }
        val before = universe.totals()
        universe.step(1f / 30f, 0f)
        assertEquals(1L, universe.gravityRebuilds)
        assertTrue(a.vx < 0f && b.vx > 0f)
        assertEquals(before.momentumX, universe.totals().momentumX, 1e-7)
    }

    @Test fun rewindAndSceneResetDoNotInterpolateUnrelatedParticles() {
        val world = CosmicWorld(initialFrequency = EventFrequency.OFF)
        repeat(30) { world.step(1f / 60f) }
        val later = world.elapsed
        assertTrue(world.rewind(1))
        assertTrue(world.elapsed < later)
        assertSame(world.grainFrames.previous, world.grainFrames.current)
        world.reset()
        assertSame(world.grainFrames.previous, world.grainFrames.current)
        assertEquals(1f, world.grainFrames.blendAt(0L), 0f)
    }

    @Test fun irradiatedCometReleasesVaporWithoutSpawningParticles() {
        val universe = AstronomicalUniverse()
        val star = CelestialBody(1, 12, 12f, 1f, .50f, .50f, 0f, 0f,
            material = MatterMaterial.HYDROGEN, temperature = 5800f, stellarClass = StellarClass.STAR)
        val comet = CelestialBody(2, 1, .18f, 1f, .60f, .50f, 0f, 0f,
            material = MatterMaterial.ICE, temperature = 90f, isComet = true)
        universe.seed(star, 120)
        universe.seed(comet, 32)
        val before = universe.totals()
        val count = universe.grains.size
        repeat(44) { universe.step(1f / 60f, 1f) }
        assertEquals(count, universe.grains.size)
        assertEquals(before.mass, universe.totals().mass, 1e-8)
        assertTrue(universe.grains.any { it.material == MatterMaterial.STEAM && it.owner < 0 })
    }
}
