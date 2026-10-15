package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Live engine tests; old GranularUniverse is no longer used by the launcher. */
class AstronomicalUniverseTest {
    @Test fun denseRockyBodyStaysBoundAfterLongQuietIntegration() {
        val universe = AstronomicalUniverse()
        val initial = CelestialBody(40, 2, 2f, 1f, .49f, .51f,
            0f, 0f, material = MatterMaterial.ROCK,
            stellarClass = StellarClass.PLANET)
        universe.seed(initial, 350)
        val mass = universe.totals().mass
        val initialIds = universe.grains.map { it.id }.toSet()
        val firstCenter = universe.summarizeBodies(listOf(initial)).single()
        val initialRms = rmsRadius(universe.grains, firstCenter)
        repeat(240) { universe.step(1f/60f, 1f) }
        val after = universe.summarizeBodies(listOf(initial)).single()
        val rms = rmsRadius(universe.grains, after)
        println("AXIOM new bound core: RMS initial " + initialRms +
            ", after 240 steps " + rms)
        assertTrue("Planet should stay compact, not scatter into space: " + rms,
            rms < maxOf(.050, initialRms * 2.5))
        assertEquals(mass, universe.totals().mass, 1e-5)
        assertEquals(initialIds, universe.grains.map { it.id }.toSet())
        assertTrue(universe.grains.all {
            it.x.isFinite() && it.y.isFinite() &&
                it.vx.isFinite() && it.vy.isFinite() && it.heat >= 0
        })
    }

    private fun rmsRadius(grains: List<SandGrain>, c: CelestialBody): Double {
        val mass = grains.sumOf { it.mass.toDouble() }
        return sqrt(grains.sumOf { p ->
            val dx = p.x - c.x; val dy = p.y - c.y
            (dx*dx + dy*dy) * p.mass.toDouble()
        } / mass)
    }

    @Test fun realMonopolesPreserveOrbitalMomentumWithoutExtraHiddenMass() {
        val u = AstronomicalUniverse()
        val big = CelestialBody(1,12,12f,1f,.50f,.50f,0f,0f,
            material=MatterMaterial.HYDROGEN,
            stellarClass=StellarClass.STAR)
        val small = CelestialBody(2,2,.8f,1f,.73f,.50f,0f,.049f,
            material=MatterMaterial.IRON)
        u.seed(big, 300)
        u.seed(small, 150)
        val before = u.totals()
        val startRadius = .23f
        repeat(300) { u.step(1f/60f,1f) }
        val m = u.summarizeBodies(listOf(big,small))
        val distance = hypot(m[0].x-m[1].x,m[0].y-m[1].y)
        println("AXIOM orbit stability: start " + startRadius +
            ", 300 steps " + distance)
        assertTrue("Orbit must stay bounded", distance in .12f.. .40f)
        assertEquals(before.mass,u.totals().mass, 1e-5)
        assertEquals(before.momentumX,u.totals().momentumX, .0006)
        assertEquals(before.momentumY,u.totals().momentumY, .0006)
    }

    @Test fun newSolverStillProducesPhysicalCollisionsAndHeat() {
        val u = AstronomicalUniverse()
        u.grains.add(SandGrain(1,.4988f,.5f,.15f,0f,
            .01f,.00002,MatterMaterial.ROCK,7))
        u.grains.add(SandGrain(2,.5012f,.5f,-.15f,0f,
            .01f,.00002,MatterMaterial.ROCK,7))
        val before = u.totals()
        repeat(20) { u.step(1f/120f, 0f) }
        val after = u.totals()
        assertTrue(after.internal > before.internal)
        assertEquals(before.mass,after.mass,1e-8)
        assertEquals(before.momentumX,after.momentumX, 2e-6)
    }

    @Test fun stress4600UsesFarFewerMonopoleQueriesThanPairwiseNbody() {
        val u = AstronomicalUniverse()
        u.updateMode(MatterMode.DETAILED)
        repeat(20) { k ->
            val angle = 2*Math.PI*k/20
            u.seed(CelestialBody(k+11,2,2.5f,1f,
                (.5+.31*kotlin.math.cos(angle)).toFloat(),
                (.5+.31*kotlin.math.sin(angle)).toFloat(),
                0f,0f),230)
        }
        assertEquals(4600,u.grains.size)
        val before = u.totals()
        repeat(2) { u.step(1f/30f,1f) }
        val start = System.nanoTime()
        repeat(12) { u.step(1f/30f,1f) }
        val spent = (System.nanoTime()-start)/1e6
        println("AXIOM 0.21 hierarchical stress 4600 grains: " +
            spent/12 + " ms/physics step; gravity " + u.lastGravityMs +
            "ms; contacts " + u.lastContactMs + "ms; " +
            "monopole visits " + u.counters.second)
        assertEquals(before.mass,u.totals().mass,1e-4)
        assertTrue(u.counters.second < 4600L * 25)
        assertTrue(u.grains.all { it.x.isFinite() && it.y.isFinite() &&
            it.vx.isFinite() && it.vy.isFinite() })
        assertTrue("No catastrophic 4600-grain freeze",spent < 25000)
    }

    @Test fun restoredSnapshotIsCompleteAndCanContinueOrbit() {
        val u = AstronomicalUniverse()
        u.seed(CelestialBody(23,2,1f,1f,.5f,.5f,.01f,.02f),75)
        val frame = u.snapshot()
        val before = u.totals()
        repeat(12) { u.step(1f/60f,1f) }
        u.restore(frame)
        assertEquals(before.mass,u.totals().mass,1e-7)
        assertEquals(before.kinetic,u.totals().kinetic,1e-7)
        u.step(1f/60f,1f)
        assertEquals(75,u.grains.size)
    }

    @Test fun quietCosmicWorldUsesNewEngineNotLegacySolver() {
        val world = CosmicWorld(115, MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM)
        assertTrue(world.sand is AstronomicalUniverse)
        val first = world.sand.totals()
        repeat(20) { world.step(1f/60f) }
        assertEquals(first.mass, world.sand.totals().mass,1e-5)
        assertTrue(abs(world.physicsCostMs) < 30000)
    }
}
