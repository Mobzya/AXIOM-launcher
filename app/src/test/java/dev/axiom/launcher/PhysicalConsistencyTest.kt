package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhysicalConsistencyTest {
    private fun body(
        id: Int, x: Float, mass: Float, vx: Float = 0f, depth: Float = 1f
    ) = CelestialBody(id, 2, mass, depth, x, .5f, vx, 0f)

    @Test fun softenedAttractionObeysActionReactionForDifferentMasses() {
        val a = body(1, .25f, 2f)
        val b = body(2, .75f, 5f)
        val force = mutualGravity(a, b, 1f)
        assertTrue(force.first.x > 0f && force.second.x < 0f)
        assertEquals(0f, a.mass * force.first.x + b.mass * force.second.x, 1e-5f)
        assertEquals(0f, a.mass * force.first.y + b.mass * force.second.y, 1e-6f)
    }

    @Test fun closeRangePressurePreservesMomentum() {
        val a = body(1, .5f, .65f)
        val b = body(2, .515f, 2.5f)
        val force = contactRepulsion(a, b)
        assertTrue(force.first.x < 0f && force.second.x > 0f)
        assertEquals(0f, a.mass * force.first.x + b.mass * force.second.x, 1e-6f)
    }

    @Test fun allocationFreeIntegratorPreservesPairMomentum() {
        val a = body(1, .49f, .7f)
        val b = body(2, .51f, 2.4f)
        val ax = FloatArray(2)
        val ay = FloatArray(2)
        accumulateForces(a, b, 1.3f, 0, 1, ax, ay)
        assertEquals(0f, a.mass * ax[0] + b.mass * ax[1], 1e-5f)
        assertEquals(0f, a.mass * ay[0] + b.mass * ay[1], 1e-6f)
        assertTrue(ax[0].isFinite() && ax[1].isFinite())
    }

    @Test fun collisionConservesLinearMomentumAndDoesNotGenerateEnergy() {
        val a = body(1, .49f, 2f, .12f)
        val b = body(2, .51f, 3f, -.08f)
        val result = collide(a, b)
        val beforeP = a.mass * a.vx + b.mass * b.vx
        val afterP = a.mass * result.avx + b.mass * result.bvx
        assertEquals(beforeP, afterP, 1e-5f)
        val initialEnergy = .5f * (a.mass * a.vx * a.vx + b.mass * b.vx * b.vx)
        val afterEnergy = .5f * (a.mass * result.avx * result.avx +
            b.mass * result.bvx * result.bvx)
        assertTrue(afterEnergy <= initialEnergy + 1e-6f)
        assertTrue(result.avx < a.vx)
    }

    @Test fun separatingBodiesAreNotImpulsivelyAttracted() {
        val a = body(1, .49f, 1f, -.1f)
        val b = body(2, .51f, 1f, .1f)
        val result = collide(a, b)
        assertEquals(a.vx, result.avx, 0f)
        assertEquals(b.vx, result.bvx, 0f)
    }

    @Test fun noSimultaneousCinematicEventAndMathematicalLesson() {
        val world = CosmicWorld(30)
        world.summonMath(MathKind.BACKPROP)
        assertTrue(world.mathMoment.active)
        world.trigger(CosmicEventKind.NOVA)
        assertFalse(world.mathMoment.active)
        assertEquals(CosmicEventKind.NOVA, world.episode.kind)

        world.summonMath(MathKind.FOURIER)
        assertTrue(world.mathMoment.active)
        assertEquals(CosmicEventKind.QUIET, world.episode.kind)
        repeat(2700) {
            world.step(1f / 60f)
            assertFalse(world.mathMoment.active &&
                world.episode.kind != CosmicEventKind.QUIET)
        }
    }

    @Test fun mathsStaysInReservedNonOverlappingScreenZones() {
        val world = CosmicWorld(56)
        repeat(100) {
            world.summonMath()
            val m = world.mathMoment
            assertTrue(m.x == .08f || m.x == .51f)
            assertTrue(m.y == .15f || m.y == .65f)
            assertTrue(m.x + .43f < .96f)
            assertTrue(m.y + .30f < .96f)
        }
    }

    @Test fun matrixLessonMatchesItsArithmetic() {
        val steps = mathSteps(MathKind.MATRIX)
        assertTrue(steps.first().contains("2; 4"))
        assertEquals("1·2 + 3·4", steps[1])
        assertEquals("14", steps[2])
    }

    @Test fun longRunningUniverseRemainsFiniteAndDensityBounded() {
        val world = CosmicWorld(22)
        world.updateGravity(2.2f)
        world.trigger(CosmicEventKind.SINGULARITY)
        repeat(3000) { world.step(1f / 60f) }
        assertTrue(world.bodies.size <= 23)
        assertTrue(world.bodies.all {
            it.mass.isFinite() && it.mass > 0f &&
            it.vx.isFinite() && it.vy.isFinite() &&
            it.x.isFinite() && it.y.isFinite()
        })
    }
}
