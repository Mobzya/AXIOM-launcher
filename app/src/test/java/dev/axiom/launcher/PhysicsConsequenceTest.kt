package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsConsequenceTest {
    @Test fun significantEventsLeaveTemporaryRemnants() {
        val world = CosmicWorld(12)
        world.trigger(CosmicEventKind.NOVA)
        repeat(450) { world.step(1f / 30f) }
        assertEquals(CosmicEventKind.QUIET, world.episode.kind)
        assertTrue(world.remnants.any { it.kind == CosmicEventKind.NOVA })
    }

    @Test fun mathematicalLessonCanBePinnedAndAdvancedByTouch() {
        val world = CosmicWorld(19)
        world.summonMath(MathKind.GRADIENT)
        val moment = world.mathMoment
        world.interact(moment.x + .03f, moment.y + .04f)
        assertTrue(world.mathPinned)
        assertTrue(world.mathMoment.age > moment.age)
        val heldAt = world.mathMoment.age
        repeat(20) { world.step(1f / 60f) }
        assertEquals(heldAt, world.mathMoment.age)
    }

    @Test fun motionSnapshotsSupportInterpolation() {
        val world = CosmicWorld(7)
        val before = world.bodies
        world.step(1f / 60f)
        assertEquals(before, world.previousBodies)
        assertTrue(world.bodies.isNotEmpty())
    }

    @Test fun repeatedStepNeverProducesNonFiniteBodies() {
        val world = CosmicWorld(27)
        world.trigger(CosmicEventKind.SINGULARITY)
        repeat(1100) { world.step(1f / 60f) }
        assertTrue(world.bodies.all {
            it.x.isFinite() && it.y.isFinite() &&
            it.vx.isFinite() && it.vy.isFinite() && it.mass > 0f
        })
    }
}
