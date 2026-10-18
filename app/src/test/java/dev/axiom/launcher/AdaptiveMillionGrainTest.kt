package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMillionGrainTest {
    @Test fun millionIsLazyCapacityNotMillionAllocatedOnStartup() {
        val world=CosmicWorld(117,MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM,1_000_000)
        assertEquals(1_000_000,world.particleLimit)
        assertEquals(1_000_000,world.sand.maxParticles)
        assertTrue("The million cap must NOT allocate 1M grains",
            world.sand.grains.size<6_000)
        assertTrue(world.sand.renderFrame().size<=96_000)
    }

    @Test fun userCanAddRealDustBeyondOld4800HardCap() {
        val world=CosmicWorld(117,MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM,20_000)
        val before=world.sand.totals()
        val n=world.sand.grains.size
        repeat(5){assertEquals(2000,world.addOrbitalParticleBatch(2000))}
        assertEquals(n+10_000,world.sand.grains.size)
        assertTrue(world.sand.totals().mass>before.mass)
        assertEquals(world.sand.grains.size,
            world.sand.grains.map { it.id }.toSet().size)
        assertEquals(world.sand.grains.size*4,world.sand.renderFrame().size)
        // 12k genuine grains still use one conserved, live numerical solver.
        world.step(1f/30f)
        assertEquals(n+10_000,world.sand.grains.size)
        assertTrue(world.sand.grains.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test fun loweringLimitRebuildsButDoesNotLeavePhantomPlanets() {
        val world=CosmicWorld(10,MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM,20_000)
        world.addOrbitalParticleBatch(2000)
        world.updateParticleLimit(100)
        assertEquals(100,world.particleLimit)
        assertTrue(world.sand.grains.size<=100)
        val owners=world.sand.grains.map { it.owner }.toSet()
        assertTrue(world.bodies.all { it.id in owners })
    }

    @Test fun massiveFrameIsStrictlySampledWithDeterministicMaterials() {
        val u=AstronomicalUniverse()
        u.updateParticleLimit(1_000_000)
        u.updateMode(MatterMode.DETAILED)
        repeat(25) { k ->
            u.seed(CelestialBody(k+100,2,2f,1f,
                .15f+k*.026f,.4f,0f,0f),1200)
        }
        assertEquals(30_000,u.grains.size)
        val a=u.renderFrame()
        val b=u.renderFrame()
        assertEquals(24_000*4,a.size)
        assertTrue(a.contentEquals(b))
        assertEquals(1_000_000,u.maxParticles)
    }

    @Test fun stellarRadiationTransfersHeatWithoutInventingMassOrEnergy() {
        val u=AstronomicalUniverse()
        u.grains.add(SandGrain(1,.4f,.4f,0f,0f,
            2f,.010,MatterMaterial.PLASMA,1))
        u.grains.add(SandGrain(2,.6f,.4f,0f,0f,
            1f,.00020,MatterMaterial.ICE,2))
        val before=u.totals()
        val hot=u.grains[0].heat
        val cold=u.grains[1].heat
        repeat(16){u.step(1f/60f,0f)}
        val after=u.totals()
        assertTrue(u.grains[0].heat<hot)
        assertTrue(u.grains[1].heat>cold)
        assertEquals(before.mass,after.mass,1e-6)
        assertEquals(before.internal+before.chemical,
            after.internal+after.chemical,1e-8)
    }

    @Test fun batchesStopAtConfiguredCapacityWithoutMassLoss() {
        val world=CosmicWorld(15,MatterMode.DETAILED,
            CosmicPreset.DEEP_SPACE,850)
        val free=850-world.sand.grains.size
        val added=world.addOrbitalParticleBatch(2000)
        assertEquals(free.coerceAtLeast(0),added)
        val finalMass=world.sand.totals().mass
        assertEquals(0,world.addOrbitalParticleBatch(2000))
        assertEquals(finalMass,world.sand.totals().mass,1e-8)
    }
}
