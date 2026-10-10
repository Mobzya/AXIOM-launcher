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
        world.setGravity(1.5f)
        repeat(300) { world.step(1f / 60f) }
        assertTrue(world.bodies.size in 1..11)
        assertTrue(world.bodies.all { it.x in .07f.. .93f && it.y in .09f.. .88f })
    }
    @Test fun tapChangesMomentum() {
        val world = CosmicWorld(17)
        val before = world.bodies.map { it.vx }
        world.disturb(.4f, .35f)
        val after = world.bodies.take(before.size).map { it.vx }
        assertTrue(before != after)
    }
}
