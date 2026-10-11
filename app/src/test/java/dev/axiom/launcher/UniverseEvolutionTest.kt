package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UniverseEvolutionTest {
    private fun body(
        id: Int = 1, mass: Float = 3f,
        x: Float = .5f,y: Float = .5f,
        vx: Float = .08f,vy: Float = -.04f
    ) = CelestialBody(id,2,mass,1f,x,y,vx,vy,age=4f)

    @Test fun shardsConserveRealMassAndBothComponentsOfMomentum() {
        val initial=body()
        for(count in 2..4){
            val after=splitAsteroid(initial,500,count)
            assertEquals(count,after.pieces.size)
            assertEquals(initial.mass,after.totalMass,1e-5f)
            assertEquals(initial.mass*initial.vx,after.momentumX,1e-5f)
            assertEquals(initial.mass*initial.vy,after.momentumY,1e-5f)
            assertTrue(after.pieces.all{it.mass>0f&&it.sizeFactor>0f})
            assertEquals(after.pieces.size,after.pieces.distinctBy{it.id}.size)
        }
    }

    @Test fun lightweightDustMovesWithoutChangingRealMass() {
        val one=ImpactDust(.4f,.5f,.10f,-.08f)
        val evolved=driftDust(one,.1f)
        assertTrue(evolved.x>one.x && evolved.y<one.y)
        assertTrue(evolved.age>one.age)
    }

    @Test fun massGivesCorrectNonConstantContactSize() {
        val small=body(mass=.2f)
        val large=body(mass=5f)
        assertTrue(contactSize(large)>contactSize(small))
        assertTrue(contactSize(large)<.035f)
    }

    @Test fun wallReflectionIsInelasticAndIncomingSpritesStayIncoming() {
        val hit=body(x=-.04f,vx=-.15f,vy=.03f)
        val bounced=reflectAtWalls(hit)
        assertTrue(bounced.vx>0f)
        assertTrue(bounced.vx<abs(hit.vx))
        assertTrue(bounced.x>=0f)
        val arriving=body(x=-.07f,vx=.18f)
        val stillEntering=reflectAtWalls(arriving)
        assertEquals(arriving.vx,stillEntering.vx,0f)
        assertEquals(arriving.x,stillEntering.x,0f)
    }

    @Test fun canAdjustCapacityTo64WithoutExceedingIt() {
        val world=CosmicWorld(29)
        world.updateMaximumBodies(64)
        world.updateWalls(true)
        assertTrue(world.wallsEnabled)
        repeat(95){world.spawnFromCorner()}
        assertEquals(64,world.bodies.size)
        assertTrue(world.bodies.all{it.kind in 1..104})
        world.updateMaximumBodies(12)
        assertEquals(12,world.bodies.size)
        world.updateMaximumBodies(100)
        assertEquals(64,world.maxBodies)
    }

    @Test fun scheduleSupportsMidnightCrossingAndOverrides() {
        val overnight=ThemeSchedule(22*60+15,5*60+30)
        assertTrue(daylightAtMinutes(23*60,overnight))
        assertTrue(daylightAtMinutes(3*60,overnight))
        assertFalse(daylightAtMinutes(13*60,overnight))
        assertTrue(daylightAtMinutes(8*60,ThemeSchedule(7*60,19*60)))
        assertFalse(daylightAtMinutes(19*60,ThemeSchedule()))
        assertTrue(resolvedLight(AxiomThemeMode.AUTO,23,overnight))
        assertFalse(resolvedLight(AxiomThemeMode.DARK,23,overnight))
        assertEquals("07:15",scheduleLabel(435))
    }

    @Test fun maximumBrightnessIfBothAutoTimesCoincide() {
        assertTrue(daylightAtMinutes(900,ThemeSchedule(360,360)))
        assertTrue(daylightAtMinutes(120,ThemeSchedule(360,360)))
    }

    @Test fun eachRareSlotIsExplicitAndExceptional() {
        assertEquals(CosmicEventKind.ECLIPSE,rareCosmicEvent(0))
        assertEquals(CosmicEventKind.MEGASTRUCTURE,rareCosmicEvent(4096))
        assertEquals(CosmicEventKind.GALAXY_COLLISION,rareCosmicEvent(8192))
        assertEquals(CosmicEventKind.VOID_BLOOM,rareCosmicEvent(12288))
        assertEquals(CosmicEventKind.GRAVITATIONAL_CAUSTIC,rareCosmicEvent(16383))
        assertEquals(null,rareCosmicEvent(42))
        assertEquals(7,(0 until 16384).count{rareCosmicEvent(it)!=null})
    }

    @Test fun allCinematicModesAreStableAndFinite() {
        val world=CosmicWorld(33)
        world.updateMaximumBodies(42)
        world.updateWalls(true)
        val modes=listOf(
            CosmicEventKind.ECHO_SHELL,CosmicEventKind.TIDAL_CAPTURE,
            CosmicEventKind.POLAR_JET,CosmicEventKind.ORBITAL_RESONANCE,
            CosmicEventKind.STELLAR_NURSERY,CosmicEventKind.DARK_FLOW,
            CosmicEventKind.GEOMETRY_SHIFT,CosmicEventKind.SOLAR_FLARE,
            CosmicEventKind.DUAL_RING,CosmicEventKind.PARTICLE_CASCADE,
            CosmicEventKind.MEGASTRUCTURE,CosmicEventKind.GALAXY_COLLISION,
            CosmicEventKind.VOID_BLOOM,
            CosmicEventKind.GRAVITATIONAL_CAUSTIC
        )
        for(kind in modes){
            world.trigger(kind)
            repeat(70){world.step(1f/60f)}
            assertTrue(world.bodies.size<=world.maxBodies)
            assertTrue(world.bodies.all {
                it.mass.isFinite()&&it.mass>0f&&
                it.x.isFinite()&&it.y.isFinite()&&
                it.vx.isFinite()&&it.vy.isFinite()
            })
        }
    }
}
