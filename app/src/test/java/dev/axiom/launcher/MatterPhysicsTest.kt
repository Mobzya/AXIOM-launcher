package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MatterPhysicsTest {
    private fun body(
        id: Int, mass: Float, vx: Float, material: MatterMaterial
    ) = CelestialBody(id, 2, mass, 1f,
        .48f + id * .002f, .5f, vx, .012f,
        material = material, temperature = 320f)

    @Test fun collisionEjectaConserveMassAndMomentum() {
        for (mode in MatterMode.entries) {
            val field = MatterField(Random(17))
            field.setMode(mode)
            val a = body(1, 2f, .14f, MatterMaterial.ROCK)
            val b = body(2, 1.6f, -.09f, MatterMaterial.HYDROGEN)
            val after = field.emitImpact(a, b, .23f)
            assertTrue(field.particles.size >= 50)
            assertTrue(field.particles.size <= mode.maxParticles)
            val mass = after.first.mass + after.second.mass +
                field.totalMass().toFloat()
            assertEquals(a.mass + b.mass, mass, 1e-4f)
            val px = after.first.mass * after.first.vx +
                after.second.mass * after.second.vx +
                field.particles.sumOf { (it.mass * it.vx).toDouble() }.toFloat()
            val py = after.first.mass * after.first.vy +
                after.second.mass * after.second.vy +
                field.particles.sumOf { (it.mass * it.vy).toDouble() }.toFloat()
            assertEquals(a.mass * a.vx + b.mass * b.vx, px, 2e-4f)
            assertEquals(a.mass * a.vy + b.mass * b.vy, py, 2e-4f)
            assertTrue(field.particles.map { it.material }.distinct().size > 1)
        }
    }

    @Test fun elementsReactAndChangePhase() {
        assertEquals(MatterMaterial.STEAM,
            reactionFor(MatterMaterial.OXYGEN, MatterMaterial.HYDROGEN, 300f)?.product)
        assertEquals(MatterMaterial.OXIDE,
            reactionFor(MatterMaterial.OXYGEN, MatterMaterial.IRON, 600f)?.product)
        assertEquals(null, reactionFor(MatterMaterial.METHANE, MatterMaterial.OXYGEN, 300f))
        assertEquals(MatterMaterial.WATER,
            equilibrateMaterial(MatterMaterial.ICE, 310f))
        assertEquals(MatterMaterial.ICE,
            equilibrateMaterial(MatterMaterial.WATER, 220f))
        assertEquals(MatterMaterial.PLASMA,
            equilibrateMaterial(MatterMaterial.HYDROGEN, 4900f))
    }

    @Test fun packetsReactInRealStepWithoutMassLoss() {
        val field = MatterField(Random(3))
        field.particles.add(MatterParticle(1, .4f, .5f, 0f, 0f,
            .02f, 300f, MatterMaterial.HYDROGEN))
        field.particles.add(MatterParticle(2, .4004f, .5f, 0f, 0f,
            .03f, 300f, MatterMaterial.OXYGEN))
        val before = field.totalMass()
        field.step(1f / 60f, 1f, ArrayList(), 12, { 100 }, false)
        assertEquals(before, field.totalMass(), 1e-6)
        assertEquals(1, field.reactions)
        assertEquals(1, field.particles.size)
        assertEquals(MatterMaterial.STEAM, field.particles.single().material)
    }

    @Test fun aggregationCreatesAPlanetWithTheOriginalMassAndMomentum() {
        val field = MatterField(Random(11))
        field.particles.add(MatterParticle(1, .40f, .43f, .01f, 0f,
            .07f, 300f, MatterMaterial.ROCK, age=2f))
        field.particles.add(MatterParticle(2, .4002f, .43f, .01f, 0f,
            .08f, 300f, MatterMaterial.IRON, age=2f))
        val bodies = ArrayList<CelestialBody>()
        field.step(1f / 60f, 1f, bodies, 16, { 100 }, false)
        assertEquals(1, field.newPlanets)
        assertEquals(1, bodies.size)
        assertEquals(.15f, bodies.single().mass, 1e-5f)
        assertEquals(.01f, bodies.single().vx, 1e-4f)
        assertTrue(field.particles.isEmpty())
    }

    @Test fun seededDiscAndAccretionNeverInventMaterial() {
        val field = MatterField(Random(13))
        val source = body(1, 1.8f, .02f, MatterMaterial.ICE)
        val remaining = field.seedOrbit(source, 32)
        assertEquals(source.mass, remaining.mass + field.totalMass().toFloat(), 1e-5f)
        assertEquals(32, field.particles.size)
        val bodies = arrayListOf(remaining)
        repeat(120) {
            field.step(1f / 60f, 1f, bodies, 12, { 99 }, false)
        }
        assertTrue(field.particles.size <= MatterMode.BALANCED.maxParticles)
        assertTrue(bodies.all { it.mass.isFinite() && it.mass > 0f })
        assertTrue(field.particles.all {
            it.x.isFinite() && it.vx.isFinite() && it.mass > 0f
        })
    }

    @Test fun qualitySelectionIsSafeForUnknownBackupValues() {
        assertEquals(MatterMode.BALANCED, matterModeFromPreference("INVALID"))
        assertEquals(MatterMode.DETAILED, matterModeFromPreference("DETAILED"))
        assertTrue(abs(MatterMode.DETAILED.maxParticles -
            MatterMode.BALANCED.maxParticles) >= 3000)
    }
}
