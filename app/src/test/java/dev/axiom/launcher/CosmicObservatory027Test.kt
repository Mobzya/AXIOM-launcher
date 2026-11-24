package dev.axiom.launcher

import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class CosmicObservatory027Test {
    @Test fun survivingGrainsKeepTheirPreviousCoordinatesAfterCaptureAndSpawn() {
        assertArrayEquals(intArrayOf(0, 8, -1),
            grainOffsetsById(intArrayOf(4, 6, 8, 9), intArrayOf(4, 8, 12)))
        val world = CosmicWorld(initialPreset = CosmicPreset.COUNTER_ROTATING_RINGS)
        world.createObject(MatterMaterial.ROCK, .7f)
        val ids = world.sand.grains.map { it.id }
        assertTrue(ids.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun blastCrossesNearerGrainsFirstAndBookkeepsExternalEnergy() {
        val universe = AstronomicalUniverse()
        val near = SandGrain(1, .60f, .50f, 0f, 0f, .01f, .000003, MatterMaterial.ROCK)
        val far = SandGrain(2, .5f, .72f, 0f, 0f, .02f, .000006, MatterMaterial.ROCK)
        universe.grains.addAll(listOf(near, far))
        val initial = universe.totals()
        val event = CosmicEpisode(CosmicEventKind.BLAST_SHELL, duration = 7f, y = .5f, serial = 20)
        for (frame in 1..72) universe.step(1f / 60, 0f, event.copy(age = frame / 60f))
        assertTrue(near.vx > 0f)
        assertEquals(0f, far.vy, 0f)
        for (frame in 73..360) universe.step(1f / 60, 0f, event.copy(age = frame / 60f))
        assertTrue(far.vy > 0f)
        val final = universe.totals()
        val input = universe.externalFrontEnergy
        assertEquals(initial.mass, final.mass, 0.0)
        assertEquals(final.kinetic + final.internal - initial.kinetic - initial.internal, input, 1e-9)
        val snapshot = universe.snapshot()
        universe.restore(snapshot)
        universe.step(1f / 60, 0f, event.copy(age = 6f))
        assertEquals(input, universe.externalFrontEnergy, 0.0)
    }

    @Test fun reverseFrontCompressesAndBipolarFrontMissesEquator() {
        fun measure(kind: CosmicEventKind): Pair<Float, Float> {
            val universe = AstronomicalUniverse()
            val polar = SandGrain(1, .66f, .50f, 0f, 0f, .01f, .000003, MatterMaterial.ROCK)
            val equator = SandGrain(2, .50f, .66f, 0f, 0f, .01f, .000003, MatterMaterial.ROCK)
            universe.grains.addAll(listOf(polar, equator))
            val duration = requireNotNull(kind.frontProfile).duration
            val event = CosmicEpisode(kind, duration = duration, y = .5f, serial = 4)
            for (frame in 1..(duration * 60).toInt()) {
                universe.step(1f / 60, 0f, event.copy(age = frame / 60f))
            }
            return polar.vx to equator.vy
        }
        val reverse = measure(CosmicEventKind.REVERSE_SHOCK)
        assertTrue(reverse.first < 0f && reverse.second < 0f)
        val outflow = measure(CosmicEventKind.BIPOLAR_OUTFLOW)
        assertTrue(outflow.first > 0f)
        assertEquals(0f, outflow.second, 0f)
    }

    @Test fun ringRecipeDistributesSelectedMassAndIceRecipeFormsSeparateLayers() {
        val world = CosmicWorld(initialPreset = CosmicPreset.DOUBLE_PLANET)
        val total = world.sand.totals().mass
        val giant = BodyRecipe.RING_GIANT
        assertTrue(world.createObject(giant.material, giant.mass, recipe = giant))
        val created = world.bodies.last()
        assertEquals(giant.mass.toDouble(), world.sand.totals().mass - total, 1e-5)
        assertEquals((giant.mass * (1f - giant.ringFraction)).toDouble(), created.mass.toDouble(), 1e-6)
        assertTrue(world.sand.grains.any { it.owner < 0 && it.material == MatterMaterial.ICE })

        val ocean = BodyRecipe.SUBGLACIAL
        assertTrue(world.createObject(ocean.material, ocean.mass, recipe = ocean))
        val moon = world.bodies.last()
        val grains = world.sand.grains.filter { it.owner == moon.id }
        fun meanRadius(material: MatterMaterial) = grains.filter { it.material == material }.map {
            val x = (it.x - moon.x).toDouble()
            val y = (it.y - moon.y).toDouble()
            sqrt(x*x + y*y)
        }.average()
        assertTrue(meanRadius(MatterMaterial.IRON) < meanRadius(MatterMaterial.WATER))
        assertTrue(meanRadius(MatterMaterial.WATER) < meanRadius(MatterMaterial.ICE))
    }

    @Test fun cadencePreservesExistingPreferencesAndOffersWiderRange() {
        assertEquals(EventCadence.NORMAL, eventCadenceFromPreference(null))
        assertEquals(EventCadence.OFTEN, eventCadenceFromPreference("OFTEN"))
        assertTrue(EventCadence.QUIET.wait(0f) > EventCadence.RARE.wait(1f))
        assertTrue(EventCadence.ACTIVE.wait(1f) < EventCadence.OFTEN.wait(0f))
    }
}
