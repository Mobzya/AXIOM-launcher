package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Persistence codec remains JVM-testable; Android AtomicFile wraps it on device. */
class CosmicPersistenceTest {
    private fun encoded(world: CosmicWorld): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { zip ->
            DataOutputStream(zip).use { world.writePersistentState(it) }
        }
        return bytes.toByteArray()
    }
    private fun restore(world: CosmicWorld, bytes: ByteArray) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { zip ->
            DataInputStream(zip).use { world.readPersistentState(it) }
        }
    }

    @Test fun fullStateSurvivesProcessRestartWithExactPositionsMassAndHeat() {
        val source = CosmicWorld(515,MatterMode.DETAILED,
            CosmicPreset.SATURN_RINGS,20_000)
        val added=source.addOrbitalParticleBatch(2000)
        assertEquals(2000,added)
        source.updateGravity(1.35f)
        source.updateTimeScale(.5f)
        source.trigger(CosmicEventKind.MAGNETAR_FLARE,
            CosmicEventIntensity.MAJOR)
        repeat(8) { source.step(1f/30f) }
        val before=source.sand.totals()
        val grains=source.sand.grains.map { it.copy() }
        val time=source.elapsed
        val ids=source.bodies.map { it.id }
        val event=source.episode
        val data=encoded(source)
        assertTrue("Saved gzip should not allocate enormous in-memory PNG frames",
            data.size < source.sand.grains.size*120)
        val restored=CosmicWorld(345,MatterMode.BALANCED,
            CosmicPreset.DEEP_SPACE)
        restore(restored,data)
        assertEquals(source.preset,restored.preset)
        assertEquals(source.matterMode,restored.matterMode)
        assertEquals(20_000,restored.particleLimit)
        assertEquals(source.sand.grains.size,restored.sand.grains.size)
        assertEquals(ids,restored.bodies.map { it.id })
        assertEquals(time,restored.elapsed,0f)
        assertEquals(.5f,restored.timeScale,0f)
        assertEquals(1.35f,restored.gravity,0f)
        assertEquals(event,restored.episode)
        assertEquals(before.mass,restored.sand.totals().mass,1e-7)
        assertEquals(before.internal,restored.sand.totals().internal,1e-7)
        for (i in grains.indices) {
            val p=grains[i]; val q=restored.sand.grains[i]
            assertEquals(p.id,q.id)
            assertEquals(p.owner,q.owner)
            assertEquals(p.material,q.material)
            assertEquals(p.mass,q.mass,0f)
            assertEquals(p.heat,q.heat,0.0)
            assertEquals(p.x,q.x,0f)
            assertEquals(p.y,q.y,0f)
            assertEquals(p.vx,q.vx,0f)
            assertEquals(p.vy,q.vy,0f)
        }
    }

    @Test fun pausedStateRemainsPausedAfterReloadAndNoOfflineCatchUp() {
        val old=CosmicWorld(21,MatterMode.BALANCED,
            CosmicPreset.SOLAR_SYSTEM)
        old.updateTimeScale(0f)
        val grains=old.sand.grains.size
        val t=old.elapsed
        val fresh=CosmicWorld(22)
        restore(fresh,encoded(old))
        assertEquals(0f,fresh.timeScale,0f)
        repeat(100) { fresh.step(1f/30f) }
        assertEquals(t,fresh.elapsed,0f)
        assertEquals(grains,fresh.sand.grains.size)
    }

    @Test fun qualityAndLowerBudgetDoNotDeleteExistingMatter() {
        val world=CosmicWorld(19,MatterMode.DETAILED,
            CosmicPreset.SOLAR_SYSTEM,20_000)
        world.addOrbitalParticleBatch(2000)
        val before=world.sand.totals()
        val ids=world.sand.grains.map { it.id }
        world.updateParticleLimit(100)
        world.updateMatterMode(MatterMode.BALANCED)
        assertEquals(100,world.particleLimit)
        assertEquals(ids,world.sand.grains.map { it.id })
        assertEquals(before.mass,world.sand.totals().mass,1e-6)
        assertEquals(0,world.addOrbitalParticleBatch(2000))
        assertEquals(before.mass,world.sand.totals().mass,1e-6)
    }

    @Test fun truncatedCheckpointNeverMutatesExistingWorld() {
        val original=CosmicWorld(6,MatterMode.BALANCED,
            CosmicPreset.SOLAR_SYSTEM)
        val data=encoded(original)
        val corrupted=data.copyOf(data.size / 2)
        val target=CosmicWorld(7,MatterMode.BALANCED,
            CosmicPreset.TITAN_SYSTEM)
        val before=target.sand.totals()
        val preset=target.preset
        var failed=false
        try { restore(target,corrupted) }
        catch (_:Exception) { failed=true }
        assertTrue(failed)
        assertEquals(preset,target.preset)
        assertEquals(before.mass,target.sand.totals().mass,1e-7)
    }

    @Test fun systemLockOnlyPausesPhysicalClockAndDoesNotResetWorld() {
        val world=CosmicWorld(19,MatterMode.DETAILED,
            CosmicPreset.BINARY_STARS)
        val before=world.sand.grains.map { it.id }
        world.suspendForBackground()
        repeat(60) { world.step(1f/30f) }
        assertEquals(0f,world.elapsed,0f)
        assertEquals(before,world.sand.grains.map { it.id })
        world.resumeFromBackground()
        world.step(1f/30f)
        assertTrue(world.elapsed > 0f)
        assertEquals(before,world.sand.grains.map { it.id })
    }
}
