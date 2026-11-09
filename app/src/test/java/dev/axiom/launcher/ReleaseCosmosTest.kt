package dev.axiom.launcher

import org.junit.Assert.*
import org.junit.Test

class ReleaseCosmosTest {
    @Test fun touchingHorizonsMergeAndFlareUsesOnlyExistingEnergy() {
        val u=AstronomicalUniverse()
        val a=CelestialBody(1,99,10f,1f,.48f,.5f,.02f,.01f,nature=CelestialNature.BLACK_HOLE)
        val b=CelestialBody(2,99,8f,1f,.503f,.5f,-.025f,-.0125f,nature=CelestialNature.BLACK_HOLE)
        u.seed(a);u.seed(b)
        u.grains.add(SandGrain(50,.6f,.5f,0f,.01f,.01f,.000001,MatterMaterial.IRON,3))
        val before=u.totals()
        val result=u.accrete(1,.013f,mapOf(2 to .012f))
        assertEquals(setOf(2),result.removedOwners)
        val diskHeat=u.grains.last().heat
        val released=u.releaseMergerFlare(1)
        assertTrue(released>0);assertTrue(u.grains.last().heat>diskHeat)
        val after=u.totals()
        assertEquals(before.mass,after.mass,1e-6)
        assertEquals(before.momentumX,after.momentumX,1e-7)
        assertEquals(before.momentumY,after.momentumY,1e-7)
        assertEquals(before.kinetic+before.internal+before.chemical,
            after.kinetic+after.internal+after.chemical+u.radiatedHeat,1e-8)
    }
    @Test fun mergerScenarioProducesOneRemnantAndOneBoundedWavefront() {
        val w=CosmicWorld(initialPreset=CosmicPreset.BLACK_HOLE_MERGER)
        w.configureObservation(events=false,maths=false)
        w.updateTimeScale(1f)
        val mass=w.sand.totals().mass
        repeat(480){w.step(1f/60f)}
        assertEquals(1,w.compactMergerCount)
        assertEquals(1,w.bodies.count {it.nature==CelestialNature.BLACK_HOLE})
        assertTrue(w.compactMergers.size<=1)
        assertEquals(mass,w.sand.totals().mass,1e-4)
        assertTrue(w.sand.grains.all {it.x.isFinite() && it.vx.isFinite() && it.heat.isFinite()})
    }
    @Test fun supermassiveCoreIsLargeWithoutInflatingParticleCount() {
        val w=CosmicWorld(initialPreset=CosmicPreset.GALACTIC_NUCLEUS)
        val hole=w.bodies.single {it.nature==CelestialNature.BLACK_HOLE}
        assertEquals(1800f,hole.mass,.001f)
        assertTrue(horizonRadius(hole.mass)>horizonRadius(14f)*5)
        assertEquals(1,w.sand.grains.count {it.owner==hole.id})
        assertTrue(w.sand.grains.size<1000)
        w.configureObservation(events=false,maths=false)
        repeat(90){w.step(1f/60f)}
        assertTrue(w.sand.grains.all {it.x.isFinite() && it.y.isFinite()})
        val giant=presetBodies(CosmicPreset.HYPERGIANT_SYSTEM).first()
        assertTrue(structureRadius(giant)>.1f)
    }
    @Test fun workshopPreservesSupermassiveRecipeMass() {
        val w=CosmicWorld(initialPreset=CosmicPreset.DEEP_SPACE)
        val before=w.sand.grains.size
        assertTrue(w.createObject(BodyRecipe.SUPERMASSIVE.material,1800f,.5f,.5f,
            LaunchStyle.REST,BodyRecipe.SUPERMASSIVE))
        val core=w.bodies.single {it.nature==CelestialNature.BLACK_HOLE}
        assertEquals(1800f,core.mass,.001f)
        assertEquals(before+1,w.sand.grains.size)
        assertEquals(1,w.sand.grains.count {it.owner==core.id})
    }
    @Test fun largeAccretionPreservesTheLedgerAndAllUncapturedPackets() {
        val u=AstronomicalUniverse()
        u.seed(CelestialBody(1,99,20f,1f,.5f,.5f,0f,0f,nature=CelestialNature.BLACK_HOLE))
        repeat(2000){i ->
            u.grains.add(SandGrain(i+100,if(i%2==0).51f else .7f,.5f,
                .01f,-.02f,.001f,.00001,MatterMaterial.CARBON,i+10))
        }
        val before=u.totals()
        val result=u.accrete(1,.03f)
        assertEquals(1001,u.grains.size)
        assertEquals(1000,result.removedOwners.size)
        assertEquals(u.grains.size,u.grains.map {it.id}.toSet().size)
        val after=u.totals()
        assertEquals(before.mass,after.mass,2e-6)
        assertEquals(before.momentumX,after.momentumX,1e-7)
        assertEquals(before.kinetic+before.internal+before.chemical,
            after.kinetic+after.internal+after.chemical+u.radiatedHeat,1e-7)
    }
    @Test fun delayedWorkerCannotAccumulateTwelveStepCatchupBursts() {
        val w=CosmicWorld(initialPreset=CosmicPreset.DEEP_SPACE)
        w.configureObservation(events=false,maths=false)
        w.updateTimeScale(2f)
        w.step(.1f,maxSubsteps=4,budgetNanos=0)
        assertEquals(1f/60f,w.elapsed,1e-7f)
        repeat(10){w.step(.1f,maxSubsteps=4,budgetNanos=0)}
        assertEquals(11f/60f,w.elapsed,1e-6f)
        val v=CosmicWorld(initialPreset=CosmicPreset.DEEP_SPACE)
        v.updateTimeScale(2f);v.configureObservation(events=false,maths=false)
        v.step(.1f,maxSubsteps=4)
        assertEquals(4f/60f,v.elapsed,1e-7f)
    }
}
