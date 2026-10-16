package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservatoryReleaseTest {
    @Test fun observatorySpawnsGenuineMassWithoutOverwritingExistingBodies() {
        val world=CosmicWorld(87, MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM)
        val before=world.sand.totals()
        val ids=world.sand.grains.map{it.id}.toSet()
        val n=world.sand.grains.size
        val count=world.bodies.size
        assertTrue(world.createObject(MatterMaterial.ICE,2.5f))
        assertEquals(count+1,world.bodies.size)
        assertTrue(world.sand.grains.size>n)
        assertTrue(world.sand.totals().mass>before.mass)
        assertEquals(ids.size, world.sand.grains.take(n).map{it.id}.toSet().size)
        assertTrue(world.sand.grains.takeLast(world.sand.grains.size-n)
            .all{it.owner==world.bodies.last().id})
        assertEquals(MatterMaterial.ICE,world.bodies.last().material)
    }

    @Test fun observatoryDeclinesBodyWhenBudgetExhaustedWithoutCreatingMass() {
        val world=CosmicWorld(13,MatterMode.BALANCED,CosmicPreset.DEEP_SPACE)
        world.populateStressScene(4600)
        val before=world.sand.totals()
        assertTrue(!world.createObject(MatterMaterial.ROCK,7f))
        assertEquals(before.mass,world.sand.totals().mass,1e-7)
    }

    @Test fun eventFocusIsRealPhysicalStarNotRandomEmptyPoint() {
        val world=CosmicWorld(20,MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM)
        val star=world.bodies.first()
        for(kind in listOf(CosmicEventKind.SOLAR_WIND,
                CosmicEventKind.TIDAL_PULSE,CosmicEventKind.COSMIC_SHOCK,
                CosmicEventKind.IONIZATION_FRONT)) {
            world.trigger(kind,CosmicEventIntensity.MAJOR)
            assertEquals(star.x,world.episode.x,1e-6f)
            assertEquals(star.y,world.episode.y,1e-6f)
            assertEquals(CosmicEventIntensity.MAJOR,world.episode.intensity)
        }
    }

    @Test fun faintEventHasRealPhysicalImpactAndNoParticleFabrication() {
        val universe=AstronomicalUniverse()
        universe.grains.add(SandGrain(11,.497f,.5f,0f,0f,
            .01f,.0002,MatterMaterial.HYDROGEN))
        universe.grains.add(SandGrain(12,.503f,.5f,0f,0f,
            .01f,.0002,MatterMaterial.HYDROGEN))
        val before=universe.totals()
        val episode=CosmicEpisode(CosmicEventKind.SOLAR_WIND,
            age=.8f,duration=12f,x=.5f,y=.5f,serial=87,
            intensity=CosmicEventIntensity.FLICKER)
        universe.step(1f/60f,0f,episode)
        val after=universe.totals()
        assertEquals(before.mass,after.mass,1e-8)
        assertEquals(2,universe.grains.size)
        assertTrue(after.kinetic>before.kinetic)
        assertTrue(after.internal<before.internal)
    }
}
