package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandsTest {
    @Test fun safeCommandsParsed() {
        assertTrue(parseAxCommand("help") is AxCommand.Output)
        assertTrue(parseAxCommand("spawn") is AxCommand.Spawn)
        assertTrue(parseAxCommand("> ls") is AxCommand.Apps)
        assertEquals(.8f, (parseAxCommand("gravity 0.8") as AxCommand.Gravity).value)
        assertTrue(parseAxCommand("gravity 100") is AxCommand.Output)
        assertTrue(parseAxCommand("sudo reboot") is AxCommand.Output)
    }
    @Test fun cinematicEventCommands() {
        assertEquals(CosmicEventKind.SINGULARITY,
            (parseAxCommand("event blackhole") as AxCommand.Event).kind)
        assertEquals(MathKind.BACKPROP,
            (parseAxCommand("event neural") as AxCommand.Model).kind)
        assertEquals(CosmicEventKind.NOVA,
            (parseAxCommand("event nova") as AxCommand.Event).kind)
        assertEquals(MathKind.ATTENTION,
            (parseAxCommand("model attention") as AxCommand.Model).kind)
        assertTrue(parseAxCommand("models") is AxCommand.Output)
        assertTrue(parseAxCommand("timeline") is AxCommand.Timeline)
        assertTrue(parseAxCommand("event unknown") is AxCommand.Output)
        assertEquals(.5f, (parseAxCommand("depth 0.5") as AxCommand.Depth).delta)
    }

    @Test fun arithmeticSupportsParenthesesAndPower() {
        assertEquals(14.0, arithmetic("2 * (3+4)"), 1e-9)
        assertEquals(64.0, arithmetic("2^6"), 1e-9)
        assertEquals(-5.0, arithmetic("-10 / 2"), 1e-9)
    }
    @Test fun arithmeticBlocksUnexpectedInput() {
        assertTrue(runCatching { arithmetic("Runtime.getRuntime()") }.isFailure)
        assertTrue(runCatching { arithmetic("1/0") }.isFailure)
    }
}

class UniverseTest {
    @Test fun physicsBodiesStayBounded() {
        val world = CosmicWorld(7)
        world.updateGravity(1.5f)
        repeat(300) { world.step(1f / 60f) }
        assertTrue(world.bodies.size in 1..21)
        assertTrue(world.bodies.all { it.x in -.24f..1.24f && it.y in -.24f..1.24f })
    }
    @Test fun mathCatalogHasSixteenThreeStageStories() {
        assertEquals(30, MathKind.entries.size)
        assertTrue(MathKind.entries.all { mathSteps(it).size == 3 })
    }
    @Test fun foregroundGravityMakesMeasurableDifference() {
        val weak = CosmicWorld(21)
        val strong = CosmicWorld(21)
        weak.updateGravity(.25f)
        strong.updateGravity(2.2f)
        repeat(400) {
            weak.step(1f / 60f)
            strong.step(1f / 60f)
        }
        val dx = kotlin.math.abs(
            weak.bodies.first().x - strong.bodies.first().x)
        val dy = kotlin.math.abs(
            weak.bodies.first().y - strong.bodies.first().y)
        assertTrue(dx + dy > .0001f)
    }
    @Test fun mathematicsAppearsIndependentlyOfCinematicEvents() {
        val world = CosmicWorld(7)
        repeat(900) { world.step(1f / 60f) }
        assertTrue(world.mathMoment.active)
        assertEquals(CosmicEventKind.QUIET, world.episode.kind)
    }
    @Test fun bodiesArriveFromCorners() {
        val world = CosmicWorld(19)
        val prior = world.bodies.size
        world.spawnFromCorner()
        assertEquals(prior + 1, world.bodies.size)
        val incoming = world.bodies.last()
        assertTrue(incoming.x < 0f || incoming.x > 1f ||
            incoming.y < 0f || incoming.y > 1f)
    }
    @Test fun rareEpisodeCanBeTriggeredAndExpires() {
        val world = CosmicWorld(19)
        world.trigger(CosmicEventKind.SINGULARITY)
        assertEquals(CosmicEventKind.SINGULARITY, world.episode.kind)
        repeat(600) { world.step(1f / 30f) }
        assertTrue(world.episode.kind != CosmicEventKind.SINGULARITY)
    }

    @Test fun moreAmbientBodiesStayWithinBound() {
        val world = CosmicWorld(5)
        repeat(80) { world.spawnFromCorner() }
        assertEquals(21, world.bodies.size)
    }

    @Test fun mathPinExpiresAutomatically() {
        val world = CosmicWorld(8)
        world.summonMath(MathKind.ATTENTION)
        val math = world.mathMoment
        world.interact(math.x+.05f,math.y+.05f)
        assertTrue(world.mathPinned)
        repeat(480) { world.step(1f/30f) }
        assertTrue(!world.mathPinned)
    }

    @Test fun tapChangesMomentum() {
        val world = CosmicWorld(17)
        val before = world.bodies.map { it.vx }
        world.disturb(.4f, .35f)
        val after = world.bodies.take(before.size).map { it.vx }
        assertTrue(before != after)
    }
}
