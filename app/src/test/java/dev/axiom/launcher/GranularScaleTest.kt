package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * JVM regression workloads, not a substitute for Android device frame pacing.
 * The timing logs are recorded in CI to compare physics changes fairly.
 */
class GranularScaleTest {
    @Test fun over4600GrainsKeepMomentumAndMassWithAdaptiveGravity() {
        val world = GranularUniverse()
        world.updateMode(MatterMode.DETAILED)
        world.seed(CelestialBody(5, 2, 15f, 1f, .47f, .48f, 0f, 0f), 4620)
        assertEquals(4620, world.grains.size)
        val before = world.totals()
        val start = System.nanoTime()
        repeat(4) { world.step(1f/30f, 1f) }
        val millis = (System.nanoTime() - start) / 1_000_000
        println("AXIOM scale benchmark: 4620 grains, 4 frames, ${millis}ms, " +
            "gravity rebuilds=${world.gravityRebuilds}, " +
            "last cell contacts=${world.counters.first}, " +
            "tree visits=${world.counters.second}")
        val after = world.totals()
        assertEquals(before.mass, after.mass, 1e-4)
        assertEquals(before.momentumX, after.momentumX, 2e-4)
        assertEquals(before.momentumY, after.momentumY, 2e-4)
        assertTrue(world.grains.all { it.x.isFinite() && it.y.isFinite() &&
            it.vx.isFinite() && it.vy.isFinite() && it.heat >= 0.0 })
        assertTrue("Gravity should not rebuild every contact substep",
            world.gravityRebuilds <= 3)
        assertTrue("Regression: 4620-grain batch took ${millis}ms", millis < 25000)
    }

    @Test fun offscreenDenseBucketDoesNotCauseQuadraticPairEnumeration() {
        val world = GranularUniverse()
        world.updateMode(MatterMode.DETAILED)
        for (i in 0 until 4600) {
            // Coordinates deliberately OUTSIDE the viewport. The old clamped
            // grid would place all these grains in the same edge bucket.
            world.grains.add(SandGrain(i, 5.8f, 5.8f,
                0f, 0f, .004f, 0.000001, MatterMaterial.IRON, -1))
        }
        val mass = world.totals().mass
        val started = System.nanoTime()
        world.step(.0008f, 1f)
        val millis = (System.nanoTime()-started)/1_000_000
        println("AXIOM worst-case dense bucket: 4600 particles in ${millis}ms")
        assertEquals(mass, world.totals().mass, 1e-6)
        // Each substep traverses the contact grid twice: once for symmetric
        // contact forces and once for restitution/heat transfer.
        assertTrue(world.counters.first <= 4600L*32*2)
        assertTrue("Dense-cloud neighbor scan regressed: ${millis}ms",
            millis < 25000)
    }

    @Test fun phaseChangeTransfersHeatToChemicalEnthalpyExactly() {
        val world = GranularUniverse()
        val particle = SandGrain(1, .5f, .5f, 0f, 0f,
            1f, .00067, MatterMaterial.ICE)
        world.grains.add(particle)
        val before = world.totalEnergy(0f)
        repeat(8) { world.step(.01f, 0f) }
        assertEquals(MatterMaterial.WATER, particle.material)
        assertEquals(before, world.totalEnergy(0f), 1e-10)
        assertEquals(1, world.grains.size)
    }

    @Test fun steamDoesNotOscillateBetweenPhasesAtTheLatentHeatBoundary() {
        val world = GranularUniverse()
        val p = SandGrain(89, .3f, .4f, 0f, 0f,
            1f, .00044, MatterMaterial.WATER)
        world.grains.add(p)
        val before = world.totalEnergy(0f)
        repeat(40) { world.step(.01f, 0f) }
        assertEquals(MatterMaterial.STEAM, p.material)
        assertEquals(before, world.totalEnergy(0f), 1e-10)
    }

    @Test fun meteorsAndBlackHoleInteractWithExistingParticlesOnly() {
        val universe = GranularUniverse()
        val n = 128
        universe.seed(CelestialBody(7, 2, 2f, 1f, .5f, .3f,
            0f, 0f), n)
        val ids = universe.grains.map { it.id }
        val mass = universe.totals().mass
        val before = universe.totals()
        val meteor = CosmicEpisode(CosmicEventKind.METEORS, 1f, 10f,
            .5f, .3f, 100)
        universe.step(1f/60f, 1f, meteor)
        assertEquals(mass, universe.totals().mass, 1e-6)
        assertEquals(before.momentumX, universe.totals().momentumX, 1e-5)
        assertEquals(before.momentumY, universe.totals().momentumY, 1e-5)
        val singularity = CosmicEpisode(CosmicEventKind.SINGULARITY, 1f,
            17f, .5f, .3f, 101)
        repeat(5) { universe.step(1f/60f, 1f, singularity) }
        assertEquals(ids, universe.grains.map { it.id })
        assertEquals(mass, universe.totals().mass, 1e-6)
        assertTrue(abs(universe.totals().momentumX - before.momentumX) < .0001)
    }

    @Test fun settingsStressSceneCanActuallyReach4600WithoutAutoCreatingMass() {
        val world = CosmicWorld(101)
        val before = world.sand.totals().mass
        world.populateStressScene(4600)
        assertEquals(MatterMode.DETAILED, world.matterMode)
        assertEquals(4600, world.sand.grains.size)
        assertTrue(world.bodies.size <= world.maxBodies)
        assertTrue(world.sand.totals().mass > before)
        val conserved = world.sand.totals().mass
        world.trigger(CosmicEventKind.METEORS)
        world.step(1f/60f)
        assertEquals(4600, world.sand.grains.size)
        assertEquals(conserved, world.sand.totals().mass, 1e-5)
        assertEquals(4600*4, world.grainFrame.size)
    }

    @Test fun stellarNurseryAndNebulaConserveGasMassAndEnergy() {
        val world = GranularUniverse()
        world.grains.add(SandGrain(1, .48f, .49f, .022f, .012f,
            .2f, .00012, MatterMaterial.HYDROGEN))
        world.grains.add(SandGrain(2, .52f, .51f, -.020f, -.011f,
            .3f, .00009, MatterMaterial.HELIUM))
        val before = world.totalEnergy(0f)
        val mass = world.totals().mass
        val px = world.totals().momentumX
        val nursery = CosmicEpisode(CosmicEventKind.STELLAR_NURSERY, 1f,
            18f, .5f, .5f, 40)
        world.step(.008f, 0f, nursery)
        assertEquals(before, world.totalEnergy(0f), 3e-8)
        assertEquals(px, world.totals().momentumX, 3e-8)
        val nebula = CosmicEpisode(CosmicEventKind.NEBULA, 1f,
            18f, .5f, .5f, 41)
        world.step(.008f, 0f, nebula)
        assertEquals(2, world.grains.size)
        assertEquals(mass, world.totals().mass, 1e-8)
        assertEquals(px, world.totals().momentumX, 3e-8)
        assertEquals(before, world.totalEnergy(0f), 3e-8)
    }

    @Test fun packedReplayRestoresCountMaterialsAndVelocities() {
        val sand = GranularUniverse()
        sand.seed(CelestialBody(6, 2, 1f, 1f,
            .2f, .5f, .1f, -.2f), 40)
        val prior = sand.snapshot()
        val before = sand.totals()
        sand.step(1f/30f, 1f)
        sand.restore(prior)
        assertEquals(40, sand.grains.size)
        assertEquals(before.mass, sand.totals().mass, 1e-8)
        assertEquals(before.kinetic, sand.totals().kinetic, 1e-7)
    }
}
